package com.fidenz.weather.config;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Rejects tokens that were not issued for this API.
 *
 * <p>Spring's default validators check the signature, the issuer and expiry - but not the
 * audience. Without this, an access token minted for <em>any other</em> API in the same Auth0
 * tenant would sail through: correctly signed, correct issuer, unexpired, wrong API. This is
 * the "confused deputy" case, and checking {@code aud} is the fix.
 */
public class AudienceValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error ERROR = new OAuth2Error(
            "invalid_token",
            "The required audience is missing",
            "https://tools.ietf.org/html/rfc6750#section-3.1");

    private final String requiredAudience;

    public AudienceValidator(String requiredAudience) {
        this.requiredAudience = requiredAudience;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        if (token.getAudience() != null && token.getAudience().contains(requiredAudience)) {
            return OAuth2TokenValidatorResult.success();
        }
        return OAuth2TokenValidatorResult.failure(ERROR);
    }
}
