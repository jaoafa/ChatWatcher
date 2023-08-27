package com.jaoafa.chatwatcher.event;

import com.jaoafa.chatwatcher.lib.Utils;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

public class AutoJoin extends ListenerAdapter {
    @Override
    public void onGuildVoiceUpdate(@NotNull GuildVoiceUpdateEvent event) {
        if (event.getMember().getUser().isBot()) return;

        // 参加と移動以外は除外
        if (event.getChannelJoined() == null) return;

        GuildVoiceState selfVoiceState = event.getGuild().getSelfMember().getVoiceState();

        // 自身がいずれかのVCに参加している
        if (selfVoiceState != null && selfVoiceState.getChannel() != null) return;

        Utils.connectVoiceChannel(event.getGuild(), event.getChannelJoined());
    }
}