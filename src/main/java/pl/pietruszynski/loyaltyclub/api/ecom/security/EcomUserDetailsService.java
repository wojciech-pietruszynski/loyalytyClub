package pl.pietruszynski.loyaltyclub.api.ecom.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import pl.pietruszynski.loyaltyclub.api.ecom.model.EcomUser;
import pl.pietruszynski.loyaltyclub.api.ecom.repository.EcomUserRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EcomUserDetailsService implements UserDetailsService {

    private final EcomUserRepository ecomUserRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        EcomUser user = ecomUserRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Ecom user not found: " + username));

        return new User(
                user.getUsername(),
                user.getPassword(),
                user.isEnabled(),
                true,
                true,
                true,
                List.of(new SimpleGrantedAuthority("ROLE_ECOM"))
        );
    }
}


