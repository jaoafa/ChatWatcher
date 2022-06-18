package com.jaoafa.chatwatcher.event;

import com.jaoafa.chatwatcher.lib.Utils;
import net.dv8tion.jda.api.entities.AudioChannel;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.managers.AudioManager;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.function.Consumer;

public class CommandMessageEvent extends ListenerAdapter {
    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) {
            return; // ボットのメッセージは無視
        }
        if (!event.isFromGuild()) {
            return; // Guild以外のメッセージは無視
        }
        String prefix = "/";

        if (!event.getMessage().getContentRaw().startsWith(prefix)) {
            return;
        }
        String[] raw = event.getMessage().getContentRaw().split(" ");
        String command = raw[0].substring(prefix.length());

        if (!commands.containsKey(command)) {
            return;
        }
        commands.get(command).accept(event);
    }

    Map<String, Consumer<MessageReceivedEvent>> commands = Map.of(
        "join", event -> joinMemberVoiceChannel(event.getMessage(), event.getMember()),
        "summon", event -> joinMemberVoiceChannel(event.getMessage(), event.getMember()),
        "left", event -> disconnectVoiceChannel(event.getMessage()),
        "leave", event -> disconnectVoiceChannel(event.getMessage())
    );

    private void joinMemberVoiceChannel(Message message, Member member) {
        if (member == null) {
            return;
        }
        GuildVoiceState state = member.getVoiceState();
        if (state == null) {
            return;
        }
        if (state.getChannel() == null) {
            return;
        }
        AudioChannel voiceChannel = state.getChannel();

        Utils.connectVoiceChannel(message.getGuild(), voiceChannel);

        message.reply(":white_check_mark: %s に接続しました。".formatted(voiceChannel.getName())).queue();
    }

    private void disconnectVoiceChannel(Message message) {
        AudioManager audioManager = message.getGuild().getAudioManager();
        if (!audioManager.isConnected()) {
            message.reply(":x: いずれのボイスチャットにも参加していないため、退出できません。").queue();
            return;
        }
        audioManager.closeAudioConnection();
        message.reply(":white_check_mark: ボイスチャットから退出しました。").queue();
    }
}
