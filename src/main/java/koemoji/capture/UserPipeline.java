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
  private final Consumer<String> onUtterance;  // called with utteranceId before its first job is enqueued
  private final Vad vad;
  private final float[] win = new float[WIN];
  private int winFill;
  private final ArrayDeque<short[]> preroll = new ArrayDeque<>();
  private final short[] cur = new short[WIN];

  // frame resampler carry (3 input frames -> 1 output sample)
  private int acc, accN;

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

  public UserPipeline(Config c, Db db, String name, Consumer<String> onUtterance) {
    this.name = name; this.c = c; this.db = db; this.onUtterance = onUtterance;
    var silero = SileroVadModelConfig.builder().setModel(c.vadModel().toString()).setThreshold(c.vadThreshold())
        .setWindowSize(WIN).build();
    vad = new Vad(VadModelConfig.builder().setSileroVadModelConfig(silero).setSampleRate(16000).setNumThreads(1).setDebug(false).build());
  }

  /** @param pcm big-endian 16-bit stereo 48 kHz (JDA output format) */
  public synchronized void accept(byte[] pcm) {
    long now = System.currentTimeMillis();
    if (now - lastAudioMs > GAP_MS) { winFill = 0; acc = 0; accN = 0; }  // Discord sends nothing while silent
    lastAudioMs = now;
    ByteBuffer b = ByteBuffer.wrap(pcm).order(ByteOrder.BIG_ENDIAN);
    while (b.remaining() >= 4) {
      acc += (b.getShort() + b.getShort()) / 2;
      if (++accN == 3) {
        short s = (short) (acc / 3);
        acc = 0; accN = 0;
        win[winFill] = s / 32768f; cur[winFill] = s;
        if (++winFill == WIN) { window(); winFill = 0; }
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
        begin();
        for (short[] w : preroll) write(w);
        preroll.clear();
      }
      return;
    }
    write(cur);
    silenceRun = speech ? 0 : silenceRun + 1;
    if (silenceRun * WIN_MS >= c.vadEndSilenceMs()) finish();
    else if (bytes >= c.maxUtteranceMs() * 32L) { finish(); begin(); }  // keep going as a new utterance
  }

  private void begin() {
    utt = UUID.randomUUID().toString();
    path = c.audioDir().resolve(utt + ".pcm");
    try { out = new BufferedOutputStream(new FileOutputStream(path.toFile())); }
    catch (IOException e) { throw new UncheckedIOException(e); }
    bytes = snapBytes = 0; rev = 0; silenceRun = 0; speechRun = 0;
    lastPartialMs = System.currentTimeMillis();
    onUtterance.accept(utt);
  }

  private void write(short[] w) {
    try {
      var bb = ByteBuffer.allocate(w.length * 2).order(ByteOrder.LITTLE_ENDIAN);
      bb.asShortBuffer().put(w);
      out.write(bb.array());
      bytes += bb.capacity();
    } catch (IOException e) { throw new UncheckedIOException(e); }
  }

  private void finish() {
    try { out.close(); } catch (IOException e) { throw new UncheckedIOException(e); }
    if (bytes < c.minUtteranceMs() * 32L) {
      path.toFile().delete();
    } else {
      enqueueAll(true);
    }
    utt = null; speechRun = 0;
    vad.reset();
  }

  /** One job per configured engine (shadow comparison); revision is shared across engines. */
  private void enqueueAll(boolean fin) {
    rev++;
    for (String e : c.engines()) db.enqueue(utt, rev, fin, path.toString(), bytes, e);
  }

  /** Called periodically: emits partial jobs, and ends an utterance when the user's packets stop arriving. */
  public synchronized void tick() {
    if (utt == null) return;
    long now = System.currentTimeMillis();
    if (now - lastAudioMs >= c.vadEndSilenceMs()) { finish(); return; }
    if (now - lastPartialMs >= c.partialMs() && bytes > snapBytes) {
      try { out.flush(); } catch (IOException e) { throw new UncheckedIOException(e); }
      snapBytes = bytes; lastPartialMs = now;
      enqueueAll(false);
    }
  }

  /** True when no utterance is open and no audio arrived for idleMs, so the pipeline can be released. */
  public synchronized boolean idle(long idleMs) {
    return utt == null && System.currentTimeMillis() - lastAudioMs > idleMs;
  }

  @Override public synchronized void close() {
    if (utt != null) finish();
    vad.release();
  }
}
