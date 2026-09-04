package com.fidenz.weather.weather;

import com.fidenz.weather.api.dto.CityComfortDto;
import com.fidenz.weather.api.dto.WeatherDashboardDto;
import com.fidenz.weather.cache.CacheLookup;
import com.fidenz.weather.city.City;
import com.fidenz.weather.city.CityRepository;
import com.fidenz.weather.comfort.ComfortIndexCalculator;
import com.fidenz.weather.comfort.ComfortInputs;
import com.fidenz.weather.comfort.ComfortResult;
import com.fidenz.weather.weather.model.OwmWeatherResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Orchestrates the whole read path: cache, fetch, score, rank.
 *
 * <p>The layering is deliberate. This class knows the <em>recipe</em>; it does not know how
 * to speak HTTP to OpenWeatherMap ({@link OpenWeatherMapClient}), how comfort is computed
 * ({@link ComfortIndexCalculator}), or how expiry works ({@link com.fidenz.weather.cache.TtlCache}).
 * Each of those can change without touching this file, and each is unit-testable alone.
 */
@Service
public class WeatherService {

    private static final Logger log = LoggerFactory.getLogger(WeatherService.class);

    private final CityRepository cityRepository;
    private final OpenWeatherMapClient client;
    private final ComfortIndexCalculator calculator;
    private final WeatherCaches caches;
    private final Clock clock;

    public WeatherService(CityRepository cityRepository,
                          OpenWeatherMapClient client,
                          ComfortIndexCalculator calculator,
                          WeatherCaches caches,
                          Clock clock) {
        this.cityRepository = cityRepository;
        this.client = client;
        this.calculator = calculator;
        this.caches = caches;
        this.clock = clock;
    }

    /**
     * Returns every city ranked most to least comfortable, serving the processed cache when
     * it is warm.
     *
     * <p>On a processed-cache hit the stored payload is returned with its {@code cacheStatus}
     * rewritten to HIT, so the caller can see that this response came from cache while
     * {@code generatedAt} still reports when the data was actually computed.
     */
    public WeatherDashboardDto getDashboard() {
        CacheLookup<WeatherDashboardDto> cached = caches.processed().get(WeatherCaches.DASHBOARD_KEY);
        if (cached.isHit()) {
            WeatherDashboardDto hit = cached.value();
            return new WeatherDashboardDto(
                    hit.generatedAt(), "HIT", hit.cityCount(), hit.cities(), hit.failures());
        }

        WeatherDashboardDto fresh = buildDashboard();
        caches.processed().put(WeatherCaches.DASHBOARD_KEY, fresh);
        return fresh;
    }

    private WeatherDashboardDto buildDashboard() {
        List<CityComfortDto> scored = new ArrayList<>();
        List<WeatherDashboardDto.CityFailureDto> failures = new ArrayList<>();

        for (City city : cityRepository.findAll()) {
            try {
                OwmWeatherResponse weather = fetchWeather(city.cityCode());
                scored.add(toDto(city, weather));
            } catch (WeatherProviderException e) {
                // One unavailable city must not take the whole dashboard down. We report it
                // alongside the cities that did work, so the UI can degrade honestly rather
                // than silently showing an incomplete ranking.
                log.warn("Skipping {} ({}): {}", city.cityName(), city.cityCode(), e.getMessage());
                failures.add(new WeatherDashboardDto.CityFailureDto(
                        city.cityCode(), city.cityName(), e.getMessage()));
            }
        }

        List<CityComfortDto> ranked = rank(scored);
        return new WeatherDashboardDto(
                Instant.now(clock), "MISS", ranked.size(), ranked, List.copyOf(failures));
    }

    /**
     * Reads one city's raw weather, going to OpenWeatherMap only on a cache miss.
     *
     * <p>Package-private so the per-city path can be tested directly.
     */
    OwmWeatherResponse fetchWeather(String cityCode) {
        CacheLookup<OwmWeatherResponse> cached = caches.raw().get(cityCode);
        if (cached.isHit()) {
            return cached.value();
        }
        OwmWeatherResponse fresh = client.fetchByCityId(cityCode);
        caches.raw().put(cityCode, fresh);
        return fresh;
    }

