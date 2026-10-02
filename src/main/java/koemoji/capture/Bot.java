package koemoji.capture;

import koemoji.Config;
import koemoji.queue.Db;

import java.util.*;
import java.util.concurrent.*;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.audio.AudioModuleConfig;
import net.dv8tion.jda.api.audio.AudioReceiveHandler;
import net.dv8tion.jda.api.audio.UserAudio;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import club.minnced.discord.jdave.interop.JDaveSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Discord side: auto join/move/leave (jaoafa/ChatWatcher style), /register /unregister /join /leave, and per-user audio capture; ASR results are posted by {@link Transcripts}. */
public final class Bot extends ListenerAdapter {
  private static final Logger log = LoggerFactory.getLogger(Bot.class);

  private static final long IDLE_EVICT_MS = 5 * 60_000;

  private final Config c;
  private final Db db;
  private final Map<Long, Handler> handlers = new ConcurrentHashMap<>();
  private final ScheduledExecutorService sched = Executors.newScheduledThreadPool(2);
  private JDA jda;
  private Transcripts transcripts;

  public Bot(Config c, Db db) { this.c = c; this.db = db; }

  public boolean connected() { return jda != null && jda.getStatus() == JDA.Status.CONNECTED; }

  public void start() throws InterruptedException {
    jda = JDABuilder.createLight(c.token(), GatewayIntent.GUILD_VOICE_STATES)
        .enableCache(CacheFlag.VOICE_STATE)
        .setMemberCachePolicy(MemberCachePolicy.VOICE)
        .setAudioModuleConfig(new AudioModuleConfig().withDaveSessionFactory(new JDaveSessionFactory()))
        .addEventListeners(this).build().awaitReady();
    var guildOnly = net.dv8tion.jda.api.interactions.InteractionContextType.GUILD;
    var admin = DefaultMemberPermissions.enabledFor(Permission.MANAGE_SERVER);
    jda.updateCommands().addCommands(
        Commands.slash("register", "Enable auto-join and post transcripts in a text channel")
            .addOptions(new OptionData(OptionType.CHANNEL, "channel", "Transcript channel (default: this channel)")
                .setChannelTypes(ChannelType.TEXT, ChannelType.NEWS))
            .setContexts(guildOnly).setDefaultPermissions(admin),
        Commands.slash("unregister", "Disable auto-join and leave the voice channel")
            .setContexts(guildOnly).setDefaultPermissions(admin),
        Commands.slash("join", "Join a voice channel (default: yours)")
            .addOptions(new OptionData(OptionType.CHANNEL, "channel", "Voice channel to join")
                .setChannelTypes(ChannelType.VOICE, ChannelType.STAGE))
            .setContexts(guildOnly).setDefaultPermissions(admin),
        Commands.slash("leave", "Leave the voice channel")
            .setContexts(guildOnly).setDefaultPermissions(admin)
    ).queue();
    transcripts = new Transcripts(c, db, id -> jda.getChannelById(MessageChannel.class, id));
    sched.scheduleWithFixedDelay(() -> guard(() -> handlers.values().forEach(Handler::tick)), 100, 100, TimeUnit.MILLISECONDS);
    sched.scheduleWithFixedDelay(() -> guard(transcripts::apply), 250, 250, TimeUnit.MILLISECONDS);
    log.info("bot ready as {}", jda.getSelfUser().getName());
  }

  private static void guard(Runnable r) { try { r.run(); } catch (Throwable t) { log.warn("tick failed", t); } }

  @Override public void onSlashCommandInteraction(SlashCommandInteractionEvent e) {
    Guild g = e.getGuild();
    if (g == null) return;
    switch (e.getName()) {
      case "register" -> {
        var opt = e.getOption("channel");
        long ch = opt != null ? opt.getAsChannel().getIdLong() : e.getChannel().getIdLong();
        db.register(g.getIdLong(), ch);
        var h = handlers.get(g.getIdLong());
        if (h != null) h.channelId = ch;  // takes effect for the next utterance without reconnecting
        e.reply("Registered. Transcripts go to <#" + ch + ">; the bot now joins voice channels automatically.").queue();
      }
      case "unregister" -> {
        db.unregister(g.getIdLong());
        disconnect(g);
        e.reply("Unregistered.").queue();
      }
      case "join" -> {
        if (db.channelOf(g.getIdLong()) == null) { e.reply("Run /register first.").setEphemeral(true).queue(); return; }
        var opt = e.getOption("channel");
        var vs = e.getMember() == null ? null : e.getMember().getVoiceState();
        AudioChannel target = opt != null ? opt.getAsChannel().asAudioChannel() : vs == null ? null : vs.getChannel();
        if (target == null) { e.reply("Join a voice channel or specify one.").setEphemeral(true).queue(); return; }
        try {
          connect(g, target);
          e.reply("Joined " + target.getAsMention()).queue();
        } catch (Exception ex) {
          log.warn("join failed", ex);
          e.reply("Failed to join: " + ex.getMessage()).setEphemeral(true).queue();
        }
      }
      case "leave" -> {
        if (!g.getAudioManager().isConnected()) { e.reply("Not in a voice channel.").setEphemeral(true).queue(); return; }
        disconnect(g);
        e.reply("Left.").queue();
      }
      default -> {}
    }
  }

