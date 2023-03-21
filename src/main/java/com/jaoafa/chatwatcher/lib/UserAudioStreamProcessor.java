package com.jaoafa.chatwatcher.lib;

import net.dv8tion.jda.api.audio.AudioReceiveHandler;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class UserAudioStreamProcessor extends Thread {
    private final String uniqId;
    private final UserAudioStream stream;

    public UserAudioStreamProcessor(String uniqId, UserAudioStream stream) {
        this.uniqId = uniqId;
        this.stream = stream;
    }

    @Override
    public void run() {
        if (System.currentTimeMillis() - stream.getLastRecordedAt() <= 1000) {
            return;
        }
        Utils.println("⏩ %s ends speaking. (and saving...): %s%n".formatted(stream.getUser().getName(), uniqId));
        UserAudioStream.remove(stream);

        try {
            Path path = PathEnums.Recorded.getPath(uniqId + "-" + stream.getStartedRecordedAt() + ".wav");
            byte[] bytes = Files.readAllBytes(stream.getPath());
            AudioSystem.write(
                new AudioInputStream(
                    new ByteArrayInputStream(bytes),
                    AudioReceiveHandler.OUTPUT_FORMAT,
                    bytes.length
                ),
                AudioFileFormat.Type.WAVE,
                path.toFile()
            );
            Files.deleteIfExists(stream.getPath());

            Utils.println("\uD83D\uDCBE %s saved. (and recognizing...): %s%n".formatted(stream.getUser().getName(), uniqId));

            Guild guild = stream.getGuild();
            User user = stream.getUser();

            new SpeechRecognizer(guild, user, path).start();
        } catch (IOException e) {
            e.printStackTrace();
            UserAudioStream.remove(stream);
        }
    }
}
