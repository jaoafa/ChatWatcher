package koemoji.capture;

import koemoji.Config;
import koemoji.queue.Db;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongFunction;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Turns finished ASR jobs into Discord messages: one message per (utterance, engine), created on the first
 * non-empty result and edited afterwards. Stale revisions are ignored and edits coalesce to the newest result.
 */
final class Transcripts {
  private static final Logger log = LoggerFactory.getLogger(Transcripts.class);

  private static final class Msg {
    volatile Long messageId;
    volatile boolean inflight;
    int appliedRev;
  }

  private static final class Utt {
    final long channelId;
    final String name;
    final Map<String, Msg> msgs = new ConcurrentHashMap<>();
    final AtomicInteger remaining;
    Utt(long channelId, String name, int engines) { this.channelId = channelId; this.name = name; remaining = new AtomicInteger(engines); }
  }

  private final Config c;
  private final Db db;
  private final LongFunction<MessageChannel> channels;
  private final Map<String, Utt> utts = new ConcurrentHashMap<>();

  Transcripts(Config c, Db db, LongFunction<MessageChannel> channels) { this.c = c; this.db = db; this.channels = channels; }

  /** Must be called before the utterance's first job is enqueued. */
  void register(String utteranceId, long channelId, String userName) {
    utts.put(utteranceId, new Utt(channelId, userName, c.engines().size()));
  }

  /** Applies the newest completed revision per (utterance, engine). */
  void apply() {
    var done = db.pollDone();
    if (done.isEmpty()) return;
    var groups = new LinkedHashMap<String, List<Db.Done>>();
    for (var d : done) groups.computeIfAbsent(d.utteranceId() + "|" + d.engine(), k -> new ArrayList<>()).add(d);
    var consumed = new ArrayList<Long>();
    groups.forEach((key, list) -> {
      String id = list.get(0).utteranceId(), engine = list.get(0).engine();
      Utt u = utts.get(id);
      if (u == null) { list.forEach(d -> consumed.add(d.id())); return; }
      Msg m = u.msgs.computeIfAbsent(engine, k -> new Msg());
      if (m.inflight) return;  // rows stay unconsumed; next tick only the newest matters
      Db.Done best = list.stream().filter(Db.Done::isFinal).findFirst()
          .orElseGet(() -> list.stream().max(Comparator.comparingInt(Db.Done::revision)).get());
      list.forEach(d -> consumed.add(d.id()));
      if (best.revision() <= m.appliedRev) return;
      if (best.status().equals("failed")) { if (best.isFinal()) settle(id, u); return; }
      m.appliedRev = best.revision();
      show(id, u, m, best);
    });
    db.markApplied(consumed);
  }

  private void settle(String id, Utt u) { if (u.remaining.decrementAndGet() <= 0) utts.remove(id); }

  private static final java.util.regex.Pattern PLACEHOLDER = java.util.regex.Pattern.compile("\\{(user|text|engine)}");

  /** Fills {user}, {text} and {engine} in one pass, so values containing a placeholder are never re-expanded. */
  String render(String engine, String user, String text) {
    String fmt = c.messageFormat();
    // several engines post separate messages; tell them apart even if the format has no {engine}
    if (c.engines().size() > 1 && !fmt.contains("{engine}")) fmt = "[{engine}] " + fmt;
    var m = PLACEHOLDER.matcher(fmt);
    var sb = new StringBuilder();
    while (m.find()) {
      String v = switch (m.group(1)) { case "user" -> user; case "text" -> text; default -> engine; };
      m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(v));
    }
    return m.appendTail(sb).toString();
  }

  private void show(String id, Utt u, Msg m, Db.Done d) {
    String text = d.text() == null ? "" : d.text().strip();
    MessageChannel ch = channels.apply(u.channelId);
    boolean fin = d.isFinal();
    if (ch == null) { if (fin) settle(id, u); return; }
    if (text.isEmpty()) {  // never create an empty message; a final-empty result retracts the partial
      if (fin) {
        if (m.messageId != null) ch.deleteMessageById(m.messageId).queue(null, err -> {});
        settle(id, u);
      }
      return;
    }
    String body = render(d.engine(), u.name, text);
    if (body.length() > 2000) body = body.substring(0, 2000);
    m.inflight = true;
    Runnable done = () -> { m.inflight = false; if (fin) settle(id, u); };
    if (m.messageId == null) {
      ch.sendMessage(body).setAllowedMentions(List.of()).queue(
          (Message sent) -> { m.messageId = sent.getIdLong(); done.run(); },
          err -> { log.warn("send failed", err); done.run(); });
    } else {
      ch.editMessageById(m.messageId, body).setAllowedMentions(List.of()).queue(
          x -> done.run(), err -> { log.warn("edit failed", err); done.run(); });
    }
  }
}
