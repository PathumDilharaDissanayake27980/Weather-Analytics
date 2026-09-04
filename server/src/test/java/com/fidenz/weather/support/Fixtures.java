package com.fidenz.weather.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fidenz.weather.weather.model.OwmWeatherResponse;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Real OpenWeatherMap payloads, captured while designing the Comfort Index.
 *
 * <p>Deliberately real rather than invented. Hand-written fixtures encode what you
 * <em>believe</em> the API returns; these encode what it actually returned - including the
 * awkward cases that hand-written ones would have quietly omitted, such as Boston's missing
 * {@code wind.gust} and Tokyo's present-only-when-raining {@code rain} object.
 */
public final class Fixtures {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Fixtures() {
    }

    /** Hot and humid: 30C that feels like 36.6C. Has wind.gust. */
    public static final String COLOMBO = read("fixtures/colombo.json");
    /** Mild, and crucially has NO wind.gust field - proving absence is normal. */
    public static final String BOSTON_NO_GUST = read("fixtures/boston-no-gust.json");
    /** Raining, so the rain object is present. */
    public static final String TOKYO_RAIN = read("fixtures/tokyo-rain.json");
    /** Cool and overcast. */
    public static final String OSLO = read("fixtures/oslo.json");

    public static OwmWeatherResponse parse(String json) {
        try {
            return MAPPER.readValue(json, OwmWeatherResponse.class);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static OwmWeatherResponse colombo() {
        return parse(COLOMBO);
    }

    public static OwmWeatherResponse boston() {
        return parse(BOSTON_NO_GUST);
    }

    public static OwmWeatherResponse tokyo() {
        return parse(TOKYO_RAIN);
    }

    public static OwmWeatherResponse oslo() {
        return parse(OSLO);
    }

    private static String read(String path) {
        try (InputStream in = Fixtures.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing test fixture: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
