package com.domistats.bot.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/** Full alliance profile page, as scraped from the /alliances/{id} DomiStats page. */
@Value
@Builder
public class AllianceDetail {
    String domistatsId;
    String name;
    Integer glory;
    Integer ranking;
    String league;
    Integer memberCount;
    Double winRate;
    String parliament;
    List<String> perks;
    String language;
    String recruitmentStatus;
    List<WarSummary> recentWars;
    String profileUrl;
}
