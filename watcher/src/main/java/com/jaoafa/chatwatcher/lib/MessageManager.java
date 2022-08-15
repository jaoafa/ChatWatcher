package com.jaoafa.chatwatcher.lib;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.User;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

public class MessageManager {
    private static final Map<String, Message> messages = new HashMap<>();

    public static void set(Guild guild, MessageChannel channel, String userId, Message message) {
        String key = guild.getId() + "-" + channel.getId() + "-" + userId;
        messages.put(key, message);
    }

    @Nullable
    public static Message get(Guild guild, MessageChannel channel, String userId) {
        String key = guild.getId() + "-" + channel.getId() + "-" + userId;
        return messages.get(key);
    }

    public static void remove(Guild guild, MessageChannel channel, String userId) {
        String key = guild.getId() + "-" + channel.getId() + "-" + userId;
        messages.remove(key);
    }
}
