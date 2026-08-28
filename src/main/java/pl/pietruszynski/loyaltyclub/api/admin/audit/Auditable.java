package pl.pietruszynski.loyaltyclub.api.admin.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Oznacza modyfikujacy endpoint panelu administracyjnego, ktorego udane wywolanie
 * ma zostac zapisane w logu audytowym.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Auditable {

    /** Nazwa akcji zapisywana w logu, np. {@code CREATE_CUSTOMER}. */
    String value();

    /** Typ zasobu, ktorego dotyczy akcja, np. {@code CUSTOMER}. */
    String resourceType();

    /** Gdy {@code true}, zapisuje parametr sciezki {@code id} jako identyfikator zasobu. */
    boolean capturePathId() default false;
}
