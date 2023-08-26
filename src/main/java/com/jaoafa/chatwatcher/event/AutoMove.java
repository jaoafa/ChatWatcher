package com.jaoafa.chatwatcher.event;

import com.jaoafa.chatwatcher.lib.Utils;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

public class AutoMove extends ListenerAdapter {
    @Override
    public void onGuildVoiceUpdate(@NotNull GuildVoiceUpdateEvent event) {
        if (event.getMember().getUser().isBot()) return;

        AudioChannel oldChannel = event.getChannelLeft();
        AudioChannel newChannel = event.getChannelJoined();

        // 移動以外は除外
        if (oldChannel == null || newChannel == null) return;

        GuildVoiceState selfVoiceState = event.getGuild().getSelfMember().getVoiceState();

        // 自身がどのVCにも参加していない
        if (selfVoiceState == null || selfVoiceState.getChannel() == null) return;

        // 移動元のチャンネルに自身がいない
        if (selfVoiceState.getChannel().getIdLong() != oldChannel.getIdLong()) return;

        long oldChannelUsers = getChannelMembers(oldChannel);
        long newChannelUsers = getChannelMembers(newChannel);

        if (isAfkChannel(newChannel)) {
            // 現在のチャンネルにまだ人がいる
            if (oldChannelUsers != 0) return;

            // 最後の一人が AFK に移動した
            event.getGuild().getAudioManager().closeAudioConnection();
            return;
        }

        // 移動先の人数が自身がいるチャンネルの人数以下の場合
        if (oldChannelUsers >= newChannelUsers) return;

        Utils.connectVoiceChannel(event.getGuild(), event.getChannelJoined());
    }

    long getChannelMembers(@NotNull AudioChannel channel) {
        return channel.getMembers().stream()
                .filter(member -> !member.getUser().isBot())
                .count();
    }

    boolean isAfkChannel(@NotNull AudioChannel channel) {
        if (channel.getGuild().getAfkChannel() == null) return false;
        return channel.getIdLong() == channel.getGuild().getAfkChannel().getIdLong();
    }
}