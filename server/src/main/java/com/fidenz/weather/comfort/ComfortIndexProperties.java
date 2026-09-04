package com.fidenz.weather.comfort;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Every tunable number in the Comfort Index, bound from {@code application.yml} under the
 * {@code comfort.*} prefix. Nothing about the formula is hard-coded in the calculator.
 *
 * <p>This exists so the index can be retuned - or extended with a new parameter - without
 * touching logic, and so tests can inject their own configuration instead of asserting
 * against magic numbers baked into a class.
 */
@ConfigurationProperties(prefix = "comfort")
public class ComfortIndexProperties {

    /**
     * A comfort curve: full marks inside {@code [idealMin, idealMax]}, falling linearly away
     * from that plateau towards a floor on each side.
     *
     * <p>The two sides are configured independently, which is the point - comfort is rarely
     * symmetric. Heat is punished faster than cold because clothing mitigates cold almost
     * indefinitely, whereas above roughly 35C the body's only remaining cooling channel is
     * sweat evaporation.
     *
     * <p>A plateau rather than a single ideal point also means small measurement wobble does
     * not reshuffle the rankings.
     */
    public static class Band {
        /** Lower edge of the full-score plateau. */
        private double idealMin;
        /** Upper edge of the full-score plateau. */
        private double idealMax;
        /** Value at which the low side bottoms out at {@link #lowerFloor}. */
        private double lowerZero;
        /** Value at which the high side bottoms out at {@link #upperFloor}. */
        private double upperZero;
        /** Worst achievable sub-score on the low side. */
        private double lowerFloor = 0.0;
        /** Worst achievable sub-score on the high side. */
        private double upperFloor = 0.0;

        public Band() {
        }

        public Band(double idealMin, double idealMax, double lowerZero, double upperZero,
                    double lowerFloor, double upperFloor) {
            this.idealMin = idealMin;
            this.idealMax = idealMax;
            this.lowerZero = lowerZero;
            this.upperZero = upperZero;
            this.lowerFloor = lowerFloor;
            this.upperFloor = upperFloor;
        }

        public double getIdealMin() {
            return idealMin;
        }

        public void setIdealMin(double idealMin) {
            this.idealMin = idealMin;
        }

        public double getIdealMax() {
            return idealMax;
        }

        public void setIdealMax(double idealMax) {
            this.idealMax = idealMax;
        }

        public double getLowerZero() {
            return lowerZero;
        }

        public void setLowerZero(double lowerZero) {
            this.lowerZero = lowerZero;
        }

        public double getUpperZero() {
            return upperZero;
        }

        public void setUpperZero(double upperZero) {
            this.upperZero = upperZero;
        }

        public double getLowerFloor() {
            return lowerFloor;
        }

        public void setLowerFloor(double lowerFloor) {
            this.lowerFloor = lowerFloor;
        }

        public double getUpperFloor() {
            return upperFloor;
        }

        public void setUpperFloor(double upperFloor) {
            this.upperFloor = upperFloor;
        }
    }

    /**
     * A one-sided penalty applied as a multiplier once a threshold is crossed.
     *
     * <p>Penalties are multiplicative rather than weighted terms because they are
     * <em>non-compensatory</em>: 44C is uncomfortable no matter how pleasant the cloud cover
     * is. A pure weighted sum would let three good parameters outvote one catastrophic one.
     */
    public static class Penalty {
        /** Value beyond which the penalty starts to apply. */
        private double threshold;
        /** Reduction in the multiplier per unit past the threshold. */
        private double slope;
        /** Lowest the multiplier may fall to. */
        private double floor;

        public Penalty() {
        }

        public Penalty(double threshold, double slope, double floor) {
            this.threshold = threshold;
            this.slope = slope;
            this.floor = floor;
        }

        public double getThreshold() {
            return threshold;
        }

        public void setThreshold(double threshold) {
            this.threshold = threshold;
        }

        public double getSlope() {
            return slope;
        }

        public void setSlope(double slope) {
            this.slope = slope;
        }

        public double getFloor() {
            return floor;
        }

        public void setFloor(double floor) {
            this.floor = floor;
        }
    }

    /**
     * Relative importance of each weighted parameter. Keys must match the band names.
     *
     * <p>Values need not sum to 1 - the calculator normalises them - so a new parameter can be
     * added without rebalancing every existing weight by hand.
     */
    private Map<String, Double> weights = new LinkedHashMap<>();

    /** ASHRAE Standard 55 puts the comfort zone at roughly 20-26C. */
    private Band temperature = new Band(20, 26, -5, 40, 0.0, 0.0);

    /** ASHRAE / EPA guidance puts ideal relative humidity at 40-60%. */
    private Band humidity = new Band(40, 60, -20, 100, 0.0, 0.0);

    /** Beaufort 2, "light breeze", is 1.6-3.3 m/s. Note that dead calm is NOT the ideal. */
    private Band wind = new Band(1.5, 3.5, 0, 15, 0.80, 0.0);

    /** Scattered cloud beats both glare and gloom, and neither extreme scores zero. */
    private Band cloudiness = new Band(10, 40, 0, 100, 0.85, 0.35);

    private Penalty rain = new Penalty(0.0, 0.06, 0.45);
    private Penalty snow = new Penalty(0.0, 0.10, 0.40);
    /** Applied to |feels_like - temp|: how much conditions amplify the raw reading. */
    private Penalty feelsGap = new Penalty(0.0, 0.03, 0.80);
    private Penalty heat = new Penalty(35.0, 0.07, 0.30);
    /** Measured downward from the threshold. */
    private Penalty cold = new Penalty(0.0, 0.04, 0.40);
    private Penalty gale = new Penalty(12.0, 0.08, 0.50);

    public ComfortIndexProperties() {
        weights.put("temperature", 0.35);
        weights.put("humidity", 0.25);
        weights.put("wind", 0.20);
        weights.put("cloudiness", 0.20);
    }

    public Map<String, Double> getWeights() {
        return weights;
    }

    public void setWeights(Map<String, Double> weights) {
        this.weights = weights;
    }

    public Band getTemperature() {
        return temperature;
    }

    public void setTemperature(Band temperature) {
        this.temperature = temperature;
    }

    public Band getHumidity() {
        return humidity;
    }

    public void setHumidity(Band humidity) {
        this.humidity = humidity;
    }

    public Band getWind() {
        return wind;
    }

    public void setWind(Band wind) {
        this.wind = wind;
    }

    public Band getCloudiness() {
        return cloudiness;
    }

    public void setCloudiness(Band cloudiness) {
        this.cloudiness = cloudiness;
    }

    public Penalty getRain() {
        return rain;
    }

    public void setRain(Penalty rain) {
        this.rain = rain;
    }

    public Penalty getSnow() {
        return snow;
    }

    public void setSnow(Penalty snow) {
        this.snow = snow;
    }

    public Penalty getFeelsGap() {
        return feelsGap;
    }

    public void setFeelsGap(Penalty feelsGap) {
        this.feelsGap = feelsGap;
    }

    public Penalty getHeat() {
        return heat;
    }

    public void setHeat(Penalty heat) {
        this.heat = heat;
    }

    public Penalty getCold() {
        return cold;
    }

    public void setCold(Penalty cold) {
        this.cold = cold;
    }

    public Penalty getGale() {
        return gale;
    }

    public void setGale(Penalty gale) {
        this.gale = gale;
    }
}
