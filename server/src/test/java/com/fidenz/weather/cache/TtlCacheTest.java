package com.fidenz.weather.cache;

import com.fidenz.weather.support.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class TtlCacheTest {

    private static final Duration TTL = Duration.ofMinutes(5);

    private MutableClock clock;
    private TtlCache<String, String> cache;

    @BeforeEach
    void setUp() {
        clock = MutableClock.startingNow();
        cache = new TtlCache<>("test", TTL, clock);
    }

    @Nested
    @DisplayName("Hit and miss")
    class HitAndMiss {

        @Test
        @DisplayName("an unknown key is a MISS")
        void unknownKeyIsAMiss() {
            CacheLookup<String> lookup = cache.get("colombo");

            assertThat(lookup.isHit()).isFalse();
            assertThat(lookup.status()).isEqualTo(CacheLookup.Status.MISS);
            assertThat(lookup.value()).isNull();
        }

        @Test
        @DisplayName("a stored key is a HIT and returns the stored value")
        void storedKeyIsAHit() {
            cache.put("colombo", "30C");

            CacheLookup<String> lookup = cache.get("colombo");

            assertThat(lookup.isHit()).isTrue();
            assertThat(lookup.value()).isEqualTo("30C");
        }

        @Test
        @DisplayName("keys are isolated from one another")
        void keysAreIsolated() {
            cache.put("colombo", "30C");

            assertThat(cache.get("colombo").isHit()).isTrue();
            assertThat(cache.get("oslo").isHit()).isFalse();
        }

        @Test
        @DisplayName("re-putting a key replaces the value and restarts its TTL")
        void putReplacesValueAndResetsTtl() {
            cache.put("colombo", "30C");
            clock.advance(Duration.ofMinutes(4));
            cache.put("colombo", "31C");

            // Four more minutes: past the original expiry, but this entry was refreshed.
            clock.advance(Duration.ofMinutes(4));

            assertThat(cache.get("colombo").value()).isEqualTo("31C");
        }
    }

    @Nested
    @DisplayName("Expiry")
    class Expiry {

        @Test
        @DisplayName("an entry survives right up to its TTL")
        void entrySurvivesUntilTtl() {
            cache.put("colombo", "30C");

            clock.advance(TTL.minusSeconds(1));

            assertThat(cache.get("colombo").isHit()).isTrue();
        }

        @Test
        @DisplayName("an entry expires exactly at its TTL")
        void entryExpiresAtTtl() {
            cache.put("colombo", "30C");

            clock.advance(TTL);

            assertThat(cache.get("colombo").isHit())
                    .as("TTL is inclusive of the boundary - at exactly 5 minutes it is stale")
                    .isFalse();
        }

        @Test
        @DisplayName("reading an expired entry evicts it")
        void readingAnExpiredEntryEvictsIt() {
            cache.put("colombo", "30C");
            clock.advance(TTL.plusSeconds(1));

            cache.get("colombo");

            assertThat(cache.size()).isZero();
            assertThat(cache.snapshot().evictions()).isEqualTo(1);
        }

        @Test
        @DisplayName("an expired entry counts as a MISS, not a stale HIT")
        void expiredEntryCountsAsMiss() {
            cache.put("colombo", "30C");
            clock.advance(TTL.plusSeconds(1));

            cache.get("colombo");

            assertThat(cache.snapshot().hits()).isZero();
            assertThat(cache.snapshot().misses()).isEqualTo(1);
        }

        @Test
        @DisplayName("purgeExpired removes stale entries that were never read again")
        void purgeExpiredRemovesUnreadStaleEntries() {
            cache.put("colombo", "30C");
            cache.put("oslo", "14C");
            clock.advance(Duration.ofMinutes(3));
            cache.put("paris", "22C");

            clock.advance(Duration.ofMinutes(3));   // colombo and oslo now stale, paris fresh

            assertThat(cache.purgeExpired()).isEqualTo(2);
            assertThat(cache.size()).isEqualTo(1);
            assertThat(cache.get("paris").isHit()).isTrue();
        }
    }

    @Nested
    @DisplayName("Statistics")
    class Statistics {

        @Test
        @DisplayName("hits and misses are counted independently")
        void countsHitsAndMisses() {
            cache.get("colombo");           // miss
            cache.put("colombo", "30C");
            cache.get("colombo");           // hit
            cache.get("colombo");           // hit
            cache.get("oslo");              // miss

            CacheSnapshot snapshot = cache.snapshot();

            assertThat(snapshot.hits()).isEqualTo(2);
            assertThat(snapshot.misses()).isEqualTo(2);
            assertThat(snapshot.hitRate()).isEqualTo(0.5);
        }

        @Test
        @DisplayName("hit rate is 0 rather than NaN before any traffic")
        void hitRateIsZeroWithNoTraffic() {
            assertThat(cache.snapshot().hitRate()).isEqualTo(0.0).isNotNaN();
        }

        @Test
        @DisplayName("the snapshot reports each entry's age and remaining lifetime")
        void snapshotReportsEntryAges() {
            cache.put("colombo", "30C");
            clock.advance(Duration.ofMinutes(2));

            CacheSnapshot.EntrySnapshot entry = cache.snapshot().entries().get(0);

            assertThat(entry.key()).isEqualTo("colombo");
            assertThat(entry.ageSeconds()).isEqualTo(120);
            assertThat(entry.expiresInSeconds()).isEqualTo(180);
            assertThat(entry.expired()).isFalse();
        }

        @Test
        @DisplayName("the snapshot flags an entry that is stale but not yet evicted")
        void snapshotFlagsStaleEntries() {
            cache.put("colombo", "30C");
            clock.advance(TTL.plusMinutes(1));

            CacheSnapshot.EntrySnapshot entry = cache.snapshot().entries().get(0);

            assertThat(entry.expired()).isTrue();
            assertThat(entry.expiresInSeconds()).isNegative();
        }

        @Test
        @DisplayName("entries are listed in a stable order")
        void entriesAreSortedByKey() {
            cache.put("oslo", "14C");
            cache.put("colombo", "30C");
            cache.put("paris", "22C");

            assertThat(cache.snapshot().entries())
                    .extracting(CacheSnapshot.EntrySnapshot::key)
                    .containsExactly("colombo", "oslo", "paris");
        }

        @Test
        @DisplayName("the snapshot reports the cache's name and TTL")
        void snapshotReportsIdentity() {
            CacheSnapshot snapshot = cache.snapshot();

            assertThat(snapshot.name()).isEqualTo("test");
            assertThat(snapshot.ttlSeconds()).isEqualTo(300);
        }
    }

    @Nested
    @DisplayName("Invalidation")
    class Invalidation {

        @Test
        @DisplayName("clear empties the cache and resets the counters")
        void clearResetsEverything() {
            cache.put("colombo", "30C");
            cache.get("colombo");
            cache.get("oslo");

            cache.clear();

            CacheSnapshot snapshot = cache.snapshot();
            assertThat(snapshot.size()).isZero();
            assertThat(snapshot.hits()).isZero();
            assertThat(snapshot.misses()).isZero();
            assertThat(snapshot.entries()).isEmpty();
        }

        @Test
        @DisplayName("invalidate removes one key without counting an eviction")
        void invalidateRemovesOneKey() {
            cache.put("colombo", "30C");
            cache.put("oslo", "14C");

            cache.invalidate("colombo");

            assertThat(cache.get("colombo").isHit()).isFalse();
            assertThat(cache.get("oslo").isHit()).isTrue();
            assertThat(cache.snapshot().evictions())
                    .as("deliberate removal is not an expiry")
                    .isZero();
        }
    }

    @Nested
    @DisplayName("Concurrency")
    class Concurrency {

        @Test
        @DisplayName("concurrent readers and writers do not corrupt the cache or its counters")
        void survivesConcurrentAccess() throws Exception {
            int threads = 8;
            int iterations = 500;
            ExecutorService pool = Executors.newFixedThreadPool(threads);
            CountDownLatch start = new CountDownLatch(1);
            AtomicInteger errors = new AtomicInteger();

            for (int t = 0; t < threads; t++) {
                final int id = t;
                pool.submit(() -> {
                    try {
                        start.await();
                        for (int i = 0; i < iterations; i++) {
                            String key = "city-" + (i % 10);
                            cache.put(key, "value-" + id);
                            cache.get(key);
                        }
                    } catch (Exception e) {
                        errors.incrementAndGet();
                    }
                });
            }

            start.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

            assertThat(errors.get()).isZero();
            assertThat(cache.size()).isEqualTo(10);
            assertThat(cache.snapshot().hits() + cache.snapshot().misses())
                    .isEqualTo((long) threads * iterations);
        }
    }
}
