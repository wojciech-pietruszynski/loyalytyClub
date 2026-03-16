package pl.pietruszynski.loyaltyclub.api.admin.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import pl.pietruszynski.loyaltyclub.api.admin.model.AdminUser;
import pl.pietruszynski.loyaltyclub.api.admin.repository.AdminUserRepository;

@Component
@RequiredArgsConstructor
public class AdminUserSeeder implements CommandLineRunner {

    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        adminUserRepository.findByUsername("admin").orElseGet(() ->
                adminUserRepository.save(AdminUser.builder()
                        .username("admin")
                        .password(passwordEncoder.encode("admin"))
                        .enabled(true)
                        .build())
        );
    }
}


