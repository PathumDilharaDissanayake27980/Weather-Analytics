package com.fidenz.weather.cache;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A small, thread-safe, in-memory cache with a fixed time-to-live per entry.
 *
 * <p><b>Why hand-rolled instead of Spring's {@code @Cacheable}?</b> The assignment requires a
 * debug endpoint that reports cache HIT/MISS. Spring's cache abstraction deliberately hides
 * that detail behind a proxy, so surfacing it would mean reaching around the abstraction
 * anyway. Writing ~100 lines here keeps the behaviour explicit, testable, and explainable.
 *
 * <p><b>Eviction is lazy.</b> Expired entries are removed when they are next read, not by a
 * background thread. Trade-off: a key that is never read again occupies memory until
 * {@link #purgeExpired()} runs. For a fixed set of ~10 cities that is irrelevant, and it
 * avoids a scheduler and its lifecycle. For an unbounded key space this would need revisiting.
 *
 * <p><b>The {@link Clock} is injected</b> so tests can advance time instead of sleeping.
 * Testing a 5-minute TTL against the wall clock would make the suite take 5 minutes.
 */
public class TtlCache<K, V> {

    private record Entry<V>(V value, Instant storedAt, Instant expiresAt) {}

    private final String name;
    private final Duration ttl;
    private final Clock clock;
    private final ConcurrentHashMap<K, Entry<V>> store = new ConcurrentHashMap<>();

    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();
    private final AtomicLong evictions = new AtomicLong();

    public TtlCache(String name, Duration ttl, Clock clock) {
        this.name = name;
        this.ttl = ttl;
        this.clock = clock;
    }

    /**
     * Reads a key, reporting whether it was a HIT or a MISS.
     * An entry past its TTL counts as a MISS and is evicted on the spot.
     */
    public CacheLookup<V> get(K key) {
        Entry<V> entry = store.get(key);
        if (entry == null) {
            misses.incrementAndGet();
            return CacheLookup.miss();
        }
        if (isExpired(entry)) {
            store.remove(key, entry);
            evictions.incrementAndGet();
            misses.incrementAndGet();
            return CacheLookup.miss();
        }
        hits.incrementAndGet();
        return CacheLookup.hit(entry.value());
    }

    public void put(K key, V value) {
        Instant now = clock.instant();
        store.put(key, new Entry<>(value, now, now.plus(ttl)));
    }

    /** Removes a single key without counting it as an eviction. */
    public void invalidate(K key) {
        store.remove(key);
    }

    /** Drops every entry and resets the hit/miss counters. Used by the debug endpoint. */
    public void clear() {
        store.clear();
        hits.set(0);
        misses.set(0);
        evictions.set(0);
    }

    /** Removes all expired entries eagerly. Not required for correctness; bounds memory. */
    public int purgeExpired() {
        int removed = 0;
        for (Map.Entry<K, Entry<V>> e : store.entrySet()) {
            if (isExpired(e.getValue()) && store.remove(e.getKey(), e.getValue())) {
                evictions.incrementAndGet();
                removed++;
            }
        }
        return removed;
    }

    public int size() {
        return store.size();
    }

    public String getName() {
        return name;
    }

    public Duration getTtl() {
        return ttl;
    }

    public CacheSnapshot snapshot() {
        Instant now = clock.instant();
        List<CacheSnapshot.EntrySnapshot> entries = new ArrayList<>();
        for (Map.Entry<K, Entry<V>> e : store.entrySet()) {
            Entry<V> v = e.getValue();
            entries.add(new CacheSnapshot.EntrySnapshot(
                    String.valueOf(e.getKey()),
                    Duration.between(v.storedAt(), now).toSeconds(),
                    Duration.between(now, v.expiresAt()).toSeconds(),
                    isExpired(v)
            ));
        }
        entries.sort(Comparator.comparing(CacheSnapshot.EntrySnapshot::key));

        long h = hits.get();
        long m = misses.get();
        double rate = (h + m) == 0 ? 0.0 : (double) h / (h + m);

        return new CacheSnapshot(name, ttl.toSeconds(), store.size(), h, m, evictions.get(), rate, entries);
    }

    private boolean isExpired(Entry<V> entry) {
        return !clock.instant().isBefore(entry.expiresAt());
    }
}
