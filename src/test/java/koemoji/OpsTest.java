package koemoji;

import koemoji.queue.Db;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.util.HashMap;
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

  @Test void healthFailsWhileDiscordIsDisconnectedAndPassesOnceConnected() throws Exception {
    var c = config(java.util.Map.of("MODE", "capture"));
    var up = new java.util.concurrent.atomic.AtomicBoolean();
    var ops = new Ops(c, new Db(c), up::get);
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
    var ops = new Ops(c, db, () -> true);
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
    var ops = new Ops(c, new Db(c), up::get);
    try {
      assertEquals(1, HealthCheck.check(ops.port()));
      up.set(true);
      assertEquals(0, HealthCheck.check(ops.port()));
    } finally { ops.close(); }
  }
}
