package com.fidenz.weather.comfort;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Tests for the Comfort Index.
 *
 * <p>A constructed index has no ground truth - there is no comfort-meter to check against -
 * so these tests assert <em>properties</em> rather than a table of expected outputs:
 *
 * <ul>
 *   <li><b>Bounds</b> - the score is always within 0-100 and never NaN, for any input.</li>
 *   <li><b>Monotonicity</b> - moving further from the ideal never <em>raises</em> the score.</li>
 *   <li><b>Asymmetry</b> - heat is punished harder than the equivalent departure into cold.</li>
 *   <li><b>Non-compensation</b> - an extreme value cannot be voted away by pleasant ones.</li>
 * </ul>
 *
 * <p>Property tests survive re-tuning. Tests that assert {@code score == 73.2} would break
 * every time a weight moved - which matters here, because the assessment requires adding a
 * parameter live and the suite must still be green afterwards.
 */
class ComfortIndexCalculatorTest {

    private final ComfortIndexProperties properties = new ComfortIndexProperties();
    private final ComfortIndexCalculator calculator = new ComfortIndexCalculator(properties);

    /** Pleasant baseline: every sub-score 1.0, every multiplier 1.0. */
    private static ComfortInputs ideal() {
        return new ComfortInputs(23, 23, 50, 2.5, 25, 0, 0, 1013);
    }

    private static ComfortInputs withTemp(double temp) {
        return new ComfortInputs(temp, temp, 50, 2.5, 25, 0, 0, 1013);
    }

    private static ComfortInputs withHumidity(double humidity) {
        return new ComfortInputs(23, 23, humidity, 2.5, 25, 0, 0, 1013);
    }

    private static ComfortInputs withWind(double wind) {
        return new ComfortInputs(23, 23, 50, wind, 25, 0, 0, 1013);
    }

    private static ComfortInputs withCloud(double cloud) {
        return new ComfortInputs(23, 23, 50, 2.5, cloud, 0, 0, 1013);
    }

    @Nested
    @DisplayName("Score bounds")
    class Bounds {

        @Test
        @DisplayName("perfect conditions score exactly 100")
        void perfectConditionsScore100() {
            assertThat(calculator.calculate(ideal()).score()).isEqualTo(100.0);
        }

        @Test
        @DisplayName("score stays within 0-100 across the whole plausible input space")
        void scoreAlwaysWithinBounds() {
            for (double temp = -60; temp <= 60; temp += 5) {
                for (double humidity = 0; humidity <= 100; humidity += 20) {
                    for (double wind = 0; wind <= 40; wind += 8) {
                        for (double cloud = 0; cloud <= 100; cloud += 25) {
                            ComfortInputs in = new ComfortInputs(
                                    temp, temp + 3, humidity, wind, cloud, 5, 2, 1000);
                            double score = calculator.calculate(in).score();
                            assertThat(score)
                                    .as("score for temp=%s humidity=%s wind=%s cloud=%s",
                                            temp, humidity, wind, cloud)
                                    .isBetween(0.0, 100.0)
                                    .isNotNaN();
                        }
                    }
                }
            }
        }

        @Test
        @DisplayName("absurd inputs still produce a usable number, never NaN or infinity")
        void absurdInputsDoNotBreakTheScore() {
            ComfortInputs absurd = new ComfortInputs(
                    -273.15, 500, 100, 200, 100, 1000, 1000, 0);
            double score = calculator.calculate(absurd).score();
            assertThat(score).isNotNaN().isFinite().isBetween(0.0, 100.0);
        }

        @Test
        @DisplayName("a NaN measurement degrades that parameter instead of poisoning the score")
        void nanInputDoesNotPropagate() {
            ComfortInputs missing = new ComfortInputs(
                    Double.NaN, Double.NaN, 50, 2.5, 25, 0, 0, 1013);
            double score = calculator.calculate(missing).score();
            assertThat(score).isNotNaN().isBetween(0.0, 100.0);
        }
    }

    @Nested
    @DisplayName("Monotonicity")
    class Monotonicity {

        @Test
        @DisplayName("above the ideal band, hotter is never more comfortable")
        void hotterIsNeverBetterAboveIdeal() {
            double previous = Double.MAX_VALUE;
            for (double temp = 26; temp <= 55; temp += 1) {
                double score = calculator.calculate(withTemp(temp)).score();
                assertThat(score).as("temp=%s", temp).isLessThanOrEqualTo(previous);
                previous = score;
            }
        }

        @Test
        @DisplayName("below the ideal band, colder is never more comfortable")
        void colderIsNeverBetterBelowIdeal() {
            double previous = Double.MAX_VALUE;
            for (double temp = 20; temp >= -30; temp -= 1) {
                double score = calculator.calculate(withTemp(temp)).score();
                assertThat(score).as("temp=%s", temp).isLessThanOrEqualTo(previous);
                previous = score;
            }
        }

