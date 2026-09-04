package com.fidenz.weather.weather.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * The OpenWeatherMap "current weather" response.
 *
 * <p>Nullability below was determined empirically from a 130-city sample rather than guessed
 * (see README, "Known limitations"). Boxed types mark fields that were genuinely absent for
 * some cities; primitives mark fields present in all 130.
 *
 * <p>{@code @JsonIgnoreProperties(ignoreUnknown = true)} is deliberate: OpenWeatherMap adds
 * fields over time, and an unknown field should never take the API down.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OwmWeatherResponse(
        Coord coord,
        List<Condition> weather,
        Main main,
        /** Metres, capped at 10000. 97% of sampled cities sat exactly at the cap, which is
         *  why visibility is not used by the Comfort Index - it cannot discriminate. */
        Integer visibility,
        Wind wind,
        Clouds clouds,
        /** Present in exactly the 20/130 cities whose condition was "Rain". Absence means
         *  genuinely no rain and is safely read as 0. */
        Precipitation rain,
        Precipitation snow,
        long dt,
        Sys sys,
        int timezone,
        long id,
        String name,
        int cod
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Coord(double lon, double lat) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Condition(int id, String main, String description, String icon) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Main(
            double temp,
            @JsonProperty("feels_like") double feelsLike,
            @JsonProperty("temp_min") double tempMin,
            @JsonProperty("temp_max") double tempMax,
            double pressure,
            double humidity,
            @JsonProperty("sea_level") Integer seaLevel,
            @JsonProperty("grnd_level") Integer grndLevel
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Wind(
            double speed,
            Integer deg,
            /**
             * Present for only 42/130 sampled cities (32%). Crucially, cities with and
             * without it had near-identical mean wind speed (4.32 vs 4.02 m/s), so absence
             * is a station-reporting artifact, NOT calm conditions. It therefore cannot be
             * imputed to 0 and is excluded from the Comfort Index.
             */
            Double gust
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Clouds(int all) {}

    /** Precipitation volume in mm. OWM reports "1h" in practice; "3h" is documented but unseen. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Precipitation(
            @JsonProperty("1h") Double oneHour,
            @JsonProperty("3h") Double threeHour
    ) {
        public double millimetresPerHour() {
            if (oneHour != null) {
                return oneHour;
            }
            return threeHour != null ? threeHour / 3.0 : 0.0;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Sys(String country, Long sunrise, Long sunset) {}

    /** First condition, or null when the array is absent or empty. The API types this as a
     *  list and does not promise a first element, so callers must handle absence. */
    public Condition primaryCondition() {
        return (weather == null || weather.isEmpty()) ? null : weather.get(0);
    }
}
