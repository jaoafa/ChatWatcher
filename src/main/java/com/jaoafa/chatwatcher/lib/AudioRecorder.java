package com.jaoafa.chatwatcher.lib;

import net.dv8tion.jda.api.audio.AudioReceiveHandler;
import net.dv8tion.jda.api.audio.UserAudio;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import org.jetbrains.annotations.NotNull;

public class AudioRecorder implements AudioReceiveHandler {
    private final Guild guild;

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
        UserAudioStream stream = UserAudioStream.of(guild, user);
        stream.record(userAudio.getAudioData(1d));
    }
}