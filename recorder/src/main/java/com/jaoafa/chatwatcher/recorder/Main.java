package com.jaoafa.chatwatcher.recorder;

import com.jaoafa.chatwatcher.recorder.event.AutoDisconnect;
import com.jaoafa.chatwatcher.recorder.event.AutoJoin;
import com.jaoafa.chatwatcher.recorder.event.CommandMessageEvent;
import com.jaoafa.chatwatcher.recorder.lib.AudioSaveThread;
import com.jaoafa.chatwatcher.recorder.lib.ChatWatcherSocketServer;
import com.jaoafa.chatwatcher.recorder.lib.PathEnums;
import com.jaoafa.chatwatcher.recorder.lib.ServerManager;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.events.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.json.JSONObject;

import javax.annotation.Nonnull;
import javax.security.auth.login.LoginException;
import java.io.IOException;
import java.nio.file.Files;
import java.util.EnumSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Main extends ListenerAdapter {
    private static JDA jda;
    private static ChatWatcherSocketServer socketServer;

    public static void main(String[] args) {
        try {
            JSONObject config = new JSONObject(Files.readString(PathEnums.Config.toPath()));
            jda = JDABuilder.createDefault(config.getString("token"), EnumSet.of(
                            GatewayIntent.GUILD_MESSAGES,
                            GatewayIntent.GUILD_MEMBERS,
                            GatewayIntent.GUILD_VOICE_STATES
                    ))
                    .enableCache(CacheFlag.VOICE_STATE)
                    .addEventListeners(
                            new Main(),
                            new CommandMessageEvent(),
                            new AutoJoin(),
                            new AutoDisconnect()
                    )
                    .build();
        } catch (IOException | LoginException e) {
            e.printStackTrace();
        }

        ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor();
        service.scheduleAtFixedRate(new AudioSaveThread(), 0, 1, TimeUnit.SECONDS);

        socketServer = new ChatWatcherSocketServer(10000);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> jda.shutdown()));
    }

    @Override
    public void onReady(@Nonnull ReadyEvent event) {
        System.out.println("Ready: " + jda.getSelfUser().getAsTag());
        ServerManager.load();
    }

    public static JDA getJDA() {
        return jda;
    }

    public static ChatWatcherSocketServer getSocketServer() {
        return socketServer;
    }
}
