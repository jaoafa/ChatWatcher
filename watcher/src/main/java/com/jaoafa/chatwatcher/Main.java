package com.jaoafa.chatwatcher;

import com.corundumstudio.socketio.Configuration;
import com.corundumstudio.socketio.SocketIOServer;
import com.jaoafa.chatwatcher.event.AutoDisconnect;
import com.jaoafa.chatwatcher.event.AutoJoin;
import com.jaoafa.chatwatcher.event.CommandMessageEvent;
import com.jaoafa.chatwatcher.event.ContainerEvent;
import com.jaoafa.chatwatcher.lib.*;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
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

public class Main extends ListenerAdapter {
    private static JDA jda;
    private static SocketIOServer socketIOServer;

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
                    new AutoDisconnect(),
                    new ContainerEvent()
                )
                .build();
        } catch (IOException | LoginException e) {
            e.printStackTrace();
        }

        SpeechRecognizeContainer.downAll(null); // すべてのコンテナを落とす (落とし忘れ対策)

        Configuration config = new Configuration();
        config.setHostname("0.0.0.0");
        config.setPort(9092);
        config.setMaxFramePayloadLength(1024 * 1024);
        config.setMaxHttpContentLength(1024 * 1024);

        socketIOServer = new SocketIOServer(config);
        socketIOServer.addEventListener("join", String.class, (client, roomId, ack) -> {
            client.joinRoom(roomId);
            client.sendEvent("joined");
            System.out.println("[SocketIO] " + client.getSessionId() + " joined " + roomId);
        });
        socketIOServer.addEventListener("leave", Void.class, (client, v, ack) -> {
            client.getAllRooms().forEach(client::leaveRoom);
            client.sendEvent("left");
            System.out.println("[SocketIO] " + client.getSessionId() + " left all rooms");
        });
        socketIOServer.addEventListener("message", String.class, (client, json, ack) -> {
            JSONObject data = new JSONObject(json);
            if(!data.has("roomId")) {
                return;
            }
            String roomId = data.getString("roomId");

            System.out.println("[SocketIO|" + roomId + "] " + json);

            String[] split = roomId.split("-");
            if (split.length != 2) {
                return;
            }
            String guildId = split[0];
            String userId = split[1];

            Guild guild = jda.getGuildById(guildId);
            if (guild == null) {
                return;
            }
            if (!ServerManager.isRegistered(guild)) {
                return;
            }

            String result = data.getString("result");
            double confidence = data.getDouble("confidence");
            double percent = Math.ceil(confidence * 10000) / 100;
            boolean isFinal = data.getBoolean("isFinal");

            String userTag = UserCache.get(userId);
            String content = "`" + userTag + "`: `" + result + "` (" + percent + "%)";

            String previous = SpeechRecognizeCache.get(roomId);
            if (previous != null && previous.equals(result) && !isFinal) {
                return;
            }
            SpeechRecognizeCache.set(roomId, result);

            ServerManager.Server server = ServerManager.getServer(guild);
            server.getMessageChannels().forEach(channel -> {
                Message message = MessageManager.get(guild, channel, userId);
                if (message == null) {
                    // メッセージがない場合は作成する
                    Message createdMessage = channel.sendMessage(content).complete();
                    MessageManager.set(guild, channel, userId, createdMessage);
                } else {
                    // メッセージがある場合は編集する
                    message.editMessage(content).queue();
                }

                if(isFinal){
                    MessageManager.remove(guild, channel, userId);
                }
            });
        });
        socketIOServer.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            socketIOServer.stop();
            SpeechRecognizeContainer.downAll(null);
            jda.shutdown();
        }));
    }

    @Override
    public void onReady(@Nonnull ReadyEvent event) {
        System.out.println("Ready: " + jda.getSelfUser().getAsTag());
        ServerManager.load();
    }

    public static JDA getJDA() {
        return jda;
    }

    public static SocketIOServer getSocketIOServer() {
        return socketIOServer;
    }
}
