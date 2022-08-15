package com.jaoafa.chatwatcher.lib;

import net.dv8tion.jda.api.entities.AudioChannel;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.managers.AudioManager;

import java.text.SimpleDateFormat;
import java.util.Date;

public class Utils {
    public static void connectVoiceChannel(Guild guild, AudioChannel voiceChannel) {
        AudioManager manager = guild.getAudioManager();
        AudioRecorder recorder = new AudioRecorder(guild);
        manager.setReceivingHandler(recorder);
        manager.setAutoReconnect(true);
        manager.openAudioConnection(voiceChannel);

        for(Member member : voiceChannel.getMembers()){
            SpeechRecognizeContainer.up(guild, member);
        }
    }
}
