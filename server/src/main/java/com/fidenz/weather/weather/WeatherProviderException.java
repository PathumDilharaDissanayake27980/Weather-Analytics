package com.fidenz.weather.weather;

/**
 * Thrown when OpenWeatherMap cannot be reached, rejects our key, or returns something we
 * cannot parse.
 *
 * <p>Wrapping the provider's failures in our own type keeps HTTP-client details from leaking
 * into the service and controller layers, and gives the exception handler a single thing to
 * translate into a 502.
 */
public class WeatherProviderException extends RuntimeException {

    public WeatherProviderException(String message) {
        super(message);
    }

    public WeatherProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
