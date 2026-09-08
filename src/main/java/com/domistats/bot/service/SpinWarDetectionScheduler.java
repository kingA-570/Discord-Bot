package com.domistats.bot.service;

import com.domistats.bot.config.DomiStatsProperties;
import com.domistats.bot.discord.EmbedFactory;
import com.domistats.bot.dto.AllianceSummary;
import com.domistats.bot.dto.CurrentWar;
import com.domistats.bot.entity.AllianceState;
import com.domistats.bot.entity.GuildConfig;
import com.domistats.bot.entity.WarRecord;
import com.domistats.bot.entity.WatchlistEntry;
import com.domistats.bot.repository.AllianceStateRepository;
import com.domistats.bot.repository.WarRecordRepository;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The heart of the "war radar": periodically polls DomiStats for the current
 * spinning list, diffs it against the last known state in Postgres, and fires
 * exactly one notification per real state transition:
 *   idle -> spinning, spinning -> war (found), war -> finished, and (optionally)
 *   significant glory/member changes on watched alliances.
 */
@Slf4j
@Component
public class SpinWarDetectionScheduler {

    private final DomiStatsClient domiStatsClient;
    private final AllianceStateRepository stateRepo;
    private final WarRecordRepository warRepo;
    private final WatchlistService watchlistService;
    private final GuildConfigService guildConfigService;
    private final NotificationDispatcher dispatcher;
    private final EmbedFactory embeds;
    private final DomiStatsProperties props;

    private static final double GLORY_CHANGE_THRESHOLD_PCT = 5.0;

    public SpinWarDetectionScheduler(DomiStatsClient domiStatsClient,
                                      AllianceStateRepository stateRepo,
                                      WarRecordRepository warRepo,
                                      WatchlistService watchlistService,
                                      GuildConfigService guildConfigService,
                                      NotificationDispatcher dispatcher,
                                      EmbedFactory embeds,
                                      DomiStatsProperties props) {
        this.domiStatsClient = domiStatsClient;
        this.stateRepo = stateRepo;
        this.warRepo = warRepo;
        this.watchlistService = watchlistService;
        this.guildConfigService = guildConfigService;
        this.dispatcher = dispatcher;
        this.embeds = embeds;
        this.props = props;
    }

    @Scheduled(fixedDelayString = "${domistats.scan-interval-ms}")
    public void scan() {
        List<AllianceSummary> spinningNow;
        try {
            spinningNow = domiStatsClient.fetchSpinningAlliances();
        } catch (Exception e) {
            log.error("DomiStats scan failed, will retry next cycle: {}", e.getMessage());
            return; // DomiStats temporarily unavailable - skip this cycle gracefully.
        }

        Set<String> spinningIdsNow = new HashSet<>();
        for (AllianceSummary summary : spinningNow) {
            if (summary.getDomistatsId() == null) continue;
            spinningIdsNow.add(summary.getDomistatsId());
            handleObservedSpinning(summary);
        }

        // Anything that WAS spinning last cycle but isn't now has either
        // entered a war or stopped spinning without a match.
        List<AllianceState> previouslySpinning = stateRepo.findBySpinningTrue();
        for (AllianceState prev : previouslySpinning) {
            if (!spinningIdsNow.contains(prev.getDomistatsId())) {
                handleStoppedSpinning(prev);
            }
        }
    }

    @Transactional
    void handleObservedSpinning(AllianceSummary summary) {
        AllianceState state = stateRepo.findByDomistatsId(summary.getDomistatsId())
                .orElseGet(AllianceState::new);

        boolean wasSpinning = state.isSpinning();
        Integer previousGlory = state.getGlory();
        Integer previousMembers = state.getMemberCount();

        state.setDomistatsId(summary.getDomistatsId());
        state.setName(summary.getName());
        state.setGlory(summary.getGlory());
        state.setRanking(summary.getRanking());
        state.setMemberCount(summary.getMemberCount());
        state.setLeague(summary.getLeague());
        state.setWinRate(summary.getWinRate());
        state.setEstimatedWeight(summary.getEstimatedWeight());
        state.setSpinning(true);
        state.setProfileUrl(summary.getProfileUrl());
        state.setLastSeenAt(Instant.now());
        state.setUpdatedAt(Instant.now());

        if (!wasSpinning) {
            state.setSpinStartedAt(Instant.now());
        }

        stateRepo.save(state);

        if (!wasSpinning) {
            notifyNewSpin(summary, state.getSpinStartedAt());
        }

        maybeNotifyStatChanges(state, previousGlory, previousMembers);
    }

