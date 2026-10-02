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

  @Test void validateRejectsConfigsThatWouldDoNothing() throws Exception {
    assertThrows(IllegalStateException.class, () -> Main.validate(config(Map.of("ASR_ENGINES", ","))));
    assertThrows(IllegalStateException.class, () -> Main.validate(config(Map.of("MODE", "worker", "WORKER_ENGINES", "sensevoice:0"))));
    assertThrows(IllegalStateException.class, () -> Main.validate(config(Map.of("MAX_UTTERANCE_MS", "0"))));
    assertThrows(IllegalStateException.class, () -> Main.validate(config(Map.of("VAD_END_SILENCE_MS", "0"))));
  }

  @Test void validateRejectsUnknownMode() throws Exception {
    assertThrows(IllegalStateException.class, () -> Main.validate(config(Map.of("MODE", "workers"))));
    for (String m : new String[] {"all", "capture", "worker"}) assertDoesNotThrow(() -> Main.validate(config(Map.of("MODE", m))));
  }
}
