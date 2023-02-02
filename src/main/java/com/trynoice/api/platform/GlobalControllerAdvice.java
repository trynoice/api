package com.trynoice.api.platform;

import jakarta.validation.ConstraintViolationException;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;


/**
 * Exception handlers for common, global handled and unhandled errors.
 */
@RestControllerAdvice
@Slf4j
public class GlobalControllerAdvice {

    @ExceptionHandler(Throwable.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    void handleInternalError(@NonNull final Throwable e) {
        log.error("uncaught exception while processing the request", e);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    @ResponseStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
    void handleHttpMediaTypeNotSupported(@NonNull final HttpMediaTypeNotSupportedException e) {
        log.trace("http media type not supported", e);
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    @ResponseStatus(HttpStatus.NOT_ACCEPTABLE)
    void handleHttpMediaNotAcceptable(@NonNull final HttpMediaTypeNotAcceptableException e) {
        log.trace("http media not acceptable", e);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    void handleHttpRequestMethodNotSupported(final HttpRequestMethodNotSupportedException e) {
        log.trace("http method not supported", e);
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    void handleNotFound(final NoHandlerFoundException e) {
        log.trace("http handler not found", e);
    }

    @ExceptionHandler({
        HttpMessageNotReadableException.class,
        MissingServletRequestParameterException.class,
        ServletRequestBindingException.class,
        BindException.class,
        MissingRequestHeaderException.class,
        TypeMismatchException.class,
        MethodArgumentNotValidException.class,
        ConstraintViolationException.class
    })
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    void handleBadRequest(final Throwable e) {
        log.trace("http request is not valid", e);
    }
}
