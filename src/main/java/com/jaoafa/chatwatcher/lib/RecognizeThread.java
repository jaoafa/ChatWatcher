package com.jaoafa.chatwatcher.lib;

import com.jaoafa.chatwatcher.Main;
import net.dv8tion.jda.api.audio.AudioReceiveHandler;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.User;
import org.json.JSONObject;
import org.vosk.Recognizer;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class RecognizeThread implements Runnable {
    @Override
    public void run() {
        try {
            AudioFormat target = new AudioFormat(48000.0f, 16, 1, true, false);
            Recognizer recognizer = new Recognizer(Main.getModel(), 48000.0f);

            Map<AudioRecorder.AudioUser, LinkedList<byte[]>> audioData = AudioRecorder.getSpokenAudioData();
            for (Map.Entry<AudioRecorder.AudioUser, LinkedList<byte[]>> entry : audioData.entrySet()) {
                AudioRecorder.AudioUser uid = entry.getKey();
                Guild guild = uid.guild();
                if (!ServerManager.isRegistered(guild)) {
                    return;
                }
                List<MessageChannel> channels = ServerManager.getMessageChannels(guild);

                LinkedList<byte[]> data = entry.getValue();
                while (!data.isEmpty()) {
                    byte[] bytes = data.poll();
                    if (bytes == null) continue;
                    AudioInputStream ais = AudioSystem.getAudioInputStream(target, new AudioInputStream(new ByteArrayInputStream(bytes), AudioReceiveHandler.OUTPUT_FORMAT, bytes.length));

                    int nbytes;
                    byte[] b = new byte[4096];
                    while ((nbytes = ais.read(b)) >= 0) {
                        recognizer.acceptWaveForm(b, nbytes);
                    }
                }
                String result = new JSONObject(recognizer.getFinalResult()).getString("text").replaceAll(" ", "");
                recognizer.close();
                AudioRecorder.removeSpokenAudioData(uid);
                if (result.isEmpty()) continue;

                User user = uid.user();
                String content = "`" + user.getAsTag() + "`: `" + result + "`";
                channels.forEach(channel -> channel.sendMessage(content).queue());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
