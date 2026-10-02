package koemoji.asr;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class WorkerTest {
  @Test void padAddsSilenceToBothEndsAtSixteenSamplesPerMs() {
    float[] out = Worker.pad(new float[] {1f, 2f}, 2);
    assertEquals(2 + 2 * 32, out.length);
    assertEquals(1f, out[32]);
    assertEquals(2f, out[33]);
    assertEquals(0f, out[0]);
    assertEquals(0f, out[out.length - 1]);
  }

  @Test void padOfZeroOrLessLeavesTheAudioUntouched() {
    float[] x = {1f};
    assertSame(x, Worker.pad(x, 0));
    assertSame(x, Worker.pad(x, -5));
  }
}
