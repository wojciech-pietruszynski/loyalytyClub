package pl.pietruszynski.loyaltyclub.security;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Uniewaznianie tokenow JWT. Token jest bezstanowy, wiec sam z siebie pozostaje
 * wazny do konca okresu waznosci -- do jego wycofania potrzebny jest stan po
 * stronie serwera. Utrzymujemy go w dwoch postaciach:
 *
 * <ul>
 *   <li>pojedynczy token wycofany wylogowaniem ({@code jti}),</li>
 *   <li>granica czasowa dla konta, uniewazniajaca wszystkie wczesniej wydane tokeny.</li>
 * </ul>
 *
 * <p>Oba zbiory sa male, a sprawdzenie sprowadza sie do dwoch odczytow po kluczu,
 * wiec koszt jest nieporownanie mniejszy niz przy sesji serwerowej.
 */
@Service
@RequiredArgsConstructor
public class TokenRevocationService {

    private final RevokedTokenRepository revokedTokenRepository;
    private final UserTokenCutoffRepository userTokenCutoffRepository;

    @Transactional
    public void revokeToken(String tokenId, String username, LocalDateTime expiresAt) {
        if (tokenId == null || tokenId.isBlank() || revokedTokenRepository.existsByTokenId(tokenId)) {
            return;
        }
        try {
            revokedTokenRepository.save(RevokedToken.builder()
                    .tokenId(tokenId)
                    .username(username)
                    .expiresAt(expiresAt)
                    .build());
        } catch (DataIntegrityViolationException ex) {
            // Rownolegle wylogowanie tym samym tokenem -- stan docelowy juz osiagniety.
        }
    }

    /**
     * Uniewaznia wszystkie tokeny konta wydane przed chwila obecna. Dolacza do
     * transakcji wywolujacego -- wycofanie sesji ma sens tylko razem ze zmiana,
     * ktora je wywolala (zmiana hasla, dezaktywacja konta).
     */
    @Transactional
    public void revokeAllTokensFor(String username, String reason) {
        UserTokenCutoff cutoff = userTokenCutoffRepository.findByUsername(username)
                .orElseGet(() -> UserTokenCutoff.builder().username(username).build());
        cutoff.setNotBefore(LocalDateTime.now());
        cutoff.setReason(reason);
        userTokenCutoffRepository.save(cutoff);
    }

    @Transactional(readOnly = true)
    public boolean isRevoked(String tokenId, String username, LocalDateTime issuedAt) {
        if (tokenId != null && !tokenId.isBlank() && revokedTokenRepository.existsByTokenId(tokenId)) {
            return true;
        }
        if (issuedAt == null) {
            return false;
        }
        return userTokenCutoffRepository.findByUsername(username)
                .map(cutoff -> issuedAt.isBefore(cutoff.getNotBefore()))
                .orElse(false);
    }

    /** Wpisy przestaja byc potrzebne, gdy token i tak wygasl. */
    @Transactional
    public int purgeExpired() {
        return revokedTokenRepository.deleteExpired(LocalDateTime.now());
    }
}
