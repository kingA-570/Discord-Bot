package com.domistats.bot.service;

import com.domistats.bot.entity.GuildConfig;
import com.domistats.bot.repository.GuildConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class GuildConfigService {

    private final GuildConfigRepository repo;

    public GuildConfigService(GuildConfigRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public GuildConfig getOrCreate(String guildId) {
        return repo.findByGuildId(guildId).orElseGet(() -> {
            GuildConfig config = new GuildConfig();
            config.setGuildId(guildId);
            return repo.save(config);
        });
    }

    @Transactional
    public GuildConfig setNotificationChannel(String guildId, String channelId) {
        GuildConfig config = getOrCreate(guildId);
        config.setNotificationChannelId(channelId);
        config.setUpdatedAt(Instant.now());
        return repo.save(config);
    }

    @Transactional
    public GuildConfig setDashboardChannel(String guildId, String channelId) {
        GuildConfig config = getOrCreate(guildId);
        config.setDashboardChannelId(channelId);
        config.setDashboardMessageId(null); // force a fresh message in the new channel
        config.setUpdatedAt(Instant.now());
        return repo.save(config);
    }

    @Transactional
    public GuildConfig toggleNotification(String guildId, String type, boolean enabled) {
        GuildConfig config = getOrCreate(guildId);
        switch (type.toLowerCase()) {
            case "spin" -> config.setNotifyNewSpin(enabled);
            case "war_found" -> config.setNotifyWarFound(enabled);
            case "watchlist" -> config.setNotifyWatchlistActivity(enabled);
            case "war_finished" -> config.setNotifyWarFinished(enabled);
            case "glory" -> config.setNotifyGloryChange(enabled);
            case "members" -> config.setNotifyMemberChange(enabled);
            default -> throw new IllegalArgumentException("Unknown notification type: " + type);
        }
        config.setUpdatedAt(Instant.now());
        return repo.save(config);
    }

    @Transactional
    public void saveDashboardMessageId(String guildId, String messageId) {
        GuildConfig config = getOrCreate(guildId);
        config.setDashboardMessageId(messageId);
        repo.save(config);
    }

    public List<GuildConfig> allWithDashboards() {
        return repo.findByDashboardChannelIdIsNotNull();
    }

    public List<GuildConfig> allGuilds() {
        return repo.findAll();
    }
}
