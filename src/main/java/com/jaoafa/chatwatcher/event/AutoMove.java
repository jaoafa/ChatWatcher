package com.jaoafa.chatwatcher.event;

import com.jaoafa.chatwatcher.lib.Utils;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

public class AutoMove extends ListenerAdapter {
    @Override
    public void onGuildVoiceUpdate(@NotNull GuildVoiceUpdateEvent event) {
        AudioChannel oldChannel = event.getOldValue();
        AudioChannel newChannel = event.getNewValue();
        if (oldChannel == null || newChannel == null) return; // 移動以外は除外
        long connectedUsers = oldChannel.getMembers().stream()
                .filter(member -> !member.getUser().isBot())
                .count();
        long newUsers = newChannel.getMembers().stream()
                .filter(member -> !member.getUser().isBot())
                .count();

        if (event.getGuild().getSelfMember().getVoiceState() == null ||
                event.getGuild().getSelfMember().getVoiceState().getChannel() == null) {
            return; // 自身がどのVCにも参加していない
        }

        if (event.getMember().getUser().isBot()) {
            return;
        }

        if (event.getGuild().getSelfMember().getVoiceState().getChannel() != oldChannel) {
            return; // 移動元チャンネルに自身が入っていない
        }

        if (event.getGuild().getAfkChannel() == null &&
                event.getGuild().getAfkChannel().getIdLong() == newChannel.getIdLong()) {
            // VCに残ったユーザーが全員Bot、または誰もいなくなった
            boolean existsUser = newChannel
                    .getMembers()
                    .stream()
                    .anyMatch(member -> !member.getUser().isBot()); // Bot以外がいるかどうか
            if (!existsUser) {
                return;
            }

            event.getGuild().getAudioManager().closeAudioConnection();
            return; // 移動先がAFKチャンネルの場合終了
        }

        if (connectedUsers >= newUsers) {
            return; // 自身がいるチャンネルの人数より、移動先の人数の方が少ない、もしくは同じ場合終了
        }

        Utils.connectVoiceChannel(event.getGuild(), event.getChannelJoined());
    }
}