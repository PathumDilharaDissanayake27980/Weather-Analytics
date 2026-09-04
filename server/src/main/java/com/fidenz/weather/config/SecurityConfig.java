package com.fidenz.weather.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Security for a stateless JSON API protected by Auth0 access tokens.
 *
 * <h2>Why the API validates tokens at all</h2>
 * Hiding the dashboard behind a login screen in React protects nothing: anyone can open
 * devtools, read the bundle, and call this API with {@code curl}. The real control is that
 * every {@code /api/**} request must carry an access token that this server verifies against
 * Auth0's published signing keys. The frontend guard is a convenience; this is the boundary.
 *
 * <h2>How verification works without a shared secret</h2>
 * Auth0 signs tokens with the private half of an RS256 key pair and publishes the public half
 * at {@code https://<domain>/.well-known/jwks.json}. Spring fetches and caches those keys, then
 * checks the signature, {@code iss}, {@code exp} - and, via {@link AudienceValidator},
 * {@code aud}. This server never sees a client secret and never talks to Auth0 to check a
 * token, which is what makes it scale.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    /**
     * The real chain: every API route requires a valid access token.
     */
    @Bean
    @ConditionalOnProperty(name = "app.security.mode", havingValue = "jwt", matchIfMissing = true)
    SecurityFilterChain jwtSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                // No cookies and no server-side session, so there is no CSRF vector to protect:
                // an attacker's page cannot make the browser attach a bearer token.
                .csrf(csrf -> csrf.disable())
                // withDefaults() resolves the bean NAMED corsConfigurationSource. Injecting
                // by type would be ambiguous: Spring MVC's mvcHandlerMappingIntrospector also
                // implements CorsConfigurationSource.
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Browsers send an unauthenticated OPTIONS preflight before any
                        // cross-origin request carrying an Authorization header.
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/health").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()));
        return http.build();
    }

    @Bean
    @ConditionalOnProperty(name = "app.security.mode", havingValue = "jwt", matchIfMissing = true)
    JwtDecoder jwtDecoder(Auth0Properties properties) {
        NimbusJwtDecoder decoder = JwtDecoders.fromIssuerLocation(properties.getIssuer());
        OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(properties.getIssuer());
        OAuth2TokenValidator<Jwt> withAudience = new AudienceValidator(properties.getAudience());
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(withIssuer, withAudience));
        return decoder;
    }

    /**
     * Development-only chain. Exists so the API can be run and demonstrated before an Auth0
     * tenant is wired up, and so controller tests can assert on payload shape without minting
     * tokens. Never enable this outside local development.
     */
    @Bean
    @ConditionalOnProperty(name = "app.security.mode", havingValue = "permissive")
    SecurityFilterChain permissiveSecurityFilterChain(HttpSecurity http) throws Exception {
        log.warn("SECURITY IS DISABLED (app.security.mode=permissive). "
                + "This must never be used outside local development.");
        http
                .csrf(csrf -> csrf.disable())
                // withDefaults() resolves the bean NAMED corsConfigurationSource. Injecting
                // by type would be ambiguous: Spring MVC's mvcHandlerMappingIntrospector also
                // implements CorsConfigurationSource.
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(AppProperties properties) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(properties.getCorsAllowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        // Lets the browser read our cache-status header from JavaScript; without this the
        // fetch response would expose only the CORS-safelisted headers.
        config.setExposedHeaders(List.of("X-Cache"));
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
