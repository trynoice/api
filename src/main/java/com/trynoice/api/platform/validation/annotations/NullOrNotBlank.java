package com.trynoice.api.platform.validation.annotations;

import com.trynoice.api.platform.validation.NullOrNotBlankValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates the annotated {@link String} field as a {@literal null} or a not blank string.
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Constraint(validatedBy = NullOrNotBlankValidator.class)
public @interface NullOrNotBlank {

    String message() default "must be null or a not blank string";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