        @Test
        @DisplayName("above 60% RH, more humidity is never more comfortable")
        void moreHumidityIsNeverBetterAboveIdeal() {
            double previous = Double.MAX_VALUE;
            for (double humidity = 60; humidity <= 100; humidity += 2) {
                double score = calculator.calculate(withHumidity(humidity)).score();
                assertThat(score).as("humidity=%s", humidity).isLessThanOrEqualTo(previous);
                previous = score;
            }
        }

        @Test
        @DisplayName("above the light-breeze band, more wind is never more comfortable")
        void moreWindIsNeverBetterAboveIdeal() {
            double previous = Double.MAX_VALUE;
            for (double wind = 3.5; wind <= 25; wind += 0.5) {
                double score = calculator.calculate(withWind(wind)).score();
                assertThat(score).as("wind=%s", wind).isLessThanOrEqualTo(previous);
                previous = score;
            }
        }

        @Test
        @DisplayName("more rain is never more comfortable")
        void moreRainIsNeverBetter() {
            double previous = Double.MAX_VALUE;
            for (double rain = 0; rain <= 30; rain += 1) {
                ComfortInputs in = new ComfortInputs(23, 23, 50, 2.5, 25, rain, 0, 1013);
                double score = calculator.calculate(in).score();
                assertThat(score).as("rain=%s mm/h", rain).isLessThanOrEqualTo(previous);
                previous = score;
            }
        }
    }

    @Nested
    @DisplayName("Design intent")
    class DesignIntent {

        @Test
        @DisplayName("heat is punished harder than the same departure into cold")
        void heatIsPunishedHarderThanCold() {
            // 10 degrees above the plateau vs 10 degrees below it.
            double hot = calculator.calculate(withTemp(36)).score();
            double cold = calculator.calculate(withTemp(10)).score();
            assertThat(hot)
                    .as("the cold side is gentler because clothing mitigates cold, "
                            + "while above ~35C sweat evaporation is the only cooling channel left")
                    .isLessThan(cold);
        }

        @Test
        @DisplayName("dead calm scores below a light breeze - zero wind is not the ideal")
        void deadCalmIsWorseThanLightBreeze() {
            double calm = calculator.calculate(withWind(0)).score();
            double breeze = calculator.calculate(withWind(2.5)).score();
            assertThat(calm).isLessThan(breeze);
        }

        @Test
        @DisplayName("dead calm still beats a gale")
        void deadCalmStillBeatsAGale() {
            double calm = calculator.calculate(withWind(0)).score();
            double gale = calculator.calculate(withWind(20)).score();
            assertThat(calm).isGreaterThan(gale);
        }

        @Test
        @DisplayName("neither cloud extreme scores zero - overcast is drab, not unbearable")
        void cloudinessNeverScoresZero() {
            assertThat(calculator.calculate(withCloud(0)).subScores().get("cloudiness"))
                    .isGreaterThan(0.5);
            assertThat(calculator.calculate(withCloud(100)).subScores().get("cloudiness"))
                    .isGreaterThanOrEqualTo(0.35);
        }

        @Test
        @DisplayName("extreme heat cannot be compensated by three pleasant parameters")
        void extremeHeatIsNotCompensatedByOtherParameters() {
            // 44C with otherwise agreeable conditions. Under a pure weighted sum the three
            // healthy sub-scores would outvote the zeroed temperature term and produce
            // roughly 50. The multiplicative half is what prevents that.
            ComfortInputs blisteringButOtherwiseFine =
                    new ComfortInputs(44, 45, 50, 2.5, 25, 0, 0, 1010);

            ComfortResult result = calculator.calculate(blisteringButOtherwiseFine);

            assertThat(result.base())
                    .as("the additive half alone would rate this as merely average")
                    .isGreaterThan(0.60);
            assertThat(result.score())
                    .as("the multiplicative half drags it down to where it belongs")
                    .isLessThan(35.0);
        }

        @Test
        @DisplayName("the feels-like gap penalises a city whose conditions amplify the reading")
        void feelsLikeGapPenalisesAmplifiedConditions() {
            // Colombo's real reading: 30C that feels like 36.6C because of 76% humidity.
            ComfortInputs amplified = new ComfortInputs(30, 36.6, 76, 5.05, 87, 0, 0, 1008);
            ComfortInputs sameButUnamplified = new ComfortInputs(30, 30, 76, 5.05, 87, 0, 0, 1008);

            assertThat(calculator.calculate(amplified).score())
                    .isLessThan(calculator.calculate(sameButUnamplified).score());
        }

