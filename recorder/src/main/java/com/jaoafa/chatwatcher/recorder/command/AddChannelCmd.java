package com.jaoafa.chatwatcher.recorder.command;

import com.jaoafa.chatwatcher.recorder.lib.ServerManager;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

public class AddChannelCmd implements BaseCmd {
    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Member member = event.getMember();
        Guild guild = event.getGuild();
        if (guild == null) {
            return;
        }
        if (member == null) {
            return;
        }
        if (!ServerManager.isRegistered(guild)) {
            event.getHook().editOriginal(":x: このサーバーは登録されていません。").queue();
            return;
        }
        OptionMapping channelMapping = event.getOption("channel");
        MessageChannel channel = channelMapping != null ? channelMapping.getAsMessageChannel() : event.getMessageChannel();
        if (channel == null) {
            event.getHook().editOriginal(":x: 登録するチャンネルを見つけられませんでした。").queue();
            return;
        }
        ServerManager.addMessageChannel(guild, channel);
        event.getHook().editOriginal(":white_check_mark: 文字起こしテキスト送信先チャンネルとして %s を追加しました。".formatted(channel.getAsMention())).queue();
    }
}
