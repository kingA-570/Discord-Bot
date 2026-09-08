package com.domistats.bot.repository;

import com.domistats.bot.entity.AllianceProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AllianceProfileRepository extends JpaRepository<AllianceProfile, Long> {
    Optional<AllianceProfile> findByGuildId(String guildId);
}
