package com.fidenz.weather.api;

import com.fidenz.weather.api.dto.WeatherDashboardDto;
import com.fidenz.weather.weather.WeatherService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The dashboard endpoint. Requires a valid Auth0 access token (see
 * {@link com.fidenz.weather.config.SecurityConfig}).
 *
 * <p>Thin by design: it delegates to {@link WeatherService} and adds nothing but HTTP
 * concerns. Business logic in a controller is logic that can only be tested through HTTP.
 */
@RestController
@RequestMapping("/api/weather")
public class WeatherController {

    private final WeatherService weatherService;

    public WeatherController(WeatherService weatherService) {
        this.weatherService = weatherService;
    }

    /**
     * Every configured city, scored and ranked most to least comfortable.
     *
     * <p>The {@code X-Cache} header mirrors the {@code cacheStatus} field so cache behaviour
     * is visible from {@code curl} without parsing the body.
     */
    @GetMapping
    public ResponseEntity<WeatherDashboardDto> getDashboard() {
        WeatherDashboardDto dashboard = weatherService.getDashboard();
        return ResponseEntity.ok()
                .header("X-Cache", dashboard.cacheStatus())
                .body(dashboard);
    }
}
