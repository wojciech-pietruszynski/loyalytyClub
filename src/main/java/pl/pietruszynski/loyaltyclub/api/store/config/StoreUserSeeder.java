package pl.pietruszynski.loyaltyclub.api.store.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import pl.pietruszynski.loyaltyclub.api.store.model.StoreUser;
import pl.pietruszynski.loyaltyclub.api.store.repository.StoreUserRepository;

@Component
@RequiredArgsConstructor
public class StoreUserSeeder implements CommandLineRunner {

    private static final String STORE_USERNAME = "store";
    private static final String STORE_PASSWORD = "8b7929d8-f588-4c1d-a3da-8aaf5b0b05c7";

    private final StoreUserRepository storeUserRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        storeUserRepository.findByUsername(STORE_USERNAME).orElseGet(() ->
                storeUserRepository.save(StoreUser.builder()
                        .username(STORE_USERNAME)
                        .password(passwordEncoder.encode(STORE_PASSWORD))
                        .enabled(true)
                        .build())
        );
    }
}


