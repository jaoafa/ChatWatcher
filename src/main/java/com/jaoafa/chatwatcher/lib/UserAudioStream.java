package com.jaoafa.chatwatcher.lib;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class UserAudioStream {
    static Map<String, UserAudioStream> userAudioStreams = new HashMap<>();

    private final Guild guild;
    private final User user;
    private final Path path;
    private final long startedRecordedAt;
    private long lastRecordedAt;

    private UserAudioStream(Guild guild, User user) {
        this.guild = guild;
        this.user = user;
        this.startedRecordedAt = System.currentTimeMillis();
        this.path = Path.of(PathEnums.UserAudioStreams.toString(), "%s-%d.cw".formatted(user.getId(), startedRecordedAt));

        try {
            if (!Files.exists(this.path.getParent())) {
                Files.createDirectories(this.path.getParent());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static UserAudioStream of(Guild guild, User user) {
        if (userAudioStreams.containsKey(guild.getId() + "-" + user.getId())) {
            return userAudioStreams.get(guild.getId() + "-" + user.getId());
        }
        UserAudioStream userAudioStream = new UserAudioStream(guild, user);
        userAudioStream.save();
        return userAudioStream;
    }

    public void record(byte[] data) {
        try {
            if (!Files.exists(this.path)) {
                Utils.println("\uD83D\uDCAC %s starts speaking...".formatted(this.user.getAsTag()));
                Files.write(this.path, data);
                return;
            }

            byte[] prev = Files.readAllBytes(this.path);
            byte[] newData = new byte[prev.length + data.length];
            System.arraycopy(prev, 0, newData, 0, prev.length);
            System.arraycopy(data, 0, newData, prev.length, data.length);
            Files.write(this.path, newData);

            lastRecordedAt = System.currentTimeMillis();
        } catch (IOException e) {
            e.printStackTrace();
        }

        save();
    }

    public void save() {
        userAudioStreams.put(guild.getId() + "-" + user.getId(), this);
    }

    public static void remove(UserAudioStream stream) {
        userAudioStreams.remove(stream.getGuild().getId() + "-" + stream.getUser().getId());
    }

    public Guild getGuild() {
        return guild;
    }

    public User getUser() {
        return user;
    }

    public Path getPath() {
        return path;
    }

    public long getStartedRecordedAt() {
        return startedRecordedAt;
    }

    public long getLastRecordedAt() {
        return lastRecordedAt;
    }

    public static Map<String, UserAudioStream> getStreams() {
        return userAudioStreams;
    }

    @Override
    public String toString() {
        return "UserAudioStream{" +
            "user=" + user.getAsTag() +
            ", path=" + path +
            ", startedRecordedAt=" + startedRecordedAt +
            ", lastRecordedAt=" + lastRecordedAt + " (" + (System.currentTimeMillis() - getLastRecordedAt()) + ")" +
            '}';
    }
}
