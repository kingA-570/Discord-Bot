package com.domistats.bot.discord.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.springframework.stereotype.Component;

@Component
public class HelpCommand implements CommandHandler {

    @Override
    public String name() {
        return "help";
    }

    @Override
    public CommandData definition() {
        return Commands.slash(name(), "Show available War Radar commands.");
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🛰️ DomiStats War Radar — Commands")
                .setDescription("""
                        `/spinning` — Alliances currently searching for a war
                        `/alliance <name>` — Detailed alliance info
                        `/war <name>` — An alliance's current war, if any
                        `/warhistory <name> [page]` — Recent war record
                        `/compare <alliance1> <alliance2>` — Side-by-side comparison
                        `/watch <name>` — Add an alliance to this server's watchlist
                        `/unwatch <name>` — Remove an alliance from the watchlist
                        `/watchlist` — Show this server's watched alliances
                        `/setprofile` — Set your alliance's war preferences for spin matching
                        `/config channel|dashboard|notifications|status` — Server settings (Manage Server permission required)
                        `/help` — This message
                        """)
                .setFooter("All comparisons and compatibility scores are the bot's own estimates, not official DomiStats figures.");
        event.replyEmbeds(embed.build()).queue();
    }
}
