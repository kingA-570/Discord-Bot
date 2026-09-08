package com.domistats.bot.discord;

import com.domistats.bot.dto.AllianceDetail;
import com.domistats.bot.dto.AllianceSummary;
import com.domistats.bot.dto.CurrentWar;
import com.domistats.bot.dto.WarSummary;
import com.domistats.bot.entity.AllianceState;
import net.dv8tion.jda.api.EmbedBuilder;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Centralizes embed construction so formatting stays consistent across commands. */
@Component
public class EmbedFactory {

    private static final Color COLOR_SPINNING = new Color(0x2ECC71);
    private static final Color COLOR_WAR = new Color(0xE74C3C);
    private static final Color COLOR_INFO = new Color(0x3498DB);
    private static final Color COLOR_NEUTRAL = new Color(0x95A5A6);

    public EmbedBuilder spinningAlliance(AllianceSummary a, Instant spinStartedAt) {
        EmbedBuilder eb = new EmbedBuilder()
                .setTitle("🔄 CURRENTLY SPINNING")
                .setColor(COLOR_SPINNING)
                .addField("⚔️ Alliance", nullSafe(a.getName()), false)
                .addField("🏆 Glory", numOrNa(a.getGlory()), true)
                .addField("📊 Ranking", a.getRanking() != null ? "#" + a.getRanking() : "N/A", true)
                .addField("👥 Members", numOrNa(a.getMemberCount()), true)
                .addField("🏅 League", nullSafe(a.getLeague()), true)
                .addField("📈 Win Rate", a.getWinRate() != null ? a.getWinRate() + "%" : "N/A", true)
                .addField("⚖️ Weight", a.getEstimatedWeight() != null ? numOrNa(a.getEstimatedWeight()) + " (est.)" : "N/A", true);

        if (spinStartedAt != null) {
            eb.addField("⏱️ Spinning for", formatDuration(Duration.between(spinStartedAt, Instant.now())), true);
        }
        eb.addField("🔗 Link", linkOrNa(a.getProfileUrl()), false);
        eb.setFooter("Last updated");
        eb.setTimestamp(Instant.now());
        return eb;
    }

    public EmbedBuilder warFound(AllianceSummary a, AllianceSummary b, String warUrl) {
        EmbedBuilder eb = new EmbedBuilder()
                .setTitle("🚨 WAR FOUND")
                .setDescription("⚔️ **" + nullSafe(a.getName()) + "** vs **" + nullSafe(b.getName()) + "**")
                .setColor(COLOR_WAR)
                .addField(nullSafe(a.getName()),
                        "🏆 Glory: " + numOrNa(a.getGlory())
                                + "\n👥 Members: " + numOrNa(a.getMemberCount())
                                + "\n🏅 League: " + nullSafe(a.getLeague())
                                + "\n📈 Win Rate: " + (a.getWinRate() != null ? a.getWinRate() + "%" : "N/A"),
                        true)
                .addField(nullSafe(b.getName()),
                        "🏆 Glory: " + numOrNa(b.getGlory())
                                + "\n👥 Members: " + numOrNa(b.getMemberCount())
                                + "\n🏅 League: " + nullSafe(b.getLeague())
                                + "\n📈 Win Rate: " + (b.getWinRate() != null ? b.getWinRate() + "%" : "N/A"),
                        true)
                .addField("🔗 DomiStats War", linkOrNa(warUrl), false)
                .setTimestamp(Instant.now());
        return eb;
    }

    public EmbedBuilder warComparison(AllianceSummary a, AllianceSummary b, String verdict, String difference) {
        EmbedBuilder eb = new EmbedBuilder()
                .setTitle("⚖️ WAR COMPARISON")
                .setColor(COLOR_INFO)
                .addField(nullSafe(a.getName()),
                        "Glory: " + numOrNa(a.getGlory())
                                + "\nWin Rate: " + (a.getWinRate() != null ? a.getWinRate() + "%" : "N/A")
                                + "\nMembers: " + numOrNa(a.getMemberCount())
                                + "\nLeague: " + nullSafe(a.getLeague()),
                        true)
                .addField(nullSafe(b.getName()),
                        "Glory: " + numOrNa(b.getGlory())
                                + "\nWin Rate: " + (b.getWinRate() != null ? b.getWinRate() + "%" : "N/A")
                                + "\nMembers: " + numOrNa(b.getMemberCount())
                                + "\nLeague: " + nullSafe(b.getLeague()),
                        true)
                .addField("Estimated difference", difference + " — " + verdict, false)
                .setFooter("This comparison is calculated by the bot and is not an official DomiStats figure.")
                .setTimestamp(Instant.now());
        return eb;
    }

