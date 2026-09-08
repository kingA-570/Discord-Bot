package com.domistats.bot.service;

import com.domistats.bot.dto.AllianceSummary;
import org.springframework.stereotype.Service;

/**
 * Produces simple heuristic strength comparisons between two alliances.
 *
 * These are the bot's OWN estimates (a weighted blend of glory, win rate,
 * and weight) and are always presented to users as such — never attributed
 * to DomiStats itself, per the project requirements.
 */
@Service
public class ComparisonService {

    public record Result(double scoreA, double scoreB, String difference, String verdict) {}

    public Result compare(AllianceSummary a, AllianceSummary b) {
        double scoreA = weightedScore(a);
        double scoreB = weightedScore(b);

        double diffPct = scoreB == 0 ? 0 : Math.abs(scoreA - scoreB) / Math.max(scoreA, scoreB) * 100;
        String difference = diffPct < 10 ? "Low" : diffPct < 25 ? "Moderate" : "High";

        String verdict;
        if (Math.abs(scoreA - scoreB) < 0.05 * Math.max(scoreA, scoreB)) {
            verdict = "Evenly matched (bot estimate)";
        } else if (scoreA > scoreB) {
            verdict = (a.getName() != null ? a.getName() : "Alliance A") + " looks stronger (bot estimate)";
        } else {
            verdict = (b.getName() != null ? b.getName() : "Alliance B") + " looks stronger (bot estimate)";
        }

        return new Result(scoreA, scoreB, difference, verdict);
    }

    private double weightedScore(AllianceSummary a) {
        double glory = a.getGlory() != null ? a.getGlory() : 0;
        double winRate = a.getWinRate() != null ? a.getWinRate() : 50;
        double weight = a.getEstimatedWeight() != null ? a.getEstimatedWeight() : 0;

        // Simple, transparent weighting - tune as real-world data comes in.
        return (glory * 0.5) + (winRate * 100 * 0.3) + (weight * 0.2);
    }
}