    /**
     * Sorts most comfortable first and assigns rank positions.
     *
     * <p>Uses standard competition ranking: equal scores share a rank and the next distinct
     * score skips accordingly (1, 2, 2, 4). Ties are broken by city name purely so the output
     * order is deterministic - without that, two cities on the same score could swap places
     * between requests for no visible reason.
     */
    static List<CityComfortDto> rank(List<CityComfortDto> cities) {
        List<CityComfortDto> sorted = new ArrayList<>(cities);
        sorted.sort(Comparator
                .comparingDouble(CityComfortDto::comfortScore).reversed()
                .thenComparing(CityComfortDto::cityName));

        List<CityComfortDto> ranked = new ArrayList<>(sorted.size());
        int rank = 0;
        int position = 0;
        Double previousScore = null;

        for (CityComfortDto city : sorted) {
            position++;
            if (previousScore == null || Double.compare(previousScore, city.comfortScore()) != 0) {
                rank = position;
                previousScore = city.comfortScore();
            }
            ranked.add(withRank(city, rank));
        }
        return List.copyOf(ranked);
    }

    private CityComfortDto toDto(City city, OwmWeatherResponse weather) {
        OwmWeatherResponse.Condition condition = weather.primaryCondition();

        ComfortInputs inputs = toComfortInputs(weather);
        ComfortResult result = calculator.calculate(inputs);

        return new CityComfortDto(
                0, // replaced by rank()
                city.cityCode(),
                // Prefer the provider's name; fall back to cities.json if it is absent.
                weather.name() != null ? weather.name() : city.cityName(),
                weather.sys() != null ? weather.sys().country() : null,
                condition != null ? condition.description() : "unknown",
                condition != null ? condition.icon() : null,
                inputs.tempC(),
                inputs.feelsLikeC(),
                inputs.humidityPct(),
                inputs.windSpeedMs(),
                inputs.cloudinessPct(),
                inputs.pressureHpa(),
                inputs.rainMmPerHour(),
                result.score(),
                label(result.score()),
                result.subScores(),
                result.multipliers(),
                Instant.ofEpochSecond(weather.dt())
        );
    }

    /**
     * Maps the provider's response onto the calculator's narrow input type.
     *
     * <p>This is the only place that knows both shapes, which is what keeps the Comfort Index
     * independent of OpenWeatherMap. Absent rain and snow become 0 - justified because a
     * 130-city sample showed the field present in exactly the cities where it was raining.
     */
    static ComfortInputs toComfortInputs(OwmWeatherResponse weather) {
        OwmWeatherResponse.Main main = weather.main();
        OwmWeatherResponse.Wind wind = weather.wind();
        OwmWeatherResponse.Clouds clouds = weather.clouds();

        return new ComfortInputs(
                main != null ? main.temp() : Double.NaN,
                main != null ? main.feelsLike() : Double.NaN,
                main != null ? main.humidity() : Double.NaN,
                wind != null ? wind.speed() : 0.0,
                clouds != null ? clouds.all() : 0.0,
                weather.rain() != null ? weather.rain().millimetresPerHour() : 0.0,
                weather.snow() != null ? weather.snow().millimetresPerHour() : 0.0,
                main != null ? main.pressure() : Double.NaN
        );
    }

    /** Human-readable band for the score, computed server-side so every client agrees. */
    static String label(double score) {
        if (score >= 80) return "Excellent";
        if (score >= 65) return "Comfortable";
        if (score >= 50) return "Moderate";
        if (score >= 35) return "Uncomfortable";
        return "Harsh";
    }

    private static CityComfortDto withRank(CityComfortDto c, int rank) {
        return new CityComfortDto(rank, c.cityCode(), c.cityName(), c.country(), c.description(),
                c.icon(), c.temperatureC(), c.feelsLikeC(), c.humidityPct(), c.windSpeedMs(),
                c.cloudinessPct(), c.pressureHpa(), c.rainMmPerHour(), c.comfortScore(),
                c.comfortLabel(), c.subScores(), c.multipliers(), c.observedAt());
    }
}
