package com.jaoafa.chatwatcher.lib;

import net.dv8tion.jda.api.audio.AudioReceiveHandler;
import net.dv8tion.jda.api.audio.UserAudio;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

public class AudioRecorder implements AudioReceiveHandler {
    private final Guild guild;
    private static final Map<AudioUser, LinkedList<byte[]>> audioData = new HashMap<>();
    private static final Map<AudioUser, Long> lastSpeakTime = new HashMap<>();

    public AudioRecorder(@NotNull Guild guild) {
        this.guild = guild;
    }

    @Override
    public boolean canReceiveUser() {
        return true;
    }

    @Override
    public void handleUserAudio(@NotNull UserAudio userAudio) {
        User user = userAudio.getUser();
        AudioUser uid = new AudioUser(guild, user);
        if (!audioData.containsKey(uid)) {
            audioData.put(uid, new LinkedList<>());
        }
        audioData.get(uid).add(userAudio.getAudioData(1.0));
        lastSpeakTime.put(uid, System.currentTimeMillis());
    }

    record AudioUser(Guild guild, User user) {
    }

    public static Map<AudioUser, LinkedList<byte[]>> getSpokenAudioData() {
        return audioData.entrySet().stream()
                .filter(entry -> System.currentTimeMillis() - lastSpeakTime.get(entry.getKey()) > 1000)
                .collect(HashMap::new, (map, entry) -> map.put(entry.getKey(), entry.getValue()), HashMap::putAll);
    }

    public static void removeSpokenAudioData(AudioUser uid) {
        audioData.remove(uid);
        lastSpeakTime.remove(uid);
    }
}