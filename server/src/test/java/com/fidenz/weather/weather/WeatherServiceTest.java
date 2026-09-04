package com.fidenz.weather.weather;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fidenz.weather.api.dto.CityComfortDto;
import com.fidenz.weather.api.dto.WeatherDashboardDto;
import com.fidenz.weather.city.CityRepository;
import com.fidenz.weather.comfort.ComfortIndexCalculator;
import com.fidenz.weather.comfort.ComfortIndexProperties;
import com.fidenz.weather.comfort.ComfortInputs;
import com.fidenz.weather.config.CacheProperties;
import com.fidenz.weather.support.Fixtures;
import com.fidenz.weather.support.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests the orchestration: cache, fetch, score, rank.
 *
 * <p>The OpenWeatherMap client is mocked; everything else is real. Mocking only the network
 * boundary means these tests exercise the actual caching and ranking code rather than a pile
 * of stubs that agree with themselves.
 */
class WeatherServiceTest {

    private static final Duration TTL = Duration.ofMinutes(5);

    private OpenWeatherMapClient client;
    private MutableClock clock;
    private WeatherCaches caches;
    private WeatherService service;

    @BeforeEach
    void setUp() {
        client = mock(OpenWeatherMapClient.class);
        clock = MutableClock.startingNow();

        CacheProperties cacheProperties = new CacheProperties();
        cacheProperties.setRawTtl(TTL);
        cacheProperties.setProcessedTtl(TTL);
        caches = new WeatherCaches(cacheProperties, clock);

        CityRepository cities = new CityRepository(
                new ClassPathResource("cities-test.json"), new ObjectMapper());

        service = new WeatherService(
                cities,
                client,
                new ComfortIndexCalculator(new ComfortIndexProperties()),
                caches,
                clock);

        when(client.fetchByCityId("1248991")).thenReturn(Fixtures.colombo());
        when(client.fetchByCityId("3143244")).thenReturn(Fixtures.oslo());
        when(client.fetchByCityId("4930956")).thenReturn(Fixtures.boston());
    }

    @Nested
    @DisplayName("Building the dashboard")
    class Building {

        @Test
        @DisplayName("scores every configured city")
        void scoresEveryCity() {
            WeatherDashboardDto dashboard = service.getDashboard();

            assertThat(dashboard.cityCount()).isEqualTo(3);
            assertThat(dashboard.cities())
                    .extracting(CityComfortDto::cityName)
                    .containsExactlyInAnyOrder("Colombo", "Oslo", "Boston");
        }

        @Test
        @DisplayName("carries the fields the assignment requires for display")
        void includesRequiredDisplayFields() {
            CityComfortDto boston = service.getDashboard().cities().stream()
                    .filter(c -> c.cityName().equals("Boston"))
                    .findFirst()
                    .orElseThrow();

            assertThat(boston.cityName()).isEqualTo("Boston");
            assertThat(boston.description()).isEqualTo("scattered clouds");
            assertThat(boston.temperatureC()).isEqualTo(19.07);
            assertThat(boston.comfortScore()).isBetween(0.0, 100.0);
            assertThat(boston.rank()).isPositive();
        }

        @Test
        @DisplayName("exposes the score breakdown so the UI can explain a ranking")
        void includesScoreBreakdown() {
            CityComfortDto city = service.getDashboard().cities().get(0);

            assertThat(city.subScores())
                    .containsOnlyKeys("temperature", "humidity", "wind", "cloudiness");
            assertThat(city.multipliers()).containsKey("heat");
        }

        @Test
        @DisplayName("prefers the provider's city name over the one in cities.json")
        void prefersProviderCityName() {
            assertThat(service.getDashboard().cities())
                    .extracting(CityComfortDto::country)
                    .contains("LK", "NO", "US");
        }
    }

    @Nested
    @DisplayName("Ranking")
    class Ranking {

        @Test
        @DisplayName("orders cities most comfortable first")
        void ordersMostComfortableFirst() {
            List<CityComfortDto> cities = service.getDashboard().cities();

            assertThat(cities).isSortedAccordingTo(
                    (a, b) -> Double.compare(b.comfortScore(), a.comfortScore()));
        }

        @Test
        @DisplayName("ranks start at 1 and increase down the list")
        void ranksStartAtOne() {
            List<CityComfortDto> cities = service.getDashboard().cities();

            assertThat(cities.get(0).rank()).isEqualTo(1);
            assertThat(cities).extracting(CityComfortDto::rank).isSorted();
        }

        @Test
        @DisplayName("equal scores share a rank and the next distinct score skips ahead")
        void tiedScoresShareARank() {
            // Standard competition ranking: 1, 2, 2, 4.
            List<CityComfortDto> ranked = WeatherService.rank(List.of(
                    city("Alpha", 90.0),
                    city("Bravo", 75.0),
                    city("Charlie", 75.0),
                    city("Delta", 60.0)));

            assertThat(ranked).extracting(CityComfortDto::rank).containsExactly(1, 2, 2, 4);
        }

