package pl.pietruszynski.loyaltyclub.api.admin.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;
import pl.pietruszynski.loyaltyclub.api.admin.model.AdminUser;
import pl.pietruszynski.loyaltyclub.api.admin.model.TechnicalUser;
import pl.pietruszynski.loyaltyclub.api.admin.repository.AdminUserRepository;
import pl.pietruszynski.loyaltyclub.api.admin.repository.TechnicalUserRepository;
import pl.pietruszynski.loyaltyclub.exception.BusinessException;
import pl.pietruszynski.loyaltyclub.exception.ResourceNotFoundException;
import pl.pietruszynski.loyaltyclub.security.TokenRevocationService;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Samoobslugowa zmiana hasla. Jedna metoda obsluguje dwa rodzaje kont, bo oba
 * loguja sie tym samym punktem koncowym panelu.
 *
 * <p>Istotne jest tu nie tylko podmienienie hasla, ale i uniewaznienie wydanych
 * wczesniej tokenow: bez tego token przejety przed zmiana hasla dzialalby
 * do konca swojej waznosci, czyli zmiana hasla nie odcinalaby napastnika.
 */
@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    private static final String NEW_PASSWORD = "NoweHaslo123";

    @Mock private AdminUserRepository adminUserRepository;
    @Mock private TechnicalUserRepository technicalUserRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private TokenRevocationService tokenRevocationService;

    @InjectMocks
    private AccountService accountService;

    @Test
    void changeOwnPassword_forAdminUser_shouldStoreEncodedPasswordAndStampChangeTime() {
        AdminUser admin = adminUser();
        when(adminUserRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("stare", "hash-stare")).thenReturn(true);
        when(passwordEncoder.encode(NEW_PASSWORD)).thenReturn("hash-nowe");

        accountService.changeOwnPassword("admin", "stare", NEW_PASSWORD);

        assertThat(admin.getPassword()).isEqualTo("hash-nowe");
        assertThat(admin.getPasswordChangedAt()).isNotNull();
        verify(adminUserRepository).save(admin);
    }

    /** Zmiana hasla odcina wszystkie wydane wczesniej tokeny, takze ten uzyty do zadania. */
    @Test
    void changeOwnPassword_shouldRevokeAllPreviouslyIssuedTokens() {
        AdminUser admin = adminUser();
        when(adminUserRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("stare", "hash-stare")).thenReturn(true);
        when(passwordEncoder.encode(NEW_PASSWORD)).thenReturn("hash-nowe");

        accountService.changeOwnPassword("admin", "stare", NEW_PASSWORD);

        verify(tokenRevocationService).revokeAllTokensFor("admin", "PASSWORD_CHANGED");
    }

    /** Konto techniczne obslugiwane jest dopiero wtedy, gdy nie ma konta panelu o tej nazwie. */
    @Test
    void changeOwnPassword_forTechnicalUser_shouldFallBackToTechnicalAccounts() {
        TechnicalUser technical = technicalUser();
        when(adminUserRepository.findByUsername("integracja")).thenReturn(Optional.empty());
        when(technicalUserRepository.findByUsername("integracja")).thenReturn(Optional.of(technical));
        when(passwordEncoder.matches("stare", "hash-stare")).thenReturn(true);
        when(passwordEncoder.encode(NEW_PASSWORD)).thenReturn("hash-nowe");

        accountService.changeOwnPassword("integracja", "stare", NEW_PASSWORD);

        assertThat(technical.getPassword()).isEqualTo("hash-nowe");
        assertThat(technical.getPasswordChangedAt()).isNotNull();
        verify(technicalUserRepository).save(technical);
        verify(tokenRevocationService).revokeAllTokensFor("integracja", "PASSWORD_CHANGED");
    }

    @Test
    void changeOwnPassword_withWrongCurrentPassword_shouldBeRejected() {
        when(adminUserRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser()));
        when(passwordEncoder.matches("zle", "hash-stare")).thenReturn(false);

        assertThatThrownBy(() -> accountService.changeOwnPassword("admin", "zle", NEW_PASSWORD))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Current password is incorrect");

        verify(adminUserRepository, never()).save(any());
        verifyNoInteractions(tokenRevocationService);
    }

    @Test
    void changeOwnPassword_forUnknownAccount_shouldReportNotFound() {
        when(adminUserRepository.findByUsername("nikt")).thenReturn(Optional.empty());
        when(technicalUserRepository.findByUsername("nikt")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.changeOwnPassword("nikt", "stare", NEW_PASSWORD))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("nikt");
    }

    /** Powtorzenie dotychczasowego hasla nie jest zmiana. */
    @Test
    void changeOwnPassword_withUnchangedPassword_shouldBeRejected() {
        assertThatThrownBy(() -> accountService.changeOwnPassword("admin", NEW_PASSWORD, NEW_PASSWORD))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("different");

        verifyNoInteractions(adminUserRepository, technicalUserRepository, tokenRevocationService);
    }

    /**
     * Polityka hasel sprawdzana jest przed czymkolwiek innym -- takze przed
     * odczytem konta, zeby nieudana proba nie ujawniala jego istnienia.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "krotkie1A",        // ponizej 12 znakow
            "bezwielkich123",   // brak wielkiej litery
            "BEZMALYCH123",     // brak malej litery
            "BezCyfrHaslo"      // brak cyfry
    })
    @MockitoSettings(strictness = Strictness.LENIENT)
    void changeOwnPassword_withWeakNewPassword_shouldBeRejectedBeforeTouchingRepositories(String weakPassword) {
        assertThatThrownBy(() -> accountService.changeOwnPassword("admin", "stare", weakPassword))
                .isInstanceOf(BusinessException.class);

        verifyNoInteractions(adminUserRepository, technicalUserRepository, tokenRevocationService);
    }

    private AdminUser adminUser() {
        return AdminUser.builder()
                .id(1L)
                .username("admin")
                .password("hash-stare")
                .enabled(true)
                .build();
    }

    private TechnicalUser technicalUser() {
        return TechnicalUser.builder()
                .id(2L)
                .username("integracja")
                .password("hash-stare")
                .enabled(true)
                .build();
    }
}
