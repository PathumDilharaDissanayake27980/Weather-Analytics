package com.fidenz.weather.api;

import com.fidenz.weather.weather.WeatherProviderException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates exceptions into HTTP responses in one place.
 *
 * <p>Without this, every controller needs its own try/catch and a single missed case leaks a
 * stack trace to the client. Responses use RFC 7807 {@code ProblemDetail}, which Spring
 * serialises as {@code application/problem+json}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * The upstream provider failed, not us - so 502 Bad Gateway rather than 500. The
     * distinction matters to whoever is on call: 500 means "our bug", 502 means "their outage".
     */
    @ExceptionHandler(WeatherProviderException.class)
    ProblemDetail handleProviderFailure(WeatherProviderException e) {
        log.warn("Weather provider failure: {}", e.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, e.getMessage());
        problem.setTitle("Weather provider unavailable");
        return problem;
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception e) {
        // Log the full stack trace for us; return a generic message to the client. Internal
        // details in an error body are an information-disclosure risk.
        log.error("Unhandled exception", e);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.");
        problem.setTitle("Internal server error");
        return problem;
    }
}
