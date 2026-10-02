package koemoji.queue;

import koemoji.Config;

import java.io.IOException;
import java.nio.file.Files;
import java.sql.*;
import java.util.*;

/** SQLite-backed durable job queue shared by capture and worker processes (WAL, atomic claim). */
public final class Db {
  public record Job(long id, String utteranceId, int revision, boolean isFinal, String audioPath, long snapshotBytes, int retryCount) {}
  public record Done(long id, String utteranceId, String engine, int revision, boolean isFinal, String status, String text) {}

  public interface Sql<T> { T run(Connection c) throws SQLException; }

  private final String url;

  public Db(Config cfg) {
    try {
      mkdirs(cfg);
    } catch (IOException e) { throw new java.io.UncheckedIOException(e); }
    url = "jdbc:sqlite:" + cfg.queueDb();
    tx(c -> {
      try (Statement s = c.createStatement()) {
        s.execute("""
            CREATE TABLE IF NOT EXISTS jobs(
              job_id INTEGER PRIMARY KEY AUTOINCREMENT, utterance_id TEXT NOT NULL, revision INTEGER NOT NULL,
              is_final INTEGER NOT NULL, audio_path TEXT NOT NULL, snapshot_bytes INTEGER NOT NULL,
              engine TEXT NOT NULL, status TEXT NOT NULL, retry_count INTEGER NOT NULL DEFAULT 0,
              next_run_at INTEGER NOT NULL, created_at INTEGER NOT NULL, started_at INTEGER, completed_at INTEGER,
              worker_id TEXT, error TEXT, text TEXT, applied INTEGER NOT NULL DEFAULT 0)""");
        s.execute("CREATE INDEX IF NOT EXISTS jobs_status ON jobs(status, next_run_at)");
        s.execute("CREATE INDEX IF NOT EXISTS jobs_utt ON jobs(utterance_id)");
        s.execute("CREATE TABLE IF NOT EXISTS guilds(guild_id INTEGER PRIMARY KEY, channel_id INTEGER NOT NULL)");
      }
      return null;
    });
  }

  private static void mkdirs(Config cfg) throws IOException {
    Files.createDirectories(cfg.queueDb().toAbsolutePath().getParent());
    Files.createDirectories(cfg.audioDir());
  }

  private Connection conn() throws SQLException {
    Properties p = new Properties();
    p.setProperty("busy_timeout", "10000");
    p.setProperty("journal_mode", "WAL");
    p.setProperty("synchronous", "NORMAL");
    return DriverManager.getConnection(url, p);
  }

  <T> T tx(Sql<T> f) {
    try (Connection c = conn()) { return f.run(c); }
    catch (SQLException e) { throw new IllegalStateException(e); }
  }

  /** Queued partials of the same utterance and engine are superseded by any newer job; finals are never dropped. */
  public void enqueue(String utt, int rev, boolean fin, String path, long bytes, String engine) {
    tx(c -> {
      c.setAutoCommit(false);
      long now = System.currentTimeMillis();
      try (var u = c.prepareStatement(
          "UPDATE jobs SET status='superseded' WHERE utterance_id=? AND engine=? AND is_final=0 AND status='queued'")) {
        u.setString(1, utt); u.setString(2, engine); u.executeUpdate();
      }
      try (var i = c.prepareStatement("""
          INSERT INTO jobs(utterance_id,revision,is_final,audio_path,snapshot_bytes,engine,status,next_run_at,created_at)
          VALUES(?,?,?,?,?,?,'queued',?,?)""")) {
        i.setString(1, utt); i.setInt(2, rev); i.setInt(3, fin ? 1 : 0); i.setString(4, path);
        i.setLong(5, bytes); i.setString(6, engine); i.setLong(7, now); i.setLong(8, now);
        i.executeUpdate();
      }
      c.commit();
      return null;
    });
  }

  /** Atomic claim: final first (oldest first), then newest partial first. */
  public Optional<Job> claim(String worker, String engine) {
    return tx(c -> {
      long now = System.currentTimeMillis();
      try (var p = c.prepareStatement("""
          UPDATE jobs SET status='processing', worker_id=?, started_at=? WHERE job_id=(
            SELECT job_id FROM jobs WHERE status='queued' AND engine=? AND next_run_at<=?
            ORDER BY is_final DESC, CASE WHEN is_final=1 THEN job_id ELSE -job_id END LIMIT 1)
          RETURNING job_id, utterance_id, revision, is_final, audio_path, snapshot_bytes, retry_count""")) {
        p.setString(1, worker); p.setLong(2, now); p.setString(3, engine); p.setLong(4, now);
        try (var r = p.executeQuery()) {
          return r.next() ? Optional.of(new Job(r.getLong(1), r.getString(2), r.getInt(3), r.getInt(4) == 1,
              r.getString(5), r.getLong(6), r.getInt(7))) : Optional.empty();
        }
      }
    });
  }

  /** Only the worker that claimed the job may settle it; a reaped worker's late result is ignored. */
  public void complete(long id, String worker, String text) {
    tx(c -> {
      try (var p = c.prepareStatement(
          "UPDATE jobs SET status='completed', text=?, completed_at=? WHERE job_id=? AND status='processing' AND worker_id=?")) {
        p.setString(1, text); p.setLong(2, System.currentTimeMillis()); p.setLong(3, id); p.setString(4, worker); p.executeUpdate();
      }
      return null;
    });
  }

