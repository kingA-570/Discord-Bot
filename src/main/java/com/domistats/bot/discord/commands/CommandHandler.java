package com.domistats.bot.discord.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;

/**
 * A single slash command. Implementations are auto-discovered as Spring beans
 * (see SlashCommandListener), so adding a new DomiStats feature is just a
 * matter of dropping in a new class here - per requirement #13 (modularity).
 */
public interface CommandHandler {

    /** Command name, e.g. "spinning" (without the leading slash). */
    String name();

    /** Slash command definition (name, description, options) registered with Discord. */
    CommandData definition();

    /** Executes the command. Implementations should ack quickly (deferReply) if DomiStats calls may be slow. */
    void handle(SlashCommandInteractionEvent event);
}
