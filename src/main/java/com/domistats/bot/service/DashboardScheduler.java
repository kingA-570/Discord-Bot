package com.domistats.bot.service;

import com.domistats.bot.discord.EmbedFactory;
import com.domistats.bot.dto.AllianceSummary;
import com.domistats.bot.entity.GuildConfig;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Edits (rather than re-posts) a single "War Radar" status embed per guild
 * that has configured a dashboard channel, per requirement #11.
 */
@Slf4j
@Component
public class DashboardScheduler {

    private final DomiStatsClient domiStatsClient;
    private final GuildConfigService guildConfigService;
    private final EmbedFactory embeds;
    private final JDA jda;

    public DashboardScheduler(DomiStatsClient domiStatsClient, GuildConfigService guildConfigService,
                               EmbedFactory embeds, JDA jda) {
        this.domiStatsClient = domiStatsClient;
        this.guildConfigService = guildConfigService;
        this.embeds = embeds;
        this.jda = jda;
    }

    @Scheduled(fixedDelayString = "${domistats.scan-interval-ms}")
    public void refreshDashboards() {
        List<GuildConfig> guildsWithDashboards = guildConfigService.allWithDashboards();
        if (guildsWithDashboards.isEmpty()) return;

        List<AllianceSummary> spinning;
        try {
            spinning = domiStatsClient.fetchSpinningAlliances();
        } catch (Exception e) {
            log.warn("Skipping dashboard refresh, DomiStats scan failed: {}", e.getMessage());
            return;
        }

        Map<String, Integer> byLeague = new HashMap<>();
        for (AllianceSummary a : spinning) {
            String league = a.getLeague() != null ? a.getLeague() : "Unknown";
            byLeague.merge(league, 1, Integer::sum);
        }

        Instant nextScan = Instant.now().plusMillis(60_000);
        var embed = embeds.warRadarDashboard(spinning.size(), byLeague, 0, 0, nextScan);

        for (GuildConfig config : guildsWithDashboards) {
            updateDashboardMessage(config, embed.build());
        }
    }

    private void updateDashboardMessage(GuildConfig config, net.dv8tion.jda.api.entities.MessageEmbed embed) {
        MessageChannel channel = jda.getChannelById(MessageChannel.class, config.getDashboardChannelId());
        if (channel == null) {
            log.warn("Dashboard channel {} not found for guild {}", config.getDashboardChannelId(), config.getGuildId());
            return;
        }

        if (config.getDashboardMessageId() != null) {
            channel.editMessageEmbedsById(config.getDashboardMessageId(), embed).queue(
                    success -> { /* no-op */ },
                    error -> {
                        log.warn("Failed to edit dashboard message for guild {}, will re-post: {}",
                                config.getGuildId(), error.getMessage());
                        postNewDashboardMessage(channel, config, embed);
                    }
            );
        } else {
            postNewDashboardMessage(channel, config, embed);
        }
    }

    private void postNewDashboardMessage(MessageChannel channel, GuildConfig config,
                                          net.dv8tion.jda.api.entities.MessageEmbed embed) {
        channel.sendMessageEmbeds(embed).queue(
                (Message msg) -> guildConfigService.saveDashboardMessageId(config.getGuildId(), msg.getId()),
                error -> log.warn("Failed to post dashboard message for guild {}: {}", config.getGuildId(), error.getMessage())
        );
    }
}
