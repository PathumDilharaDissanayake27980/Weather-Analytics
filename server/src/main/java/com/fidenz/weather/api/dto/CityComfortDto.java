package com.fidenz.weather.api.dto;

import java.time.Instant;
import java.util.Map;

/**
 * One ranked city, as returned to the browser.
 *
 * <p>Everything here is computed server-side, including {@code rank} and {@code comfortLabel}.
 * The assignment requires the score to be produced by the backend; keeping the rank and the
 * label there too means every client sees identical results and the frontend stays a pure
 * presentation layer.
 *
 * <p>{@code subScores} and {@code multipliers} are included so the UI can show <em>why</em> a
 * city scored what it did. That transparency is what makes a constructed index defensible -
 * an opaque number nobody can interrogate is much harder to trust.
 */
public record CityComfortDto(
        int rank,
        String cityCode,
        String cityName,
        String country,
        String description,
        String icon,
        double temperatureC,
        double feelsLikeC,
        double humidityPct,
        double windSpeedMs,
        double cloudinessPct,
        double pressureHpa,
        double rainMmPerHour,
        double comfortScore,
        String comfortLabel,
        Map<String, Double> subScores,
        Map<String, Double> multipliers,
        Instant observedAt
) {}