    @Transactional
    void handleStoppedSpinning(AllianceState state) {
        // Check if it entered a war.
        var currentWarOpt = domiStatsClient.fetchCurrentWar(state.getDomistatsId());

        state.setSpinning(false);
        state.setUpdatedAt(Instant.now());

        if (currentWarOpt.isPresent()) {
            CurrentWar war = currentWarOpt.get();
            state.setInWar(true);
            state.setCurrentWarId(war.getDomistatsWarId());
            stateRepo.save(state);

            if (war.getDomistatsWarId() != null) {
                handleWarFound(war);
            }
        } else {
            // Stopped spinning without a detected war (e.g. left the search, or
            // DomiStats hasn't published the match yet - it may show up next cycle).
            stateRepo.save(state);
        }
    }

    private void handleWarFound(CurrentWar war) {
        boolean alreadyKnown = warRepo.findByDomistatsWarId(war.getDomistatsWarId()).isPresent();
        if (alreadyKnown) {
            return; // Already notified - never send duplicate war-found alerts.
        }

        WarRecord record = new WarRecord();
        record.setDomistatsWarId(war.getDomistatsWarId());
        record.setAllianceAId(war.getAllianceA() != null ? war.getAllianceA().getDomistatsId() : null);
        record.setAllianceBId(war.getAllianceB() != null ? war.getAllianceB().getDomistatsId() : null);
        record.setNotifiedFound(true);
        warRepo.save(record);

        if (war.getAllianceA() == null || war.getAllianceB() == null) {
            log.warn("War {} detected but missing one side's alliance data, skipping notification", war.getDomistatsWarId());
            return;
        }

        EmbedBuilder embed = embeds.warFound(war.getAllianceA(), war.getAllianceB(), war.getWarUrl());
        broadcastToInterestedGuilds(war.getAllianceA().getDomistatsId(), war.getAllianceB().getDomistatsId(),
                embed, GuildConfig::isNotifyWarFound);
    }

    private void notifyNewSpin(AllianceSummary summary, Instant spinStartedAt) {
        EmbedBuilder embed = embeds.spinningAlliance(summary, spinStartedAt);
        broadcastToInterestedGuilds(summary.getDomistatsId(), null, embed, GuildConfig::isNotifyNewSpin);
    }

    private void maybeNotifyStatChanges(AllianceState state, Integer previousGlory, Integer previousMembers) {
        List<WatchlistEntry> watchers = watchlistService.watchersOf(state.getDomistatsId());
        if (watchers.isEmpty()) return;

        boolean gloryChanged = previousGlory != null && state.getGlory() != null
                && Math.abs(state.getGlory() - previousGlory) >= previousGlory * (GLORY_CHANGE_THRESHOLD_PCT / 100.0);
        boolean membersChanged = previousMembers != null && state.getMemberCount() != null
                && !previousMembers.equals(state.getMemberCount());

        if (!gloryChanged && !membersChanged) return;

        for (WatchlistEntry watcher : watchers) {
            GuildConfig config = guildConfigService.getOrCreate(watcher.getGuildId());
            if (gloryChanged && config.isNotifyGloryChange()) {
                EmbedBuilder embed = new EmbedBuilder()
                        .setTitle("📈 Glory Change — " + state.getName())
                        .setDescription(previousGlory + " → " + state.getGlory())
                        .setTimestamp(Instant.now());
                dispatcher.send(config, embed);
            }
            if (membersChanged && config.isNotifyMemberChange()) {
                EmbedBuilder embed = new EmbedBuilder()
                        .setTitle("👥 Member Count Change — " + state.getName())
                        .setDescription(previousMembers + " → " + state.getMemberCount())
                        .setTimestamp(Instant.now());
                dispatcher.send(config, embed);
            }
        }
    }

    /** Sends to every guild that watches either alliance, plus every guild that enabled the given global toggle. */
    private void broadcastToInterestedGuilds(String allianceAId, String allianceBId, EmbedBuilder embed,
                                              java.util.function.Predicate<GuildConfig> globalToggle) {
        Set<String> notifiedGuildIds = new HashSet<>();

        Set<String> watcherGuildIds = new HashSet<>();
        watchlistService.watchersOf(allianceAId).forEach(w -> watcherGuildIds.add(w.getGuildId()));
        if (allianceBId != null) {
            watchlistService.watchersOf(allianceBId).forEach(w -> watcherGuildIds.add(w.getGuildId()));
        }
        for (String guildId : watcherGuildIds) {
            GuildConfig config = guildConfigService.getOrCreate(guildId);
            if (config.isNotifyWatchlistActivity()) {
                dispatcher.send(config, embed);
                notifiedGuildIds.add(guildId);
            }
        }

        for (GuildConfig config : guildConfigService.allGuilds()) {
            if (notifiedGuildIds.contains(config.getGuildId())) continue; // avoid double notification
            if (globalToggle.test(config)) {
                dispatcher.send(config, embed);
            }
        }
    }
}
