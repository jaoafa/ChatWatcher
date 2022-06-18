package com.jaoafa.chatwatcher;

import com.jaoafa.chatwatcher.event.AutoDisconnect;
import com.jaoafa.chatwatcher.event.AutoJoin;
import com.jaoafa.chatwatcher.event.CommandMessageEvent;
import com.jaoafa.chatwatcher.lib.ReportMap;
import com.jaoafa.chatwatcher.lib.UserAudioStreamMonitor;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.json.JSONObject;

import javax.security.auth.login.LoginException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class Main extends ListenerAdapter {
    public static void main(String[] args) {
        try {
            JSONObject config = new JSONObject(Files.readString(Path.of("config.json")));
            JDABuilder.createDefault(config.getString("token"), EnumSet.of(
                    GatewayIntent.GUILD_MESSAGES,
                    GatewayIntent.GUILD_MEMBERS,
                    GatewayIntent.GUILD_VOICE_STATES
                ))
                .enableCache(CacheFlag.VOICE_STATE)
                .addEventListeners(
                    new CommandMessageEvent(),
                    new AutoJoin(),
                    new AutoDisconnect()
                )
                .build();
        } catch (IOException | LoginException e) {
            e.printStackTrace();
        }

        ScheduledExecutorService executorService = Executors.newScheduledThreadPool(5);
        UserAudioStreamMonitor.register(executorService);
        ReportMap.register(executorService);
    }
}
