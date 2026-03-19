package pl.pietruszynski.loyaltyclub.api.store.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import pl.pietruszynski.loyaltyclub.api.store.model.StoreUser;
import pl.pietruszynski.loyaltyclub.api.store.repository.StoreUserRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StoreUserDetailsServiceTest {

    @Mock private StoreUserRepository storeUserRepository;

    @InjectMocks
    private StoreUserDetailsService storeUserDetailsService;

    @Test
    void loadUserByUsername_existingUser_shouldReturnStoreRole() {
        StoreUser user = StoreUser.builder()
                .username("store01")
                .password("encoded")
                .enabled(true)
                .build();

        when(storeUserRepository.findByUsername("store01")).thenReturn(Optional.of(user));

        UserDetails userDetails = storeUserDetailsService.loadUserByUsername("store01");

        assertThat(userDetails.getUsername()).isEqualTo("store01");
        assertThat(userDetails.getAuthorities()).anyMatch(a -> a.getAuthority().equals("ROLE_STORE"));
    }

    @Test
    void loadUserByUsername_notFound_shouldThrow() {
        when(storeUserRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> storeUserDetailsService.loadUserByUsername("ghost"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("ghost");
    }
}
