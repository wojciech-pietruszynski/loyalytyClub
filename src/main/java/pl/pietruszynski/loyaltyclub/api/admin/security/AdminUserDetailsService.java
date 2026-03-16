package pl.pietruszynski.loyaltyclub.api.admin.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import pl.pietruszynski.loyaltyclub.api.admin.model.AdminUser;
import pl.pietruszynski.loyaltyclub.api.admin.model.TechnicalUser;
import pl.pietruszynski.loyaltyclub.api.admin.repository.AdminUserRepository;
import pl.pietruszynski.loyaltyclub.api.admin.repository.TechnicalUserRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminUserDetailsService implements UserDetailsService {

    private final AdminUserRepository adminUserRepository;
    private final TechnicalUserRepository technicalUserRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AdminUser adminUser = adminUserRepository.findByUsername(username).orElse(null);
        if (adminUser != null) {
            return new User(
                    adminUser.getUsername(),
                    adminUser.getPassword(),
                    adminUser.isEnabled(),
                    true,
                    true,
                    true,
                    List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
            );
        }

        TechnicalUser technicalUser = technicalUserRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        return new User(
                technicalUser.getUsername(),
                technicalUser.getPassword(),
                technicalUser.isEnabled(),
                true,
                true,
                true,
                List.of(new SimpleGrantedAuthority("ROLE_TECHNICAL"))
        );
    }
}


