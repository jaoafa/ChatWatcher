package koemoji;

import koemoji.asr.Models;
import koemoji.asr.SherpaEngine;
import koemoji.asr.Worker;
import koemoji.capture.Bot;
import koemoji.queue.Db;

public final class Main {
  public static void main(String[] args) throws Exception {
    Config c = Config.fromEnv();
    validate(c);
    Models.ensure(c);
    Db db = new Db(c);
    if (c.worker()) Worker.start(c, db);
    Bot bot = null;
    if (c.capture()) {
      if (c.token() == null || c.token().isBlank()) throw new IllegalStateException("DISCORD_TOKEN is required");
      bot = new Bot(c, db);
      bot.start();
    }
    Bot b = bot;
    if (c.healthPort() > 0) new Ops(c, db, () -> b != null && b.connected());
    Thread.currentThread().join();
  }

  /** Fails fast on config that would silently strand jobs: unknown engines, or engines no local worker serves. */
  static void validate(Config c) {
    if (!java.util.Set.of("all", "capture", "worker").contains(c.mode()))
      throw new IllegalStateException("MODE must be all, capture or worker: " + c.mode());
    for (String e : java.util.stream.Stream.concat(c.engines().stream(), c.workerEngines().stream()).toList())
      if (!SherpaEngine.DIRS.containsKey(e)) throw new IllegalStateException("Unknown engine: " + e + " (known: " + SherpaEngine.DIRS.keySet() + ")");
    if (c.capture() && c.worker())
      for (String e : c.engines())
        if (!c.workerEngines().contains(e)) throw new IllegalStateException("No worker for engine " + e + " (add it to WORKER_ENGINES)");
  }
}
