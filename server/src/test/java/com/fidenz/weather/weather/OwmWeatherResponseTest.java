package com.fidenz.weather.weather;

import com.fidenz.weather.support.Fixtures;
import com.fidenz.weather.weather.model.OwmWeatherResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Deserialisation tests against real captured payloads.
 *
 * <p>These exist because the parts of the response that are easy to get wrong are exactly the
 * parts that are absent most of the time - so a bug here would surface as a production
 * {@code NullPointerException} on whichever city happened to be raining.
 */
class OwmWeatherResponseTest {

    @Test
    @DisplayName("snake_case fields map onto camelCase record components")
    void mapsSnakeCaseFields() {
        OwmWeatherResponse colombo = Fixtures.colombo();

        assertThat(colombo.main().temp()).isEqualTo(30.02);
        assertThat(colombo.main().feelsLike()).isEqualTo(36.62);
        assertThat(colombo.main().seaLevel()).isEqualTo(1008);
        assertThat(colombo.main().grndLevel()).isEqualTo(1010);
    }

    @Test
    @DisplayName("a missing wind.gust deserialises as null rather than failing")
    void missingGustIsNull() {
        // Boston is the reason gust is excluded from the Comfort Index: it was absent for
        // 68% of a 130-city sample, with no relationship to actual wind speed.
        OwmWeatherResponse boston = Fixtures.boston();

        assertThat(boston.wind().speed()).isEqualTo(1.54);
        assertThat(boston.wind().gust()).isNull();
    }

    @Test
    @DisplayName("an absent rain object deserialises as null, not as zero rainfall")
    void absentRainIsNull() {
        assertThat(Fixtures.colombo().rain()).isNull();
        assertThat(Fixtures.colombo().snow()).isNull();
    }

    @Test
    @DisplayName("the quoted 1h rain field is read correctly")
    void readsQuotedOneHourField() {
        OwmWeatherResponse tokyo = Fixtures.tokyo();

        assertThat(tokyo.rain()).isNotNull();
        assertThat(tokyo.rain().oneHour()).isEqualTo(3.87);
        assertThat(tokyo.rain().millimetresPerHour()).isEqualTo(3.87);
    }

    @Test
    @DisplayName("a 3h-only reading is converted to an hourly rate")
    void convertsThreeHourReadingToHourly() {
        OwmWeatherResponse.Precipitation threeHourOnly =
                new OwmWeatherResponse.Precipitation(null, 6.0);

        assertThat(threeHourOnly.millimetresPerHour()).isEqualTo(2.0);
    }

    @Test
    @DisplayName("precipitation with neither field reads as zero")
    void emptyPrecipitationIsZero() {
        assertThat(new OwmWeatherResponse.Precipitation(null, null).millimetresPerHour())
                .isEqualTo(0.0);
    }

    @Test
    @DisplayName("the primary condition is the first element of the weather array")
    void primaryConditionIsFirstElement() {
        assertThat(Fixtures.tokyo().primaryCondition().description()).isEqualTo("moderate rain");
        assertThat(Fixtures.colombo().primaryCondition().main()).isEqualTo("Clouds");
    }

    @Test
    @DisplayName("an absent or empty weather array yields no primary condition")
    void handlesMissingConditionArray() {
        OwmWeatherResponse noConditions = Fixtures.parse(
                "{\"main\":{\"temp\":20,\"feels_like\":20,\"temp_min\":19,\"temp_max\":21,"
                        + "\"pressure\":1013,\"humidity\":50},\"wind\":{\"speed\":2},"
                        + "\"clouds\":{\"all\":10},\"dt\":1788520215,\"id\":1,\"name\":\"Nowhere\",\"cod\":200}");

        assertThat(noConditions.primaryCondition()).isNull();
    }

    @Test
    @DisplayName("unknown fields are ignored, so the API can add fields without breaking us")
    void ignoresUnknownFields() {
        OwmWeatherResponse withExtras = Fixtures.parse(
                "{\"main\":{\"temp\":20,\"feels_like\":20,\"temp_min\":19,\"temp_max\":21,"
                        + "\"pressure\":1013,\"humidity\":50,\"brand_new_field\":42},"
                        + "\"wind\":{\"speed\":2},\"clouds\":{\"all\":10},\"dt\":1,\"id\":1,"
                        + "\"name\":\"Nowhere\",\"cod\":200,\"some_future_thing\":\"x\"}");

        assertThat(withExtras.main().temp()).isEqualTo(20.0);
    }
}
