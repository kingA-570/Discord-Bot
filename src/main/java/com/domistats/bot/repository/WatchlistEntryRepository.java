package com.domistats.bot.repository;

import com.domistats.bot.entity.WatchlistEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WatchlistEntryRepository extends JpaRepository<WatchlistEntry, Long> {
    List<WatchlistEntry> findByGuildId(String guildId);
    Optional<WatchlistEntry> findByGuildIdAndDomistatsId(String guildId, String domistatsId);
    List<WatchlistEntry> findByDomistatsId(String domistatsId);
    void deleteByGuildIdAndDomistatsId(String guildId, String domistatsId);
}
