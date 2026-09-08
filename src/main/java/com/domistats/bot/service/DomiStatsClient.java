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
import java.util.List;
import java.util.Optional;

/**
 * Talks to DomiStats over plain HTTP(S) and parses the HTML it returns.
 *
 * IMPORTANT / TODO before deploying:
 *  - DomiStats does not (as far as this project assumes) expose a public JSON API,
 *    so this scrapes rendered HTML. Verify https://domistats.com/robots.txt and
 *    DomiStats' Terms of Service allow this kind of automated access before running
 *    the bot against production, and reach out to the site owner if a lighter-weight
 *    or officially sanctioned data source (API, data export, partnership) is possible.
 *  - The CSS selectors below are best-guess placeholders based on the fields
 *    described in the project spec (alliance name, glory, ranking, league, etc.).
 *    They WILL need to be corrected against the live DOM - use the `data-*`
 *    attributes or stable class names present in DomiStats' actual markup.
 *  - All requests go through RateLimiter.acquire() first, so no code path can
 *    hammer DomiStats regardless of how many Discord commands fire concurrently.
 */
@Slf4j
@Component
public class DomiStatsClient {

    private final DomiStatsProperties props;
    private final RateLimiter rateLimiter;

    public DomiStatsClient(DomiStatsProperties props, RateLimiter rateLimiter) {
        this.props = props;
        this.rateLimiter = rateLimiter;
    }

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /** All alliances currently shown as "spinning" / searching for a war. */
    @Cacheable(CacheConfig.SPINNING_LIST_CACHE)
    public List<AllianceSummary> fetchSpinningAlliances() {
        Document doc = fetchWithRetry(props.getBaseUrl() + "/alliances?status=spinning");
        List<AllianceSummary> results = new ArrayList<>();

        // TODO: adjust selector to match DomiStats' actual alliance-row markup.
        Elements rows = doc.select(".alliance-row, tr.alliance-list-item");
        for (Element row : rows) {
            try {
                results.add(parseAllianceSummaryRow(row));
            } catch (Exception e) {
                log.warn("Failed to parse a spinning-alliance row, skipping: {}", e.getMessage());
            }
        }
        return results;
    }

    /** Full detail page for one alliance. */
    @Cacheable(value = CacheConfig.ALLIANCE_DETAIL_CACHE, key = "#domistatsId")
    public Optional<AllianceDetail> fetchAllianceDetail(String domistatsId) {
        Document doc = fetchWithRetry(props.getBaseUrl() + "/alliances/" + domistatsId);
        if (doc == null) {
            return Optional.empty();
        }
        return Optional.of(parseAllianceDetail(doc, domistatsId));
    }

    /** Look up an alliance's DomiStats id by (fuzzy/exact) name search. */
    public Optional<AllianceSummary> searchAllianceByName(String name) {
        Document doc = fetchWithRetry(props.getBaseUrl() + "/alliances?search=" + urlEncode(name));
        // TODO: adjust selector; take the top/best match from the search results table.
        Element row = doc.selectFirst(".alliance-row, tr.alliance-list-item");
        if (row == null) {
            return Optional.empty();
        }
        return Optional.of(parseAllianceSummaryRow(row));
    }

    /** Current war for an alliance, if any. */
    public Optional<CurrentWar> fetchCurrentWar(String domistatsId) {
        Document doc = fetchWithRetry(props.getBaseUrl() + "/alliances/" + domistatsId + "/current_war");
        if (doc == null) {
            return Optional.empty();
        }
        Element warBlock = doc.selectFirst(".current-war, .war-summary");
        if (warBlock == null) {
            return Optional.empty(); // Not currently in a war.
        }
        return Optional.of(parseCurrentWar(warBlock));
    }

