package com.fidenz.weather.weather;

import com.fidenz.weather.weather.model.OwmWeatherResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * The only class that knows how to talk to OpenWeatherMap.
 *
 * <p>Isolating the provider here means the API key exists in exactly one place, provider
 * errors are translated once, and the rest of the application depends on our types rather
 * than theirs. Swapping weather providers would touch this file and nothing else.
 *
 * <p>Takes a {@link RestClient.Builder} rather than a finished {@code RestClient} so tests
 * can bind a {@code MockRestServiceServer} to the same builder and assert on the outgoing
 * request without a network call or a real API key.
 */
@Component
public class OpenWeatherMapClient {

    private static final Logger log = LoggerFactory.getLogger(OpenWeatherMapClient.class);

    private final RestClient restClient;
    private final OpenWeatherMapProperties properties;

    public OpenWeatherMapClient(RestClient.Builder builder, OpenWeatherMapProperties properties) {
        this.properties = properties;
        this.restClient = builder.baseUrl(properties.getBaseUrl()).build();
    }

    /**
     * Fetches current weather for one OpenWeatherMap city id.
     *
     * @throws WeatherProviderException if the provider is unreachable, rejects the request,
     *                                  or returns a body we cannot use
     */
    public OwmWeatherResponse fetchByCityId(String cityId) {
        try {
            OwmWeatherResponse response = restClient.get()
                    .uri(uri -> uri.path("/weather")
                            .queryParam("id", cityId)
                            .queryParam("appid", properties.getApiKey())
                            .queryParam("units", properties.getUnits())
                            .build())
                    .retrieve()
                    .body(OwmWeatherResponse.class);

            if (response == null) {
                throw new WeatherProviderException("Empty response from OpenWeatherMap for city " + cityId);
            }
            return response;

        } catch (RestClientResponseException e) {
            // 401 means the key is missing, wrong, or not yet activated - a very common
            // first-run problem, so it gets its own message.
            int status = e.getStatusCode().value();
            String detail = switch (status) {
                case 401 -> "OpenWeatherMap rejected the API key (401). A newly created key can "
                        + "take up to two hours to activate.";
                case 404 -> "OpenWeatherMap does not know city id " + cityId + " (404).";
                case 429 -> "OpenWeatherMap rate limit exceeded (429). The free tier allows "
                        + "60 calls/minute; caching should normally keep us well under it.";
                default -> "OpenWeatherMap returned HTTP " + status + " for city " + cityId;
            };
            log.warn("{}", detail);
            throw new WeatherProviderException(detail, e);

        } catch (RestClientException e) {
            log.warn("Could not reach OpenWeatherMap for city {}: {}", cityId, e.getMessage());
            throw new WeatherProviderException(
                    "Could not reach OpenWeatherMap for city " + cityId, e);
        }
    }
}
