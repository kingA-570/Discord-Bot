package com.domistats.bot.discord.commands;

import com.domistats.bot.entity.WatchlistEntry;
import com.domistats.bot.service.WatchlistService;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class WatchlistCommand implements CommandHandler {

    private final WatchlistService watchlistService;

    public WatchlistCommand(WatchlistService watchlistService) {
        this.watchlistService = watchlistService;
    }

    @Override
    public String name() {
        return "watchlist";
    }

    @Override
    public CommandData definition() {
        return Commands.slash(name(), "Show this server's watched alliances.");
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        List<WatchlistEntry> entries = watchlistService.list(event.getGuild().getId());
        if (entries.isEmpty()) {
            event.reply("This server isn't watching any alliances yet. Use `/watch <alliance>` to add one.").queue();
            return;
        }

        StringBuilder sb = new StringBuilder();
        for (WatchlistEntry e : entries) {
            sb.append("• ").append(e.getAllianceName()).append("\n");
        }

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("👀 Watched Alliances")
                .setDescription(sb.toString());
        event.replyEmbeds(embed.build()).queue();
    }
}
