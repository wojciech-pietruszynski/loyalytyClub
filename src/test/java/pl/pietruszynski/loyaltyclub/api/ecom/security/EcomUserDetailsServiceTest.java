package pl.pietruszynski.loyaltyclub.api.ecom.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import pl.pietruszynski.loyaltyclub.api.ecom.model.EcomUser;
import pl.pietruszynski.loyaltyclub.api.ecom.repository.EcomUserRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EcomUserDetailsServiceTest {

    @Mock private EcomUserRepository ecomUserRepository;

    @InjectMocks
    private EcomUserDetailsService ecomUserDetailsService;

    @Test
    void loadUserByUsername_existingUser_shouldReturnEcomRole() {
        EcomUser user = EcomUser.builder()
                .username("ecom01")
                .password("encoded")
                .enabled(true)
                .build();

        when(ecomUserRepository.findByUsername("ecom01")).thenReturn(Optional.of(user));

        UserDetails userDetails = ecomUserDetailsService.loadUserByUsername("ecom01");

        assertThat(userDetails.getUsername()).isEqualTo("ecom01");
        assertThat(userDetails.getAuthorities()).anyMatch(a -> a.getAuthority().equals("ROLE_ECOM"));
    }

    @Test
    void loadUserByUsername_notFound_shouldThrow() {
        when(ecomUserRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ecomUserDetailsService.loadUserByUsername("ghost"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("ghost");
    }
}
