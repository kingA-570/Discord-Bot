package com.domistats.bot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.cache.annotation.EnableCaching;

/**
 * Entry point for the DomiStats War Radar Discord bot.
 *
 * Architecture (see README.md for full detail):
 *   Discord <-> JDA <-> Discord command layer
 *                          |
 *                     Bot services (Alliance/War/Watchlist/Config)
 *                          |
 *                DomiStatsClient (scraper, cached, rate-limited)
 *                          |
 *                PostgreSQL (state history, watchlists, server config)
 *                          |
 *              SpinWarDetectionScheduler (periodic poll + diffing)
 *                          |
 *                  NotificationDispatcher -> Discord
 */
@SpringBootApplication
@EnableScheduling
@EnableCaching
public class DomiStatsBotApplication {
    public static void main(String[] args) {
        SpringApplication.run(DomiStatsBotApplication.class, args);
    }
}
