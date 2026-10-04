package koemoji.capture;

import koemoji.Config;
import koemoji.queue.Db;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongFunction;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;
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
    final long guildId;
    final Map<String, Long> routes;
    final String name;
    final Map<String, Msg> msgs = new ConcurrentHashMap<>();
    final AtomicInteger remaining;
    Utt(long guildId, Map<String, Long> routes, String name, int engines) {
      this.guildId = guildId; this.routes = Map.copyOf(routes); this.name = name; remaining = new AtomicInteger(engines);
    }
  }

  private final Config c;
  private final Db db;
  private final LongFunction<MessageChannel> channels;
  private final Map<String, Utt> utts = new ConcurrentHashMap<>();

  Transcripts(Config c, Db db, LongFunction<MessageChannel> channels) { this.c = c; this.db = db; this.channels = channels; }

  /** Captures destination routes before the utterance's first job is enqueued. */
  void register(String utteranceId, long guildId, String userName) {
    var engines = new HashSet<>(c.engines());
    utts.put(utteranceId, new Utt(guildId, db.routesOf(guildId, engines), userName, engines.size()));
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
  String render(String engine, String user, String text, boolean sharesDestination) {
    String fmt = c.messageFormat();
    if (sharesDestination && !fmt.contains("{engine}")) fmt = "[{engine}] " + fmt;
    var m = PLACEHOLDER.matcher(fmt);
    var sb = new StringBuilder();
    while (m.find()) {
      String v = switch (m.group(1)) { case "user" -> user; case "text" -> text; default -> engine; };
      m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(v));
    }
    return m.appendTail(sb).toString();
  }

  /** Display names are user-controlled: neutralise Markdown (formatting, masked links, headings) in them. */
  static String escapeName(String name) {
    return MarkdownSanitizer.escape(name).replaceAll("([\\[\\]()#>\\-])", "\\\\$1");
  }

  private static String escapeCodeSpan(String value) { return value.replace('`', '｀'); }

  private void show(String id, Utt u, Msg m, Db.Done d) {
    String text = d.text() == null ? "" : d.text().strip();
    Long channelId = u.routes.get(d.engine());
    boolean fin = d.isFinal();
    if (channelId == null) {
      log.warn("no transcript channel configured for guild {}, engine {}; dropping utterance {}", u.guildId, d.engine(), id);
      if (fin) settle(id, u);
      return;
    }
    MessageChannel ch = channels.apply(channelId);
    if (ch == null) {
      log.warn("transcript channel {} not found for guild {}, engine {}; dropping utterance {}", channelId, u.guildId, d.engine(), id);
      if (fin) settle(id, u);
      return;
    }
    if (text.isEmpty()) {  // never create an empty message; a final-empty result retracts the partial
      if (fin) {
        if (m.messageId != null) ch.deleteMessageById(m.messageId).queue(null, err -> {});
        settle(id, u);
      }
      return;
    }
    boolean defaultFormat = c.messageFormat().equals(Config.DEFAULT_MESSAGE_FORMAT);
    String user = defaultFormat ? escapeCodeSpan(u.name) : escapeName(u.name);
    String transcript = defaultFormat ? escapeCodeSpan(text) : text;
    boolean sharesDestination = u.routes.values().stream().filter(channelId::equals).count() > 1;
    String body = render(d.engine(), user, transcript, sharesDestination);
    if (body.length() > 2000) {
      body = defaultFormat ? body.substring(0, 1999) + "`" : body.substring(0, 2000);
    }
    m.inflight = true;
    Runnable done = () -> { m.inflight = false; if (fin) settle(id, u); };
    try {
      if (m.messageId == null) {
        ch.sendMessage(body).setAllowedMentions(List.of()).queue(
            (Message sent) -> { m.messageId = sent.getIdLong(); done.run(); },
            err -> { log.warn("send failed for guild {}, engine {}, channel {}", u.guildId, d.engine(), channelId, err); done.run(); });
      } else {
        ch.editMessageById(m.messageId, body).setAllowedMentions(List.of()).queue(
            x -> done.run(), err -> { log.warn("edit failed for guild {}, engine {}, channel {}", u.guildId, d.engine(), channelId, err); done.run(); });
      }
    } catch (RuntimeException ex) {  // JDA checks permissions synchronously (e.g. the bot lost Send Messages)
      log.warn("cannot post for guild {}, engine {}, channel {}", u.guildId, d.engine(), channelId, ex);
      done.run();
    }
  }
}
