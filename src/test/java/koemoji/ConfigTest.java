package koemoji;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConfigTest {
  private static Config of(Map<String, String> env) { return Config.of(env::get); }

  @Test void defaultsApplyWhenUnsetOrBlank() {
    var c = of(Map.of("ASR_THREADS", "  "));
    assertEquals(4, c.asrThreads());
    assertEquals(List.of("sensevoice"), c.engines());
    assertEquals("ja", c.language());
    assertTrue(c.includeBots());
  }

  @Test void workerEnginesDefaultToAsrEnginesAndExpandCounts() {
    assertEquals(List.of("a", "b"), of(Map.of("ASR_ENGINES", "a, b")).workerEngines());
    assertEquals(List.of("a", "a", "b"), of(Map.of("ASR_ENGINES", "a,b", "WORKER_ENGINES", "a:2,b")).workerEngines());
  }

  @Test void modeSelectsWhichSidesRun() {
    assertTrue(of(Map.of()).capture() && of(Map.of()).worker());
    assertTrue(of(Map.of("MODE", "capture")).capture() && !of(Map.of("MODE", "capture")).worker());
    assertTrue(!of(Map.of("MODE", "worker")).capture() && of(Map.of("MODE", "worker")).worker());
  }

  @Test void workerCountMustBeAnInteger() {
    var e = assertThrows(NumberFormatException.class, () -> of(Map.of("WORKER_ENGINES", "a:x")));
    assertTrue(e.getMessage().contains("WORKER_ENGINES"));
  }

  @Test void invalidNumbersAreRejected() {
    var e = assertThrows(NumberFormatException.class, () -> of(Map.of("ASR_THREADS", "many")));
    assertTrue(e.getMessage().contains("ASR_THREADS"));
    assertTrue(assertThrows(NumberFormatException.class, () -> of(Map.of("VAD_THRESHOLD", "high"))).getMessage().contains("VAD_THRESHOLD"));
  }
}
