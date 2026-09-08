package com.domistats.bot.discord.commands;

import com.domistats.bot.discord.EmbedFactory;
import com.domistats.bot.dto.AllianceDetail;
import com.domistats.bot.dto.AllianceSummary;
import com.domistats.bot.service.AllianceLookupService;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class AllianceCommand implements CommandHandler {

    private final AllianceLookupService lookupService;
    private final EmbedFactory embeds;

    public AllianceCommand(AllianceLookupService lookupService, EmbedFactory embeds) {
        this.lookupService = lookupService;
        this.embeds = embeds;
    }

    @Override
    public String name() {
        return "alliance";
    }

    @Override
    public CommandData definition() {
        return Commands.slash(name(), "Show detailed information about an alliance.")
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

        Optional<AllianceDetail> detail = lookupService.detail(summary.get().getDomistatsId());
        if (detail.isEmpty()) {
            event.getHook().sendMessage("Found the alliance but DomiStats didn't return detail data - try again shortly.").queue();
            return;
        }

        event.getHook().sendMessageEmbeds(embeds.allianceInfo(detail.get()).build()).queue();
    }
}
