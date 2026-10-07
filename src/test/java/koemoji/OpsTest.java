package koemoji;

import koemoji.queue.Db;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.Test;

class OpsTest {
  private static Config config(java.util.Map<String, String> extra) throws Exception {
    var tmp = Files.createTempDirectory("cwops");
    var env = new HashMap<>(extra);
    env.put("AUDIO_DIR", tmp.resolve("audio").toString());
    env.put("QUEUE_DB", tmp.resolve("q.db").toString());
    env.put("HEALTH_PORT", "0");
    return Config.of(env::get);
  }

  private static HttpResponse<String> get(Ops ops, String path) throws Exception {
    return HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://localhost:" + ops.port() + path)).build(),
        HttpResponse.BodyHandlers.ofString());
  }

  private static HttpResponse<String> update(Ops ops, String path, String method, String secret) throws Exception {
    var request = HttpRequest.newBuilder(URI.create("http://localhost:" + ops.port() + path));
    if (secret != null) request.header("X-Koemoji-Update-Secret", secret);
    return HttpClient.newHttpClient().send(request.method(method, HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
  }

  @Test void healthFailsWhileDiscordIsDisconnectedAndPassesOnceConnected() throws Exception {
    var c = config(java.util.Map.of("MODE", "capture"));
    var up = new java.util.concurrent.atomic.AtomicBoolean();
    var db = new Db(c);
    var ops = new Ops(c, db, up::get, new UpdateDrain(db, null, null));
    try {
      assertEquals(503, get(ops, "/health").statusCode());
      up.set(true);
      assertEquals(200, get(ops, "/health").statusCode());
    } finally { ops.close(); }
  }

  @Test void metricsExposeJobCountsPerEngineAndStatus() throws Exception {
    var c = config(java.util.Map.of("MODE", "capture"));
    var db = new Db(c);
    db.enqueue("u1", 1, true, "/x", 10, "sensevoice");
    var ops = new Ops(c, db, () -> true, new UpdateDrain(db, null, null));
    try {
      assertTrue(get(ops, "/metrics").body().contains("koemoji_jobs{engine=\"sensevoice\",status=\"queued\"} 1"));
    } finally { ops.close(); }
  }

  @Test void healthCheckPassesWhenTheHealthServerIsDisabled() throws Exception {
    assertEquals(0, HealthCheck.check(0));
  }

  @Test void healthCheckFollowsTheHealthEndpoint() throws Exception {
    var c = config(java.util.Map.of("MODE", "capture"));
    var up = new java.util.concurrent.atomic.AtomicBoolean();
    var db = new Db(c);
    var ops = new Ops(c, db, up::get, new UpdateDrain(db, null, null));
    try {
      assertEquals(1, HealthCheck.check(ops.port()));
      up.set(true);
      assertEquals(0, HealthCheck.check(ops.port()));
    } finally { ops.close(); }
  }

  @Test void updateControlRequiresTheConfiguredSecret() throws Exception {
    var c = config(java.util.Map.of("MODE", "capture", "UPDATE_CONTROL_SECRET", "test-secret"));
    var db = new Db(c);
    var ops = new Ops(c, db, () -> true, new UpdateDrain(db, null, null));
    try {
      assertEquals(401, update(ops, "/update/status", "GET", null).statusCode());
      assertEquals(401, update(ops, "/update/drain", "POST", "wrong-secret").statusCode());
      assertEquals(200, update(ops, "/update/status", "GET", "test-secret").statusCode());
    } finally { ops.close(); }
  }

  @Test void drainWaitsForJobsAndAppliedResultsBeforeReportingReady() throws Exception {
    var c = config(java.util.Map.of("MODE", "capture", "UPDATE_CONTROL_SECRET", "test-secret"));
    var db = new Db(c);
    db.enqueue("u1", 1, true, "/x", 10, "sensevoice");
    var drain = new UpdateDrain(db, null, null);
    var ops = new Ops(c, db, () -> true, drain);
    try {
      assertEquals(202, update(ops, "/update/drain", "POST", "test-secret").statusCode());
      assertTrue(drain.status().phase() != UpdateDrain.Phase.READY);
      var job = db.claim("test-worker", "sensevoice").orElseThrow();
      db.complete(job.id(), "test-worker", "recognized");
      assertTrue(waitFor(() -> db.unappliedResultCount() == 1));
      assertEquals(UpdateDrain.Phase.DRAINING, drain.status().phase());
      db.markApplied(List.of(job.id()));
      assertTrue(waitFor(() -> drain.status().phase() == UpdateDrain.Phase.READY));
    } finally { ops.close(); }
  }

  private static boolean waitFor(java.util.function.BooleanSupplier condition) throws InterruptedException {
    long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
    while (System.nanoTime() < deadline) {
      if (condition.getAsBoolean()) return true;
      Thread.sleep(20);
    }
    return condition.getAsBoolean();
  }
}
