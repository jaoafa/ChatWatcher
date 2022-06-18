package com.jaoafa.chatwatcher.command;

import com.jaoafa.chatwatcher.lib.Utils;
import net.dv8tion.jda.api.entities.AudioChannel;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

public class SummonCmd implements BaseCmd {
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
        OptionMapping channelMapping = event.getOption("channel");
        AudioChannel channel = channelMapping != null ? channelMapping.getAsVoiceChannel() : getConnectedChannel(member);
        if (channel == null) {
            event.getHook().editOriginal(":x: 接続先のボイスチャンネルを見つけられませんでした。").queue();
            return;
        }
        Utils.connectVoiceChannel(guild, channel);

        event.getHook().editOriginal(":white_check_mark: %s に接続しました。".formatted(channel.getAsMention())).queue();
    }

    private AudioChannel getConnectedChannel(Member member) {
        GuildVoiceState state = member.getVoiceState();
        if (state == null) {
            return null;
        }
        if (state.getChannel() == null) {
            return null;
        }
        return state.getChannel();
    }
}
