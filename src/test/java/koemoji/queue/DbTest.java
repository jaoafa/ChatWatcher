package koemoji.queue;

import koemoji.Config;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DbTest {
  Db db;

  @BeforeEach void setUp() throws Exception {
    var tmp = Files.createTempDirectory("cwdb");
    var env = Map.of("AUDIO_DIR", tmp.resolve("audio").toString(), "QUEUE_DB", tmp.resolve("q.db").toString());
    db = new Db(Config.of(env::get));
  }

  private void enqueue(String utt, int rev, boolean fin, String engine) { db.enqueue(utt, rev, fin, "/x/" + utt, 100, engine); }

  @Test void finalIsClaimedBeforePartialsThenNewestPartialFirst() {
    enqueue("u1", 1, false, "e");
    enqueue("u2", 1, false, "e");
    enqueue("u3", 1, true, "e");
    assertTrue(db.claim("w", "e").get().isFinal());
    assertEquals("u2", db.claim("w", "e").get().utteranceId());
    assertEquals("u1", db.claim("w", "e").get().utteranceId());
    assertTrue(db.claim("w", "e").isEmpty());
  }

  @Test void newerJobSupersedesQueuedPartialsOfSameUtteranceOnly() {
    enqueue("u1", 1, false, "e");
    enqueue("u1", 2, false, "e");
    enqueue("u2", 1, false, "e");
    enqueue("u1", 3, true, "e");
    var revs = new java.util.ArrayList<String>();
    db.claim("w", "e").ifPresent(j -> revs.add(j.utteranceId() + j.revision()));
    db.claim("w", "e").ifPresent(j -> revs.add(j.utteranceId() + j.revision()));
    assertTrue(db.claim("w", "e").isEmpty());
    assertEquals(java.util.List.of("u13", "u21"), revs);
  }

  @Test void claimOnlyReturnsJobsOfTheWorkersEngine() {
    enqueue("u1", 1, true, "a");
    assertTrue(db.claim("w", "b").isEmpty());
    assertTrue(db.claim("w", "a").isPresent());
  }

  @Test void claimingTwiceNeverReturnsTheSameJob() {
    enqueue("u1", 1, true, "e");
    assertTrue(db.claim("w1", "e").isPresent());
    assertTrue(db.claim("w2", "e").isEmpty());
  }

  @Test void finalRetriesUntilLimitThenFailsWhilePartialFailsImmediately() {
    enqueue("u1", 1, true, "e");
    db.fail(db.claim("w", "e").get(), "w", "boom", 1, 0);
    var retried = db.claim("w", "e").get();
    assertEquals(1, retried.retryCount());
    db.fail(retried, "w", "boom", 1, 0);
    assertTrue(db.claim("w", "e").isEmpty());
    assertEquals("failed", db.pollDone().get(0).status());

    enqueue("u2", 1, false, "e");
    db.fail(db.claim("w", "e").get(), "w", "boom", 5, 0);
    assertTrue(db.claim("w", "e").isEmpty());
  }

  @Test void backoffDelaysTheRetry() {
    enqueue("u1", 1, true, "e");
    db.fail(db.claim("w", "e").get(), "w", "boom", 3, 60_000);
    assertTrue(db.claim("w", "e").isEmpty());
  }

  @Test void stuckProcessingJobsAreRequeued() {
    enqueue("u1", 1, true, "e");
    db.claim("dead-worker", "e");
    assertEquals(1, db.requeueStale(-1000, 5));
    assertTrue(db.claim("w", "e").isPresent());
  }

  @Test void completionByAReapedWorkerIsIgnored() {
    enqueue("u1", 1, true, "e");
    var j = db.claim("w1", "e").get();
    db.requeueStale(-1000, 5);
    var again = db.claim("w2", "e").get();
    db.complete(j.id(), "w1", "late");
    assertTrue(db.pollDone().isEmpty());
    db.complete(again.id(), "w2", "ok");
    assertEquals("ok", db.pollDone().get(0).text());
  }

  @Test void audioExpiresOnlyAfterEveryEnginesFinalSettled() throws Exception {
    enqueue("u1", 1, true, "a");
    enqueue("u1", 1, true, "b");
    db.complete(db.claim("w", "a").get().id(), "w", "x");
    Thread.sleep(5);
    assertTrue(db.expired(0, 0).isEmpty());
    db.complete(db.claim("w", "b").get().id(), "w", "y");
    Thread.sleep(5);
    assertEquals(1, db.expired(0, 0).size());
    assertTrue(db.expired(60_000, 60_000).isEmpty());
  }

  @Test void utterancesThatNeverGotAFinalAreReclaimedAfterTheFailedTtl() throws Exception {
    enqueue("u1", 1, false, "a");
    db.complete(db.claim("w", "a").get().id(), "w", "x");
    Thread.sleep(5);
    assertTrue(db.expired(60_000, 60_000).isEmpty());  // still young: the utterance may be open
    assertEquals(1, db.expired(0, 0).size());
  }

  @Test void appliedResultsAreNotPolledAgain() {
    enqueue("u1", 1, true, "e");
    db.complete(db.claim("w", "e").get().id(), "w", "x");
    var done = db.pollDone();
    assertEquals(1, done.size());
    db.markApplied(java.util.List.of(done.get(0).id()));
    assertTrue(db.pollDone().isEmpty());
  }

  @Test void countsGroupJobsByEngineAndStatus() {
    enqueue("u1", 1, true, "a");
    enqueue("u2", 1, true, "a");
    enqueue("u3", 1, true, "b");
    db.claim("w", "b");
    var counts = db.counts();
    assertEquals(2L, counts.get("a|queued"));
    assertEquals(1L, counts.get("b|processing"));
  }

  @Test void knowsPathsOfEnqueuedAudioOnly() {
    enqueue("u1", 1, true, "e");
    assertTrue(db.knowsPath("/x/u1"));
    assertFalse(db.knowsPath("/x/other"));
  }

  @Test void guildRegistrationRoundTripsAndReRegisteringReplacesTheChannel() {
    assertNull(db.channelOf(1));
    db.register(1, 10);
    db.register(1, 11);
    assertEquals(11L, db.channelOf(1));
    assertNull(db.channelOf(2));
    db.unregister(1);
    assertNull(db.channelOf(1));
  }

  @Test void staleJobsAreFailedOnceRetriesAreUsedUp() {
    enqueue("u1", 1, true, "e");
    db.claim("dead-worker", "e");
    assertEquals(1, db.requeueStale(-1000, 0));  // retry_count 0 >= retryMax 0: give up instead of requeueing
    assertTrue(db.claim("w", "e").isEmpty());
    assertEquals("failed", db.pollDone().get(0).status());
  }
}
