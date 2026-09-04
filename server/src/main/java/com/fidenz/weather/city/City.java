package com.fidenz.weather.city;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One entry from {@code cities.json}.
 *
 * <p>{@code cityCode} is a String because that is how the source file stores it
 * ({@code "CityCode": "1248991"}). Typing it as a number here would compile and then build
 * subtly wrong URLs.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record City(
        @JsonProperty("CityCode") String cityCode,
        @JsonProperty("CityName") String cityName
) {}
