package com.fidenz.weather.api.dto;

import java.time.Instant;
import java.util.List;

/**
 * The dashboard payload: every city ranked most to least comfortable.
 *
 * @param generatedAt  when this payload was computed (not when it was served - a cached
 *                     payload keeps its original timestamp, which is how the UI can show
 *                     the data's true age)
 * @param cacheStatus  HIT or MISS for the processed-result cache, surfaced for the
 *                     assignment's debug requirement and mirrored in the X-Cache header
 * @param cityCount    number of cities successfully scored
 * @param cities       ranked, most comfortable first
 * @param failures     cities that could not be fetched; the dashboard degrades rather than
 *                     failing outright when one city is unavailable
 */
public record WeatherDashboardDto(
        Instant generatedAt,
        String cacheStatus,
        int cityCount,
        List<CityComfortDto> cities,
        List<CityFailureDto> failures
) {
    public record CityFailureDto(String cityCode, String cityName, String reason) {}
}
