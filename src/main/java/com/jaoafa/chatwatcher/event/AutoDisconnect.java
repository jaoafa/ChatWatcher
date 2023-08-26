package com.jaoafa.chatwatcher.event;

import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

public class AutoDisconnect extends ListenerAdapter {
    @Override
    public void onGuildVoiceUpdate(@NotNull GuildVoiceUpdateEvent event) {
        if (event.getMember().getUser().isBot()) return;

        AudioChannel oldChannel = event.getChannelLeft();
        AudioChannel newChannel = event.getChannelJoined();

        // 退出以外は除外
        if (oldChannel == null || newChannel != null) return;

        GuildVoiceState selfVoiceState = event.getGuild().getSelfMember().getVoiceState();

        // 自身がどのVCにも参加していない
        if (selfVoiceState == null || selfVoiceState.getChannel() == null) return;

        // 退出されたチャンネルが自身のいるVCと異なる
        if (selfVoiceState.getChannel().getIdLong() != oldChannel.getIdLong()) return;

        // 現在のチャンネルにまだ人がいるかどうか
        boolean userExists = oldChannel
                .getMembers()
                .stream()
                .anyMatch(member -> !member.getUser().isBot()); // Bot以外がいるかどうか

        if (userExists) return;

        event.getGuild().getAudioManager().closeAudioConnection();
    }
}