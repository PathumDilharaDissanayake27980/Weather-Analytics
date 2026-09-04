package com.fidenz.weather.city;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.UncheckedIOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CityRepositoryTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private CityRepository repositoryFor(String resource) {
        return new CityRepository(new ClassPathResource(resource), mapper);
    }

    @Test
    @DisplayName("parses the CityCode and CityName fields from cities.json")
    void parsesCityEntries() {
        CityRepository repository = repositoryFor("cities-test.json");

        assertThat(repository.findAll())
                .extracting(City::cityName)
                .containsExactly("Colombo", "Oslo", "Boston");
    }

    @Test
    @DisplayName("city codes are kept as strings, exactly as the file stores them")
    void cityCodesAreStrings() {
        CityRepository repository = repositoryFor("cities-test.json");

        assertThat(repository.findAllCityCodes())
                .containsExactly("1248991", "3143244", "4930956");
    }

    @Test
    @DisplayName("file order is preserved, so the ordering is ours to decide later")
    void preservesFileOrder() {
        assertThat(repositoryFor("cities-test.json").findAll())
                .extracting(City::cityCode)
                .containsExactly("1248991", "3143244", "4930956");
    }

    @Test
    @DisplayName("the shipped city list meets the assignment's minimum of 10 cities")
    void shippedCityListMeetsMinimum() {
        CityRepository repository = repositoryFor("cities.json");

        assertThat(repository.count())
                .as("the assignment requires at least 10 cities to be processed")
                .isGreaterThanOrEqualTo(10);
    }

    @Test
    @DisplayName("the shipped city list has no duplicate city codes")
    void shippedCityListHasNoDuplicates() {
        assertThat(repositoryFor("cities.json").findAllCityCodes()).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("unknown JSON fields in the city file are ignored")
    void ignoresUnknownFields() {
        // Temp and Status exist in the source file but are stale sample data we never use.
        assertThat(repositoryFor("cities-test.json").findAll()).allSatisfy(city -> {
            assertThat(city.cityCode()).isNotBlank();
            assertThat(city.cityName()).isNotBlank();
        });
    }

    @Test
    @DisplayName("an empty city list fails at startup rather than at first request")
    void rejectsEmptyCityList() {
        assertThatThrownBy(() -> repositoryFor("cities-empty.json"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("empty");
    }

    @Test
    @DisplayName("a missing city file fails at startup with a message naming the file")
    void rejectsMissingFile() {
        assertThatThrownBy(() -> repositoryFor("does-not-exist.json"))
                .isInstanceOf(UncheckedIOException.class)
                .hasMessageContaining("does-not-exist.json");
    }

    @Test
    @DisplayName("the returned list cannot be modified by callers")
    void returnsImmutableList() {
        CityRepository repository = repositoryFor("cities-test.json");

        assertThatThrownBy(() -> repository.findAll().add(new City("1", "Nowhere")))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
