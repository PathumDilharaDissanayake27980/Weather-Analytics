package com.fidenz.weather.api;

import com.fidenz.weather.api.dto.CityComfortDto;
import com.fidenz.weather.api.dto.WeatherDashboardDto;
import com.fidenz.weather.weather.WeatherProviderException;
import com.fidenz.weather.weather.WeatherService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-layer tests for the dashboard endpoint.
 *
 * <p>Security is permissive here (see {@code src/test/resources/application.yml}) so these
 * tests can focus on the payload contract. Authentication has its own test class - mixing the
 * two would mean every payload assertion needed a token, obscuring what is being checked.
 */
@SpringBootTest
@AutoConfigureMockMvc
class WeatherControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WeatherService weatherService;

    private static WeatherDashboardDto dashboard(String cacheStatus) {
        return new WeatherDashboardDto(
                Instant.parse("2026-09-04T12:00:00Z"),
                cacheStatus,
                2,
                List.of(
                        new CityComfortDto(1, "4930956", "Boston", "US", "scattered clouds", "03d",
                                19.07, 19.46, 93, 1.54, 36, 1010, 0, 71.4, "Comfortable",
                                Map.of("temperature", 0.94, "humidity", 0.18),
                                Map.of("heat", 1.0),
                                Instant.parse("2026-09-04T11:54:56Z")),
                        new CityComfortDto(2, "1248991", "Colombo", "LK", "overcast clouds", "04d",
                                30.02, 36.62, 76, 5.05, 87, 1008, 0, 43.9, "Uncomfortable",
                                Map.of("temperature", 0.71, "humidity", 0.60),
                                Map.of("feelsGap", 0.80),
                                Instant.parse("2026-09-04T11:10:15Z"))),
                List.of());
    }

    @Test
    @DisplayName("returns the ranked cities as JSON")
    void returnsRankedCities() throws Exception {
        when(weatherService.getDashboard()).thenReturn(dashboard("MISS"));

        mockMvc.perform(get("/api/weather"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.cityCount").value(2))
                .andExpect(jsonPath("$.cities", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$.cities[0].cityName").value("Boston"))
                .andExpect(jsonPath("$.cities[0].rank").value(1))
                .andExpect(jsonPath("$.cities[1].rank").value(2));
    }

    @Test
    @DisplayName("each city carries the five fields the assignment requires")
    void carriesRequiredDisplayFields() throws Exception {
        when(weatherService.getDashboard()).thenReturn(dashboard("MISS"));

        mockMvc.perform(get("/api/weather"))
                .andExpect(jsonPath("$.cities[0].cityName").exists())
                .andExpect(jsonPath("$.cities[0].description").value("scattered clouds"))
                .andExpect(jsonPath("$.cities[0].temperatureC").value(19.07))
                .andExpect(jsonPath("$.cities[0].comfortScore").value(71.4))
                .andExpect(jsonPath("$.cities[0].rank").value(1));
    }

    @Test
    @DisplayName("exposes the score breakdown for the UI")
    void exposesScoreBreakdown() throws Exception {
        when(weatherService.getDashboard()).thenReturn(dashboard("MISS"));

        mockMvc.perform(get("/api/weather"))
                .andExpect(jsonPath("$.cities[0].subScores.temperature").value(0.94))
                .andExpect(jsonPath("$.cities[1].multipliers.feelsGap").value(0.80));
    }

    @Test
    @DisplayName("reports a cache MISS in both the body and the X-Cache header")
    void reportsCacheMiss() throws Exception {
        when(weatherService.getDashboard()).thenReturn(dashboard("MISS"));

        mockMvc.perform(get("/api/weather"))
                .andExpect(header().string("X-Cache", "MISS"))
                .andExpect(jsonPath("$.cacheStatus").value("MISS"));
    }

    @Test
    @DisplayName("reports a cache HIT in both the body and the X-Cache header")
    void reportsCacheHit() throws Exception {
        when(weatherService.getDashboard()).thenReturn(dashboard("HIT"));

        mockMvc.perform(get("/api/weather"))
                .andExpect(header().string("X-Cache", "HIT"))
                .andExpect(jsonPath("$.cacheStatus").value("HIT"));
    }

    @Test
    @DisplayName("a provider outage is reported as 502, not 500")
    void providerFailureIsBadGateway() throws Exception {
        when(weatherService.getDashboard())
                .thenThrow(new WeatherProviderException("OpenWeatherMap is unreachable"));

        mockMvc.perform(get("/api/weather"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.title").value("Weather provider unavailable"));
    }

    @Test
    @DisplayName("an unexpected failure returns 500 without leaking internals")
    void unexpectedFailureIsGeneric() throws Exception {
        when(weatherService.getDashboard())
                .thenThrow(new IllegalStateException("connection string user=admin password=hunter2"));

        mockMvc.perform(get("/api/weather"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred."))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("hunter2"))));
    }

    @Test
    @DisplayName("null fields are omitted from the payload")
    void omitsNullFields() throws Exception {
        when(weatherService.getDashboard()).thenReturn(dashboard("MISS"));

        mockMvc.perform(get("/api/weather"))
                .andExpect(jsonPath("$.cities[0].icon").exists());
    }
}