    public EmbedBuilder allianceInfo(AllianceDetail d) {
        EmbedBuilder eb = new EmbedBuilder()
                .setTitle("🏛️ " + nullSafe(d.getName()))
                .setColor(COLOR_INFO)
                .addField("🏆 Glory", numOrNa(d.getGlory()), true)
                .addField("📊 Ranking", d.getRanking() != null ? "#" + d.getRanking() : "N/A", true)
                .addField("🏅 League", nullSafe(d.getLeague()), true)
                .addField("👥 Members", numOrNa(d.getMemberCount()), true)
                .addField("📈 Win Rate", d.getWinRate() != null ? d.getWinRate() + "%" : "N/A", true)
                .addField("🏛️ Parliament", nullSafe(d.getParliament()), true)
                .addField("🌐 Language", nullSafe(d.getLanguage()), true)
                .addField("📢 Recruitment", nullSafe(d.getRecruitmentStatus()), true);

        if (d.getPerks() != null && !d.getPerks().isEmpty()) {
            eb.addField("✨ Perks", String.join(", ", d.getPerks()), false);
        }
        eb.addField("🔗 DomiStats Profile", linkOrNa(d.getProfileUrl()), false);
        eb.setTimestamp(Instant.now());
        return eb;
    }

    public EmbedBuilder currentWar(String allianceName, CurrentWar war) {
        if (war == null) {
            return new EmbedBuilder()
                    .setTitle("⚔️ Current War — " + nullSafe(allianceName))
                    .setColor(COLOR_NEUTRAL)
                    .setDescription("This alliance is **not currently in a war**.")
                    .setTimestamp(Instant.now());
        }
        EmbedBuilder eb = new EmbedBuilder()
                .setTitle("⚔️ Current War — " + nullSafe(allianceName))
                .setColor(COLOR_WAR)
                .addField("Opponent", war.getAllianceB() != null ? nullSafe(war.getAllianceB().getName()) : "N/A", true)
                .addField("Status", nullSafe(war.getStatus()), true)
                .addField("War Size", numOrNa(war.getWarSize()), true);

        if (war.getScoreA() != null || war.getScoreB() != null) {
            eb.addField("Score", numOrNa(war.getScoreA()) + " — " + numOrNa(war.getScoreB()), true);
        }
        if (war.getStartInfo() != null) eb.addField("Start", war.getStartInfo(), true);
        if (war.getEndInfo() != null) eb.addField("End", war.getEndInfo(), true);
        eb.addField("🔗 DomiStats War", linkOrNa(war.getWarUrl()), false);
        eb.setTimestamp(Instant.now());
        return eb;
    }

    public EmbedBuilder warHistory(String allianceName, List<WarSummary> wars, int page, int totalPages) {
        EmbedBuilder eb = new EmbedBuilder()
                .setTitle("🏆 ALLIANCE WAR HISTORY")
                .setDescription("**" + nullSafe(allianceName) + "**")
                .setColor(COLOR_INFO);

        long wins = wars.stream().filter(WarSummary::isWin).count();
        long losses = wars.size() - wins;

        StringBuilder sb = new StringBuilder();
        for (WarSummary w : wars) {
            sb.append(w.isWin() ? "✅ Win" : "❌ Loss")
                    .append(" — ").append(numOrNa(w.getOwnScore()))
                    .append(" vs ").append(numOrNa(w.getOpponentScore()))
                    .append(" — ").append(nullSafe(w.getOpponentName()))
                    .append("\n");
        }
        eb.addField("Recent Wars", sb.length() > 0 ? sb.toString() : "No recent wars found.", false);

        double winRate = wars.isEmpty() ? 0.0 : (100.0 * wins / wars.size());
        eb.addField("Record", wins + "W - " + losses + "L", true);
        eb.addField("Win Rate", String.format("%.0f%%", winRate), true);
        eb.setFooter("Page " + page + " of " + totalPages);
        eb.setTimestamp(Instant.now());
        return eb;
    }

