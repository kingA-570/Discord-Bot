package com.domistats.bot.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class WarSummary {
    String domistatsWarId;
    String opponentName;
    boolean win;
    Integer ownScore;
    Integer opponentScore;
    String warUrl;
}
