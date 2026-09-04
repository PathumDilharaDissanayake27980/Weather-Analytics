package com.fidenz.weather;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Weather Analytics API.
 *
 * <p>{@code @ConfigurationPropertiesScan} registers every {@code @ConfigurationProperties}
 * class in this package tree, so each one does not need listing individually.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class WeatherAnalyticsApplication {

    public static void main(String[] args) {
        SpringApplication.run(WeatherAnalyticsApplication.class, args);
    }
}
