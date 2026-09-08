package com.domistats.bot.discord.commands;

import com.domistats.bot.discord.EmbedFactory;
import com.domistats.bot.dto.AllianceSummary;
import com.domistats.bot.dto.CurrentWar;
import com.domistats.bot.service.AllianceLookupService;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class WarCommand implements CommandHandler {

    private final AllianceLookupService lookupService;
    private final EmbedFactory embeds;

    public WarCommand(AllianceLookupService lookupService, EmbedFactory embeds) {
        this.lookupService = lookupService;
        this.embeds = embeds;
    }

    @Override
    public String name() {
        return "war";
    }

    @Override
    public CommandData definition() {
        return Commands.slash(name(), "Show an alliance's current war, if any.")
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

        Optional<CurrentWar> war = lookupService.currentWar(summary.get().getDomistatsId());
        event.getHook().sendMessageEmbeds(embeds.currentWar(summary.get().getName(), war.orElse(null)).build()).queue();
    }
}
