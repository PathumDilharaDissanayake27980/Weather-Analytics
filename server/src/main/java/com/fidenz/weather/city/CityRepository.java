package com.fidenz.weather.city;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.List;

/**
 * Loads the configured city list once at startup and serves it from memory.
 *
 * <p>Read once rather than per request: the file never changes while the app runs, and
 * re-reading it on every request would add disk I/O to a hot path for no benefit. The
 * trade-off is that editing {@code cities.json} requires a restart, which is stated in the
 * README under known limitations.
 *
 * <p>A failure to read or parse the file is fatal at startup rather than at first request.
 * Failing loudly and immediately beats failing mysteriously under load.
 */
@Repository
public class CityRepository {

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CitiesFile(@JsonProperty("List") List<City> list) {}

    private final List<City> cities;

    public CityRepository(@Value("${app.cities-file:classpath:cities.json}") Resource citiesFile,
                          ObjectMapper objectMapper) {
        this.cities = load(citiesFile, objectMapper);
    }

    private static List<City> load(Resource resource, ObjectMapper objectMapper) {
        try (InputStream in = resource.getInputStream()) {
            CitiesFile parsed = objectMapper.readValue(in, CitiesFile.class);
            if (parsed == null || parsed.list() == null || parsed.list().isEmpty()) {
                throw new IllegalStateException("City list is empty: " + resource.getDescription());
            }
            return List.copyOf(parsed.list());
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to read city list from " + resource.getDescription(), e);
        }
    }

    public List<City> findAll() {
        return Collections.unmodifiableList(cities);
    }

    /** The CityCode values, in file order. */
    public List<String> findAllCityCodes() {
        return cities.stream().map(City::cityCode).toList();
    }

    public int count() {
        return cities.size();
    }
}
