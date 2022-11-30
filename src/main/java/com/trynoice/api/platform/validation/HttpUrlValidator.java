package com.trynoice.api.platform.validation;

import com.trynoice.api.platform.validation.annotations.HttpUrl;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.val;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * Validates a {@link String} type as an HTTP URL. It must have {@code http} or {@code https}
 * scheme and its {@code host} must not be blank.
 */
public class HttpUrlValidator implements ConstraintValidator<HttpUrl, String> {

    @Override
    public void initialize(HttpUrl constraintAnnotation) {
        ConstraintValidator.super.initialize(constraintAnnotation);
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        try {
            val url = new URL(value);
            return ("http".equals(url.getProtocol()) || "https".equals(url.getProtocol())) && !url.getHost().isBlank();
        } catch (MalformedURLException ignored) {
            return false;
        }
    }
}
