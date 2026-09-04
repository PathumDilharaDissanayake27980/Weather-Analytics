package com.fidenz.weather.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/**
 * A {@link Clock} that tests can wind forward by hand.
 *
 * <p>This is why every time-dependent component takes an injected clock. Testing a five-minute
 * TTL against the wall clock would mean either a five-minute test suite or - far more likely -
 * no expiry test at all.
 */
public class MutableClock extends Clock {

    private Instant instant;
    private final ZoneId zone;

    public MutableClock(Instant start) {
        this(start, ZoneId.of("UTC"));
    }

    public MutableClock(Instant start, ZoneId zone) {
        this.instant = start;
        this.zone = zone;
    }

    public static MutableClock startingNow() {
        return new MutableClock(Instant.parse("2026-09-04T12:00:00Z"));
    }

    public void advance(Duration duration) {
        this.instant = this.instant.plus(duration);
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new MutableClock(instant, zone);
    }

    @Override
    public Instant instant() {
        return instant;
    }
}
