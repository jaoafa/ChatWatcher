package com.jaoafa.chatwatcher.event;

import com.jaoafa.chatwatcher.lib.SpeechRecognizeContainer;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceLeaveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

public class AutoDisconnect extends ListenerAdapter {
    @Override
    public void onGuildVoiceLeave(@NotNull GuildVoiceLeaveEvent event) {
        if (event.getGuild().getSelfMember().getVoiceState() == null ||
            event.getGuild().getSelfMember().getVoiceState().getChannel() == null) {
            return; // 自身がどのVCにも参加していない
        }
        if (event.getGuild().getSelfMember().getVoiceState().getChannel().getIdLong() != event.getChannelLeft().getIdLong()) {
            return; // 退出されたチャンネルが自身のいるVCと異なる
        }

        // VCに残ったユーザーが全員Bot、または誰もいなくなった
        boolean existsUser = event
            .getChannelLeft()
            .getMembers()
            .stream()
            .anyMatch(member -> !member.getUser().isBot()); // Bot以外がいるかどうか

        if (existsUser) {
            return;
        }
        event.getGuild().getAudioManager().closeAudioConnection();
        SpeechRecognizeContainer.downAll(event.getGuild());
    }
}