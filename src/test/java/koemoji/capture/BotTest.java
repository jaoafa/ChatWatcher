package koemoji.capture;

import static koemoji.capture.Bot.Move.*;
import static koemoji.capture.Bot.decide;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** decide(inChannel, leftBotChannel, joinedAny, joinedAfk, botChannelHumans, joinedHumans) */
class BotTest {
  @Test void idleBotJoinsTheChannelAHumanJoined() {
    assertEquals(JOIN, decide(false, false, true, false, 0, 1));
  }

  @Test void idleBotIgnoresAfkChannelAndLeaves() {
    assertEquals(NONE, decide(false, false, true, true, 0, 1));
    assertEquals(NONE, decide(false, true, false, false, 0, 0));
  }

  @Test void busyBotIgnoresJoinsToOtherChannels() {
    assertEquals(NONE, decide(true, false, true, false, 2, 1));
  }

  @Test void botFollowsHumansMovingToABusierChannelOnly() {
    assertEquals(JOIN, decide(true, true, true, false, 1, 2));
    assertEquals(NONE, decide(true, true, true, false, 2, 2));
    assertEquals(NONE, decide(true, true, true, false, 2, 1));
  }

  @Test void botFollowsTheLastHumanEvenToAChannelWithOnlyThem() {
    assertEquals(JOIN, decide(true, true, true, false, 0, 1));
  }

  @Test void botLeavesWhenTheLastHumanLeavesOrGoesAfk() {
    assertEquals(LEAVE, decide(true, true, false, false, 0, 0));
    assertEquals(LEAVE, decide(true, true, true, true, 0, 1));
  }

  @Test void botStaysWhileHumansRemain() {
    assertEquals(NONE, decide(true, true, false, false, 1, 0));
    assertEquals(NONE, decide(true, true, true, true, 1, 1));
  }
}
