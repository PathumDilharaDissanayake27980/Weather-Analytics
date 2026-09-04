package com.fidenz.weather.comfort;

import java.util.Map;

/**
 * A Comfort Index score plus the full working, so the UI and the README can show
 * <em>why</em> a city scored what it did rather than just the number.
 *
 * @param score       final 0-100 value
 * @param base        weighted sum of sub-scores, 0-1
 * @param modifier    product of all multipliers, 0-1
 * @param subScores   per-parameter sub-score, 0-1 (insertion-ordered)
 * @param multipliers per-penalty multiplier, 0-1 (1.0 means "did not apply")
 */
public record ComfortResult(
        double score,
        double base,
        double modifier,
        Map<String, Double> subScores,
        Map<String, Double> multipliers
) {}
