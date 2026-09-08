package com.domistats.bot.discord.commands;

import com.domistats.bot.dto.AllianceSummary;
import com.domistats.bot.service.AllianceLookupService;
import com.domistats.bot.service.WatchlistService;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class WatchCommand implements CommandHandler {

    private final AllianceLookupService lookupService;
    private final WatchlistService watchlistService;

    public WatchCommand(AllianceLookupService lookupService, WatchlistService watchlistService) {
        this.lookupService = lookupService;
        this.watchlistService = watchlistService;
    }

    @Override
    public String name() {
        return "watch";
    }

    @Override
    public CommandData definition() {
        return Commands.slash(name(), "Add an alliance to this server's watchlist.")
                .addOptions(new OptionData(OptionType.STRING, "name", "Alliance name", true));
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        event.deferReply().queue();
        String name = event.getOption("name").getAsString();

        Optional<AllianceSummary> summary = lookupService.findByName(name);
        if (summary.isEmpty()) {
            event.getHook().sendMessage("Couldn't find an alliance matching \"" + name + "\" on DomiStats.").queue();
            return;
        }

        watchlistService.watch(event.getGuild().getId(), summary.get().getDomistatsId(),
                summary.get().getName(), event.getUser().getId());

        event.getHook().sendMessage("👀 Now watching **" + summary.get().getName() + "**. "
                + "This server will get notified on spins, wars, and other activity for this alliance.").queue();
    }
}
