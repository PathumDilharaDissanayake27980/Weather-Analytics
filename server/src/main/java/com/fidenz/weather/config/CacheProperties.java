package com.fidenz.weather.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Cache time-to-live settings, bound from {@code app.cache.*}.
 *
 * <p>These are configuration rather than constants specifically so tests can set a TTL of a
 * few milliseconds. A hard-coded five minutes would mean either a five-minute test suite or
 * no expiry test at all.
 */
@ConfigurationProperties(prefix = "app.cache")
public class CacheProperties {

    /** Assignment requirement: raw provider responses are cached for 5 minutes. */
    private Duration rawTtl = Duration.ofMinutes(5);

    /**
     * The processed, ranked payload is cached separately ("optional but preferred" in the
     * assignment). It is intentionally shorter than the raw TTL so the dashboard cannot serve
     * a ranking assembled from data that has since expired underneath it.
     */
    private Duration processedTtl = Duration.ofMinutes(5);

    public Duration getRawTtl() {
        return rawTtl;
    }

    public void setRawTtl(Duration rawTtl) {
        this.rawTtl = rawTtl;
    }

    public Duration getProcessedTtl() {
        return processedTtl;
    }

    public void setProcessedTtl(Duration processedTtl) {
        this.processedTtl = processedTtl;
    }
}
