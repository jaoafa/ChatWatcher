package koemoji.capture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import koemoji.Config;
import koemoji.UpdateDrain;
import koemoji.queue.Db;
import net.dv8tion.jda.api.entities.Guild;
import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.Proxy;
import java.sql.DriverManager;
import java.util.Map;
import org.junit.jupiter.api.Test;

class UserPipelineDrainTest {
  @Test void finalEnqueueFailureIsReportedToTheDrain() throws Exception {
    Fixture fixture = fixture();
    UserPipeline pipeline = fixture.pipeline();

    Bot bot = new Bot(fixture.config(), fixture.db());
    Object handler = handler(bot);
    var users = field(handler.getClass(), "users");
    ((Map<Long, UserPipeline>) users.get(handler)).put(1L, pipeline);
    var handlers = field(Bot.class, "handlers");
    ((Map<Long, Object>) handlers.get(bot)).put(1L, handler);
    var drain = new UpdateDrain(fixture.db(), bot, null);

    assertFalse(drain.begin());
    assertEquals(UpdateDrain.Phase.FAILED, drain.status().phase());
    assertTrue(field(handler.getClass(), "accepting").getBoolean(handler));
  }

  private record Fixture(Config config, Db db, UserPipeline pipeline) {}

  private static Fixture fixture() throws Exception {
    Path dir = Files.createTempDirectory("koemoji-drain");
    var env = Map.of(
        "MODE", "capture",
        "AUDIO_DIR", dir.resolve("audio").toString(),
        "QUEUE_DB", dir.resolve("queue.db").toString(),
        "ASR_ENGINES", "test",
        "VAD_START_MS", "32",
        "MIN_UTTERANCE_MS", "1");
    Config config = Config.of(env::get);
    Db db = new Db(config);
    try (var connection = DriverManager.getConnection("jdbc:sqlite:" + config.queueDb());
        var statement = connection.createStatement()) {
      statement.execute("CREATE TRIGGER reject_final BEFORE INSERT ON jobs WHEN NEW.is_final=1 "
          + "BEGIN SELECT RAISE(ABORT, 'test failure'); END");
    }
    var pipeline = new UserPipeline(config, db, "test", id -> {}, new UserPipeline.VadDetector() {
      @Override public float compute(float[] samples) { return 1; }
      @Override public void reset() {}
      @Override public void release() {}
    });
    pipeline.accept(new byte[UserPipeline.WIN * 3 * 4 * 5]);
    return new Fixture(config, db, pipeline);
  }

  private static Object handler(Bot bot) throws Exception {
    Class<?> type = java.util.Arrays.stream(Bot.class.getDeclaredClasses())
        .filter(nested -> nested.getSimpleName().equals("Handler")).findFirst().orElseThrow();
    var constructor = type.getDeclaredConstructor(Bot.class, Guild.class);
    constructor.setAccessible(true);
    Guild guild = (Guild) Proxy.newProxyInstance(Guild.class.getClassLoader(), new Class<?>[] {Guild.class},
        (proxy, method, args) -> null);
    return constructor.newInstance(bot, guild);
  }

  private static java.lang.reflect.Field field(Class<?> type, String name) throws Exception {
    var field = type.getDeclaredField(name);
    field.setAccessible(true);
    return field;
  }
}
