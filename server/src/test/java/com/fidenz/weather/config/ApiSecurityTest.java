package com.fidenz.weather.config;

import com.fidenz.weather.api.dto.WeatherDashboardDto;
import com.fidenz.weather.weather.WeatherService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Authorisation rules for the API.
 *
 * <p>Runs with {@code app.security.mode=jwt}, overriding the permissive default used by the
 * payload-shape tests. The {@link JwtDecoder} is mocked so no Auth0 tenant, network call or
 * real token is needed - this class is testing our <em>rules</em>, while
 * {@link AudienceValidatorTest} tests the one piece of token validation we wrote ourselves.
 *
 * <p>The point these tests make is the one that matters most in this application: the React
 * app's login screen is a convenience, and this is the actual security boundary. Anyone can
 * call the API directly, so the API must refuse them.
 */
@SpringBootTest(properties = "app.security.mode=jwt")
@AutoConfigureMockMvc
class ApiSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    /** Prevents the real decoder from being built, which would fetch Auth0's JWKS at startup. */
    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private WeatherService weatherService;

    @Test
    @DisplayName("the dashboard is refused without a token")
    void dashboardRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/weather"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("the dashboard is served with a valid token")
    void dashboardAllowsAuthenticatedCaller() throws Exception {
        when(weatherService.getDashboard()).thenReturn(new WeatherDashboardDto(
                Instant.parse("2026-09-04T12:00:00Z"), "MISS", 0, List.of(), List.of()));

        mockMvc.perform(get("/api/weather").with(jwt()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("a garbage bearer token is refused")
    void rejectsMalformedToken() throws Exception {
        // A real decoder rejects an unparseable or badly signed token with BadJwtException;
        // the mock has to do the same or it would return null and mask the behaviour.
        when(jwtDecoder.decode(anyString()))
                .thenThrow(new BadJwtException("Malformed token"));

        mockMvc.perform(get("/api/weather").header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a token that fails validation - wrong audience, expired - is refused")
    void rejectsTokenFailingValidation() throws Exception {
        // This is what AudienceValidator's failure looks like once it reaches the filter chain.
        when(jwtDecoder.decode(anyString()))
                .thenThrow(new BadJwtException("The required audience is missing"));

        mockMvc.perform(get("/api/weather")
                        .header("Authorization", "Bearer token-for-a-different-api"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a non-bearer Authorization header is refused")
    void rejectsNonBearerScheme() throws Exception {
        mockMvc.perform(get("/api/weather").header("Authorization", "Basic dXNlcjpwYXNz"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("the debug endpoint is protected too")
    void debugEndpointRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/debug/cache"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("cache flushing is protected - an open flush endpoint is a DoS lever")
    void cacheFlushRequiresAuthentication() throws Exception {
        mockMvc.perform(delete("/api/debug/cache"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("the debug endpoint is reachable with a token")
    void debugEndpointAllowsAuthenticatedCaller() throws Exception {
        mockMvc.perform(get("/api/debug/cache").with(jwt()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("health is public so an uptime probe does not need a token")
    void healthIsPublic() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("unmapped paths are denied rather than falling through to a default")
    void unmappedPathsAreDenied() throws Exception {
        mockMvc.perform(get("/actuator/env").with(jwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a CORS preflight succeeds without a token, as browsers require")
    void preflightDoesNotRequireAuthentication() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .options("/api/weather")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "authorization"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("an untrusted origin is not granted CORS access")
    void rejectsUntrustedOrigin() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .options("/api/weather")
                        .header("Origin", "https://evil.example.com")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }
}
