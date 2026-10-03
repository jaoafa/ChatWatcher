package koemoji.capture;

import static org.junit.jupiter.api.Assertions.*;

import koemoji.Config;
import koemoji.queue.Db;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TranscriptsTest {
  static final long CHANNEL = 7, FIRST_MESSAGE = 1000;

  final List<String> calls = new ArrayList<>();
  final Map<Long, MessageChannel> destinations = new ConcurrentHashMap<>();
  Db db;
  Transcripts t;

  /** Channel double recording sends/edits/deletes; every JDA action chain completes synchronously. */
  private MessageChannel channel() {
    return channel(CHANNEL, false);
  }

  private MessageChannel channel(long channelId, boolean fail) {
    return proxy(MessageChannel.class, (self, m, a) -> switch (m.getName()) {
      case "sendMessage" -> action(channelId, "send:" + a[0], FIRST_MESSAGE + (channelId == CHANNEL ? 0 : channelId), m.getReturnType(), fail);
      case "editMessageById" -> action(channelId, "edit:" + a[0] + ":" + a[1], null, m.getReturnType(), fail);
      case "deleteMessageById" -> action(channelId, "delete:" + a[0], null, m.getReturnType(), fail);
      default -> null;
    });
  }

  private Object action(long channelId, String what, Long newId, Class<?> type, boolean fail) {
    boolean[] mentionsOff = {false};
    return proxy(type, (self, m, a) -> switch (m.getName()) {
      case "setAllowedMentions" -> { mentionsOff[0] = ((java.util.Collection<?>) a[0]).isEmpty(); yield self; }
      case "queue" -> {
        calls.add((channelId == CHANNEL ? "" : "channel-" + channelId + ":") + what
            + (what.startsWith("delete") || mentionsOff[0] ? "" : " !mentions-allowed"));
        if (fail) {
          @SuppressWarnings("unchecked") var err = (Consumer<Throwable>) a[1];
          if (err != null) err.accept(new RuntimeException("denied"));
        } else {
          @SuppressWarnings("unchecked") var ok = (Consumer<Object>) a[0];
          if (ok != null) ok.accept(newId == null ? null : proxy(Message.class, (s2, m2, a2) -> m2.getName().equals("getIdLong") ? newId : null));
        }
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

  private void start(String engines) throws Exception { start(engines, null); }

  private void start(String engines, String format) throws Exception {
    var tmp = Files.createTempDirectory("cwtr");
    Map<String, String> env = Map.of("AUDIO_DIR", tmp.resolve("audio").toString(), "QUEUE_DB", tmp.resolve("q.db").toString(), "ASR_ENGINES", engines);
    if (format != null) { env = new java.util.HashMap<>(env); env.put("MESSAGE_FORMAT", format); }
    var c = Config.of(env::get);
    db = new Db(c);
    db.register(1, CHANNEL);
    calls.clear();
    destinations.clear();
    destinations.put(CHANNEL, channel());
    t = new Transcripts(c, db, destinations::get);
    t.register("u1", 1, "bob");
  }

  private void result(String engine, int rev, boolean fin, String text) {
    db.enqueue("u1", rev, fin, "/x", 1, engine);
    db.complete(db.claim("w", engine).get().id(), "w", text);
    t.apply();
  }

  @Test void displayNamesCannotInjectMarkdown() {
    String e = Transcripts.escapeName("[Admin](https://evil.example) # x");
    assertFalse(e.contains("[Admin]("));
    assertTrue(e.startsWith("\\["));
  }

  @Test void firstResultCreatesTheMessageAndLaterRevisionsEditIt() {
    result("a", 1, false, "hello");
    result("a", 2, false, "hello world");
    result("a", 3, true, "Hello, world.");
    assertEquals(List.of("send:bob: hello", "edit:1000:bob: hello world", "edit:1000:bob: Hello, world."), calls);
  }

  @Test void aLateResultFromAnOlderRevisionIsIgnored() {
    db.enqueue("u1", 1, false, "/x", 1, "a");
    var old = db.claim("w", "a").get();
    result("a", 2, false, "new");
    db.complete(old.id(), "w", "old");
    t.apply();
    assertEquals(List.of("send:bob: new"), calls);
  }

  @Test void emptyPartialsPostNothingAndAnEmptyFinalRetractsTheMessage() {
    result("a", 1, false, "  ");
    assertEquals(List.of(), calls);
    result("a", 2, false, "um");
    result("a", 3, true, "");
    assertEquals(List.of("send:bob: um", "delete:1000"), calls);
  }

  @Test void eachEngineGetsItsOwnMessage() throws Exception {
    start("a,b");
    result("a", 1, true, "one");
    result("b", 1, true, "two");
    assertEquals(List.of("send:[a] bob: one", "send:[b] bob: two"), calls);
  }

  @Test void engineRoutesCanShareAChannel() throws Exception {
    start("a,b");
    db.register(1, "a", CHANNEL);
    db.register(1, "b", CHANNEL);
    t.register("u1", 1, "bob");

    result("a", 1, true, "one");
    result("b", 1, true, "two");

    assertEquals(List.of("send:[a] bob: one", "send:[b] bob: two"), calls);
  }

  @Test void routesAreSnapshottedWhenTheUtteranceIsRegistered() throws Exception {
    start("a");
    db.register(1, "a", 8);
    destinations.put(8L, channel(8, false));

    result("a", 1, false, "partial");
    result("a", 2, true, "final");

    assertEquals(List.of("send:bob: partial", "edit:" + FIRST_MESSAGE + ":bob: final"), calls);
  }

  @Test void aMissingEngineRouteDoesNotBlockOtherEngines() throws Exception {
    start("a,b");
    db.unregister(1);
    db.register(1, "a", 8);
    destinations.put(8L, channel(8, false));
    t.register("u1", 1, "bob");

    result("a", 1, true, "one");
    result("b", 1, true, "two");

    assertEquals(List.of("channel-8:send:" + "[a] bob: one"), calls);
  }

  @Test void aFailedEngineChannelDoesNotBlockAnotherEngine() throws Exception {
    start("a,b");
    db.unregister(1);
    db.register(1, "a", 8);
    db.register(1, "b", 9);
    destinations.put(8L, channel(8, true));
    destinations.put(9L, channel(9, false));
    t.register("u1", 1, "bob");

    result("a", 1, true, "one");
    result("b", 1, true, "two");

    assertEquals(List.of("channel-8:send:[a] bob: one", "channel-9:send:[b] bob: two"), calls);
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
    assertEquals(List.of("send:bob: @everyone hi"), calls);
  }

  @Test void customFormat() throws Exception {
    start("a", "**{user}** ({engine}): {text}");
    result("a", 1, true, "{user} hi");
    assertEquals(List.of("send:**bob** (a): {user} hi"), calls);
  }

  @Test void multipleEnginesPrefixWhenFormatLacksEngine() throws Exception {
    start("a,b", "> {user}: {text}");
    result("a", 1, true, "x");
    assertEquals(List.of("send:[a] > bob: x"), calls);
  }
}
