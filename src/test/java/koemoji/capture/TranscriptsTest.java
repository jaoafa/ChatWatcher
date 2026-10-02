package koemoji.capture;

import static org.junit.jupiter.api.Assertions.*;

import koemoji.Config;
import koemoji.queue.Db;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TranscriptsTest {
  static final long CHANNEL = 7, FIRST_MESSAGE = 1000;

  final List<String> calls = new ArrayList<>();
  Db db;
  Transcripts t;

  /** Channel double recording sends/edits/deletes; every JDA action chain completes synchronously. */
  private MessageChannel channel() {
    return proxy(MessageChannel.class, (self, m, a) -> switch (m.getName()) {
      case "sendMessage" -> action("send:" + a[0], FIRST_MESSAGE, m.getReturnType());
      case "editMessageById" -> action("edit:" + a[0] + ":" + a[1], null, m.getReturnType());
      case "deleteMessageById" -> action("delete:" + a[0], null, m.getReturnType());
      default -> null;
    });
  }

  private Object action(String what, Long newId, Class<?> type) {
    boolean[] mentionsOff = {false};
    return proxy(type, (self, m, a) -> switch (m.getName()) {
      case "setAllowedMentions" -> { mentionsOff[0] = ((java.util.Collection<?>) a[0]).isEmpty(); yield self; }
      case "queue" -> {
        calls.add(what + (what.startsWith("delete") || mentionsOff[0] ? "" : " !mentions-allowed"));
        @SuppressWarnings("unchecked") var ok = (Consumer<Object>) a[0];
        if (ok != null) ok.accept(newId == null ? null : proxy(Message.class, (s2, m2, a2) -> m2.getName().equals("getIdLong") ? newId : null));
        yield null;
      }
      default -> self;
    });
  }

  @SuppressWarnings("unchecked")
  private static <T> T proxy(Class<?> type, Handler h) {
    return (T) Proxy.newProxyInstance(TranscriptsTest.class.getClassLoader(), new Class<?>[] {type},
        (self, m, a) -> h.handle(self, m, a));
  }

  private interface Handler { Object handle(Object self, java.lang.reflect.Method m, Object[] a) throws Throwable; }

  @BeforeEach void setUp() throws Exception { start("a"); }

  private void start(String engines) throws Exception {
    var tmp = Files.createTempDirectory("cwtr");
    var env = Map.of("AUDIO_DIR", tmp.resolve("audio").toString(), "QUEUE_DB", tmp.resolve("q.db").toString(), "ASR_ENGINES", engines);
    var c = Config.of(env::get);
    db = new Db(c);
    calls.clear();
    t = new Transcripts(c, db, id -> id == CHANNEL ? channel() : null);
    t.register("u1", CHANNEL, "bob");
  }

  /** Enqueues, claims and completes one job, then applies it. */
  private void result(String engine, int rev, boolean fin, String text) {
    db.enqueue("u1", rev, fin, "/x", 1, engine);
    db.complete(db.claim("w", engine).get().id(), "w", text);
    t.apply();
  }

  @Test void firstResultCreatesTheMessageAndLaterRevisionsEditIt() {
    result("a", 1, false, "hello");
    result("a", 2, false, "hello world");
    result("a", 3, true, "Hello, world.");
    assertEquals(List.of("send:[a] bob: hello", "edit:1000:[a] bob: hello world", "edit:1000:[a] bob: Hello, world."), calls);
  }

  @Test void aLateResultFromAnOlderRevisionIsIgnored() {
    db.enqueue("u1", 1, false, "/x", 1, "a");
    var old = db.claim("w", "a").get();
    result("a", 2, false, "new");
    db.complete(old.id(), "w", "old");
    t.apply();
    assertEquals(List.of("send:[a] bob: new"), calls);
  }

  @Test void emptyPartialsPostNothingAndAnEmptyFinalRetractsTheMessage() {
    result("a", 1, false, "  ");
    assertEquals(List.of(), calls);
    result("a", 2, false, "um");
    result("a", 3, true, "");
    assertEquals(List.of("send:[a] bob: um", "delete:1000"), calls);
  }

  @Test void eachEngineGetsItsOwnMessage() throws Exception {
    start("a,b");
    result("a", 1, true, "one");
    result("b", 1, true, "two");
    assertEquals(List.of("send:[a] bob: one", "send:[b] bob: two"), calls);
  }

  @Test void bodiesAreCappedAtDiscordsLimit() {
    result("a", 1, true, "x".repeat(5000));
    assertEquals(1, calls.size());
    assertEquals(2000, calls.get(0).length() - "send:".length());
  }

  @Test void failedFinalPostsNothing() {
    db.enqueue("u1", 1, true, "/x", 1, "a");
    db.fail(db.claim("w", "a").get(), "w", "boom", 0, 0);
    t.apply();
    assertEquals(List.of(), calls);
  }

  @Test void resultsForUnknownUtterancesAreDiscardedWithoutPosting() {
    db.enqueue("other", 1, true, "/x", 1, "a");
    db.complete(db.claim("w", "a").get().id(), "w", "text");
    t.apply();
    assertEquals(List.of(), calls);
    assertTrue(db.pollDone().isEmpty());
  }

  @Test void messagesNeverPingAnyone() {
    result("a", 1, true, "@everyone hi");
    assertEquals(List.of("send:[a] bob: @everyone hi"), calls);
  }
}
