package com.jaoafa.chatwatcher;

import com.jaoafa.chatwatcher.event.AutoDisconnect;
import com.jaoafa.chatwatcher.event.AutoJoin;
import com.jaoafa.chatwatcher.event.CommandMessageEvent;
import com.jaoafa.chatwatcher.lib.ServerManager;
import com.jaoafa.chatwatcher.lib.UserAudioStreamMonitor;
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
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Timer;

public class Main extends ListenerAdapter {
    private static JDA jda;

    public static void main(String[] args) {
        try {
            JSONObject config = new JSONObject(Files.readString(Path.of("config.json")));
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

        Timer timer = new Timer();
        UserAudioStreamMonitor.register(timer);
    }

    @Override
    public void onReady(@Nonnull ReadyEvent event) {
        ServerManager.load();
    }

    public static JDA getJDA() {
        return jda;
    }
}
