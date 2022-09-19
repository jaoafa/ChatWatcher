package com.jaoafa.chatwatcher.recognizer.lib;

import com.jaoafa.chatwatcher.recognizer.Main;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONException;
import org.json.JSONObject;
import org.vosk.Recognizer;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.function.Consumer;

public class ChatWatcherSocketClient extends WebSocketClient {

    public ChatWatcherSocketClient(URI uri) {
        super(uri);

        connect();
    }

    enum ChatWatcherActions {
        RECORDED((json) -> {
            if (!json.has("guildId") || !json.has("userId") || !json.has("path")) {
                return;
            }
            String guildId = json.getString("guildId");
            String userId = json.getString("userId");
            String path = json.getString("path");

            try (Recognizer recognizer = new Recognizer(Main.getModel(), 48000.0f);
                 InputStream ais = AudioSystem.getAudioInputStream(new BufferedInputStream(new FileInputStream(path)))) {
                int bytes;
                byte[] b = new byte[4096];
                while ((bytes = ais.read(b)) >= 0) {
                    recognizer.acceptWaveForm(b, bytes);
                }
                String result = new JSONObject(recognizer.getFinalResult()).getString("text").replaceAll(" ", "");
                if (result.isEmpty()) return;
                Files.deleteIfExists(Path.of(path));

                Main.getSocketClient().send(new JSONObject()
                        .put("action", "recognized")
                        .put("guildId", guildId)
                        .put("userId", userId)
                        .put("content", result)
                        .toString());
            } catch (IOException | UnsupportedAudioFileException e) {
                throw new RuntimeException(e);
            }
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
    public void onOpen(ServerHandshake server) {
        System.out.println("[ChatWatcherSocketClient] onOpen");
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        System.out.printf("[ChatWatcherSocketClient] onClose: #%d %s%n", code, reason);
    }

    @Override
    public void onMessage(String message) {
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
    public void onError(Exception exception) {
        System.out.println("[ChatWatcherSocketServer] onError");
        exception.printStackTrace();
    }
}
