package com.domistats.bot.service;

import com.domistats.bot.entity.WatchlistEntry;
import com.domistats.bot.repository.WatchlistEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class WatchlistService {

    private final WatchlistEntryRepository repo;

    public WatchlistService(WatchlistEntryRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public WatchlistEntry watch(String guildId, String domistatsId, String allianceName, String addedByUserId) {
        return repo.findByGuildIdAndDomistatsId(guildId, domistatsId).orElseGet(() -> {
            WatchlistEntry entry = new WatchlistEntry();
            entry.setGuildId(guildId);
            entry.setDomistatsId(domistatsId);
            entry.setAllianceName(allianceName);
            entry.setAddedByUserId(addedByUserId);
            return repo.save(entry);
        });
    }

    @Transactional
    public boolean unwatch(String guildId, String domistatsId) {
        Optional<WatchlistEntry> existing = repo.findByGuildIdAndDomistatsId(guildId, domistatsId);
        existing.ifPresent(e -> repo.deleteByGuildIdAndDomistatsId(guildId, domistatsId));
        return existing.isPresent();
    }

    public List<WatchlistEntry> list(String guildId) {
        return repo.findByGuildId(guildId);
    }

    public List<WatchlistEntry> watchersOf(String domistatsId) {
        return repo.findByDomistatsId(domistatsId);
    }
}