  private void connect(Guild g, AudioChannel ch) {
    Long text = db.channelOf(g.getIdLong());
    if (text == null) return;
    Handler h = handlers.computeIfAbsent(g.getIdLong(), id -> new Handler(g, text));
    g.getAudioManager().setReceivingHandler(h);
    g.getAudioManager().openAudioConnection(ch);
  }

  private void disconnect(Guild g) {
    g.getAudioManager().closeAudioConnection();
    g.getAudioManager().setReceivingHandler(null);
    Handler h = handlers.remove(g.getIdLong());
    if (h != null) h.close();
  }

  private static long humans(AudioChannel ch) {
    return ch.getMembers().stream().filter(m -> !m.getUser().isBot()).count();
  }

  private static boolean isAfk(AudioChannel ch) {
    var afk = ch.getGuild().getAfkChannel();
    return afk != null && afk.getIdLong() == ch.getIdLong();
  }

  enum Move { NONE, JOIN, LEAVE }

  /** What to do when a human's voice state changed; counts are humans in the bot's channel / the joined channel after the change. */
  static Move decide(boolean inChannel, boolean leftBotChannel, boolean joinedAny, boolean joinedAfk, long botChannelHumans, long joinedHumans) {
    boolean joinable = joinedAny && !joinedAfk;
    if (!inChannel) return joinable ? Move.JOIN : Move.NONE;
    if (!leftBotChannel) return Move.NONE;
    if (joinable && joinedHumans > botChannelHumans) return Move.JOIN;
    return botChannelHumans == 0 ? Move.LEAVE : Move.NONE;
  }

  /** Join when idle, follow users to a busier channel, leave when the last human leaves. */
  @Override public void onGuildVoiceUpdate(GuildVoiceUpdateEvent e) {
    Guild g = e.getGuild();
    if (e.getMember().getUser().isBot()) {
      if (e.getMember().equals(g.getSelfMember()) && e.getChannelJoined() == null) disconnect(g);
      return;
    }
    if (db.channelOf(g.getIdLong()) == null) return;
    AudioChannel from = e.getChannelLeft(), to = e.getChannelJoined(), cur = g.getSelfMember().getVoiceState().getChannel();
    switch (decide(cur != null, cur != null && from != null && from.getIdLong() == cur.getIdLong(),
        to != null, to != null && isAfk(to), cur == null ? 0 : humans(cur), to == null ? 0 : humans(to))) {
      case JOIN -> connect(g, to);
      case LEAVE -> disconnect(g);
      case NONE -> {}
    }
  }

  /** Receives JDA audio and fans it out to per-user pipelines. */
  private final class Handler implements AudioReceiveHandler {
    private final Guild guild; private volatile long channelId;
    private final Map<Long, UserPipeline> users = new ConcurrentHashMap<>();
    private final Map<Long, String> names = new ConcurrentHashMap<>();

    Handler(Guild g, long channelId) { this.guild = g; this.channelId = channelId; }

    @Override public boolean canReceiveUser() { return true; }

    @Override public void handleUserAudio(UserAudio ua) {
      var user = ua.getUser();
      if (user.isBot() && !c.includeBots()) return;
      long uid = user.getIdLong();
      names.computeIfAbsent(uid, k -> {
        var m = guild.getMember(user);
        return m != null ? m.getEffectiveName() : user.getEffectiveName();
      });
      users.computeIfAbsent(uid, k -> new UserPipeline(c, db, names.get(uid),
          id -> transcripts.register(id, channelId, names.get(uid)))).accept(ua.getAudioData(1.0));
    }

    void tick() {
      users.values().forEach(p -> {
        try { p.tick(); } catch (Throwable t) { log.warn("tick failed", t); }  // one user must not stop the others
      });
      users.values().removeIf(p -> {
        if (!p.idle(IDLE_EVICT_MS)) return false;
        p.close();
        return true;
      });
    }
    void close() { users.values().forEach(UserPipeline::close); users.clear(); }
  }
}
