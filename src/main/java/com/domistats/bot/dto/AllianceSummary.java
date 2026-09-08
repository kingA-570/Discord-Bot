package com.domistats.bot.dto;

import lombok.Builder;
import lombok.Value;

/** Lightweight alliance snapshot as scraped from a DomiStats listing/detail page. */
@Value
@Builder
public class AllianceSummary {
    String domistatsId;
    String name;
    Integer glory;
    Integer ranking;
    Integer memberCount;
    String league;
    Double winRate;
    Integer estimatedWeight;
    boolean spinning;
    boolean inWar;
    String currentWarId;
    String profileUrl;
}