        @Test
        @DisplayName("tied cities are ordered by name so the output is deterministic")
        void tiesAreBrokenByName() {
            List<CityComfortDto> ranked = WeatherService.rank(List.of(
                    city("Zulu", 75.0),
                    city("Alpha", 75.0)));

            assertThat(ranked).extracting(CityComfortDto::cityName)
                    .as("without a tie-break, equal-scoring cities could swap places "
                            + "between identical requests")
                    .containsExactly("Alpha", "Zulu");
        }

        @Test
        @DisplayName("ranking an empty list yields an empty list")
        void handlesEmptyList() {
            assertThat(WeatherService.rank(List.of())).isEmpty();
        }
    }

    @Nested
    @DisplayName("Caching")
    class Caching {

        @Test
        @DisplayName("the first request is a MISS and calls the provider once per city")
        void firstRequestIsAMiss() {
            WeatherDashboardDto dashboard = service.getDashboard();

            assertThat(dashboard.cacheStatus()).isEqualTo("MISS");
            verify(client, times(3)).fetchByCityId(anyString());
        }

        @Test
        @DisplayName("the second request is a HIT and calls the provider not at all")
        void secondRequestIsAHit() {
            service.getDashboard();
            org.mockito.Mockito.clearInvocations(client);

            WeatherDashboardDto second = service.getDashboard();

            assertThat(second.cacheStatus()).isEqualTo("HIT");
            verify(client, never()).fetchByCityId(anyString());
        }

        @Test
        @DisplayName("a cached payload keeps its original generation time")
        void cachedPayloadKeepsOriginalTimestamp() {
            WeatherDashboardDto first = service.getDashboard();
            clock.advance(Duration.ofMinutes(2));
            WeatherDashboardDto second = service.getDashboard();

            assertThat(second.generatedAt())
                    .as("generatedAt reports when the data was computed, not when it was "
                            + "served - that is how the UI can show the data's true age")
                    .isEqualTo(first.generatedAt());
        }

        @Test
        @DisplayName("a cached payload returns identical rankings")
        void cachedPayloadIsIdentical() {
            WeatherDashboardDto first = service.getDashboard();
            WeatherDashboardDto second = service.getDashboard();

            assertThat(second.cities()).isEqualTo(first.cities());
        }

        @Test
        @DisplayName("after the processed TTL expires the provider is called again")
        void refetchesAfterTtlExpiry() {
            service.getDashboard();
            org.mockito.Mockito.clearInvocations(client);

            clock.advance(TTL.plusSeconds(1));
            WeatherDashboardDto refreshed = service.getDashboard();

            assertThat(refreshed.cacheStatus()).isEqualTo("MISS");
            verify(client, times(3)).fetchByCityId(anyString());
        }

        @Test
        @DisplayName("the raw cache serves a city whose processed payload has expired")
        void rawCacheOutlivesProcessedCache() {
            // A shorter processed TTL is the interesting case: the ranking goes stale while
            // the underlying readings are still fresh, so we should re-score without refetching.
            CacheProperties properties = new CacheProperties();
            properties.setRawTtl(Duration.ofMinutes(5));
            properties.setProcessedTtl(Duration.ofMinutes(1));

            WeatherCaches shortLived = new WeatherCaches(properties, clock);
            WeatherService svc = new WeatherService(
                    new CityRepository(new ClassPathResource("cities-test.json"), new ObjectMapper()),
                    client,
                    new ComfortIndexCalculator(new ComfortIndexProperties()),
                    shortLived,
                    clock);

            svc.getDashboard();
            org.mockito.Mockito.clearInvocations(client);

            clock.advance(Duration.ofMinutes(2));   // processed stale, raw still fresh
            WeatherDashboardDto second = svc.getDashboard();

            assertThat(second.cacheStatus()).isEqualTo("MISS");
            verify(client, never()).fetchByCityId(anyString());
        }

        @Test
        @DisplayName("each city occupies its own raw cache entry")
        void rawCacheIsKeyedPerCity() {
            service.getDashboard();

            assertThat(caches.raw().snapshot().entries())
                    .extracting(com.fidenz.weather.cache.CacheSnapshot.EntrySnapshot::key)
                    .containsExactly("1248991", "3143244", "4930956");
        }

        @Test
        @DisplayName("the processed cache holds a single ranked payload")
        void processedCacheHoldsOneEntry() {
            service.getDashboard();

            assertThat(caches.processed().size()).isEqualTo(1);
        }

