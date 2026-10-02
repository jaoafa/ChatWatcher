package koemoji.asr;

import koemoji.Config;
import koemoji.queue.Db;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.*;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Bounded pool of ASR workers polling the SQLite queue, plus TTL/crash-recovery reaper. */
public final class Worker {
  private static final Logger log = LoggerFactory.getLogger(Worker.class);
  private static final long STALE_MS = 5 * 60_000;

  private static final AtomicInteger alive = new AtomicInteger();
  /** Per "engine|final|partial": [jobs, nanoseconds spent recognizing]. */
  private static final Map<String, long[]> stats = new ConcurrentHashMap<>();

  public static int alive() { return alive.get(); }
  public static Map<String, long[]> stats() { return stats; }

  public static void start(Config c, Db db) {
    String host = System.getenv().getOrDefault("HOSTNAME", "local") + "-" + ProcessHandle.current().pid();
    for (int i = 0; i < c.workerEngines().size(); i++) {
      String id = host + "-" + i, engine = c.workerEngines().get(i);
      Thread.ofPlatform().daemon().name("asr-" + engine + "-" + i).start(() -> loop(c, db, id, engine));
    }
    var reaper = Executors.newSingleThreadScheduledExecutor(r -> Thread.ofPlatform().daemon().name("reaper").unstarted(r));
    reaper.scheduleWithFixedDelay(() -> {
      try { reap(c, db); } catch (Exception e) { log.warn("reaper failed", e); }
    }, 10, 30, TimeUnit.SECONDS);
  }

  private static void loop(Config c, Db db, String id, String engineName) {
    boolean up = false;
    try (AsrEngine engine = AsrEngine.create(engineName, c)) {
      log.info("worker {} ready ({})", id, engine.capabilities());
      alive.incrementAndGet();
      up = true;
      while (!Thread.currentThread().isInterrupted()) {
        var job = db.claim(id, engineName);
        if (job.isEmpty()) { Thread.sleep(100); continue; }
        var j = job.get();
        try {
          float[] pcm = pad(read(j.audioPath(), j.snapshotBytes()), c.padMs());
          long t = System.nanoTime();
          String text = j.isFinal() ? engine.recognizeFinal(pcm) : engine.recognizePartial(pcm);
          long took = System.nanoTime() - t;
          db.complete(j.id(), id, text);
          stats.merge(engineName + "|" + (j.isFinal() ? "final" : "partial"), new long[] {1, took},
              (a, b) -> new long[] {a[0] + b[0], a[1] + b[1]});
        } catch (Exception e) {
          log.warn("job {} failed (retry {})", j.id(), j.retryCount(), e);
          db.fail(j, id, String.valueOf(e), c.retryMax(), c.retryBackoffMs());
        }
      }
    } catch (InterruptedException ignored) {
    } catch (Throwable t) {
      log.error("worker {} died", id, t);
    } finally {
      if (up) alive.decrementAndGet();
    }
  }

  /** Zero-pads both ends; some models drop the first/last sound when audio starts or stops abruptly. */
  public static float[] pad(float[] x, int ms) {
    int n = ms * 16;
    if (n <= 0) return x;
    float[] out = new float[x.length + 2 * n];
    System.arraycopy(x, 0, out, n, x.length);
    return out;
  }

  /** Reads only the first snapshotBytes so a still-growing recording file is an immutable snapshot. */
  private static float[] read(String path, long bytes) throws IOException {
    byte[] b = new byte[(int) bytes];
    try (var f = new RandomAccessFile(path, "r")) { f.readFully(b); }
    var s = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
    float[] out = new float[s.remaining()];
    for (int i = 0; i < out.length; i++) out[i] = s.get(i) / 32768f;
    return out;
  }

  private static void reap(Config c, Db db) throws IOException {
    int n = db.requeueStale(STALE_MS);
    if (n > 0) log.warn("requeued {} stale jobs", n);
    for (String[] e : db.expired(c.audioTtlMin() * 60_000L, c.failedAudioTtlMin() * 60_000L)) {
      Files.deleteIfExists(Path.of(e[1]));
      db.deleteUtterance(e[0]);
    }
    // orphans: files never referenced by any job (e.g. capture crashed mid-utterance)
    long cutoff = System.currentTimeMillis() - 3_600_000;
    try (var ls = Files.list(c.audioDir())) {
      for (Path p : (Iterable<Path>) ls::iterator)
        if (Files.getLastModifiedTime(p).toMillis() < cutoff && !db.knowsPath(p.toString())) Files.deleteIfExists(p);
    }
  }
}
