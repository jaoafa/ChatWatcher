package com.jaoafa.chatwatcher.lib;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

public class UserCache {
    static Map<String, String> users = new HashMap<>();

    public static void set(User user){
        users.put(user.getId(), user.getAsTag());
    }

    @Nullable
    public static String get(String id){
        return users.get(id);
    }
}
