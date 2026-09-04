package com.fidenz.weather.weather;

import com.fidenz.weather.api.dto.WeatherDashboardDto;
import com.fidenz.weather.cache.CacheSnapshot;
import com.fidenz.weather.cache.TtlCache;
import com.fidenz.weather.config.CacheProperties;
import com.fidenz.weather.weather.model.OwmWeatherResponse;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;

/**
 * Owns the application's two caches and nothing else.
 *
 * <h2>Why two caches rather than one</h2>
 * They answer different questions and expire independently:
 *
 * <ul>
 *   <li><b>raw-weather</b> - one entry per city, keyed by city code. Protects the
 *       OpenWeatherMap quota. Still useful to any future per-city endpoint.</li>
 *   <li><b>processed-dashboard</b> - a single entry holding the fully scored and ranked
 *       payload. A hit here skips scoring and sorting entirely, not just the network.</li>
 * </ul>
 *
 * <p>A single cache could not do both: caching only raw responses would re-score ten cities
 * on every request, while caching only the processed payload would refetch all ten cities
 * the moment it expired, even for a city whose raw data was still perfectly fresh.
 */
@Component
public class WeatherCaches {

    public static final String RAW_CACHE = "raw-weather";
    public static final String PROCESSED_CACHE = "processed-dashboard";
    /** The processed cache holds one payload, so its key is a constant. */
    public static final String DASHBOARD_KEY = "dashboard";

    private final TtlCache<String, OwmWeatherResponse> raw;
    private final TtlCache<String, WeatherDashboardDto> processed;

    public WeatherCaches(CacheProperties properties, Clock clock) {
        this.raw = new TtlCache<>(RAW_CACHE, properties.getRawTtl(), clock);
        this.processed = new TtlCache<>(PROCESSED_CACHE, properties.getProcessedTtl(), clock);
    }

    public TtlCache<String, OwmWeatherResponse> raw() {
        return raw;
    }

    public TtlCache<String, WeatherDashboardDto> processed() {
        return processed;
    }

    public List<CacheSnapshot> snapshots() {
        return List.of(raw.snapshot(), processed.snapshot());
    }

    public void clearAll() {
        raw.clear();
        processed.clear();
    }
}