    /** Recent war history for an alliance. */
    public List<WarSummary> fetchWarHistory(String domistatsId, int page) {
        Document doc = fetchWithRetry(props.getBaseUrl() + "/alliance_wars/" + domistatsId + "?page=" + page);
        List<WarSummary> wars = new ArrayList<>();
        Elements rows = doc.select(".war-history-row, tr.war-row");
        for (Element row : rows) {
            try {
                wars.add(parseWarSummaryRow(row));
            } catch (Exception e) {
                log.warn("Failed to parse a war-history row, skipping: {}", e.getMessage());
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
    // Parsing (placeholder selectors — verify against live DOM)
    // ------------------------------------------------------------------

    private AllianceSummary parseAllianceSummaryRow(Element row) {
        String id = row.attr("data-alliance-id");
        String name = text(row, ".alliance-name");
        Integer glory = parseInt(text(row, ".glory"));
        Integer ranking = parseInt(text(row, ".ranking"));
        Integer members = parseInt(text(row, ".member-count"));
        String league = text(row, ".league");
        Double winRate = parsePercent(text(row, ".win-rate"));
        Integer weight = parseInt(text(row, ".weight"));
        String href = row.selectFirst("a") != null ? row.selectFirst("a").attr("abs:href") : null;

        return AllianceSummary.builder()
                .domistatsId(id)
                .name(name)
                .glory(glory)
                .ranking(ranking)
                .memberCount(members)
                .league(league)
                .winRate(winRate)
                .estimatedWeight(weight)
                .spinning(row.hasClass("spinning") || !row.select(".spinning-badge").isEmpty())
                .inWar(!row.select(".in-war-badge").isEmpty())
                .profileUrl(href)
                .build();
    }

    private AllianceDetail parseAllianceDetail(Document doc, String domistatsId) {
        List<String> perks = new ArrayList<>();
        doc.select(".perk-list .perk").forEach(el -> perks.add(el.text()));

        List<WarSummary> recentWars = new ArrayList<>();
        doc.select(".recent-wars .war-row").forEach(row -> {
            try {
                recentWars.add(parseWarSummaryRow(row));
            } catch (Exception ignored) {
                // best-effort
            }
        });

        return AllianceDetail.builder()
                .domistatsId(domistatsId)
                .name(text(doc, ".alliance-name, h1.name"))
                .glory(parseInt(text(doc, ".glory")))
                .ranking(parseInt(text(doc, ".ranking")))
                .league(text(doc, ".league"))
                .memberCount(parseInt(text(doc, ".member-count")))
                .winRate(parsePercent(text(doc, ".win-rate")))
                .parliament(text(doc, ".parliament"))
                .perks(perks)
                .language(text(doc, ".language"))
                .recruitmentStatus(text(doc, ".recruitment-status"))
                .recentWars(recentWars)
                .profileUrl(props.getBaseUrl() + "/alliances/" + domistatsId)
                .build();
    }

    private CurrentWar parseCurrentWar(Element warBlock) {
        Element allianceAEl = warBlock.selectFirst(".war-alliance-a");
        Element allianceBEl = warBlock.selectFirst(".war-alliance-b");

        AllianceSummary allianceA = allianceAEl != null ? parseAllianceSummaryRow(allianceAEl) : null;
        AllianceSummary allianceB = allianceBEl != null ? parseAllianceSummaryRow(allianceBEl) : null;

        return CurrentWar.builder()
                .domistatsWarId(warBlock.attr("data-war-id"))
                .allianceA(allianceA)
                .allianceB(allianceB)
                .status(text(warBlock, ".war-status"))
                .warSize(parseInt(text(warBlock, ".war-size")))
                .scoreA(parseInt(text(warBlock, ".score-a")))
                .scoreB(parseInt(text(warBlock, ".score-b")))
                .startInfo(text(warBlock, ".war-start"))
                .endInfo(text(warBlock, ".war-end"))
                .warUrl(warBlock.selectFirst("a") != null ? warBlock.selectFirst("a").attr("abs:href") : null)
                .build();
    }

    private WarSummary parseWarSummaryRow(Element row) {
        Integer ownScore = parseInt(text(row, ".own-score"));
        Integer oppScore = parseInt(text(row, ".opponent-score"));
        boolean win = row.hasClass("win") || !row.select(".result-win").isEmpty();

        return WarSummary.builder()
                .domistatsWarId(row.attr("data-war-id"))
                .opponentName(text(row, ".opponent-name"))
                .win(win)
                .ownScore(ownScore)
                .opponentScore(oppScore)
                .warUrl(row.selectFirst("a") != null ? row.selectFirst("a").attr("abs:href") : null)
                .build();
    }

    // ------------------------------------------------------------------
    // Small parsing helpers
    // ------------------------------------------------------------------

    private String text(Element root, String selector) {
        Element el = root.selectFirst(selector);
        return el != null ? el.text().trim() : null;
    }

    private Integer parseInt(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return Integer.parseInt(raw.replaceAll("[^0-9-]", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Double parsePercent(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return Double.parseDouble(raw.replaceAll("[^0-9.]", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
