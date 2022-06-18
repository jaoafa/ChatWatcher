package com.jaoafa.chatwatcher.lib;

import net.dv8tion.jda.api.audio.AudioReceiveHandler;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class UserAudioStreamMonitor implements Runnable {
    public static void register(ScheduledExecutorService executorService) {
        executorService.scheduleAtFixedRate(new UserAudioStreamMonitor(), 0, 100, TimeUnit.MILLISECONDS);
    }

    @Override
    public void run() {
        for (Map.Entry<String, UserAudioStream> entry : UserAudioStream.getStreams().entrySet()) {
            String userId = entry.getKey();
            UserAudioStream stream = entry.getValue();

            if (stream.getLastRecordedAt() + 1000 > System.currentTimeMillis()) {
                continue;
            }
            System.out.println("User " + userId + " has stopped speaking");
            Path cwPath = stream.getPath();
            stream.destroy();

            try {
                Path path = PathEnums.Recorded.getPath(userId + "-" + stream.getStartedRecordedAt() + ".wav");
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
                Files.deleteIfExists(cwPath);

                new SpeechRecognizer(path).start();
            } catch (IOException e) {
                e.printStackTrace();
                stream.destroy();
            }
        }
    }
}