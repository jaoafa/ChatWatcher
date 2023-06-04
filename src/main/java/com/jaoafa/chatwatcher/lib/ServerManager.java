package com.jaoafa.chatwatcher.lib;

import com.jaoafa.chatwatcher.Main;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    public static void addMessageChannel(Guild guild, String type, MessageChannel channel) {
        if (!isRegistered(guild)) {
            throw new IllegalArgumentException("Guild is not registered.");
        }
        getServer(guild).addMessageChannel(type, channel);
        save();
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

    static class Server {
        private Guild guild;
        private final Map<String, List<MessageChannel>> messageChannels = new HashMap<>();

        public Server(Guild guild) {
            this.guild = guild;
        }

        protected void addMessageChannel(String type, MessageChannel channel) {
            if (!messageChannels.containsKey(type)) {
                messageChannels.put(type, new ArrayList<>());
            }
            List<MessageChannel> channels = messageChannels.get(type);
            if (channels.contains(channel)) {
                return;
            }
            channels.add(channel);
            messageChannels.put(type, channels);
        }

        public Guild getGuild() {
            return guild;
        }

        public List<MessageChannel> getMessageChannels(String type) {
            return messageChannels.get(type);
        }

        public String serialize() {
            JSONObject object = new JSONObject();
            object.put("guild_id", guild.getId());
            JSONObject jsonMessageChannels = new JSONObject();
            for (Map.Entry<String, List<MessageChannel>> entry : messageChannels.entrySet()) {
                JSONArray jsonMessageChannelIds = new JSONArray();
                for (MessageChannel MessageChannel : entry.getValue()) {
                    jsonMessageChannelIds.put(MessageChannel.getId());
                }
                jsonMessageChannels.put(entry.getKey(), jsonMessageChannelIds);
            }
            object.put("channels", jsonMessageChannels);
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

            if (!object.has("channels")) {
                return server;
            }

            JSONObject jsonMessageChannels = object.getJSONObject("channels");
            for (String type : jsonMessageChannels.keySet()) {
                JSONArray jsonMessageChannelIds = jsonMessageChannels.getJSONArray(type);
                for (int i = 0; i < jsonMessageChannelIds.length(); i++) {
                    String id = jsonMessageChannelIds.getString(i);
                    TextChannel channel = guild.getTextChannelById(id);
                    if (channel == null) {
                        continue;
                    }
                    server.addMessageChannel(type, channel);
                }
            }
            return server;
        }
    }
}