        @Test
        @DisplayName("rain lowers the score but never annihilates it")
        void rainIsPenalisedButFloored() {
            ComfortInputs torrential = new ComfortInputs(23, 23, 50, 2.5, 25, 100, 0, 1013);
            ComfortResult result = calculator.calculate(torrential);

            assertThat(result.multipliers().get("rain")).isEqualTo(0.45);
            assertThat(result.score()).isGreaterThan(0.0);
        }
    }

    @Nested
    @DisplayName("Band scoring")
    class Bands {

        @ParameterizedTest(name = "{0}C scores {1}")
        @CsvSource({
                "20, 1.0", "23, 1.0", "26, 1.0",
                "33, 0.5",      // halfway from the 26C plateau edge to the 40C zero point
                "7.5, 0.5",     // halfway from the 20C plateau edge to the -5C zero point
                "40, 0.0", "-5, 0.0",
                "80, 0.0", "-80, 0.0"   // clamped, never negative
        })
        void temperatureBand(double temp, double expected) {
            assertThat(ComfortIndexCalculator.bandScore(properties.getTemperature(), temp))
                    .isCloseTo(expected, within(1e-9));
        }

        @ParameterizedTest(name = "{0}% RH scores {1}")
        @CsvSource({
                "40, 1.0", "50, 1.0", "60, 1.0",
                "80, 0.5",              // the humid side spans only 40 points
                "10, 0.5",              // the dry side spans 60 - deliberately gentler
                "100, 0.0",
                "0, 0.3333333333"
        })
        void humidityBand(double humidity, double expected) {
            assertThat(ComfortIndexCalculator.bandScore(properties.getHumidity(), humidity))
                    .isCloseTo(expected, within(1e-9));
        }

        @ParameterizedTest(name = "{0} m/s scores {1}")
        @CsvSource({
                "1.5, 1.0", "2.5, 1.0", "3.5, 1.0",
                "0, 0.8",               // still air is unpleasant, not catastrophic
                "0.75, 0.9",
                "9.25, 0.5",
                "15, 0.0", "30, 0.0"
        })
        void windBand(double wind, double expected) {
            assertThat(ComfortIndexCalculator.bandScore(properties.getWind(), wind))
                    .isCloseTo(expected, within(1e-9));
        }

        @ParameterizedTest(name = "{0}% cloud scores {1}")
        @CsvSource({
                "10, 1.0", "25, 1.0", "40, 1.0",
                "0, 0.85",
                "70, 0.675",
                "100, 0.35"
        })
        void cloudinessBand(double cloud, double expected) {
            assertThat(ComfortIndexCalculator.bandScore(properties.getCloudiness(), cloud))
                    .isCloseTo(expected, within(1e-9));
        }

        @Test
        @DisplayName("a band with a zero-width side falls straight to its floor")
        void degenerateBandFallsToFloor() {
            ComfortIndexProperties.Band degenerate =
                    new ComfortIndexProperties.Band(10, 20, 10, 20, 0.2, 0.3);
            assertThat(ComfortIndexCalculator.bandScore(degenerate, 5)).isEqualTo(0.2);
            assertThat(ComfortIndexCalculator.bandScore(degenerate, 25)).isEqualTo(0.3);
        }
    }

    @Nested
    @DisplayName("Penalties")
    class Penalties {

        @ParameterizedTest(name = "{0}C gives a heat multiplier of {1}")
        @CsvSource({
                "20, 1.0", "35, 1.0",   // inactive at or below the threshold
                "40, 0.65",
                "44, 0.37",
                "60, 0.30"              // floored
        })
        void heatPenalty(double temp, double expected) {
            assertThat(ComfortIndexCalculator.penaltyAbove(properties.getHeat(), temp))
                    .isCloseTo(expected, within(1e-9));
        }

        @ParameterizedTest(name = "{0}C gives a cold multiplier of {1}")
        @CsvSource({
                "5, 1.0", "0, 1.0",
                "-10, 0.6",
                "-40, 0.40"             // floored
        })
        void coldPenalty(double temp, double expected) {
            assertThat(ComfortIndexCalculator.penaltyBelow(properties.getCold(), temp))
                    .isCloseTo(expected, within(1e-9));
        }

        @Test
        @DisplayName("penalties are inactive by default, so a mild city keeps a modifier of 1.0")
        void mildConditionsHaveNoPenalties() {
            ComfortResult result = calculator.calculate(ideal());
            assertThat(result.modifier()).isEqualTo(1.0);
            assertThat(result.multipliers()).allSatisfy((name, value) ->
                    assertThat(value).as(name).isEqualTo(1.0));
        }

        @ParameterizedTest
        @ValueSource(doubles = {0.0, 5.0, 12.0})
        @DisplayName("wind at or below the gale threshold triggers no gale penalty")
        void galeInactiveBelowThreshold(double wind) {
            assertThat(ComfortIndexCalculator.penaltyAbove(properties.getGale(), wind))
                    .isEqualTo(1.0);
        }
    }

