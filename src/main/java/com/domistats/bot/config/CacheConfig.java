package com.domistats.bot.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Two named caches so alliance-detail pages (change slowly) and the spinning
 * list (changes more often) can have independent TTLs, keeping request volume
 * to DomiStats as low as possible.
 */
@Configuration
public class CacheConfig {

    public static final String ALLIANCE_DETAIL_CACHE = "allianceDetail";
    public static final String SPINNING_LIST_CACHE = "spinningList";

    @Bean
    public CacheManager cacheManager(DomiStatsProperties props) {
        CaffeineCacheManager manager = new CaffeineCacheManager(ALLIANCE_DETAIL_CACHE, SPINNING_LIST_CACHE);
        manager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(props.getAllianceCacheTtlSeconds(), TimeUnit.SECONDS)
                .maximumSize(5_000));
        return manager;
    }
}