    public EmbedBuilder allianceComparison(AllianceSummary a, AllianceSummary b, String summary) {
        EmbedBuilder eb = new EmbedBuilder()
                .setTitle("🔬 ALLIANCE COMPARISON")
                .setColor(COLOR_INFO)
                .addField(nullSafe(a.getName()),
                        statBlock(a), true)
                .addField(nullSafe(b.getName()),
                        statBlock(b), true)
                .addField("Bot's Assessment", summary, false)
                .setFooter("This assessment is the bot's own estimate, not an official DomiStats calculation.")
                .setTimestamp(Instant.now());
        return eb;
    }

    public EmbedBuilder compatibilityMatch(AllianceSummary candidate, int compatibilityPercent) {
        String light = compatibilityPercent >= 80 ? "🟢" : compatibilityPercent >= 50 ? "🟡" : "🔴";
        return new EmbedBuilder()
                .setTitle("🔍 POSSIBLE MATCH")
                .setColor(COLOR_SPINNING)
                .addField("🔥 " + nullSafe(candidate.getName()),
                        "Glory: " + numOrNa(candidate.getGlory())
                                + "\nWeight: " + numOrNa(candidate.getEstimatedWeight())
                                + "\nMembers: " + numOrNa(candidate.getMemberCount())
                                + "\nWin Rate: " + (candidate.getWinRate() != null ? candidate.getWinRate() + "%" : "N/A"),
                        false)
                .addField("Compatibility", compatibilityPercent + "% " + light, false)
                .setFooter("Compatibility score is the bot's own estimate, not an official DomiStats figure.")
                .setTimestamp(Instant.now());
    }

    public EmbedBuilder warRadarDashboard(int totalSpinning, java.util.Map<String, Integer> byLeague,
                                           int recentlyMatched, int warsCompleted, Instant nextScan) {
        StringBuilder leagues = new StringBuilder();
        byLeague.forEach((league, count) -> leagues.append("🏅 ").append(league).append(": ").append(count).append("\n"));

        return new EmbedBuilder()
                .setTitle("🔄 WAR RADAR")
                .setColor(COLOR_INFO)
                .addField("🔴 Alliances Spinning", String.valueOf(totalSpinning), false)
                .addField("By League", leagues.length() > 0 ? leagues.toString() : "None", false)
                .addField("⚔️ Recently Matched", String.valueOf(recentlyMatched), true)
                .addField("🏆 Wars Completed", String.valueOf(warsCompleted), true)
                .setFooter("Last scan: " + DateTimeFormatter.ISO_LOCAL_TIME.format(
                        Instant.now().atZone(java.time.ZoneOffset.UTC)) + " UTC · Next scan: "
                        + DateTimeFormatter.ISO_LOCAL_TIME.format(nextScan.atZone(java.time.ZoneOffset.UTC)) + " UTC")
                .setTimestamp(Instant.now());
    }

    // ------------------------------------------------------------------

    private String statBlock(AllianceSummary a) {
        return "Glory: " + numOrNa(a.getGlory())
                + "\nRanking: " + (a.getRanking() != null ? "#" + a.getRanking() : "N/A")
                + "\nMembers: " + numOrNa(a.getMemberCount())
                + "\nLeague: " + nullSafe(a.getLeague())
                + "\nWin Rate: " + (a.getWinRate() != null ? a.getWinRate() + "%" : "N/A")
                + "\nWeight: " + numOrNa(a.getEstimatedWeight());
    }

    private String formatDuration(Duration d) {
        long minutes = d.toMinutes();
        if (minutes < 60) return minutes + " minutes";
        long hours = minutes / 60;
        long remMinutes = minutes % 60;
        return hours + "h " + remMinutes + "m";
    }

    private String nullSafe(String s) {
        return (s == null || s.isBlank()) ? "Unknown" : s;
    }

    private String numOrNa(Number n) {
        return n == null ? "N/A" : n.toString();
    }

    private String linkOrNa(String url) {
        return (url == null || url.isBlank()) ? "N/A" : url;
    }
}
