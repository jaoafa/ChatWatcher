package koemoji;

import koemoji.asr.Worker;
import koemoji.queue.Db;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.function.BooleanSupplier;

/** Health check and Prometheus-style metrics over plain HTTP (JDK built-in server). */
final class Ops {
  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(Ops.class);
  private final HttpServer server;

  Ops(Config c, Db db, BooleanSupplier discordConnected) throws IOException {
    server = HttpServer.create(new InetSocketAddress(c.healthPort()), 0);
    server.createContext("/health", ex -> {
      String problem = null;
      if (c.capture() && !discordConnected.getAsBoolean()) problem = "discord not connected";
      else if (c.worker() && Worker.alive() < c.workerEngines().size())
        problem = "workers alive " + Worker.alive() + "/" + c.workerEngines().size();
      reply(ex, problem == null ? 200 : 503, problem == null ? "ok\n" : problem + "\n");
    });
    server.createContext("/metrics", ex -> {
      String body;
      try { body = metrics(db); }
      catch (RuntimeException e) {  // the JDK server would only log this at TRACE and drop the connection
        log.warn("metrics failed", e);
        reply(ex, 503, "metrics unavailable\n");
        return;
      }
      reply(ex, 200, body);
    });
    server.start();
  }

  int port() { return server.getAddress().getPort(); }

  void close() { server.stop(0); }

  static String metrics(Db db) {
    var sb = new StringBuilder();
    sb.append("# TYPE koemoji_jobs gauge\n");
    db.counts().forEach((k, n) -> {
      String[] p = k.split("\\|");
      sb.append("koemoji_jobs{engine=\"").append(p[0]).append("\",status=\"").append(p[1]).append("\"} ").append(n).append('\n');
    });
    sb.append("# TYPE koemoji_workers_alive gauge\nkoemoji_workers_alive ").append(Worker.alive()).append('\n');
    sb.append("# TYPE koemoji_asr_jobs_total counter\n# TYPE koemoji_asr_seconds_total counter\n");
    Worker.stats().forEach((k, v) -> {
      String[] p = k.split("\\|");
      String labels = "{engine=\"" + p[0] + "\",kind=\"" + p[1] + "\"}";
      sb.append("koemoji_asr_jobs_total").append(labels).append(' ').append(v[0]).append('\n');
      sb.append("koemoji_asr_seconds_total").append(labels).append(' ').append(v[1] / 1e9).append('\n');
    });
    return sb.toString();
  }

  private static void reply(com.sun.net.httpserver.HttpExchange ex, int code, String body) throws IOException {
    byte[] b = body.getBytes(StandardCharsets.UTF_8);
    ex.sendResponseHeaders(code, b.length);
    try (var o = ex.getResponseBody()) { o.write(b); }
  }
}
