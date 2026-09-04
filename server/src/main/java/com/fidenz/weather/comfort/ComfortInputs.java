package com.fidenz.weather.comfort;

/**
 * The narrow set of measurements the Comfort Index needs.
 *
 * <p>Deliberately NOT the OpenWeatherMap response type. Keeping the calculator ignorant of
 * where its numbers came from means it can be unit-tested with plain values, and swapping
 * weather providers would not touch the scoring logic at all.
 *
 * <p>{@code rainMmPerHour} and {@code snowMmPerHour} are 0 when absent from the API response.
 * That is safe: across a 130-city sample the {@code rain} field was present in exactly the
 * 20 cities whose condition was "Rain" - absence means genuinely no rain, not "unknown".
 * Contrast {@code wind.gust}, which was absent for 68% of cities with no relationship to
 * actual wind speed, and is therefore excluded from the index entirely.
 */
public record ComfortInputs(
        double tempC,
        double feelsLikeC,
        double humidityPct,
        double windSpeedMs,
        double cloudinessPct,
        double rainMmPerHour,
        double snowMmPerHour,
        double pressureHpa
) {}
