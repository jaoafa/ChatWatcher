package com.jaoafa.chatwatcher.recognizer;

import com.jaoafa.chatwatcher.recognizer.lib.ChatWatcherSocketClient;
import com.jaoafa.chatwatcher.recognizer.lib.PathEnums;
import org.vosk.Model;

import java.io.IOException;
import java.net.URI;

public class Main {
    private static Model model;
    private static ChatWatcherSocketClient socketClient;

    public static void main(String[] args) {
        try {
            model = new Model(PathEnums.Model.toPath().toString());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        socketClient = new ChatWatcherSocketClient(URI.create("ws://recorder:10000"));
    }

    public static Model getModel() {
        return model;
    }

    public static ChatWatcherSocketClient getSocketClient() {
        return socketClient;
    }
}
