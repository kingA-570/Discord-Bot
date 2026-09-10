package com.domistats.bot.service;

import com.domistats.bot.config.CacheConfig;
import com.domistats.bot.config.DomiStatsProperties;
import com.domistats.bot.dto.AllianceDetail;
import com.domistats.bot.dto.AllianceSummary;
import com.domistats.bot.dto.CurrentWar;
import com.domistats.bot.dto.WarSummary;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Talks to DomiStats over plain HTTP(S) and parses the HTML it returns.
 *
 * Verified against the live DomiStats markup (September 2026):
 *  - Alliance ranking/spinning list  : /ranking?status=spinning     -> rows in table.good-table tbody tr
 *  - Alliance profile                : /alliance/{id}                -> span.alliance-name-big, key/values, etc.
 *  - Alliance search                 : /alliances?search={name}      -> rows in table.good-table tbody tr
 *  - War history                     : /alliance_wars/{id}?page=N    -> blocks in div.war
 *  - Current wars                    : /wars                         -> blocks in div.war
 *
 * NB: war blocks are also server-rendered on /alliance_wars/{id}; the page only *enhances*
 * them client-side, so no JS execution is required.
 *
 * All requests go through RateLimiter.acquire() first, so no code path can hammer
 * DomiStats regardless of how many Discord commands fire concurrently.
 */
@Slf4j
@Component
public class DomiStatsClient {

    private static final Pattern ALLIANCE_ID_PATTERN = Pattern.compile("(\\d+)");

    private final DomiStatsProperties props;
    private final RateLimiter rateLimiter;

    public DomiStatsClient(DomiStatsProperties props, RateLimiter rateLimiter) {
        this.props = props;
        this.rateLimiter = rateLimiter;
    }

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /**
     * Alliances in currently active wars (the closest public analogue to DomiStats'
     * VIP-only "War Radar / spinning" data). Source: the /wars listing (100 live wars).
     */
    @Cacheable(CacheConfig.SPINNING_LIST_CACHE)
    public List<AllianceSummary> fetchSpinningAlliances() {
        Document doc = fetchWithRetry(props.getBaseUrl() + "/wars");
        if (doc == null) {
            return List.of();
        }
        List<AllianceSummary> results = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();
        for (Element warBlock : doc.select("div.war")) {
            Element left = warBlock.selectFirst(".war-opponent-left");
            Element right = warBlock.selectFirst(".war-opponent-right");
            addWarAlliance(results, seenIds, left);
            addWarAlliance(results, seenIds, right);
        }
        return results;
    }

    private void addWarAlliance(List<AllianceSummary> results, Set<String> seenIds, Element block) {
        if (block == null) {
            return;
        }
        try {
            AllianceSummary summary = parseWarAllianceSummary(block);
            if (summary.getDomistatsId() != null && seenIds.add(summary.getDomistatsId())) {
                results.add(summary);
            }
        } catch (Exception e) {
            log.warn("Failed to parse an active-war alliance, skipping: {}", e.getMessage());
        }
    }

    /** Full detail page for one alliance. */
    @Cacheable(value = CacheConfig.ALLIANCE_DETAIL_CACHE, key = "#domistatsId")
    public Optional<AllianceDetail> fetchAllianceDetail(String domistatsId) {
        Document doc = fetchWithRetry(props.getBaseUrl() + "/alliance/" + domistatsId);
        if (doc == null) {
            return Optional.empty();
        }
        return Optional.of(parseAllianceDetail(doc, domistatsId));
    }

