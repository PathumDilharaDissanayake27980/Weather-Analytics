package com.fidenz.weather.comfort;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Computes the Comfort Index: a 0-100 score describing how pleasant a city's current
 * weather is for a healthy adult, lightly clothed, at rest or walking, outdoors, now.
 *
 * <p><b>Naming the subject matters.</b> "Comfort" is undefined until you say comfortable
 * <em>for whom, doing what</em>. A hiker, an office worker and someone with asthma want
 * different weather. Every weight below is justified against that one sentence.
 *
 * <h2>Shape of the formula</h2>
 * <pre>
 *   base     = weighted mean of per-parameter sub-scores      (each 0-1)
 *   modifier = product of penalty multipliers                 (each 0-1, usually 1.0)
 *   score    = clamp(100 * base * modifier, 0, 100)
 * </pre>
 *
 * <p><b>Why hybrid rather than a plain weighted sum?</b> A weighted sum is
 * <em>compensatory</em>: every parameter can be offset by another. A city at 44C with
 * perfect humidity, a gentle breeze and pleasant cloud would still score respectably,
 * because three good terms outvote one catastrophic one. That is plainly wrong - 44C is
 * uncomfortable regardless of anything else. The multiplicative half fixes exactly that,
 * while the additive half keeps normal-range behaviour smooth and explainable.
 *
 * <p><b>Why not purely multiplicative?</b> It is unforgivingly harsh - a single mediocre
 * parameter drags everything down, scores bunch up near zero, and "weights" become
 * exponents, which are far harder to justify in a README.
 *
 * <p>This class is deliberately free of Spring, HTTP and OpenWeatherMap concepts. It takes
 * numbers and returns a number, which is what makes it trivially unit-testable and what
 * makes adding a parameter a small, safe change.
 */
@Component
public class ComfortIndexCalculator {

    private final ComfortIndexProperties properties;

    public ComfortIndexCalculator(ComfortIndexProperties properties) {
        this.properties = properties;
    }

    public ComfortResult calculate(ComfortInputs in) {
        Map<String, Double> subScores = new LinkedHashMap<>();
        subScores.put("temperature", bandScore(properties.getTemperature(), in.tempC()));
        subScores.put("humidity", bandScore(properties.getHumidity(), in.humidityPct()));
        subScores.put("wind", bandScore(properties.getWind(), in.windSpeedMs()));
        subScores.put("cloudiness", bandScore(properties.getCloudiness(), in.cloudinessPct()));

        double base = weightedMean(subScores);

        Map<String, Double> multipliers = new LinkedHashMap<>();
        multipliers.put("rain", penaltyAbove(properties.getRain(), in.rainMmPerHour()));
        multipliers.put("snow", penaltyAbove(properties.getSnow(), in.snowMmPerHour()));
        multipliers.put("feelsGap",
                penaltyAbove(properties.getFeelsGap(), Math.abs(in.feelsLikeC() - in.tempC())));
        multipliers.put("heat", penaltyAbove(properties.getHeat(), in.tempC()));
        multipliers.put("cold", penaltyBelow(properties.getCold(), in.tempC()));
        multipliers.put("gale", penaltyAbove(properties.getGale(), in.windSpeedMs()));

        double modifier = 1.0;
        for (double m : multipliers.values()) {
            modifier *= m;
        }

        double score = round1(clamp(100.0 * base * modifier, 0.0, 100.0));

        return new ComfortResult(
                score,
                round4(base),
                round4(modifier),
                Collections.unmodifiableMap(round4(subScores)),
                Collections.unmodifiableMap(round4(multipliers))
        );
    }

    /**
     * Weighted mean of the sub-scores, normalised by the weights actually used.
     *
     * <p>Normalising means the configured weights do not have to sum to 1. That is what lets
     * a new parameter be added by appending one weight, without rebalancing the others - and
     * it guarantees the result stays within 0-1 whatever the configuration says.
     */
    private double weightedMean(Map<String, Double> subScores) {
        double weighted = 0.0;
        double totalWeight = 0.0;
        for (Map.Entry<String, Double> e : subScores.entrySet()) {
            double w = properties.getWeights().getOrDefault(e.getKey(), 0.0);
            weighted += w * e.getValue();
            totalWeight += w;
        }
        return totalWeight == 0.0 ? 0.0 : weighted / totalWeight;
    }

    /**
     * Scores one measurement against its comfort band: 1.0 inside the plateau, falling
     * linearly to a floor on each side. Values beyond the zero points are clamped, never
     * negative, so the sub-score is always within 0-1.
     */
    static double bandScore(ComfortIndexProperties.Band band, double value) {
        if (Double.isNaN(value)) {
            return 0.0;
        }
        if (value >= band.getIdealMin() && value <= band.getIdealMax()) {
            return 1.0;
        }
        if (value < band.getIdealMin()) {
            double span = band.getIdealMin() - band.getLowerZero();
            if (span <= 0) {
                return band.getLowerFloor();
            }
            double t = clamp((value - band.getLowerZero()) / span, 0.0, 1.0);
            return band.getLowerFloor() + (1.0 - band.getLowerFloor()) * t;
        }
        double span = band.getUpperZero() - band.getIdealMax();
        if (span <= 0) {
            return band.getUpperFloor();
        }
        double t = clamp((band.getUpperZero() - value) / span, 0.0, 1.0);
        return band.getUpperFloor() + (1.0 - band.getUpperFloor()) * t;
    }

    /** Penalty that engages once {@code value} rises above the threshold. */
    static double penaltyAbove(ComfortIndexProperties.Penalty penalty, double value) {
        if (Double.isNaN(value) || value <= penalty.getThreshold()) {
            return 1.0;
        }
        double excess = value - penalty.getThreshold();
        return clamp(1.0 - penalty.getSlope() * excess, penalty.getFloor(), 1.0);
    }

    /** Penalty that engages once {@code value} falls below the threshold. */
    static double penaltyBelow(ComfortIndexProperties.Penalty penalty, double value) {
        if (Double.isNaN(value) || value >= penalty.getThreshold()) {
            return 1.0;
        }
        double deficit = penalty.getThreshold() - value;
        return clamp(1.0 - penalty.getSlope() * deficit, penalty.getFloor(), 1.0);
    }

    static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static double round4(double v) {
        return Math.round(v * 10_000.0) / 10_000.0;
    }

    private static Map<String, Double> round4(Map<String, Double> values) {
        Map<String, Double> rounded = new LinkedHashMap<>();
        values.forEach((k, v) -> rounded.put(k, round4(v)));
        return rounded;
    }
}
