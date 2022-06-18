package com.jaoafa.chatwatcher.lib;

import com.jaoafa.chatwatcher.Main;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
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

    public static void addTextChannel(Guild guild, TextChannel channel) {
        if (!isRegistered(guild)) {
            throw new IllegalArgumentException("Guild is not registered.");
        }
        getServer(guild).addTextChannel(channel);
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
        private List<TextChannel> textChannels = new ArrayList<>();

        public Server(Guild guild) {
            this.guild = guild;
        }

        protected void addTextChannel(TextChannel channel) {
            textChannels.add(channel);
        }

        protected void removeTextChannel(TextChannel channel) {
            textChannels.remove(channel);
        }

        public Guild getGuild() {
            return guild;
        }

        public List<TextChannel> getTextChannels() {
            return textChannels;
        }

        public String serialize() {
            JSONObject object = new JSONObject();
            object.put("guild_id", guild.getId());
            object.put("text_channel_ids", textChannels.stream().map(TextChannel::getId).toArray());
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
            List<TextChannel> textChannels = new ArrayList<>();
            JSONArray textChannelIds = object.getJSONArray("text_channel_ids");
            for (int i = 0; i < textChannelIds.length(); i++) {
                TextChannel textChannel = guild.getTextChannelById(textChannelIds.getString(i));
                if (textChannel == null) {
                    throw new IllegalArgumentException("TextChannel is null.");
                }
                textChannels.add(textChannel);
            }
            server.textChannels = textChannels;
            return server;
        }
    }
}
