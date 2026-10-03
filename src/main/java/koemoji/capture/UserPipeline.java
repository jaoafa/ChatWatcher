package koemoji.capture;

import koemoji.Config;
import koemoji.queue.Db;

import com.k2fsa.sherpa.onnx.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Per-user audio pipeline: 48 kHz stereo big-endian PCM -> 16 kHz mono -> Silero VAD -> utterance file
 * (append-only s16le) -> partial/final jobs in the queue. All methods are synchronized: audio arrives on a JDA
 * thread, tick() on the scheduler thread.
 */
public final class UserPipeline implements Closeable {
  public static final int WIN = 512;           // Silero window at 16 kHz = 32 ms
  private static final int WIN_MS = 32;
  private static final int GAP_MS = 200;

  private final Config c;
  private final Db db;
  private final Consumer<String> onUtterance;  // called with utteranceId right before its first job is enqueued
  private final VadDetector vad;
  private final float[] win = new float[WIN];
  private int winFill;
  private final ArrayDeque<short[]> preroll = new ArrayDeque<>();
  private final short[] cur = new short[WIN];

  private static final int DECIMATE = 3;       // 48 kHz -> 16 kHz
  private int resampleSum, resampleCount;

  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(UserPipeline.class);
  private final String name;
  private float maxProb;
  private int winCount;
  private int speechRun, silenceRun;
  private String utt;
  private Path path;
  private OutputStream out;
  private long bytes, snapBytes;
  private int rev;
  private long lastPartialMs, lastAudioMs;
  private boolean closed;

  public UserPipeline(Config c, Db db, String name, Consumer<String> onUtterance) {
    this(c, db, name, onUtterance, silero(c));
  }

  UserPipeline(Config c, Db db, String name, Consumer<String> onUtterance, VadDetector vad) {
    this.name = name; this.c = c; this.db = db; this.onUtterance = onUtterance;
    this.vad = vad;
  }

  private static VadDetector silero(Config c) {
    var silero = SileroVadModelConfig.builder().setModel(c.vadModel().toString()).setThreshold(c.vadThreshold())
        .setWindowSize(WIN).build();
    var model = new Vad(VadModelConfig.builder().setSileroVadModelConfig(silero).setSampleRate(16000).setNumThreads(1).setDebug(false).build());
    return new VadDetector() {
      @Override public float compute(float[] samples) { return model.compute(samples); }
      @Override public void reset() { model.reset(); }
      @Override public void release() { model.release(); }
    };
  }

  /** @param pcm big-endian 16-bit stereo 48 kHz (JDA output format) */
  public synchronized void accept(byte[] pcm) {
    if (closed) return;  // the VAD is native and released by close(); a late packet must not touch it
    long now = System.currentTimeMillis();
    if (now - lastAudioMs > GAP_MS) { winFill = 0; resampleSum = 0; resampleCount = 0; }  // Discord sends nothing while silent
    lastAudioMs = now;
    ByteBuffer b = ByteBuffer.wrap(pcm).order(ByteOrder.BIG_ENDIAN);
    while (b.remaining() >= 4) {
      resampleSum += (b.getShort() + b.getShort()) / 2;
      if (++resampleCount == DECIMATE) {
        short s = (short) (resampleSum / DECIMATE);
        resampleSum = 0; resampleCount = 0;
        win[winFill] = s / 32768f; cur[winFill] = s;
        if (++winFill == WIN) { winFill = 0; window(); }
      }
    }
  }

  private void window() {
    float prob = vad.compute(win);
    maxProb = Math.max(maxProb, prob);
    if (++winCount % 150 == 0) { log.info("vad {}: maxProb={} (threshold {})", name, maxProb, c.vadThreshold()); maxProb = 0; }
    boolean speech = prob >= c.vadThreshold();
    if (utt == null) {
      preroll.addLast(cur.clone());
      while (preroll.size() > Math.max(c.prerollMs() / WIN_MS, c.vadStartMs() / WIN_MS)) preroll.removeFirst();
      speechRun = speech ? speechRun + 1 : 0;
      if (speechRun >= Math.max(1, c.vadStartMs() / WIN_MS)) {
        if (begin()) for (short[] w : preroll) write(w);
        preroll.clear();
        speechRun = 0;
      }
      return;
    }
    write(cur);
    if (utt == null) return;  // the write failed and the utterance was dropped
    silenceRun = speech ? 0 : silenceRun + 1;
    if (silenceRun * WIN_MS >= c.vadEndSilenceMs()) finish();
    else if (bytes >= c.maxUtteranceMs() * 32L) { finish(); begin(); }  // keep going as a new utterance
  }