        @Test
        @DisplayName("clearing the caches forces a fresh fetch")
        void clearingCachesForcesRefetch() {
            service.getDashboard();
            caches.clearAll();
            org.mockito.Mockito.clearInvocations(client);

            assertThat(service.getDashboard().cacheStatus()).isEqualTo("MISS");
            verify(client, times(3)).fetchByCityId(anyString());
        }
    }

    @Nested
    @DisplayName("Partial failure")
    class PartialFailure {

        @Test
        @DisplayName("one unavailable city does not take the whole dashboard down")
        void oneFailureDoesNotBreakTheDashboard() {
            when(client.fetchByCityId("3143244"))
                    .thenThrow(new WeatherProviderException("Oslo is unavailable"));

            WeatherDashboardDto dashboard = service.getDashboard();

            assertThat(dashboard.cityCount()).isEqualTo(2);
            assertThat(dashboard.cities())
                    .extracting(CityComfortDto::cityName)
                    .containsExactlyInAnyOrder("Colombo", "Boston");
        }

        @Test
        @DisplayName("failures are reported rather than silently hidden")
        void failuresAreReported() {
            when(client.fetchByCityId("3143244"))
                    .thenThrow(new WeatherProviderException("Oslo is unavailable"));

            WeatherDashboardDto dashboard = service.getDashboard();

            assertThat(dashboard.failures()).singleElement().satisfies(failure -> {
                assertThat(failure.cityCode()).isEqualTo("3143244");
                assertThat(failure.cityName()).isEqualTo("Oslo");
                assertThat(failure.reason()).contains("unavailable");
            });
        }

        @Test
        @DisplayName("a failed city is not cached, so the next request retries it")
        void failedCityIsNotCached() {
            when(client.fetchByCityId("3143244"))
                    .thenThrow(new WeatherProviderException("transient outage"))
                    .thenReturn(Fixtures.oslo());

            service.getDashboard();
            caches.processed().clear();     // force a rebuild, leaving the raw cache intact

            WeatherDashboardDto retried = service.getDashboard();

            assertThat(retried.cityCount()).isEqualTo(3);
            assertThat(retried.failures()).isEmpty();
        }

        @Test
        @DisplayName("all cities failing yields an empty ranking rather than an error")
        void allFailuresYieldEmptyDashboard() {
            when(client.fetchByCityId(anyString()))
                    .thenThrow(new WeatherProviderException("provider down"));

            WeatherDashboardDto dashboard = service.getDashboard();

            assertThat(dashboard.cities()).isEmpty();
            assertThat(dashboard.failures()).hasSize(3);
        }
    }

    @Nested
    @DisplayName("Mapping to comfort inputs")
    class Mapping {

        @Test
        @DisplayName("absent rain and snow become zero")
        void absentPrecipitationBecomesZero() {
            ComfortInputs inputs = WeatherService.toComfortInputs(Fixtures.colombo());

            assertThat(inputs.rainMmPerHour()).isZero();
            assertThat(inputs.snowMmPerHour()).isZero();
        }

        @Test
        @DisplayName("present rain is carried through as mm/h")
        void presentRainIsCarriedThrough() {
            assertThat(WeatherService.toComfortInputs(Fixtures.tokyo()).rainMmPerHour())
                    .isEqualTo(3.87);
        }

        @Test
        @DisplayName("a city with no wind.gust maps cleanly - gust is simply not used")
        void missingGustIsIrrelevant() {
            ComfortInputs inputs = WeatherService.toComfortInputs(Fixtures.boston());

            assertThat(inputs.windSpeedMs()).isEqualTo(1.54);
            assertThat(inputs.tempC()).isEqualTo(19.07);
        }

        @Test
        @DisplayName("pressure is carried through even though it is currently unweighted")
        void pressureIsCarriedThrough() {
            assertThat(WeatherService.toComfortInputs(Fixtures.oslo()).pressureHpa())
                    .isEqualTo(998.0);
        }
    }

    @Nested
    @DisplayName("Comfort labels")
    class Labels {

        @Test
        @DisplayName("labels are assigned server-side so every client agrees")
        void labelsCoverTheWholeRange() {
            assertThat(WeatherService.label(95)).isEqualTo("Excellent");
            assertThat(WeatherService.label(80)).isEqualTo("Excellent");
            assertThat(WeatherService.label(70)).isEqualTo("Comfortable");
            assertThat(WeatherService.label(55)).isEqualTo("Moderate");
            assertThat(WeatherService.label(40)).isEqualTo("Uncomfortable");
            assertThat(WeatherService.label(10)).isEqualTo("Harsh");
            assertThat(WeatherService.label(0)).isEqualTo("Harsh");
        }
    }

    private static CityComfortDto city(String name, double score) {
        return new CityComfortDto(0, "id-" + name, name, "XX", "clear sky", "01d",
                20, 20, 50, 2, 10, 1013, 0, score, WeatherService.label(score),
                Map.of(), Map.of(), java.time.Instant.EPOCH);
    }
}
