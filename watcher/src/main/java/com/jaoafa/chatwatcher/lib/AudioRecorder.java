package com.jaoafa.chatwatcher.lib;

import com.corundumstudio.socketio.SocketIOServer;
import com.jaoafa.chatwatcher.Main;
import net.dv8tion.jda.api.audio.AudioReceiveHandler;
import net.dv8tion.jda.api.audio.UserAudio;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import org.jetbrains.annotations.NotNull;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

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
        try {
            User user = userAudio.getUser();
            byte[] audioData = userAudio.getAudioData(1d);
            AudioInputStream stream = new AudioInputStream(
                new ByteArrayInputStream(audioData),
                AudioReceiveHandler.OUTPUT_FORMAT,
                audioData.length
            );
            ByteArrayOutputStream arrayOutputStream = new ByteArrayOutputStream();
            AudioSystem.write(stream, AudioFileFormat.Type.WAVE, arrayOutputStream);

            // System.out.println("[AudioRecorder] " + user.getName() + ": " + audioData.length + " bytes");

            SocketIOServer server = Main.getSocketIOServer();
            String roomId = guild.getId() + "-" + user.getId();
            byte[] bytes = arrayOutputStream.toByteArray();
            //noinspection PrimitiveArrayArgumentToVarargsMethod
            server.getRoomOperations(roomId).sendEvent("audio", bytes);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}