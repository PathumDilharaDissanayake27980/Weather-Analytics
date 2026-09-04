package com.fidenz.weather.cache;

import java.util.List;

/**
 * Point-in-time view of a cache, returned by the debug endpoint.
 */
public record CacheSnapshot(
        String name,
        long ttlSeconds,
        int size,
        long hits,
        long misses,
        long evictions,
        double hitRate,
        List<EntrySnapshot> entries
) {
    /**
     * @param ageSeconds           how long ago this entry was stored
     * @param expiresInSeconds     seconds until expiry; negative means already expired
     *                             but not yet evicted (eviction here is lazy)
     */
    public record EntrySnapshot(String key, long ageSeconds, long expiresInSeconds, boolean expired) {}
}
