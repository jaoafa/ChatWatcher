package com.jaoafa.chatwatcher.lib;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
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
    }

    public static void println(String str) {
        SimpleDateFormat sdf = new SimpleDateFormat("[yyyy/MM/dd HH:mm:ss] ");
        System.out.println(sdf.format(new Date()) + str);
    }
}
