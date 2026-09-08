package com.domistats.bot.discord.commands;

import com.domistats.bot.discord.EmbedFactory;
import com.domistats.bot.dto.AllianceSummary;
import com.domistats.bot.service.AllianceLookupService;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SpinningCommand implements CommandHandler {

    private static final int MAX_EMBEDS_PER_REPLY = 10;

    private final AllianceLookupService lookupService;
    private final EmbedFactory embeds;

    public SpinningCommand(AllianceLookupService lookupService, EmbedFactory embeds) {
        this.lookupService = lookupService;
        this.embeds = embeds;
    }

    @Override
    public String name() {
        return "spinning";
    }

    @Override
    public CommandData definition() {
        return Commands.slash(name(), "Show alliances currently spinning / searching for a war.");
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        event.deferReply().queue();
        List<AllianceSummary> spinning = lookupService.spinningAlliances();

        if (spinning.isEmpty()) {
            event.getHook().sendMessage("No alliances are currently spinning.").queue();
            return;
        }

        List<EmbedBuilder> allEmbeds = spinning.stream()
                .limit(MAX_EMBEDS_PER_REPLY)
                .map(a -> embeds.spinningAlliance(a, null))
                .toList();

        event.getHook().sendMessageEmbeds(allEmbeds.stream().map(EmbedBuilder::build).toList()).queue();

        if (spinning.size() > MAX_EMBEDS_PER_REPLY) {
            event.getHook().sendMessage(
                    "...and " + (spinning.size() - MAX_EMBEDS_PER_REPLY) + " more. Use /watch to track specific alliances."
            ).queue();
        }
    }
}
