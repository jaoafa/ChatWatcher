package com.jaoafa.chatwatcher.recorder.event;

import com.jaoafa.chatwatcher.recorder.lib.ServerManager;
import com.jaoafa.chatwatcher.recorder.lib.Utils;
import net.dv8tion.jda.api.entities.AudioChannel;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceJoinEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceMoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

public class AutoJoin extends ListenerAdapter {
    @Override
    public void onGuildVoiceJoin(@NotNull GuildVoiceJoinEvent event) {
        if (!ServerManager.isRegistered(event.getGuild())) {
            return;
        }
        if (event.getMember().getUser().isBot()) {
            return;
        }
        if (event.getGuild().getSelfMember().getVoiceState() != null &&
                event.getGuild().getSelfMember().getVoiceState().getChannel() != null) {
            return; // 自身がいずれかのVCに参加している
        }

        Utils.connectVoiceChannel(event.getGuild(), event.getChannelJoined());
    }

    @Override
    public void onGuildVoiceMove(GuildVoiceMoveEvent event) {
        if (!ServerManager.isRegistered(event.getGuild())) {
            return;
        }
        AudioChannel oldChannel = event.getOldValue();
        AudioChannel newChannel = event.getNewValue();
        long newUsers = newChannel.getMembers().stream()
                .filter(member -> !member.getUser().isBot())
                .count();

        if (event.getGuild().getSelfMember().getVoiceState() == null ||
                event.getGuild().getSelfMember().getVoiceState().getChannel() == null) {
            return; // 自身がどのVCにも参加していない
        }
        AudioChannel connectedChannel = event.getGuild().getSelfMember().getVoiceState().getChannel();
        long connectedUsers = newChannel.getMembers().stream()
                .filter(member -> !member.getUser().isBot())
                .count();

        if (event.getMember().getUser().isBot()) {
            return;
        }

        if (connectedChannel != oldChannel) {
            return; // 移動元チャンネルに自身が入っていない
        }

        if (connectedUsers >= newUsers) {
            return; // 自身がいるチャンネルの人数より、移動先の人数の方が少ない、もしくは同じ場合終了
        }

        Utils.connectVoiceChannel(event.getGuild(), newChannel);
    }
}