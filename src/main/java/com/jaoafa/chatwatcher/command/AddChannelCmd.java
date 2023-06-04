package com.jaoafa.chatwatcher.command;

import com.jaoafa.chatwatcher.lib.ServerManager;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
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
        MessageChannel channel = channelMapping != null ? channelMapping.getAsChannel().asTextChannel() : event.getMessageChannel();
        OptionMapping typeMapping = event.getOption("type");
        String type = typeMapping != null ? typeMapping.getAsString() : null;
        ServerManager.addMessageChannel(guild, type, channel);
        event.getHook().editOriginal(":white_check_mark: 文字起こしタイプ `%s` の送信先チャンネルとして %s を追加しました。".formatted(type, channel.getAsMention())).queue();
    }
}
