package pl.pietruszynski.loyaltyclub.api.ecom.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import pl.pietruszynski.loyaltyclub.api.ecom.model.EcomUser;
import pl.pietruszynski.loyaltyclub.api.ecom.repository.EcomUserRepository;

@Component
@RequiredArgsConstructor
public class EcomUserSeeder implements CommandLineRunner {

    private static final String ECOM_USERNAME = "ecom";
    private static final String ECOM_PASSWORD = "c7d8e90a-c901-46d0-968c-97fa6a044e58";

    private final EcomUserRepository ecomUserRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        ecomUserRepository.findByUsername(ECOM_USERNAME).orElseGet(() ->
                ecomUserRepository.save(EcomUser.builder()
                        .username(ECOM_USERNAME)
                        .password(passwordEncoder.encode(ECOM_PASSWORD))
                        .enabled(true)
                        .build())
        );
    }
}


