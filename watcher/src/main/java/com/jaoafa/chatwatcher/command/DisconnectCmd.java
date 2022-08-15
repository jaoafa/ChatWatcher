package com.jaoafa.chatwatcher.command;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.managers.AudioManager;

public class DisconnectCmd implements BaseCmd {
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
        AudioManager audioManager = guild.getAudioManager();
        if (!audioManager.isConnected()) {
            event.getHook().editOriginal(":x: いずれのボイスチャットにも参加していないため、退出できません。").queue();
            return;
        }
        audioManager.closeAudioConnection();
        event.getHook().editOriginal(":white_check_mark: ボイスチャットから退出しました。").queue();
    }
}
