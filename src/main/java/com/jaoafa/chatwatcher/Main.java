package com.jaoafa.chatwatcher;

import com.jaoafa.chatwatcher.event.AutoDisconnect;
import com.jaoafa.chatwatcher.event.AutoJoin;
import com.jaoafa.chatwatcher.event.AutoMove;
import com.jaoafa.chatwatcher.event.CommandMessageEvent;
import com.jaoafa.chatwatcher.lib.FileDeleteProcessor;
import com.jaoafa.chatwatcher.lib.ServerManager;
import com.jaoafa.chatwatcher.lib.UserAudioStreamMonitor;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Timer;

public class Main extends ListenerAdapter {
    private static JDA jda;

    public static void main(String[] args) {
        try {
            JSONObject config = new JSONObject(Files.readString(Path.of("/data/config.json")));
            jda = JDABuilder.createDefault(config.getString("token"))
                    .setChunkingFilter(ChunkingFilter.ALL)
                    .setMemberCachePolicy(MemberCachePolicy.ALL)
                    .enableIntents(
                            GatewayIntent.GUILD_MEMBERS,
                            GatewayIntent.GUILD_PRESENCES,
                            GatewayIntent.GUILD_MESSAGES,
                            GatewayIntent.GUILD_VOICE_STATES,
                            GatewayIntent.MESSAGE_CONTENT
                    )
                    .addEventListeners(
                            new Main(),
                            new CommandMessageEvent(),
                            new AutoJoin(),
                            new AutoMove(),
                            new AutoDisconnect()
                    )
                    .build();
        } catch (IOException e) {
            e.printStackTrace();
        }

        Timer timer = new Timer();
        UserAudioStreamMonitor.register(timer);
        FileDeleteProcessor.register(timer);
    }

    @Override
    public void onReady(@NotNull ReadyEvent event) {
        ServerManager.load();
    }

    public static JDA getJDA() {
        return jda;
    }
}
