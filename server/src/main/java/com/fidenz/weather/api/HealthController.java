package com.fidenz.weather.api;

import com.fidenz.weather.city.CityRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;

/**
 * Unauthenticated liveness check.
 *
 * <p>Public on purpose: a health probe that needs a token cannot be used by a load balancer
 * or an uptime monitor. It reveals only that the service is up and how many cities are
 * configured.
 */
@RestController
public class HealthController {

    public record HealthResponse(String status, int cityCount, Instant serverTime) {}

    private final CityRepository cityRepository;
    private final Clock clock;

    public HealthController(CityRepository cityRepository, Clock clock) {
        this.cityRepository = cityRepository;
        this.clock = clock;
    }

    @GetMapping("/api/health")
    public HealthResponse health() {
        return new HealthResponse("UP", cityRepository.count(), Instant.now(clock));
    }
}
