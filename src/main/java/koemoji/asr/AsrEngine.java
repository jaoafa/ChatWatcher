package koemoji.asr;

import koemoji.Config;

/** Swappable ASR backend. Samples are 16 kHz mono float in [-1, 1]. */
public interface AsrEngine extends AutoCloseable {
  record Result(String text, double logProbabilitySum, int scoredTokenCount) {}

  record Capabilities(String name, boolean nativeStreaming, int maxInputSeconds, boolean punctuation) {}

  Capabilities capabilities();
  Result recognizePartial(float[] samples);
  Result recognizeFinal(float[] samples);
  @Override void close();

  static AsrEngine create(String name, Config c) {
    return new SherpaEngine(name, c);
  }
}