    /** Look up an alliance's DomiStats id by (fuzzy/exact) name search. */
    public Optional<AllianceSummary> searchAllianceByName(String name) {
        Document doc = fetchWithRetry(props.getBaseUrl() + "/alliances?search=" + urlEncode(name));
        if (doc == null) {
            return Optional.empty();
        }
        Element row = doc.selectFirst("table.good-table > tbody > tr");
        if (row == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(parseAllianceSummaryRow(row, false));
    }

    /**
     * Current war for an alliance, if any. Looks the alliance up in the global
     * /wars listing (the most recent, currently-running wars).
     */
    public Optional<CurrentWar> fetchCurrentWar(String domistatsId) {
        Document doc = fetchWithRetry(props.getBaseUrl() + "/wars");
        if (doc == null) {
            return Optional.empty();
        }
        for (Element warBlock : doc.select("div.war")) {
            Elements allianceLinks = warBlock.select("a[href^='/alliance_wars/']");
            for (Element link : allianceLinks) {
                Matcher m = ALLIANCE_ID_PATTERN.matcher(link.attr("href"));
                if (m.find() && m.group(1).equals(domistatsId)) {
                    return Optional.of(parseCurrentWar(warBlock));
                }
            }
        }
        return Optional.empty();
    }

    /** Recent war history for an alliance. */
    public List<WarSummary> fetchWarHistory(String domistatsId, int page) {
        Document doc = fetchWithRetry(props.getBaseUrl() + "/alliance_wars/" + domistatsId + "?page=" + page);
        if (doc == null) {
            return List.of();
        }
        List<WarSummary> wars = new ArrayList<>();
        Elements blocks = doc.select("div.war");
        for (Element block : blocks) {
            try {
                wars.add(parseWarSummaryRow(block, domistatsId));
            } catch (Exception e) {
                log.warn("Failed to parse a war-history block, skipping: {}", e.getMessage());
            }
        }
        return wars;
    }

    // ------------------------------------------------------------------
    // HTTP + retry/backoff
    // ------------------------------------------------------------------

    private Document fetchWithRetry(String url) {
        int attempt = 0;
        IOException lastError = null;
        while (attempt <= props.getMaxRetries()) {
            rateLimiter.acquire();
            try {
                Connection.Response response = Jsoup.connect(url)
                        .userAgent(props.getUserAgent())
                        .timeout(props.getRequestTimeoutMs())
                        .header("Accept", "text/html")
                        .followRedirects(true)
                        .execute();

                if (response.statusCode() == 429) {
                    long backoff = backoffMs(attempt);
                    log.warn("DomiStats rate-limited us (429) on {}, backing off {}ms", url, backoff);
                    sleep(backoff);
                    attempt++;
                    continue;
                }
                if (response.statusCode() >= 500) {
                    long backoff = backoffMs(attempt);
                    log.warn("DomiStats returned {} on {}, retrying in {}ms", response.statusCode(), url, backoff);
                    sleep(backoff);
                    attempt++;
                    continue;
                }
                return response.parse();
            } catch (IOException e) {
                lastError = e;
                long backoff = backoffMs(attempt);
                log.warn("Request to {} failed ({}), retrying in {}ms [attempt {}/{}]",
                        url, e.getMessage(), backoff, attempt + 1, props.getMaxRetries());
                sleep(backoff);
                attempt++;
            }
        }
        log.error("Giving up on {} after {} attempts", url, props.getMaxRetries(), lastError);
        return null; // Callers must handle DomiStats being (temporarily) unavailable.
    }

    private long backoffMs(int attempt) {
        // Exponential backoff with a simple cap.
        long base = props.getRetryBaseBackoffMs();
        long backoff = base * (1L << Math.min(attempt, 6));
        return Math.min(backoff, 30_000L);
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String urlEncode(String s) {
        return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------
    // Parsing: alliance ranking/spinning rows (table.good-table tbody tr)
    // ------------------------------------------------------------------

    /**
     * Parses one alliance row from the ranking/search tables.
     * Column layout (verified): Ranking, Alliance, History, Glory,
     * Historic Glory, Win Ratio, Members, Parliament, Last War, Perks, League, ...
     */
    private AllianceSummary parseAllianceSummaryRow(Element row, boolean spinningMarked) {
        Element nameLink = row.selectFirst("td:nth-child(2) a[href^='/alliance/']");
        String id = null;
        String profileUrl = null;
        if (nameLink != null) {
            id = extractId(nameLink.attr("abs:href"));
            profileUrl = nameLink.absUrl("href");
        }

        String name = text(row, "td:nth-child(2) .name-container .alliance-name");
        if (name == null || name.isBlank()) {
            name = text(row, "td:nth-child(2) .name-icons") != null
                    ? text(row, "td:nth-child(2) span.alliance-name")
                    : null;
        }

        String league = rankLeague(text(row, "td:nth-child(11)"));

        return AllianceSummary.builder()
                .domistatsId(id)
                .name(name)
                .glory(parseInt(text(row, "td:nth-child(4) .alliance-name")))
                .ranking(parseInt(text(row, "td:nth-child(1)")))
                .memberCount(parseTotalMembers(text(row, "td:nth-child(7)")))
                .league(league)
                .winRate(parsePercent(text(row, "td:nth-child(6)")))
                .estimatedWeight(null)
                .spinning(spinningMarked)
                .inWar(false)
                .currentWarId(null)
                .profileUrl(profileUrl)
                .build();
    }

    // ------------------------------------------------------------------
    // Parsing: alliance detail (/alliance/{id})
    // ------------------------------------------------------------------

    private AllianceDetail parseAllianceDetail(Document doc, String domistatsId) {
        List<String> perks = new ArrayList<>();
        Element crestPerk = doc.selectFirst(".crest-perk");
        if (crestPerk != null && !crestPerk.text().isBlank()) {
            perks.add(crestPerk.text().trim());
        }

        List<WarSummary> recentWars = new ArrayList<>();
        doc.select("div.war").forEach(block -> {
            try {
                recentWars.add(parseWarSummaryRow(block, domistatsId));
            } catch (Exception ignored) {
                // best-effort
            }
        });

        return AllianceDetail.builder()
                .domistatsId(domistatsId)
                .name(text(doc, "span.alliance-name-big"))
                .glory(parseInt(text(doc, "span.value-text-bright[data-tooltip='Glory']")))
                .ranking(parseInt(text(doc, "div.ranking-div[data-tooltip='Ranking']")))
                .league(rankLeague(text(doc, "div.league-ranking-div.no-wrap")))
                .memberCount(parseKeyValue(doc, "Members"))
                .winRate(parsePercent(text(doc, "span.value-text-bright[data-tooltip*='Win Ratio']")))
                .parliament(text(doc, "div.laws-percent"))
                .perks(perks)
                .language(text(doc, "span[data-tooltip='Language']"))
                .recruitmentStatus(text(doc, "span[data-tooltip='Privacy'] .value-text-bright"))
                .recentWars(recentWars)
                .profileUrl(props.getBaseUrl() + "/alliance/" + domistatsId)
                .build();
    }

    // ------------------------------------------------------------------
    // Parsing: war blocks (div.war) shared by /wars and /alliance_wars/{id}
    // ------------------------------------------------------------------

    private CurrentWar parseCurrentWar(Element warBlock) {
        Element leftBlock = warBlock.selectFirst(".war-opponent-left");
        Element rightBlock = warBlock.selectFirst(".war-opponent-right");
        Element center = warBlock.selectFirst(".war-glory-center");

        AllianceSummary allianceA = parseWarAllianceSummary(leftBlock);
        AllianceSummary allianceB = parseWarAllianceSummary(rightBlock);

        return CurrentWar.builder()
                .domistatsWarId(null)
                .allianceA(allianceA)
                .allianceB(allianceB)
                .status("ONGOING")
                .warSize(null)
                .scoreA(parseInt(text(center, ".war-stars")))
                .scoreB(parseInt(lastText(center, ".war-stars")))
                .startInfo(text(center, ".war-rel-time"))
                .endInfo(null)
                .warUrl(null)
                .build();
    }

    private WarSummary parseWarSummaryRow(Element warBlock, String subjectAllianceId) {
        Element leftBlock = warBlock.selectFirst(".war-opponent-left");
        Element rightBlock = warBlock.selectFirst(".war-opponent-right");
        Element center = warBlock.selectFirst(".war-glory-center");

        Element subjectBlock = blockForAlliance(warBlock, subjectAllianceId);
        Element opponentBlock = subjectBlock == leftBlock ? rightBlock : leftBlock;
        if (subjectBlock == null) {
            subjectBlock = leftBlock;
            opponentBlock = rightBlock;
        }

        boolean win = !subjectBlock.select(".winner, .war-winner").isEmpty();

        return WarSummary.builder()
                .domistatsWarId(null)
                .opponentName(text(opponentBlock, ".war-alliance-name"))
                .win(win)
                .ownScore(parseInt(text(center, ".war-stars")))
                .opponentScore(parseInt(lastText(center, ".war-stars")))
                .warUrl(opponentBlock.selectFirst("a[href^='/alliance_wars/']") != null
                        ? opponentBlock.selectFirst("a[href^='/alliance_wars/']").absUrl("href")
                        : null)
                .build();
    }

    /** Parses one side (alliance) of a war block into a summary. */
    private AllianceSummary parseWarAllianceSummary(Element block) {
        Element link = block.selectFirst("a[href^='/alliance_wars/']");
        String id = null;
        String profileUrl = null;
        if (link != null) {
            id = extractId(link.attr("href"));
            profileUrl = link.absUrl("href");
        }

        String leagueRaw = text(block, ".war-alliance-ranking.league-ranking.no-wrap");
        String league = null;
        int leagueRank = -1;
        if (leagueRaw != null) {
            Matcher m = Pattern.compile("(\\D+)\\s*#?(\\d+)").matcher(leagueRaw.trim());
            if (m.matches()) {
                league = m.group(1).trim().isEmpty() ? null : m.group(1).trim();
                leagueRank = parseInt(m.group(2));
            }
        }

        return AllianceSummary.builder()
                .domistatsId(id)
                .name(text(block, ".war-alliance-name"))
                .glory(parseInt(text(block, ".war-alliance-glory")))
                .ranking(parseInt(text(block, ".war-alliance-ranking.no-wrap")))
                .memberCount(null)
                .league(league != null ? league : rankLeague(leagueRaw))
                .winRate(null)
                .estimatedWeight(null)
                .spinning(false)
                .inWar(true)
                .currentWarId(null)
                .profileUrl(profileUrl)
                .build();
    }

    private Element blockForAlliance(Element warBlock, String allianceId) {
        for (Element block : warBlock.select(".war-opponent-left, .war-opponent-right")) {
            Element link = block.selectFirst("a[href^='/alliance_wars/']");
            if (link != null && allianceId != null && allianceId.equals(extractId(link.attr("href")))) {
                return block;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Small parsing helpers
    // ------------------------------------------------------------------

    /** Reads the league name from a "Heavy 1119 #1" style string. */
    private String rankLeague(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty() || Character.isDigit(trimmed.charAt(0))) {
            return null;
        }
        return trimmed.split("\\s+|#")[0];
    }

    /** "41 / 42" -> 42 (total members). */
    private Integer parseTotalMembers(String raw) {
        if (raw == null) {
            return null;
        }
        Matcher m = Pattern.compile("(\\d+)\\s*/\\s*(\\d+)").matcher(raw);
        if (m.find()) {
            return parseInt(m.group(2));
        }
        return parseInt(raw);
    }

    /** Finds a "Key Value" element whose key equals {@code key} and returns the value. */
    private Integer parseKeyValue(Document doc, String key) {
        for (Element kv : doc.select(".alliance-key-value")) {
            String t = kv.text().trim();
            if (t.startsWith(key + " ") || t.equals(key)) {
                return parseInt(t.substring(key.length()));
            }
        }
        return null;
    }

    /** Strips a leading "#" and non-digit characters; returns (plain) integer or null. */
    private Integer parseInt(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        Matcher m = Pattern.compile("-?\\d+").matcher(raw.replace(",", ""));
        if (!m.find()) {
            return null;
        }
        return Integer.valueOf(m.group());
    }

    private Double parsePercent(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(raw.replaceAll("[^0-9.]", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String text(Element root, String selector) {
        Element el = root.selectFirst(selector);
        return el != null ? el.text().trim() : null;
    }

    private String lastText(Element root, String selector) {
        Elements els = root.select(selector);
        Element el = els.isEmpty() ? null : els.last();
        return el != null ? el.text().trim() : null;
    }

    private String extractId(String href) {
        if (href == null) {
            return null;
        }
        Matcher m = ALLIANCE_ID_PATTERN.matcher(href);
        return m.find() ? m.group(1) : null;
    }
}