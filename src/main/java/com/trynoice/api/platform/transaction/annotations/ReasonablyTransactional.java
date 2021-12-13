package com.trynoice.api.platform.transaction.annotations;

import org.springframework.transaction.annotation.Transactional;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A reasonable meta-annotation for {@link Transactional} with {@link Transactional#rollbackFor()
 * rollbackFor} set to {@link Exception} instead of {@link RuntimeException} and {@link Error}.
 *
 * @see <a
 * href="https://docs.spring.io/spring-framework/docs/4.2.x/spring-framework-reference/html/transaction.html#transaction-declarative-rolling-back">
 * Rolling back a declarative transaction - Spring documentation</a>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@Transactional(rollbackFor = Exception.class)
public @interface ReasonablyTransactional {
}
