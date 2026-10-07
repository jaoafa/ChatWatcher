package koemoji;

import koemoji.asr.Worker;
import koemoji.capture.Bot;
import koemoji.queue.Db;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** 外部 updater がプロセスを置き換える前に、アプリケーション内の処理を drain する。 */
public final class UpdateDrain {
  public enum Phase { RUNNING, DRAINING, READY, FAILED }

  public record Status(Phase phase, long pendingJobs, long unappliedResults, long transcriptInflight,
      int activeWorkers, long activePipelines) {}

  private static final long MAX_DRAIN_MS = 60_000;
  private static final long READY_LEASE_MS = 60_000;

  private final Db db;
  private final Bot bot;
  private final Worker worker;
  private final AtomicReference<Phase> phase = new AtomicReference<>(Phase.RUNNING);
  private volatile long drainStartedAt;
  private volatile long readyAt;

  public UpdateDrain(Db db, Bot bot, Worker worker) {
    this.db = db;
    this.bot = bot;
    this.worker = worker;
    Executors.newSingleThreadScheduledExecutor(r -> Thread.ofPlatform().daemon().name("update-drain-lease").unstarted(r))
        .scheduleWithFixedDelay(this::maintain, 1, 1, TimeUnit.SECONDS);
  }

  public Status status() {
    return new Status(phase.get(), db.pendingJobCount(), db.unappliedResultCount(),
        bot == null ? 0 : bot.transcriptInflight(), Worker.alive(), bot == null ? 0 : bot.activePipelines());
  }

  public synchronized boolean begin() {
    if (bot != null && !bot.drainSucceeded()) {
      phase.set(Phase.FAILED);
      return false;
    }
    if (!phase.compareAndSet(Phase.RUNNING, Phase.DRAINING)) return false;
    drainStartedAt = System.nanoTime();
    if (bot != null) {
      bot.beginDrain();
      if (!bot.drainSucceeded()) {
        phase.set(Phase.FAILED);
        bot.resumeDrain();
        if (worker != null) worker.resumeClaims();
        return false;
      }
    }
    Thread.ofPlatform().daemon().name("update-drain").start(this::run);
    return true;
  }

  public synchronized boolean cancel() {
    Phase current = phase.get();
    if (current == Phase.RUNNING) return false;
    if (current != Phase.FAILED) phase.set(Phase.RUNNING);
    if (bot != null) bot.resumeDrain();
    if (worker != null) worker.resumeClaims();
    return true;
  }

  private void run() {
    while (phase.get() == Phase.DRAINING) {
      if ((System.nanoTime() - drainStartedAt) / 1_000_000 >= MAX_DRAIN_MS) {
        cancel();
        return;
      }
      Status current = status();
      if ((bot == null || bot.drainSucceeded()) && current.pendingJobs() == 0 && current.unappliedResults() == 0
          && current.transcriptInflight() == 0) {
        synchronized (this) {
          if (phase.get() != Phase.DRAINING) return;
          if (worker != null) worker.stopClaims();
          if (worker == null || worker.isIdle()) {
            phase.set(Phase.READY);
            readyAt = System.nanoTime();
            return;
          }
        }
      }
      try { Thread.sleep(100); }
      catch (InterruptedException e) { Thread.currentThread().interrupt(); cancel(); return; }
    }
  }

  public void maintain() {
    if (phase.get() == Phase.READY && (System.nanoTime() - readyAt) / 1_000_000 >= READY_LEASE_MS) cancel();
  }
}
