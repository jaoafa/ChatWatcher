package com.jaoafa.chatwatcher;

import com.jaoafa.chatwatcher.event.AutoDisconnect;
import com.jaoafa.chatwatcher.event.AutoJoin;
import com.jaoafa.chatwatcher.event.CommandMessageEvent;
import com.jaoafa.chatwatcher.lib.PathEnums;
import com.jaoafa.chatwatcher.lib.RecognizeThread;
import com.jaoafa.chatwatcher.lib.ServerManager;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.events.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.json.JSONObject;
import org.vosk.Model;

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
    private static Model model;

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

        try {
            model = new Model(PathEnums.Model.toPath().toString());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor();
        service.scheduleAtFixedRate(new RecognizeThread(), 0, 1, TimeUnit.SECONDS);

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

    public static Model getModel() {
        return model;
    }
}
