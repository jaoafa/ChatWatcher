package com.jaoafa.chatwatcher.lib;

import net.dv8tion.jda.api.audio.AudioReceiveHandler;
import net.dv8tion.jda.api.audio.UserAudio;
import net.dv8tion.jda.api.entities.User;
import org.jetbrains.annotations.NotNull;

public class AudioRecorder implements AudioReceiveHandler {
    @Override
    public boolean canReceiveUser() {
        return true;
    }

    @Override
    public void handleUserAudio(@NotNull UserAudio userAudio) {
        User user = userAudio.getUser();
        if (user.getIdLong() == 184405311681986560L) {
            return;
        }
        UserAudioStream stream = UserAudioStream.of(user);
        stream.record(userAudio.getAudioData(1d));
    }
}