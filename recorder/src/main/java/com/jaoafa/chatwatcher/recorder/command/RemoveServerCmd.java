package com.jaoafa.chatwatcher.recorder.command;

import com.jaoafa.chatwatcher.recorder.event.CommandMessageEvent;
import com.jaoafa.chatwatcher.recorder.lib.ServerManager;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

public class RemoveServerCmd implements BaseCmd {
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
            event.getHook().editOriginal(":x: 登録されていません。").queue();
            return;
        }
        ServerManager.unregister(guild);
        event.getHook().editOriginal(":white_check_mark: サーバーを登録解除しました。").queue();

        CommandMessageEvent.registerCommand(event.getJDA());
    }
}
