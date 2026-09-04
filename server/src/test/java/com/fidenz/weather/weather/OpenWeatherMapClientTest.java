package com.fidenz.weather.weather;

import com.fidenz.weather.support.Fixtures;
import com.fidenz.weather.weather.model.OwmWeatherResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.GET;

/**
 * Tests the OpenWeatherMap client against a mock HTTP server.
 *
 * <p>No network and no API key: {@link MockRestServiceServer} intercepts the request, which
 * lets us assert on the URL we build - including that the key is actually attached - and
 * replay canned responses for the failure modes that are hard to trigger on demand.
 */
class OpenWeatherMapClientTest {

    private OpenWeatherMapClient client;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        OpenWeatherMapProperties properties = new OpenWeatherMapProperties();
        properties.setApiKey("test-key-123");
        properties.setBaseUrl("https://api.openweathermap.org/data/2.5");
        properties.setUnits("metric");

        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new OpenWeatherMapClient(builder, properties);
    }

    @Test
    @DisplayName("builds the documented request: /weather with id, appid and units")
    void buildsCorrectRequest() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(
                        "https://api.openweathermap.org/data/2.5/weather")))
                .andExpect(method(GET))
                .andExpect(queryParam("id", "1248991"))
                .andExpect(queryParam("appid", "test-key-123"))
                .andExpect(queryParam("units", "metric"))
                .andRespond(withSuccess(Fixtures.COLOMBO, MediaType.APPLICATION_JSON));

        client.fetchByCityId("1248991");

        server.verify();
    }

    @Test
    @DisplayName("asks for metric units so temperatures arrive in Celsius, not Kelvin")
    void requestsMetricUnits() {
        server.expect(queryParam("units", "metric"))
                .andRespond(withSuccess(Fixtures.COLOMBO, MediaType.APPLICATION_JSON));

        OwmWeatherResponse response = client.fetchByCityId("1248991");

        assertThat(response.main().temp())
                .as("30C, not 303K - asking the provider for Celsius removes a conversion "
                        + "step and a whole class of unit bugs")
                .isEqualTo(30.02);
    }

    @Test
    @DisplayName("deserialises a successful response into our model")
    void deserialisesSuccessfulResponse() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("id=1248991")))
                .andRespond(withSuccess(Fixtures.COLOMBO, MediaType.APPLICATION_JSON));

        OwmWeatherResponse response = client.fetchByCityId("1248991");

        assertThat(response.name()).isEqualTo("Colombo");
        assertThat(response.sys().country()).isEqualTo("LK");
        assertThat(response.main().humidity()).isEqualTo(76.0);
        assertThat(response.clouds().all()).isEqualTo(87);
    }

    @Test
    @DisplayName("a 401 is reported as an API-key problem, mentioning activation delay")
    void translatesUnauthorised() {
        server.expect(requestTo(org.hamcrest.Matchers.any(String.class)))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .body("{\"cod\":401,\"message\":\"Invalid API key\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.fetchByCityId("1248991"))
                .isInstanceOf(WeatherProviderException.class)
                .hasMessageContaining("rejected the API key")
                .hasMessageContaining("two hours");
    }

    @Test
    @DisplayName("a 404 names the city id that was not found")
    void translatesNotFound() {
        server.expect(requestTo(org.hamcrest.Matchers.any(String.class)))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .body("{\"cod\":\"404\",\"message\":\"city not found\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.fetchByCityId("9999999"))
                .isInstanceOf(WeatherProviderException.class)
                .hasMessageContaining("9999999")
                .hasMessageContaining("404");
    }

    @Test
    @DisplayName("a 429 explains the free-tier rate limit")
    void translatesRateLimit() {
        server.expect(requestTo(org.hamcrest.Matchers.any(String.class)))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .body("{\"cod\":429}").contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.fetchByCityId("1248991"))
                .isInstanceOf(WeatherProviderException.class)
                .hasMessageContaining("rate limit");
    }

    @Test
    @DisplayName("an unexpected 5xx is still wrapped in our own exception type")
    void translatesServerError() {
        server.expect(requestTo(org.hamcrest.Matchers.any(String.class)))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> client.fetchByCityId("1248991"))
                .isInstanceOf(WeatherProviderException.class)
                .hasMessageContaining("503");
    }

    @Test
    @DisplayName("an empty body is a provider failure, not a null returned to callers")
    void rejectsEmptyBody() {
        server.expect(requestTo(org.hamcrest.Matchers.any(String.class)))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.fetchByCityId("1248991"))
                .isInstanceOf(WeatherProviderException.class)
                .hasMessageContaining("Empty response");
    }
}
