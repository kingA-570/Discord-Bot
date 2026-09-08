package com.domistats.bot.service;

import com.domistats.bot.config.DomiStatsProperties;
import org.springframework.stereotype.Component;

import java.util.concurrent.locks.ReentrantLock;

/**
 * Enforces a minimum spacing between outbound requests to DomiStats
 * (domistats.min-request-interval-ms) regardless of how many callers
 * (slash commands, scheduled scans) are asking for data concurrently.
 *
 * This is intentionally simple (a mutex + sleep) rather than a full token
 * bucket, since DomiStats request volume from this bot should be low and
 * bursty concurrency isn't expected.
 */
@Component
public class RateLimiter {

    private final DomiStatsProperties props;
    private final ReentrantLock lock = new ReentrantLock();
    private volatile long lastRequestAtMs = 0L;

    public RateLimiter(DomiStatsProperties props) {
        this.props = props;
    }

    /** Blocks the calling thread until it is safe to issue the next request. */
    public void acquire() {
        lock.lock();
        try {
            long now = System.currentTimeMillis();
            long earliestNext = lastRequestAtMs + props.getMinRequestIntervalMs();
            long waitMs = earliestNext - now;
            if (waitMs > 0) {
                try {
                    Thread.sleep(waitMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            lastRequestAtMs = System.currentTimeMillis();
        } finally {
            lock.unlock();
        }
    }
}
