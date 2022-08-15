package com.jaoafa.chatwatcher.event;

import com.jaoafa.chatwatcher.lib.SpeechRecognizeContainer;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceJoinEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceLeaveEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceMoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class ContainerEvent extends ListenerAdapter {
    @Override
    public void onGuildVoiceJoin(@NotNull GuildVoiceJoinEvent event) {
        if (event.getGuild().getSelfMember().getVoiceState() == null ||
            event.getGuild().getSelfMember().getVoiceState().getChannel() == null) {
            return; // 自身がどのVCにも参加していない
        }
        if (event.getGuild().getSelfMember().getVoiceState().getChannel().getIdLong() != event.getChannelJoined().getIdLong()) {
            return; // 参加チャンネルが自身のいるVCと異なる
        }

        SpeechRecognizeContainer.up(event.getGuild(), event.getMember());
    }

    @Override
    public void onGuildVoiceMove(@NotNull GuildVoiceMoveEvent event) {
        if (event.getGuild().getSelfMember().getVoiceState() == null ||
            event.getGuild().getSelfMember().getVoiceState().getChannel() == null) {
            return; // 自身がどのVCにも参加していない
        }

        if(event.getMember().getIdLong() == event.getGuild().getSelfMember().getIdLong()) {
            // 自分が移動した

            // すべてのコンテナを落とす
            SpeechRecognizeContainer.downAll(event.getGuild());
            return;
        }

        if (event.getGuild().getSelfMember().getVoiceState().getChannel().getIdLong() != event.getChannelJoined().getIdLong()) {
            // 移動先チャンネルが自身のいるVCと異なる
            SpeechRecognizeContainer.down(event.getGuild(), event.getMember()); // 落とす
            return;
        }

        SpeechRecognizeContainer.up(event.getGuild(), event.getMember());
    }

    @Override
    public void onGuildVoiceLeave(@NotNull GuildVoiceLeaveEvent event) {
        if (event.getGuild().getSelfMember().getVoiceState() == null ||
            event.getGuild().getSelfMember().getVoiceState().getChannel() == null) {
            return; // 自身がどのVCにも参加していない
        }
        if (event.getGuild().getSelfMember().getVoiceState().getChannel().getIdLong() != event.getChannelLeft().getIdLong()) {
            return; // 退出されたチャンネルが自身のいるVCと異なる
        }
        SpeechRecognizeContainer.down(event.getGuild(), event.getMember());
    }
}
