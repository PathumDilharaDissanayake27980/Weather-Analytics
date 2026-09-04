package com.fidenz.weather.weather;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * OpenWeatherMap connection settings, bound from {@code openweather.*}.
 *
 * <p>The API key is supplied by environment variable and never committed. It also never
 * leaves the server - the browser talks only to this API, which is the entire reason a
 * backend exists here rather than the frontend calling OpenWeatherMap directly.
 */
@ConfigurationProperties(prefix = "openweather")
public class OpenWeatherMapProperties {

    private String apiKey = "";
    private String baseUrl = "https://api.openweathermap.org/data/2.5";
    /** OWM accepts standard | metric | imperial. Metric asks for Celsius at source rather
     *  than converting Kelvin in code - one fewer place to get it wrong. */
    private String units = "metric";
    private Duration connectTimeout = Duration.ofSeconds(3);
    private Duration readTimeout = Duration.ofSeconds(5);

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getUnits() {
        return units;
    }

    public void setUnits(String units) {
        this.units = units;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }
}
