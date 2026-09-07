package pl.pietruszynski.loyaltyclub.api.ecom.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import pl.pietruszynski.loyaltyclub.api.ecom.model.EcomUser;
import pl.pietruszynski.loyaltyclub.api.ecom.repository.EcomUserRepository;

/**
 * Zaklada konto systemowe sklepu internetowego na pustej bazie.
 *
 * <p>Zasady te same, co w
 * {@link pl.pietruszynski.loyaltyclub.api.admin.config.AdminUserSeeder}:
 * komponent powstaje wylacznie przy {@code loyaltyclub.seed.enabled=true},
 * a haslo pochodzi z konfiguracji i nie ma wartosci domyslnej.
 *
 * <p>Poprzednia wersja trzymala haslo jako stala w kodzie publicznego
 * repozytorium. Konto e-commerce pozwala odczytac profil uczestnika, saldo
 * punktow i historie transakcji, wiec ujawnienie stalej otwieralo dostep do
 * danych osobowych calej kartoteki w zakresie kraju przypisanego kontu.
 */
@Component
@ConditionalOnProperty(name = "loyaltyclub.seed.enabled", havingValue = "true")
public class EcomUserSeeder implements CommandLineRunner {

    private static final String ECOM_USERNAME = "ecom";

    private final EcomUserRepository ecomUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final String ecomPassword;

    public EcomUserSeeder(EcomUserRepository ecomUserRepository,
                          PasswordEncoder passwordEncoder,
                          @Value("${loyaltyclub.seed.ecom.password}") String ecomPassword) {
        this.ecomUserRepository = ecomUserRepository;
        this.passwordEncoder = passwordEncoder;
        if (ecomPassword == null || ecomPassword.isBlank()) {
            throw new IllegalStateException(
                    "Zakladanie kont jest wlaczone (loyaltyclub.seed.enabled=true), ale haslo konta "
                            + "sklepu internetowego jest puste. Ustaw zmienna LOYALTYCLUB_SEED_ECOM_PASSWORD. "
                            + "Haslo nie ma wartosci domyslnej celowo -- patrz .env.example.");
        }
        this.ecomPassword = ecomPassword;
    }

    @Override
    public void run(String... args) {
        ecomUserRepository.findByUsername(ECOM_USERNAME).orElseGet(() ->
                ecomUserRepository.save(EcomUser.builder()
                        .username(ECOM_USERNAME)
                        .password(passwordEncoder.encode(ecomPassword))
                        .enabled(true)
                        .build())
        );
    }
}
