package com.jaoafa.chatwatcher.event;

import com.jaoafa.chatwatcher.lib.Utils;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

public class AutoJoin extends ListenerAdapter {
    @Override
    public void onGuildVoiceUpdate(@NotNull GuildVoiceUpdateEvent event) {
        if (event.getChannelJoined() == null) return; // 参加と移動以外は除外
        if (event.getMember().getUser().isBot()) {
            return;
        }
        if (event.getGuild().getSelfMember().getVoiceState() != null &&
                event.getGuild().getSelfMember().getVoiceState().getChannel() != null) {
            return; // 自身がいずれかのVCに参加している
        }
        Utils.connectVoiceChannel(event.getGuild(), event.getChannelJoined());
    }
}