  /** Finals retry with exponential backoff up to retryMax; partials are disposable and just fail. */
  public void fail(Job j, String worker, String err, int retryMax, int backoffMs) {
    tx(c -> {
      long now = System.currentTimeMillis();
      boolean retry = j.isFinal() && j.retryCount() < retryMax;
      try (var p = c.prepareStatement(retry
          ? "UPDATE jobs SET status='queued', retry_count=retry_count+1, next_run_at=?, error=? WHERE job_id=? AND status='processing' AND worker_id=?"
          : "UPDATE jobs SET status='failed', completed_at=?, error=? WHERE job_id=? AND status='processing' AND worker_id=?")) {
        p.setLong(1, retry ? now + ((long) backoffMs << j.retryCount()) : now);
        p.setString(2, err); p.setLong(3, j.id()); p.setString(4, worker); p.executeUpdate();
      }
      return null;
    });
  }

  /**
   * Crash recovery: jobs stuck in processing (worker died) go back to the queue; ones that already used up
   * retryMax attempts are failed instead, so a job that keeps killing its worker cannot loop forever.
   */
  public int requeueStale(long timeoutMs, int retryMax) {
    return tx(c -> {
      long now = System.currentTimeMillis();
      try (var give = c.prepareStatement(
               "UPDATE jobs SET status='failed', completed_at=?, error='worker lost' WHERE status='processing' AND started_at<? AND retry_count>=?");
           var retry = c.prepareStatement(
               "UPDATE jobs SET status='queued', retry_count=retry_count+1 WHERE status='processing' AND started_at<?")) {
        give.setLong(1, now); give.setLong(2, now - timeoutMs); give.setInt(3, retryMax);
        int failed = give.executeUpdate();
        retry.setLong(1, now - timeoutMs);
        return failed + retry.executeUpdate();
      }
    });
  }

  public List<Done> pollDone() {
    return tx(c -> {
      var out = new ArrayList<Done>();
      try (var s = c.createStatement(); var r = s.executeQuery("""
          SELECT job_id, utterance_id, engine, revision, is_final, status, text FROM jobs
          WHERE applied=0 AND status IN ('completed','failed') ORDER BY job_id""")) {
        while (r.next()) out.add(new Done(r.getLong(1), r.getString(2), r.getString(3), r.getInt(4), r.getInt(5) == 1, r.getString(6), r.getString(7)));
      }
      return out;
    });
  }

  public void markApplied(Collection<Long> ids) {
    if (ids.isEmpty()) return;
    tx(c -> {
      try (var p = c.prepareStatement("UPDATE jobs SET applied=1 WHERE job_id=?")) {
        for (long id : ids) { p.setLong(1, id); p.addBatch(); }
        p.executeBatch();
      }
      return null;
    });
  }

  /** Utterances whose finals all settled longer ago than the TTLs: [utteranceId, audioPath]. */
  public List<String[]> expired(long doneTtlMs, long failedTtlMs) {
    return tx(c -> {
      var out = new ArrayList<String[]>();
      long now = System.currentTimeMillis();
      // all engines' finals are settled; the TTL clock starts at the last one (longer TTL if any failed)
      try (var p = c.prepareStatement("""
          SELECT utterance_id, MIN(audio_path) FROM jobs WHERE is_final=1 GROUP BY utterance_id
          HAVING SUM(status IN ('queued','processing'))=0 AND MAX(completed_at) < ? - CASE WHEN SUM(status='failed')>0 THEN ? ELSE ? END""")) {
        p.setLong(1, now); p.setLong(2, failedTtlMs); p.setLong(3, doneTtlMs);
        try (var r = p.executeQuery()) { while (r.next()) out.add(new String[] {r.getString(1), r.getString(2)}); }
      }
      return out;
    });
  }

  public void deleteUtterance(String utt) {
    tx(c -> {
      try (var p = c.prepareStatement("DELETE FROM jobs WHERE utterance_id=?")) { p.setString(1, utt); p.executeUpdate(); }
      return null;
    });
  }

  /** Job counts keyed by "engine|status". */
  public Map<String, Long> counts() {
    return tx(c -> {
      var out = new TreeMap<String, Long>();
      try (var s = c.createStatement(); var r = s.executeQuery("SELECT engine, status, COUNT(*) FROM jobs GROUP BY engine, status")) {
        while (r.next()) out.put(r.getString(1) + "|" + r.getString(2), r.getLong(3));
      }
      return out;
    });
  }

  public boolean knowsPath(String path) {
    return tx(c -> {
      try (var p = c.prepareStatement("SELECT 1 FROM jobs WHERE audio_path=? LIMIT 1")) {
        p.setString(1, path);
        try (var r = p.executeQuery()) { return r.next(); }
      }
    });
  }

  public void register(long guild, long channel) {
    tx(c -> {
      try (var p = c.prepareStatement("INSERT OR REPLACE INTO guilds(guild_id, channel_id) VALUES(?,?)")) {
        p.setLong(1, guild);
        p.setLong(2, channel);
        p.executeUpdate();
      }
      return null;
    });
  }

  public void unregister(long guild) {
    tx(c -> {
      try (var p = c.prepareStatement("DELETE FROM guilds WHERE guild_id=?")) {
        p.setLong(1, guild);
        p.executeUpdate();
      }
      return null;
    });
  }

  /** Transcript channel of a registered guild, or null when the guild is not registered. */
  public Long channelOf(long guild) {
    return tx(c -> {
      try (var p = c.prepareStatement("SELECT channel_id FROM guilds WHERE guild_id=?")) {
        p.setLong(1, guild);
        try (var r = p.executeQuery()) { return r.next() ? r.getLong(1) : null; }
      }
    });
  }
}
