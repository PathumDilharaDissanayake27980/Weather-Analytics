package com.fidenz.weather.cache;

import java.util.Optional;

/**
 * Result of a cache read, carrying both the value and whether it was a HIT or a MISS.
 *
 * <p>A plain {@code Optional<V>} would lose the HIT/MISS distinction that the assignment's
 * debug endpoint (and the {@code X-Cache} response header) needs to expose.
 */
public record CacheLookup<V>(Status status, V value) {

    public enum Status { HIT, MISS }

    public static <V> CacheLookup<V> hit(V value) {
        return new CacheLookup<>(Status.HIT, value);
    }

    public static <V> CacheLookup<V> miss() {
        return new CacheLookup<>(Status.MISS, null);
    }

    public boolean isHit() {
        return status == Status.HIT;
    }

    public Optional<V> asOptional() {
        return Optional.ofNullable(value);
    }
}
