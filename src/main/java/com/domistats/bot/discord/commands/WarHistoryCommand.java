package com.domistats.bot.discord.commands;

import com.domistats.bot.discord.EmbedFactory;
import com.domistats.bot.dto.AllianceSummary;
import com.domistats.bot.dto.WarSummary;
import com.domistats.bot.service.AllianceLookupService;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Shows recent wars for an alliance. Pagination here is kept simple (a "page"
 * option the user re-runs the command with); wiring up Discord Button
 * pagination is a natural next step and just needs a ButtonInteractionListener
 * that re-calls AllianceLookupService.warHistory(id, page +/- 1).
 */
@Component
public class WarHistoryCommand implements CommandHandler {

    private static final int PAGE_SIZE = 10;

    private final AllianceLookupService lookupService;
    private final EmbedFactory embeds;

    public WarHistoryCommand(AllianceLookupService lookupService, EmbedFactory embeds) {
        this.lookupService = lookupService;
        this.embeds = embeds;
    }

    @Override
    public String name() {
        return "warhistory";
    }

    @Override
    public CommandData definition() {
        return Commands.slash(name(), "Show an alliance's recent war history.")
                .addOptions(
                        new OptionData(OptionType.STRING, "name", "Alliance name", true),
                        new OptionData(OptionType.INTEGER, "page", "Page number (default 1)", false).setMinValue(1)
                );
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        event.deferReply().queue();
        String name = event.getOption("name").getAsString();
        int page = event.getOption("page") != null ? event.getOption("page").getAsInt() : 1;

        Optional<AllianceSummary> summary = lookupService.findByName(name);
        if (summary.isEmpty()) {
            event.getHook().sendMessage("Couldn't find an alliance matching \"" + name + "\" on DomiStats.").queue();
            return;
        }

        List<WarSummary> wars = lookupService.warHistory(summary.get().getDomistatsId(), page);
        // DomiStats doesn't expose a total-pages count in this scraping approach;
        // we just indicate whether this page looks "full" (i.e. there may be more).
        int totalPagesEstimate = wars.size() == PAGE_SIZE ? page + 1 : page;

        event.getHook().sendMessageEmbeds(
                embeds.warHistory(summary.get().getName(), wars, page, totalPagesEstimate).build()
        ).queue();
    }
}
