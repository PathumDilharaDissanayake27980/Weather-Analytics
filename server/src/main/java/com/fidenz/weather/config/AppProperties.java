package com.fidenz.weather.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Application-level settings bound from {@code app.*}.
 */
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    /**
     * Origins permitted to call this API from a browser.
     *
     * <p>Explicitly listed rather than "*" - a wildcard would let any site on the internet
     * make authenticated calls on a logged-in user's behalf.
     */
    private List<String> corsAllowedOrigins = List.of("http://localhost:5173");

    private Security security = new Security();

    public static class Security {
        /**
         * {@code jwt} (default) validates an Auth0 access token on every request.
         *
         * <p>{@code permissive} disables authentication entirely and exists only so the API
         * can be run locally before an Auth0 tenant is configured, and so controller tests
         * can focus on payload shape. It must never be used in a deployed environment; the
         * application logs a warning at startup when it is active.
         */
        private String mode = "jwt";

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }
    }

    public List<String> getCorsAllowedOrigins() {
        return corsAllowedOrigins;
    }

    public void setCorsAllowedOrigins(List<String> corsAllowedOrigins) {
        this.corsAllowedOrigins = corsAllowedOrigins;
    }

    public Security getSecurity() {
        return security;
    }

    public void setSecurity(Security security) {
        this.security = security;
    }
}