  /** @return false if the utterance file could not be created (nothing is left open) */
  private boolean begin() {
    String id = UUID.randomUUID().toString();
    Path p = c.audioDir().resolve(id + ".pcm");
    try { out = new BufferedOutputStream(new FileOutputStream(p.toFile())); }
    catch (IOException e) { log.warn("cannot create {}; dropping this utterance", p, e); return false; }
    utt = id; path = p;
    bytes = snapBytes = 0; rev = 0; silenceRun = 0; speechRun = 0;
    lastPartialMs = System.currentTimeMillis();
    return true;
  }

  private void write(short[] w) {
    if (utt == null) return;
    try {
      var bb = ByteBuffer.allocate(w.length * 2).order(ByteOrder.LITTLE_ENDIAN);
      bb.asShortBuffer().put(w);
      out.write(bb.array());
      bytes += bb.capacity();
    } catch (IOException e) { abort(e); }
  }

  /**
   * Disk trouble: stop writing and wait for the next utterance instead of failing on every packet. If partials were
   * already queued, close the utterance with a final over the flushed prefix so its rows and file are reclaimed
   * through the normal path; otherwise nothing refers to the file and it is deleted.
   */
  private void abort(IOException e) {
    log.warn("utterance file failed; closing utterance {}", utt, e);
    try { out.close(); } catch (IOException ignored) {}
    try {
      if (rev > 0) { bytes = snapBytes; enqueueAll(true); }
      else path.toFile().delete();
    } catch (RuntimeException ex) {
      log.warn("could not enqueue a final for utterance {}", utt, ex);
    } finally {
      utt = null; speechRun = 0;
      vad.reset();
    }
  }

  private void finish() {
    try { out.close(); } catch (IOException e) { abort(e); return; }
    try {
      if (bytes < c.minUtteranceMs() * 32L) path.toFile().delete();
      else enqueueAll(true);
    } catch (RuntimeException e) {
      log.warn("enqueue failed; dropping utterance {}", utt, e);  // file is left for the TTL/orphan sweep
    } finally {
      utt = null; speechRun = 0;
      vad.reset();
    }
  }

  /** One job per configured engine (shadow comparison); revision is shared across engines. */
  private void enqueueAll(boolean fin) {
    if (rev == 0) onUtterance.accept(utt);  // registered only once a job exists, so dropped blips leave nothing behind
    rev++;
    for (String e : c.engines()) db.enqueue(utt, rev, fin, path.toString(), bytes, e);
  }

  /** Called periodically: emits partial jobs, and ends an utterance when the user's packets stop arriving. */
  public synchronized void tick() {
    if (utt == null) return;
    long now = System.currentTimeMillis();
    if (now - lastAudioMs >= c.vadEndSilenceMs()) { finish(); return; }
    if (now - lastPartialMs >= c.partialMs() && bytes > snapBytes) {
      try { out.flush(); } catch (IOException e) { abort(e); return; }
      snapBytes = bytes; lastPartialMs = now;
      enqueueAll(false);
    }
  }

  /** True when no utterance is open and no audio arrived for idleMs, so the pipeline can be released. */
  public synchronized boolean idle(long idleMs) {
    return utt == null && System.currentTimeMillis() - lastAudioMs > idleMs;
  }

  @Override public synchronized void close() {
    if (closed) return;
    closed = true;
    try { if (utt != null) finish(); }
    finally { vad.release(); }
  }

  interface VadDetector {
    float compute(float[] samples);
    void reset();
    void release();
  }
}
