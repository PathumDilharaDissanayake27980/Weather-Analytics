package com.fidenz.weather.config;

import com.fidenz.weather.weather.OpenWeatherMapProperties;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.time.Clock;

@Configuration
public class AppConfig {

    /**
     * A single injectable {@link Clock}.
     *
     * <p>Every time-dependent component takes this rather than calling {@code Instant.now()}
     * directly, which is what lets the cache tests jump five minutes forward instantly
     * instead of sleeping. Reaching for the system clock inside business logic is the usual
     * reason time-based behaviour ends up untested.
     */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * Applies connect and read timeouts to outbound HTTP.
     *
     * <p>Without them a hung provider would hold a request thread indefinitely, and ten
     * cities' worth of hung calls would exhaust the pool. Bounded waits turn a provider
     * outage into a fast, partial degradation instead of a stalled dashboard.
     */
    @Bean
    RestClientCustomizer restClientCustomizer(OpenWeatherMapProperties properties) {
        return builder -> {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout((int) properties.getConnectTimeout().toMillis());
            factory.setReadTimeout((int) properties.getReadTimeout().toMillis());
            builder.requestFactory(factory);
        };
    }
}
