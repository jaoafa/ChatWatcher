package com.jaoafa.chatwatcher.event;

import com.jaoafa.chatwatcher.command.*;
import com.jaoafa.chatwatcher.lib.ServerManager;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

import java.util.List;
import java.util.Map;

public class CommandMessageEvent extends ListenerAdapter {
    private static final List<SubcommandData> defaultSubCommands = List.of(
            new SubcommandData("add-server", "このサーバを新規登録します。"),
            new SubcommandData("remove-server", "このサーバを登録解除します。")
    );

    private static final List<SubcommandData> registeredSubCommands = List.of(
            new SubcommandData("summon", "実行者が参加しているボイスチャンネルに参加します。")
                    .addOptions(
                            new OptionData(OptionType.CHANNEL, "channel", "参加するボイスチャンネル")
                                    .setChannelTypes(ChannelType.VOICE)
                                    .setRequired(false)
                    ),
            new SubcommandData("disconnect", "ボイスチャンネルから退出します。"),
            new SubcommandData("add-channel", "サーバに文字起こしテキスト送信先チャンネルを登録します。")
                    .addOptions(
                            new OptionData(OptionType.STRING, "type", "文字起こしタイプ")
                                    .setRequired(true),
                            new OptionData(OptionType.CHANNEL, "channel", "登録する文字起こしテキスト送信先チャンネル")
                                    .setChannelTypes(ChannelType.TEXT)
                                    .setRequired(false)
                    ),
            new SubcommandData("remove-channel", "サーバから文字起こしテキスト送信先チャンネルを登録解除します。")
    );

    private final Map<String, BaseCmd> commands = Map.of(
            "summon", new SummonCmd(),
            "disconnect", new DisconnectCmd(),
            "add-server", new AddServerCmd(),
            "remove-server", new RemoveServerCmd(),
            "add-channel", new AddChannelCmd(),
            "remove-channel", new RemoveServerCmd()
    );

    @Override
    public void onReady(ReadyEvent event) {
        registerCommand(event.getJDA());
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) {
            return;
        }
        if (!event.isGuildCommand()) {
            return;
        }
        if (!event.getName().equals("chatwatcher")) {
            return;
        }
        String subCommandName = event.getSubcommandName();
        if (!commands.containsKey(subCommandName)) {
            event.reply("このコマンドは存在しません。").queue();
            return;
        }
        event.deferReply().queue();
        commands.get(subCommandName).execute(event);
    }

    public static void registerCommand(JDA jda) {
        List<Guild> guilds = jda.getGuilds();
        for (Guild guild : guilds) {
            SlashCommandData slashCommandData = Commands.slash("chatwatcher", "チャットウォッチャー")
                    .addSubcommands(defaultSubCommands);
            if (ServerManager.isRegistered(guild)) {
                slashCommandData.addSubcommands(registeredSubCommands);
            }
            guild.upsertCommand(slashCommandData).queue();
        }
    }
}
