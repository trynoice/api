package com.trynoice.api.platform.validation.annotations;

import com.trynoice.api.platform.validation.HttpUrlValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates the annotated {@link String} field as an HTTP URL. It must have {@code http} or {@code
 * https} scheme and its {@code host} must not be blank. Please note that <b>{@code null} values are
 * also considered valid.</b>
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Constraint(validatedBy = HttpUrlValidator.class)
public @interface HttpUrl {

    String message() default "must be valid http/https url";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
