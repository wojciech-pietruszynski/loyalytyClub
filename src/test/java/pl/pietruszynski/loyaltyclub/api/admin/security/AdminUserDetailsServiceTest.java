package pl.pietruszynski.loyaltyclub.api.admin.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import pl.pietruszynski.loyaltyclub.api.admin.model.AdminUser;
import pl.pietruszynski.loyaltyclub.api.admin.model.TechnicalUser;
import pl.pietruszynski.loyaltyclub.api.admin.repository.AdminUserRepository;
import pl.pietruszynski.loyaltyclub.api.admin.repository.TechnicalUserRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminUserDetailsServiceTest {

    @Mock private AdminUserRepository adminUserRepository;
    @Mock private TechnicalUserRepository technicalUserRepository;

    @InjectMocks
    private AdminUserDetailsService adminUserDetailsService;

    @Test
    void loadUserByUsername_adminUser_shouldReturnAdminRole() {
        AdminUser admin = AdminUser.builder()
                .username("admin")
                .password("encoded")
                .enabled(true)
                .build();

        when(adminUserRepository.findByUsername("admin")).thenReturn(Optional.of(admin));

        UserDetails userDetails = adminUserDetailsService.loadUserByUsername("admin");

        assertThat(userDetails.getUsername()).isEqualTo("admin");
        assertThat(userDetails.getAuthorities()).anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    @Test
    void loadUserByUsername_technicalUser_shouldReturnTechnicalRole() {
        when(adminUserRepository.findByUsername("techpl")).thenReturn(Optional.empty());

        TechnicalUser tech = TechnicalUser.builder()
                .username("techpl")
                .password("encoded")
                .country("PL")
                .enabled(true)
                .build();

        when(technicalUserRepository.findByUsername("techpl")).thenReturn(Optional.of(tech));

        UserDetails userDetails = adminUserDetailsService.loadUserByUsername("techpl");

        assertThat(userDetails.getUsername()).isEqualTo("techpl");
        assertThat(userDetails.getAuthorities()).anyMatch(a -> a.getAuthority().equals("ROLE_TECHNICAL"));
    }

    @Test
    void loadUserByUsername_notFound_shouldThrow() {
        when(adminUserRepository.findByUsername("ghost")).thenReturn(Optional.empty());
        when(technicalUserRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminUserDetailsService.loadUserByUsername("ghost"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("ghost");
    }
}
