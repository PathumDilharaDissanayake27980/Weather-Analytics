package com.fidenz.weather.api;

import com.fidenz.weather.cache.CacheSnapshot;
import com.fidenz.weather.weather.WeatherCaches;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Cache introspection, required by the assignment ("a debug endpoint to show cache
 * status (HIT / MISS)").
 *
 * <p>Deliberately kept behind the same authentication as the rest of the API. It exposes no
 * secrets, but cache keys and hit rates are operational detail, and an endpoint that can
 * flush a cache is a denial-of-service lever if left open. The README documents how to call
 * it with a token.
 */
@RestController
@RequestMapping("/api/debug")
public class DebugController {

    public record CacheStatusResponse(Instant serverTime, List<CacheSnapshot> caches) {}

    private final WeatherCaches caches;
    private final Clock clock;

    public DebugController(WeatherCaches caches, Clock clock) {
        this.caches = caches;
        this.clock = clock;
    }

    /**
     * Current state of both caches: size, TTL, cumulative hits and misses, hit rate, and
     * every live key with its age and remaining lifetime.
     */
    @GetMapping("/cache")
    public CacheStatusResponse cacheStatus() {
        return new CacheStatusResponse(Instant.now(clock), caches.snapshots());
    }

    /**
     * Empties both caches and resets the counters, so the next request is a guaranteed MISS.
     * Makes cache behaviour demonstrable without waiting five minutes for a TTL to expire.
     */
    @DeleteMapping("/cache")
    public ResponseEntity<Void> clearCaches() {
        caches.clearAll();
        return ResponseEntity.noContent().build();
    }
}
