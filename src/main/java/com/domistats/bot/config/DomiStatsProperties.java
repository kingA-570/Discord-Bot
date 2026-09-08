package com.domistats.bot.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "domistats")
@Getter
@Setter
public class DomiStatsProperties {
    private String baseUrl;
    private long minRequestIntervalMs;
    private long scanIntervalMs;
    private long allianceCacheTtlSeconds;
    private long spinningCacheTtlSeconds;
    private String userAgent;
    private int requestTimeoutMs;
    private int maxRetries;
    private long retryBaseBackoffMs;
}
