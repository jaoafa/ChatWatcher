package com.jaoafa.chatwatcher.recorder.lib;

import com.jaoafa.chatwatcher.recorder.Main;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.User;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public class ChatWatcherSocketServer extends WebSocketServer {
    public ChatWatcherSocketServer(int port) {
        super(new InetSocketAddress(port));
        start();
    }

    enum ChatWatcherActions {
        RECOGNIZED((json) -> {
            if (!json.has("guildId") || !json.has("userId") || !json.has("content")) {
                return;
            }
            String guildId = json.getString("guildId");
            String userId = json.getString("userId");
            String content = json.getString("content");

            JDA jda = Main.getJDA();
            if (jda == null) {
                return;
            }
            Guild guild = jda.getGuildById(guildId);
            if (guild == null) {
                return;
            }
            User user = jda.getUserById(userId);
            if (user == null) {
                user = jda.retrieveUserById(userId).complete();
            }
            List<MessageChannel> channels = ServerManager.getMessageChannels(guild);
            String message = "`%s`: `%s`".formatted(user.getAsTag(), content);
            channels.forEach(channel -> channel.sendMessage(message).queue());
        }),
        ;

        private final Consumer<JSONObject> action;

        ChatWatcherActions(Consumer<JSONObject> action) {
            this.action = action;
        }

        public void execute(JSONObject json) {
            action.accept(json);
        }
    }

    @Override
    public void onStart() {
        System.out.println("[ChatWatcherSocketServer] onStart");
    }

    @Override
    public void onOpen(WebSocket ws, ClientHandshake client) {
        System.out.println("[ChatWatcherSocketServer] onOpen: " + ws.getRemoteSocketAddress().getAddress().getHostAddress());
    }

    @Override
    public void onClose(WebSocket ws, int code, String reason, boolean remote) {
        System.out.printf("[ChatWatcherSocketServer] onClose(%s): #%d %s%n", ws.getRemoteSocketAddress().getAddress().getHostAddress(), code, reason);
    }

    @Override
    public void onMessage(WebSocket ws, String message) {
        try {
            JSONObject json = new JSONObject(message);
            if (!json.has("action")) {
                return;
            }
            String action = json.getString("action");
            ChatWatcherActions.valueOf(action.toUpperCase(Locale.ROOT)).execute(json);
        } catch (JSONException | IllegalArgumentException ignored) {
        }
    }

    @Override
    public void onError(WebSocket ws, Exception exception) {
        System.out.println("[ChatWatcherSocketServer] onError: " + ws.getRemoteSocketAddress().getAddress().getHostAddress());
        exception.printStackTrace();
    }
}
