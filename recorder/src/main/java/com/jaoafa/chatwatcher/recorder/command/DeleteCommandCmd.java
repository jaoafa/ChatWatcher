package com.jaoafa.chatwatcher.recorder.command;

import com.jaoafa.chatwatcher.recorder.event.CommandMessageEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

public class DeleteCommandCmd implements BaseCmd {
    @Override
    public void execute(SlashCommandInteractionEvent event) {
        CommandMessageEvent.unregisterCommand(event.getJDA());
    }
}
