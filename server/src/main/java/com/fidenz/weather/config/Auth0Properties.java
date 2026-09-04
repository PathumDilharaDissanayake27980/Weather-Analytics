package com.fidenz.weather.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Auth0 settings bound from {@code auth0.*}.
 *
 * <p>Neither value is a secret - both appear in the browser too. The API never holds an
 * Auth0 client secret, because it never exchanges codes for tokens: it only <em>verifies</em>
 * tokens the browser already obtained, using Auth0's public signing keys.
 */
@ConfigurationProperties(prefix = "auth0")
public class Auth0Properties {

    /** e.g. {@code your-tenant.us.auth0.com}. */
    private String domain = "";

    /**
     * The API identifier configured in Auth0.
     *
     * <p>Validating it is what stops a token minted for a <em>different</em> API in the same
     * tenant from being replayed against this one. Signature validity alone is not enough.
     */
    private String audience = "";

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public String getAudience() {
        return audience;
    }

    public void setAudience(String audience) {
        this.audience = audience;
    }

    /** Auth0 issues tokens with an {@code iss} of {@code https://<domain>/} - trailing slash included. */
    public String getIssuer() {
        return "https://" + domain + "/";
    }
}
