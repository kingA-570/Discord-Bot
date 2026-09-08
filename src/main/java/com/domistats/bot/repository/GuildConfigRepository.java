package com.domistats.bot.repository;

import com.domistats.bot.entity.GuildConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GuildConfigRepository extends JpaRepository<GuildConfig, Long> {
    Optional<GuildConfig> findByGuildId(String guildId);
    List<GuildConfig> findByDashboardChannelIdIsNotNull();
}
