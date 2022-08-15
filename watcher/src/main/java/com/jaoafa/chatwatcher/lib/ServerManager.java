package com.jaoafa.chatwatcher.lib;

import com.jaoafa.chatwatcher.Main;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.TextChannel;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class ServerManager {
    private static List<Server> servers = new ArrayList<>();

    public static void register(Guild guild) {
        if (servers.stream().anyMatch(server -> server.getGuild().getId().equals(guild.getId()))) {
            return;
        }
        servers.add(new Server(guild));
        save();
    }

    public static void unregister(Guild guild) {
        servers.removeIf(server -> server.getGuild().getId().equals(guild.getId()));
        save();
    }

    public static Server getServer(Guild guild) {
        return servers
            .stream()
            .filter(server -> server
                .getGuild()
                .getId()
                .equals(guild.getId()))
            .findFirst()
            .orElse(null);
    }

    public static boolean isRegistered(Guild guild) {
        return getServer(guild) != null;
    }

    public static void addMessageChannel(Guild guild, MessageChannel channel) {
        if (!isRegistered(guild)) {
            throw new IllegalArgumentException("Guild is not registered.");
        }
        getServer(guild).addMessageChannel(channel);
        save();
    }

    public static void removeMessageChannel(Guild guild, MessageChannel channel) {
        if (!isRegistered(guild)) {
            throw new IllegalArgumentException("Guild is not registered.");
        }
        getServer(guild).removeMessageChannel(channel);
        save();
    }

    public static List<MessageChannel> getMessageChannels(Guild guild) {
        if (!isRegistered(guild)) {
            throw new IllegalArgumentException("Guild is not registered.");
        }
        return getServer(guild).getMessageChannels();
    }

    public static void save() {
        try {
            JSONObject object = new JSONObject();
            JSONArray jsonServer = new JSONArray();
            servers.stream().map(Server::serialize).forEach(jsonServer::put);
            object.put("servers", jsonServer);
            if (!Files.exists(PathEnums.Servers.toPath().getParent())) {
                Files.createDirectories(PathEnums.Servers.toPath().getParent());
            }
            Files.writeString(PathEnums.Servers.toPath(), object.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void load() {
        if (!Files.exists(PathEnums.Servers.toPath())) {
            return;
        }
        try {
            String json = Files.readString(PathEnums.Servers.toPath());
            JSONObject object = new JSONObject(json);
            List<Server> servers = new ArrayList<>();
            JSONArray jsonServers = object.getJSONArray("servers");
            JDA jda = Main.getJDA();
            for (int i = 0; i < jsonServers.length(); i++) {
                servers.add(Server.deserialize(jda, jsonServers.getString(i)));
            }
            ServerManager.servers = servers;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static class Server {
        private Guild guild;
        private List<MessageChannel> MessageChannels = new ArrayList<>();

        public Server(Guild guild) {
            this.guild = guild;
        }

        protected void addMessageChannel(MessageChannel channel) {
            MessageChannels.add(channel);
        }

        protected void removeMessageChannel(MessageChannel channel) {
            MessageChannels.remove(channel);
        }

        public Guild getGuild() {
            return guild;
        }

        public List<MessageChannel> getMessageChannels() {
            return MessageChannels;
        }

        public String serialize() {
            JSONObject object = new JSONObject();
            object.put("guild_id", guild.getId());
            object.put("text_channel_ids", MessageChannels.stream().map(MessageChannel::getId).toArray());
            return object.toString();
        }

        public static Server deserialize(JDA jda, String json) {
            JSONObject object = new JSONObject(json);
            Guild guild = jda.getGuildById(object.getString("guild_id"));
            if (guild == null) {
                throw new IllegalArgumentException("Guild is null.");
            }

            Server server = new Server(guild);
            server.guild = guild;
            List<MessageChannel> MessageChannels = new ArrayList<>();
            JSONArray MessageChannelIds = object.getJSONArray("text_channel_ids");
            for (int i = 0; i < MessageChannelIds.length(); i++) {
                TextChannel textChannel = guild.getTextChannelById(MessageChannelIds.getString(i));

                MessageChannel MessageChannel = textChannel != null ? textChannel : guild.getThreadChannelById(MessageChannelIds.getString(i));
                if (MessageChannel == null) {
                    throw new IllegalArgumentException("MessageChannel is null.");
                }
                MessageChannels.add(MessageChannel);
            }
            server.MessageChannels = MessageChannels;
            return server;
        }
    }
}
