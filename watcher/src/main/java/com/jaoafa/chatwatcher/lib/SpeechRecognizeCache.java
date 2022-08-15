package com.jaoafa.chatwatcher.lib;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

public class SpeechRecognizeCache {
    static Map<String, String> caches = new HashMap<>();

    public static void set(String roomId, String text) {
        caches.put(roomId, text);
    }

    @Nullable
    public static String get(String roomId){
        return caches.get(roomId);
    }

    public static void remove(String roomId){
        caches.remove(roomId);
    }
}
