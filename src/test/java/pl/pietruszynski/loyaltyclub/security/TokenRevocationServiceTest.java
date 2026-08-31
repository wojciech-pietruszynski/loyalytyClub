package pl.pietruszynski.loyaltyclub.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Token JWT jest bezstanowy, wiec bez stanu po stronie serwera skradziony token
 * dziala do konca okresu waznosci i nie da sie go uniewaznic.
 */
@ExtendWith(MockitoExtension.class)
class TokenRevocationServiceTest {

    @Mock private RevokedTokenRepository revokedTokenRepository;
    @Mock private UserTokenCutoffRepository userTokenCutoffRepository;

    @InjectMocks
    private TokenRevocationService tokenRevocationService;

    @Test
    void revokeToken_shouldStoreTokenId() {
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(15);
        when(revokedTokenRepository.existsByTokenId("jti-1")).thenReturn(false);

        tokenRevocationService.revokeToken("jti-1", "admin", expiresAt);

        ArgumentCaptor<RevokedToken> captor = ArgumentCaptor.forClass(RevokedToken.class);
        verify(revokedTokenRepository).save(captor.capture());
        assertThat(captor.getValue().getTokenId()).isEqualTo("jti-1");
        assertThat(captor.getValue().getUsername()).isEqualTo("admin");
    }

    /** Powtorne wylogowanie tym samym tokenem nie jest bledem. */
    @Test
    void revokeToken_alreadyRevoked_shouldBeNoOp() {
        when(revokedTokenRepository.existsByTokenId("jti-1")).thenReturn(true);

        tokenRevocationService.revokeToken("jti-1", "admin", LocalDateTime.now().plusMinutes(15));

        verify(revokedTokenRepository, never()).save(any());
    }

    @Test
    void isRevoked_revokedTokenId_shouldReturnTrue() {
        when(revokedTokenRepository.existsByTokenId("jti-1")).thenReturn(true);

        assertThat(tokenRevocationService.isRevoked("jti-1", "admin", LocalDateTime.now())).isTrue();
    }

    /** Zmiana hasla uniewaznia wszystkie tokeny wydane wczesniej. */
    @Test
    void isRevoked_tokenIssuedBeforeCutoff_shouldReturnTrue() {
        LocalDateTime cutoff = LocalDateTime.now();
        when(revokedTokenRepository.existsByTokenId("jti-2")).thenReturn(false);
        when(userTokenCutoffRepository.findByUsername("admin")).thenReturn(Optional.of(
                UserTokenCutoff.builder().username("admin").notBefore(cutoff).reason("PASSWORD_CHANGED").build()));

        assertThat(tokenRevocationService.isRevoked("jti-2", "admin", cutoff.minusMinutes(1))).isTrue();
    }

    @Test
    void isRevoked_tokenIssuedAfterCutoff_shouldReturnFalse() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(1);
        when(revokedTokenRepository.existsByTokenId("jti-3")).thenReturn(false);
        when(userTokenCutoffRepository.findByUsername("admin")).thenReturn(Optional.of(
                UserTokenCutoff.builder().username("admin").notBefore(cutoff).reason("PASSWORD_CHANGED").build()));

        assertThat(tokenRevocationService.isRevoked("jti-3", "admin", LocalDateTime.now())).isFalse();
    }

    @Test
    void isRevoked_noRevocationRecorded_shouldReturnFalse() {
        when(revokedTokenRepository.existsByTokenId("jti-4")).thenReturn(false);
        when(userTokenCutoffRepository.findByUsername("admin")).thenReturn(Optional.empty());

        assertThat(tokenRevocationService.isRevoked("jti-4", "admin", LocalDateTime.now())).isFalse();
    }

    @Test
    void revokeAllTokensFor_shouldReuseExistingCutoffRow() {
        UserTokenCutoff existing = UserTokenCutoff.builder()
                .username("admin")
                .notBefore(LocalDateTime.now().minusDays(1))
                .reason("PASSWORD_CHANGED")
                .build();
        when(userTokenCutoffRepository.findByUsername("admin")).thenReturn(Optional.of(existing));

        tokenRevocationService.revokeAllTokensFor("admin", "ACCOUNT_DISABLED");

        assertThat(existing.getReason()).isEqualTo("ACCOUNT_DISABLED");
        assertThat(existing.getNotBefore()).isAfter(LocalDateTime.now().minusMinutes(1));
        verify(userTokenCutoffRepository).save(existing);
    }
}
