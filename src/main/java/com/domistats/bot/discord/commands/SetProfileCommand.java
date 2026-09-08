package com.domistats.bot.discord.commands;

import com.domistats.bot.discord.EmbedFactory;
import com.domistats.bot.dto.AllianceSummary;
import com.domistats.bot.entity.AllianceProfile;
import com.domistats.bot.repository.AllianceProfileRepository;
import com.domistats.bot.service.AllianceLookupService;
import com.domistats.bot.service.CompatibilityService;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * Feature 9: lets a server declare what it wants in a war opponent, then
 * surfaces currently-spinning alliances that best fit that profile.
 * Compatibility scores are always the bot's own estimate (see CompatibilityService).
 */
@Component
public class SetProfileCommand implements CommandHandler {

    private final AllianceProfileRepository profileRepo;
    private final AllianceLookupService lookupService;
    private final CompatibilityService compatibilityService;
    private final EmbedFactory embeds;

    public SetProfileCommand(AllianceProfileRepository profileRepo, AllianceLookupService lookupService,
                              CompatibilityService compatibilityService, EmbedFactory embeds) {
        this.profileRepo = profileRepo;
        this.lookupService = lookupService;
        this.compatibilityService = compatibilityService;
        this.embeds = embeds;
    }

    @Override
    public String name() {
        return "setprofile";
    }

    @Override
    public CommandData definition() {
        return Commands.slash(name(), "Set this server's preferred war-opponent profile for spin matching.")
                .addOptions(
                        new OptionData(OptionType.STRING, "alliance", "Your alliance's name", true),
                        new OptionData(OptionType.STRING, "league", "Preferred league", false),
                        new OptionData(OptionType.INTEGER, "players", "Your player count", false),
                        new OptionData(OptionType.INTEGER, "weight", "Your target war weight", false),
                        new OptionData(OptionType.INTEGER, "min_glory", "Minimum opponent glory", false),
                        new OptionData(OptionType.INTEGER, "max_glory", "Maximum opponent glory", false)
                );
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        event.deferReply().queue();
        String guildId = event.getGuild().getId();

        AllianceProfile profile = profileRepo.findByGuildId(guildId).orElseGet(AllianceProfile::new);
        profile.setGuildId(guildId);
        profile.setAllianceName(event.getOption("alliance").getAsString());
        if (event.getOption("league") != null) profile.setLeague(event.getOption("league").getAsString());
        if (event.getOption("players") != null) profile.setPlayerCount(event.getOption("players").getAsInt());
        if (event.getOption("weight") != null) profile.setTargetWeight(event.getOption("weight").getAsInt());
        if (event.getOption("min_glory") != null) profile.setMinGlory(event.getOption("min_glory").getAsInt());
        if (event.getOption("max_glory") != null) profile.setMaxGlory(event.getOption("max_glory").getAsInt());
        profile.setUpdatedAt(Instant.now());
        profileRepo.save(profile);

        List<AllianceSummary> spinning = lookupService.spinningAlliances();
        List<AllianceSummary> topMatches = spinning.stream()
                .sorted(Comparator.comparingInt((AllianceSummary a) -> compatibilityService.score(profile, a)).reversed())
                .limit(3)
                .toList();

        event.getHook().sendMessage("✅ Profile saved for **" + profile.getAllianceName() + "**.").queue();

        if (topMatches.isEmpty()) {
            event.getHook().sendMessage("No alliances are currently spinning to match against.").queue();
            return;
        }

        List<EmbedBuilder> matchEmbeds = topMatches.stream()
                .map(a -> embeds.compatibilityMatch(a, compatibilityService.score(profile, a)))
                .toList();
        event.getHook().sendMessageEmbeds(matchEmbeds.stream().map(EmbedBuilder::build).toList()).queue();
    }
}
