package com.jaoafa.chatwatcher.lib;

import net.dv8tion.jda.api.entities.User;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class UserAudioStream {
    static Map<String, UserAudioStream> userAudioStreams = new HashMap<>();

    private final User user;
    private final Path path;
    private final long startedRecordedAt;
    private long lastRecordedAt;

    private UserAudioStream(User user) {
        this.user = user;
        this.startedRecordedAt = System.currentTimeMillis();
        this.path = Path.of("UserAudioStreams/%s-%d.cw".formatted(user.getId(), startedRecordedAt));

        try {
            if (!Files.exists(this.path.getParent())) {
                Files.createDirectories(this.path.getParent());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static UserAudioStream of(User user) {
        if (userAudioStreams.containsKey(user.getId())) {
            return userAudioStreams.get(user.getId());
        }
        UserAudioStream userAudioStream = new UserAudioStream(user);
        userAudioStream.save();
        return userAudioStream;
    }

    public void record(byte[] data) {
        try {
            if (!Files.exists(this.path)) {
                System.out.println("UserAudioStream.record: " + this.path + " does not exist. Creating...");
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
        userAudioStreams.put(user.getId(), this);
    }

    public void destroy() {
        userAudioStreams.remove(user.getId());
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
        return Collections.unmodifiableMap(userAudioStreams);
    }

    @Override
    public String toString() {
        return "UserAudioStream{" +
            "user=" + user.getAsTag() +
            ", path=" + path +
            ", startedRecordedAt=" + new Date(startedRecordedAt) +
            ", lastRecordedAt=" + new Date(lastRecordedAt) +
            '}';
    }
}
