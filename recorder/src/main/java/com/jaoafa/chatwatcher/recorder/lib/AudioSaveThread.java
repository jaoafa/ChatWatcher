package com.jaoafa.chatwatcher.recorder.lib;

import com.jaoafa.chatwatcher.recorder.Main;
import net.dv8tion.jda.api.audio.AudioReceiveHandler;
import net.dv8tion.jda.api.entities.Guild;
import org.json.JSONObject;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.Map;

public class AudioSaveThread implements Runnable {
    @Override
    public void run() {
        try {
            AudioFormat target = new AudioFormat(48000.0f, 16, 1, true, false);

            Map<AudioRecorder.AudioUser, LinkedList<byte[]>> audioData = AudioRecorder.getSpokenAudioData();
            for (Map.Entry<AudioRecorder.AudioUser, LinkedList<byte[]>> entry : audioData.entrySet()) {
                AudioRecorder.AudioUser uid = entry.getKey();
                Guild guild = uid.guild();
                if (!ServerManager.isRegistered(guild)) {
                    return;
                }

                byte[] merged = null;
                LinkedList<byte[]> data = entry.getValue();
                while (!data.isEmpty()) {
                    byte[] bytes = data.poll();
                    if (bytes == null) continue;
                    if (merged == null) {
                        merged = bytes;
                        continue;
                    }
                    byte[] newData = new byte[merged.length + bytes.length];
                    System.arraycopy(merged, 0, newData, 0, merged.length);
                    System.arraycopy(bytes, 0, newData, merged.length, bytes.length);
                    merged = newData;
                }
                if (merged == null) continue;
                AudioInputStream ais = AudioSystem.getAudioInputStream(target, new AudioInputStream(new ByteArrayInputStream(merged), AudioReceiveHandler.OUTPUT_FORMAT, merged.length));
                Path path = PathEnums.AudioData.getPath(uid.guild().getId() + "-" + uid.user().getId() + ".wav");
                AudioSystem.write(ais, AudioFileFormat.Type.WAVE, path.toFile());
                AudioRecorder.removeSpokenAudioData(uid);

                Main.getSocketServer().broadcast(new JSONObject()
                        .put("action", "recorded")
                        .put("guildId", uid.guild().getId())
                        .put("userId", uid.user().getId())
                        .put("path", path.toString())
                        .toString());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
