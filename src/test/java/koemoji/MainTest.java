package koemoji;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MainTest {
  private static Config config(Map<String, String> extra) throws Exception {
    var tmp = Files.createTempDirectory("cwmain");
    var env = new HashMap<>(extra);
    env.put("AUDIO_DIR", tmp.resolve("audio").toString());
    env.put("QUEUE_DB", tmp.resolve("q.db").toString());
    return Config.of(env::get);
  }

  @Test void validateRejectsUnknownEnginesAndEnginesWithoutWorker() throws Exception {
    assertThrows(IllegalStateException.class, () -> Main.validate(config(java.util.Map.of("ASR_ENGINES", "nope"))));
    assertThrows(IllegalStateException.class, () -> Main.validate(config(java.util.Map.of(
        "ASR_ENGINES", "sensevoice,reazon-ja", "WORKER_ENGINES", "sensevoice"))));
    assertDoesNotThrow(() -> Main.validate(config(java.util.Map.of("ASR_ENGINES", "sensevoice,reazon-ja"))));
    assertDoesNotThrow(() -> Main.validate(config(java.util.Map.of(
        "MODE", "capture", "ASR_ENGINES", "sensevoice,reazon-ja", "WORKER_ENGINES", "sensevoice"))));
  }
}
