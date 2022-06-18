package com.jaoafa.chatwatcher.lib;

import net.dv8tion.jda.api.entities.AudioChannel;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.managers.AudioManager;

public class Utils {
    public static void connectVoiceChannel(Guild guild, AudioChannel voiceChannel) {
        AudioManager manager = guild.getAudioManager();
        AudioRecorder recorder = new AudioRecorder();
        manager.setReceivingHandler(recorder);
        manager.setAutoReconnect(true);
        manager.openAudioConnection(voiceChannel);
    }
}
