package pl.pietruszynski.loyaltyclub.api.admin.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import pl.pietruszynski.loyaltyclub.api.admin.model.AdminUser;
import pl.pietruszynski.loyaltyclub.api.admin.repository.AdminUserRepository;

/**
 * Zaklada konto administratora na pustej bazie.
 *
 * <p><b>Komponent jest domyslnie wylaczony.</b> Powstaje wylacznie wtedy, gdy
 * wlasciwosc {@code loyaltyclub.seed.enabled} ma wartosc {@code true}, czyli
 * w srodowisku deweloperskim i w testach. We wdrozeniu produkcyjnym wlasciwosc
 * pozostaje nieustawiona, wiec konta zakladane sa swiadoma czynnoscia
 * operatora, a nie przy okazji pierwszego startu aplikacji.
 *
 * <p>Haslo pochodzi z konfiguracji i <b>nie ma wartosci domyslnej</b>. Przy
 * wlaczonym zakladaniu kont i nieustawionym hasle rozwiazanie placeholdera
 * konczy sie bledem, wiec kontekst nie wstaje. Jest to zachowanie zamierzone:
 * awaria przy starcie jest tansza niz srodowisko dzialajace z haslem, ktorego
 * nikt swiadomie nie wybral.
 *
 * <p>Poprzednia wersja zakladala konto z para {@code admin/admin} wpisana
 * wprost w kod publicznego repozytorium, bez wymogu zmiany przy pierwszym
 * logowaniu. Odpowiada to kategorii "Identification and Authentication
 * Failures" z zestawienia OWASP Top 10.
 */
@Component
@ConditionalOnProperty(name = "loyaltyclub.seed.enabled", havingValue = "true")
public class AdminUserSeeder implements CommandLineRunner {

    private static final String ADMIN_USERNAME = "admin";

    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminPassword;

    public AdminUserSeeder(AdminUserRepository adminUserRepository,
                           PasswordEncoder passwordEncoder,
                           @Value("${loyaltyclub.seed.admin.password}") String adminPassword) {
        this.adminUserRepository = adminUserRepository;
        this.passwordEncoder = passwordEncoder;
        if (adminPassword == null || adminPassword.isBlank()) {
            throw new IllegalStateException(
                    "Zakladanie kont jest wlaczone (loyaltyclub.seed.enabled=true), ale haslo konta "
                            + "administratora jest puste. Ustaw zmienna LOYALTYCLUB_SEED_ADMIN_PASSWORD. "
                            + "Haslo nie ma wartosci domyslnej celowo -- patrz .env.example.");
        }
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(String... args) {
        adminUserRepository.findByUsername(ADMIN_USERNAME).orElseGet(() ->
                adminUserRepository.save(AdminUser.builder()
                        .username(ADMIN_USERNAME)
                        .password(passwordEncoder.encode(adminPassword))
                        .enabled(true)
                        .build())
        );
    }
}
