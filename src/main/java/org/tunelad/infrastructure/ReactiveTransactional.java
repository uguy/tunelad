package org.tunelad.infrastructure;

import org.springframework.core.annotation.AliasFor;
import org.springframework.transaction.annotation.Transactional;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * {@code @Transactional} for reactive services: runs in a reactive R2DBC transaction. Plain
 * {@code @Transactional} uses the blocking transaction manager, which the Spring Modulith Event Publication
 * Registry and {@code @ApplicationModuleListener} methods rely on.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Transactional(transactionManager = ReactiveTransactional.MANAGER)
public @interface ReactiveTransactional {

    /** Bean name of the reactive R2DBC transaction manager. */
    String MANAGER = "reactiveTransactionManager";

    @AliasFor(annotation = Transactional.class)
    boolean readOnly() default false;
}
