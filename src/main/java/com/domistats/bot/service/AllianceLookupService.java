package com.domistats.bot.service;

import com.domistats.bot.dto.AllianceDetail;
import com.domistats.bot.dto.AllianceSummary;
import com.domistats.bot.dto.CurrentWar;
import com.domistats.bot.dto.WarSummary;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/** Thin façade over DomiStatsClient used by Discord command handlers. */
@Service
public class AllianceLookupService {

    private final DomiStatsClient client;

    public AllianceLookupService(DomiStatsClient client) {
        this.client = client;
    }

    public Optional<AllianceSummary> findByName(String name) {
        return client.searchAllianceByName(name);
    }

    public Optional<AllianceDetail> detail(String domistatsId) {
        return client.fetchAllianceDetail(domistatsId);
    }

    public Optional<CurrentWar> currentWar(String domistatsId) {
        return client.fetchCurrentWar(domistatsId);
    }

    public List<WarSummary> warHistory(String domistatsId, int page) {
        return client.fetchWarHistory(domistatsId, page);
    }

    public List<AllianceSummary> spinningAlliances() {
        return client.fetchSpinningAlliances();
    }
}