    @Nested
    @DisplayName("Configuration")
    class Configuration {

        @Test
        @DisplayName("weights are normalised, so they need not sum to 1")
        void weightsAreNormalised() {
            // Same relative importance as the defaults, expressed on a 0-100 scale.
            Map<String, Double> unnormalised = new LinkedHashMap<>();
            unnormalised.put("temperature", 35.0);
            unnormalised.put("humidity", 25.0);
            unnormalised.put("wind", 20.0);
            unnormalised.put("cloudiness", 20.0);

            ComfortIndexProperties scaled = new ComfortIndexProperties();
            scaled.setWeights(unnormalised);

            ComfortInputs mixed = new ComfortInputs(31, 33, 85, 6, 95, 0, 0, 1005);

            assertThat(new ComfortIndexCalculator(scaled).calculate(mixed).score())
                    .as("normalising is what lets a new parameter be added without "
                            + "rebalancing every existing weight")
                    .isEqualTo(calculator.calculate(mixed).score());
        }

        @Test
        @DisplayName("re-weighting changes the score, proving nothing is hard-coded")
        void reweightingChangesTheScore() {
            ComfortIndexProperties humidityOnly = new ComfortIndexProperties();
            humidityOnly.setWeights(new LinkedHashMap<>(Map.of("humidity", 1.0)));

            ComfortInputs humidButOtherwisePleasant =
                    new ComfortInputs(23, 24, 95, 2.5, 25, 0, 0, 1013);

            double defaultScore = calculator.calculate(humidButOtherwisePleasant).score();
            double humidityWeighted =
                    new ComfortIndexCalculator(humidityOnly).calculate(humidButOtherwisePleasant).score();

            assertThat(humidityWeighted).isLessThan(defaultScore);
        }

        @Test
        @DisplayName("all weights zero yields a zero base rather than a divide-by-zero")
        void zeroWeightsDoNotDivideByZero() {
            ComfortIndexProperties noWeights = new ComfortIndexProperties();
            noWeights.setWeights(new LinkedHashMap<>());

            ComfortResult result = new ComfortIndexCalculator(noWeights).calculate(ideal());

            assertThat(result.base()).isEqualTo(0.0);
            assertThat(result.score()).isEqualTo(0.0).isNotNaN();
        }

        @Test
        @DisplayName("the result exposes its full working for the UI and the README")
        void resultExposesBreakdown() {
            ComfortResult result = calculator.calculate(ideal());

            assertThat(result.subScores())
                    .containsOnlyKeys("temperature", "humidity", "wind", "cloudiness");
            assertThat(result.multipliers())
                    .containsOnlyKeys("rain", "snow", "feelsGap", "heat", "cold", "gale");
        }

        @Test
        @DisplayName("the breakdown is immutable - callers cannot rewrite a score's working")
        void breakdownIsImmutable() {
            ComfortResult result = calculator.calculate(ideal());
            assertThat(result.subScores()).isUnmodifiable();
            assertThat(result.multipliers()).isUnmodifiable();
        }
    }

    @Nested
    @DisplayName("Real observations")
    class RealObservations {

        /**
         * Face validity: with no ground truth available, the strongest available check is
         * whether the ranking agrees with strong human priors. These are genuine readings
         * captured from OpenWeatherMap while designing the index.
         */
        @Test
        @DisplayName("real cities rank in an order a person would recognise")
        void realCitiesRankPlausibly() {
            double oslo = calculator.calculate(
                    new ComfortInputs(14.2, 13.74, 79, 1.11, 98, 0, 0, 998)).score();
            double boston = calculator.calculate(
                    new ComfortInputs(19.07, 19.46, 93, 1.54, 36, 0, 0, 1010)).score();
            double colombo = calculator.calculate(
                    new ComfortInputs(30.02, 36.62, 76, 5.05, 87, 0, 0, 1008)).score();
            double dubai = calculator.calculate(
                    new ComfortInputs(36.96, 41.0, 55, 4.0, 0, 0, 0, 1004)).score();
            double tokyoInRain = calculator.calculate(
                    new ComfortInputs(22.81, 23.44, 88, 4.63, 100, 5.62, 0, 1013)).score();

            assertThat(boston).as("mild and partly cloudy should lead").isGreaterThan(oslo);
            assertThat(oslo).as("cool and overcast beats hot and humid").isGreaterThan(colombo);
            assertThat(colombo).as("humid heat beats desert heat").isGreaterThan(dubai);
            assertThat(tokyoInRain).as("moderate rain is a serious penalty").isLessThan(boston);
        }
    }
}
