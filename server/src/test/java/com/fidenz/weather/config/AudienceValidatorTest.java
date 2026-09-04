package com.fidenz.weather.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The audience check is the one validation Spring does not perform by default, and its
 * absence is a real vulnerability rather than a theoretical one: without it, an access token
 * minted for any other API in the same Auth0 tenant would pass every other check - correct
 * signature, correct issuer, unexpired - and be accepted here.
 */
class AudienceValidatorTest {

    private static final String REQUIRED = "https://weather-analytics-api";

    private final AudienceValidator validator = new AudienceValidator(REQUIRED);

    private static Jwt tokenWithAudience(List<String> audience) {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("sub", "auth0|123")
                .audience(audience)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }

    @Test
    @DisplayName("accepts a token issued for this API")
    void acceptsMatchingAudience() {
        OAuth2TokenValidatorResult result = validator.validate(tokenWithAudience(List.of(REQUIRED)));

        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    @DisplayName("accepts a token whose audience list contains this API among others")
    void acceptsAudienceAmongSeveral() {
        // Auth0 commonly issues an aud array containing both the API identifier and the
        // /userinfo endpoint.
        Jwt token = tokenWithAudience(List.of(
                REQUIRED, "https://tenant.eu.auth0.com/userinfo"));

        assertThat(validator.validate(token).hasErrors()).isFalse();
    }

    @Test
    @DisplayName("rejects a token minted for a different API in the same tenant")
    void rejectsDifferentAudience() {
        Jwt token = tokenWithAudience(List.of("https://some-other-api"));

        OAuth2TokenValidatorResult result = validator.validate(token);

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).first()
                .extracting("description")
                .isEqualTo("The required audience is missing");
    }

    @Test
    @DisplayName("rejects a token with an empty audience")
    void rejectsEmptyAudience() {
        assertThat(validator.validate(tokenWithAudience(List.of())).hasErrors()).isTrue();
    }

    @Test
    @DisplayName("the audience comparison is exact, not a prefix match")
    void audienceComparisonIsExact() {
        Jwt token = tokenWithAudience(List.of(REQUIRED + "-staging"));

        assertThat(validator.validate(token).hasErrors()).isTrue();
    }
}
