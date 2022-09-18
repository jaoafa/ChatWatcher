package com.jaoafa.chatwatcher.command;

import com.jaoafa.chatwatcher.lib.ServerManager;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

import java.util.List;

public class RemoveChannelCmd implements BaseCmd {
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
            event.getHook().editOriginal(":x: 登録解除するチャンネルを見つけられませんでした。").queue();
            return;
        }
        List<MessageChannel> channels = ServerManager.getMessageChannels(guild);
        if (channels.stream().noneMatch(c -> c.getId().equals(channel.getId()))) {
            event.getHook().editOriginal(":x: このチャンネルは登録されていません。").queue();
            return;
        }
        ServerManager.removeMessageChannel(guild, channel);
        event.getHook().editOriginal(":white_check_mark: チャンネル %s を削除しました。".formatted(channel.getAsMention())).queue();
    }
}
