package pl.pietruszynski.loyaltyclub;

import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Test warstwy persystencji: kontekst ograniczony do JPA, schemat zbudowany
 * z mapowan encji na bazie w pamieci, kazdy test w transakcji wycofywanej
 * po zakonczeniu.
 *
 * <p>Poziom potrzebny dlatego, ze zapytania {@code @Query} sa lancuchami znakow --
 * kompilator nie sprawdza ani skladni JPQL, ani nazw pol, ani semantyki warunkow.
 * Testy jednostkowe serwisow zaslaniaja ten obszar mockiem repozytorium, wiec
 * blad w zapytaniu przechodzilby przez cala bramke testowa az do uruchomienia.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@DataJpaTest
@ActiveProfiles("test")
public @interface PersistenceTest {
}
