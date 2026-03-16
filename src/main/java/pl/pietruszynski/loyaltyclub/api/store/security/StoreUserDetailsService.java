package pl.pietruszynski.loyaltyclub.api.store.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import pl.pietruszynski.loyaltyclub.api.store.model.StoreUser;
import pl.pietruszynski.loyaltyclub.api.store.repository.StoreUserRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StoreUserDetailsService implements UserDetailsService {

    private final StoreUserRepository storeUserRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        StoreUser user = storeUserRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Store user not found: " + username));

        return new User(
                user.getUsername(),
                user.getPassword(),
                user.isEnabled(),
                true,
                true,
                true,
                List.of(new SimpleGrantedAuthority("ROLE_STORE"))
        );
    }
}


