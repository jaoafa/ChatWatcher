package com.jaoafa.chatwatcher.recorder.command;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

public interface BaseCmd {
    void execute(SlashCommandInteractionEvent event);
}
