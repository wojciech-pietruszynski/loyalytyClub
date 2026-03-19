package pl.pietruszynski.loyaltyclub.api.admin.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import pl.pietruszynski.loyaltyclub.api.admin.dto.TechnicalUserCreateRequest;
import pl.pietruszynski.loyaltyclub.api.admin.model.TechnicalUser;
import pl.pietruszynski.loyaltyclub.api.admin.repository.TechnicalUserRepository;
import pl.pietruszynski.loyaltyclub.exception.ResourceNotFoundException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TechnicalUserServiceTest {

    @Mock private TechnicalUserRepository technicalUserRepository;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks
    private TechnicalUserService technicalUserService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(technicalUserService, "availableCountryCodesConfig", "PL,DE");
    }

    // -----------------------------------------------------------------------
    // getAllTechnicalUsers
    // -----------------------------------------------------------------------

    @Test
    void getAllTechnicalUsers_shouldReturnSortedByUsername() {
        TechnicalUser b = techUser("beta", "PL");
        TechnicalUser a = techUser("alpha", "PL");
        when(technicalUserRepository.findAll()).thenReturn(List.of(b, a));

        List<TechnicalUser> result = technicalUserService.getAllTechnicalUsers();

        assertThat(result).extracting(TechnicalUser::getUsername).containsExactly("alpha", "beta");
    }

    // -----------------------------------------------------------------------
    // createTechnicalUser
    // -----------------------------------------------------------------------

    @Test
    void createTechnicalUser_valid_shouldEncodePasswordAndSave() {
        TechnicalUserCreateRequest req = new TechnicalUserCreateRequest("techpl", "secret", "PL", true);
        when(technicalUserRepository.existsByUsername("techpl")).thenReturn(false);
        when(passwordEncoder.encode("secret")).thenReturn("encoded-secret");
        when(technicalUserRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TechnicalUser result = technicalUserService.createTechnicalUser(req);

        assertThat(result.getUsername()).isEqualTo("techpl");
        assertThat(result.getPassword()).isEqualTo("encoded-secret");
        assertThat(result.getPasswordPreview()).isEqualTo("secret");
        assertThat(result.getCountry()).isEqualTo("PL");
        assertThat(result.isEnabled()).isTrue();
    }

    @Test
    void createTechnicalUser_defaultEnabled_whenEnabledIsNull() {
        TechnicalUserCreateRequest req = new TechnicalUserCreateRequest("techpl", "secret", "PL", null);
        when(technicalUserRepository.existsByUsername("techpl")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("enc");
        when(technicalUserRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TechnicalUser result = technicalUserService.createTechnicalUser(req);

        assertThat(result.isEnabled()).isTrue();
    }

    @Test
    void createTechnicalUser_disallowedCountry_shouldThrow() {
        TechnicalUserCreateRequest req = new TechnicalUserCreateRequest("techfr", "secret", "FR", true);

        assertThatThrownBy(() -> technicalUserService.createTechnicalUser(req))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Country code is not allowed");
    }

    @Test
    void createTechnicalUser_duplicateUsername_shouldThrow() {
        TechnicalUserCreateRequest req = new TechnicalUserCreateRequest("existing", "secret", "PL", true);
        when(technicalUserRepository.existsByUsername("existing")).thenReturn(true);

        assertThatThrownBy(() -> technicalUserService.createTechnicalUser(req))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void createTechnicalUser_blankUsername_shouldThrow() {
        TechnicalUserCreateRequest req = new TechnicalUserCreateRequest("", "secret", "PL", true);

        assertThatThrownBy(() -> technicalUserService.createTechnicalUser(req))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("required");
    }

    @Test
    void createTechnicalUser_blankPassword_shouldThrow() {
        TechnicalUserCreateRequest req = new TechnicalUserCreateRequest("techpl", "", "PL", true);

        assertThatThrownBy(() -> technicalUserService.createTechnicalUser(req))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("required");
    }

    @Test
    void createTechnicalUser_countryNormalized_shouldSaveUppercased() {
        TechnicalUserCreateRequest req = new TechnicalUserCreateRequest("techpl", "secret", "pl", true);
        when(technicalUserRepository.existsByUsername("techpl")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("enc");
        when(technicalUserRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TechnicalUser result = technicalUserService.createTechnicalUser(req);

        assertThat(result.getCountry()).isEqualTo("PL");
    }

    // -----------------------------------------------------------------------
    // setTechnicalUserEnabled
    // -----------------------------------------------------------------------

    @Test
    void setTechnicalUserEnabled_existing_shouldUpdateAndSave() {
        TechnicalUser user = techUser("tech", "PL");
        user.setId(1L);
        user.setEnabled(true);

        when(technicalUserRepository.findById(1L)).thenReturn(Optional.of(user));
        when(technicalUserRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TechnicalUser result = technicalUserService.setTechnicalUserEnabled(1L, false);

        assertThat(result.isEnabled()).isFalse();
    }

    @Test
    void setTechnicalUserEnabled_missing_shouldThrowResourceNotFound() {
        when(technicalUserRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> technicalUserService.setTechnicalUserEnabled(99L, true))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // -----------------------------------------------------------------------
    // updateTechnicalUserPassword
    // -----------------------------------------------------------------------

    @Test
    void updateTechnicalUserPassword_valid_shouldEncodeAndSave() {
        TechnicalUser user = techUser("tech", "PL");
        user.setId(1L);

        when(technicalUserRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("newpass")).thenReturn("encoded-newpass");
        when(technicalUserRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TechnicalUser result = technicalUserService.updateTechnicalUserPassword(1L, "newpass");

        assertThat(result.getPassword()).isEqualTo("encoded-newpass");
        assertThat(result.getPasswordPreview()).isEqualTo("newpass");
    }

    @Test
    void updateTechnicalUserPassword_blank_shouldThrow() {
        assertThatThrownBy(() -> technicalUserService.updateTechnicalUserPassword(1L, ""))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Password is required");
    }

    @Test
    void updateTechnicalUserPassword_missing_shouldThrowResourceNotFound() {
        when(technicalUserRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> technicalUserService.updateTechnicalUserPassword(99L, "pass"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // -----------------------------------------------------------------------
    // resolveTechnicalUserCountry
    // -----------------------------------------------------------------------

    @Test
    void resolveTechnicalUserCountry_enabledUser_shouldReturnNormalizedCountry() {
        TechnicalUser user = techUser("tech", "pl");
        user.setEnabled(true);

        when(technicalUserRepository.findByUsername("tech")).thenReturn(Optional.of(user));

        String country = technicalUserService.resolveTechnicalUserCountry("tech");

        assertThat(country).isEqualTo("PL");
    }

    @Test
    void resolveTechnicalUserCountry_disabledUser_shouldThrow() {
        TechnicalUser user = techUser("tech", "PL");
        user.setEnabled(false);

        when(technicalUserRepository.findByUsername("tech")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> technicalUserService.resolveTechnicalUserCountry("tech"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("disabled");
    }

    @Test
    void resolveTechnicalUserCountry_missing_shouldThrowResourceNotFound() {
        when(technicalUserRepository.findByUsername("nobody")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> technicalUserService.resolveTechnicalUserCountry("nobody"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private TechnicalUser techUser(String username, String country) {
        return TechnicalUser.builder()
                .username(username)
                .password("encoded")
                .passwordPreview("plain")
                .country(country)
                .enabled(true)
                .build();
    }
}
