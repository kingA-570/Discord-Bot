package com.domistats.bot.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class CurrentWar {
    String domistatsWarId;
    AllianceSummary allianceA;
    AllianceSummary allianceB;
    String status;       // e.g. "ONGOING", "PREP", "FINISHED"
    Integer warSize;
    Integer scoreA;
    Integer scoreB;
    String startInfo;
    String endInfo;
    String warUrl;
}
