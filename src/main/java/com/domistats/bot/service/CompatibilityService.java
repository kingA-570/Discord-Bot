package com.domistats.bot.service;

import com.domistats.bot.dto.AllianceSummary;
import com.domistats.bot.entity.AllianceProfile;
import org.springframework.stereotype.Service;

/**
 * Scores how well a currently-spinning alliance matches a guild's declared
 * war preferences (/setprofile). This is always the bot's own estimate.
 */
@Service
public class CompatibilityService {

    public int score(AllianceProfile profile, AllianceSummary candidate) {
        int points = 0;
        int maxPoints = 0;

        // Glory range match.
        maxPoints += 40;
        if (profile.getMinGlory() != null && profile.getMaxGlory() != null && candidate.getGlory() != null) {
            if (candidate.getGlory() >= profile.getMinGlory() && candidate.getGlory() <= profile.getMaxGlory()) {
                points += 40;
            } else {
                int distance = candidate.getGlory() < profile.getMinGlory()
                        ? profile.getMinGlory() - candidate.getGlory()
                        : candidate.getGlory() - profile.getMaxGlory();
                points += Math.max(0, 40 - (distance / 200));
            }
        }

        // League match.
        maxPoints += 25;
        if (profile.getLeague() != null && candidate.getLeague() != null
                && profile.getLeague().equalsIgnoreCase(candidate.getLeague())) {
            points += 25;
        }

        // Weight closeness.
        maxPoints += 25;
        if (profile.getTargetWeight() != null && candidate.getEstimatedWeight() != null) {
            int distance = Math.abs(profile.getTargetWeight() - candidate.getEstimatedWeight());
            points += Math.max(0, 25 - (distance / 20));
        }

        // Player count closeness.
        maxPoints += 10;
        if (profile.getPlayerCount() != null && candidate.getMemberCount() != null) {
            int distance = Math.abs(profile.getPlayerCount() - candidate.getMemberCount());
            points += Math.max(0, 10 - distance);
        }

        if (maxPoints == 0) return 0;
        return (int) Math.round(100.0 * points / maxPoints);
    }
}
