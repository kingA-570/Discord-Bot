package com.domistats.bot.discord.commands;

import com.domistats.bot.discord.EmbedFactory;
import com.domistats.bot.dto.AllianceSummary;
import com.domistats.bot.service.AllianceLookupService;
import com.domistats.bot.service.ComparisonService;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class CompareCommand implements CommandHandler {

    private final AllianceLookupService lookupService;
    private final ComparisonService comparisonService;
    private final EmbedFactory embeds;

    public CompareCommand(AllianceLookupService lookupService, ComparisonService comparisonService, EmbedFactory embeds) {
        this.lookupService = lookupService;
        this.comparisonService = comparisonService;
        this.embeds = embeds;
    }

    @Override
    public String name() {
        return "compare";
    }

    @Override
    public CommandData definition() {
        return Commands.slash(name(), "Compare two alliances side by side.")
                .addOptions(
                        new OptionData(OptionType.STRING, "alliance1", "First alliance name", true),
                        new OptionData(OptionType.STRING, "alliance2", "Second alliance name", true)
                );
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        event.deferReply().queue();
        String name1 = event.getOption("alliance1").getAsString();
        String name2 = event.getOption("alliance2").getAsString();

        Optional<AllianceSummary> a = lookupService.findByName(name1);
        Optional<AllianceSummary> b = lookupService.findByName(name2);

        if (a.isEmpty() || b.isEmpty()) {
            String missing = a.isEmpty() ? name1 : name2;
            event.getHook().sendMessage("Couldn't find an alliance matching \"" + missing + "\" on DomiStats.").queue();
            return;
        }

        var result = comparisonService.compare(a.get(), b.get());
        event.getHook().sendMessageEmbeds(
                embeds.allianceComparison(a.get(), b.get(), result.verdict()).build()
        ).queue();
    }
}
