package pl.pietruszynski.loyaltyclub.api.store.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import pl.pietruszynski.loyaltyclub.api.store.model.StoreUser;
import pl.pietruszynski.loyaltyclub.api.store.repository.StoreUserRepository;

/**
 * Zaklada konto systemowe kasy na pustej bazie.
 *
 * <p>Zasady te same, co w
 * {@link pl.pietruszynski.loyaltyclub.api.admin.config.AdminUserSeeder}:
 * komponent powstaje wylacznie przy {@code loyaltyclub.seed.enabled=true},
 * a haslo pochodzi z konfiguracji i nie ma wartosci domyslnej.
 *
 * <p>Poprzednia wersja trzymala haslo jako stala w kodzie publicznego
 * repozytorium. Konto kasowe jest kontem maszynowym, wiec jego przejecie
 * pozwalaloby rejestrowac sprzedaz i naliczac punkty w imieniu sklepu --
 * ujawnienie stalej bylo tu rownowazne z ujawnieniem poswiadczen produkcyjnych.
 */
@Component
@ConditionalOnProperty(name = "loyaltyclub.seed.enabled", havingValue = "true")
public class StoreUserSeeder implements CommandLineRunner {

    private static final String STORE_USERNAME = "store";

    private final StoreUserRepository storeUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final String storePassword;

    public StoreUserSeeder(StoreUserRepository storeUserRepository,
                           PasswordEncoder passwordEncoder,
                           @Value("${loyaltyclub.seed.store.password}") String storePassword) {
        this.storeUserRepository = storeUserRepository;
        this.passwordEncoder = passwordEncoder;
        if (storePassword == null || storePassword.isBlank()) {
            throw new IllegalStateException(
                    "Zakladanie kont jest wlaczone (loyaltyclub.seed.enabled=true), ale haslo konta "
                            + "kasy jest puste. Ustaw zmienna LOYALTYCLUB_SEED_STORE_PASSWORD. "
                            + "Haslo nie ma wartosci domyslnej celowo -- patrz .env.example.");
        }
        this.storePassword = storePassword;
    }

    @Override
    public void run(String... args) {
        storeUserRepository.findByUsername(STORE_USERNAME).orElseGet(() ->
                storeUserRepository.save(StoreUser.builder()
                        .username(STORE_USERNAME)
                        .password(passwordEncoder.encode(storePassword))
                        .enabled(true)
                        .build())
        );
    }
}
