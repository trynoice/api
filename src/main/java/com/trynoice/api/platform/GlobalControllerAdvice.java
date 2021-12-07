package com.trynoice.api.platform;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

import javax.servlet.http.HttpServletResponse;
import javax.validation.ConstraintViolationException;

/**
 * Exception handlers for common, global handled and unhandled errors.
 */
@RestControllerAdvice
@Slf4j
public class GlobalControllerAdvice {

    /**
     * Builds an {@link AuthenticationEntryPoint} for REST APIs where authentication is not feasible
     * through HTTP redirects or implicit credentials. Clients must manually send their credentials
     * to authentication endpoint(s) upon receiving a 401 response status.
     *
     * @return an {@link AuthenticationEntryPoint} that always responds its clients with 401 status
     * instead of initiating an authentication scheme.
     */
    public AuthenticationEntryPoint noOpAuthenticationEntrypoint() {
        return (request, response, authException) -> response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @ExceptionHandler(Throwable.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    void handleInternalError(@NonNull final Throwable e) {
        log.error("encountered an uncaught exception while processing the request", e);
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class})
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    void handleInvalidRequestBody() {
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    @ResponseStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
    void handleHttpMediaTypeNotSupported() {
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    void handleHttpRequestMethodNotSupport() {
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    void handleNotFound() {
    }

    @ExceptionHandler({AuthenticationException.class})
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    void handleUnauthorized() {
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    void handleBadRequest() {
    }
}
