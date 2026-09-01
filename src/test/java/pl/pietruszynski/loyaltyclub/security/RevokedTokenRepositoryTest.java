package pl.pietruszynski.loyaltyclub.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import pl.pietruszynski.loyaltyclub.PersistenceTest;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lista uniewaznionych tokenow. Wpis mozna usunac dopiero wtedy, gdy sam token
 * juz wygasl -- skasowany za wczesnie przywrocilby waznosc tokenowi, ktory
 * zostal odwolany wylogowaniem.
 */
@PersistenceTest
class RevokedTokenRepositoryTest {

    @Autowired private TestEntityManager entityManager;
    @Autowired private RevokedTokenRepository revokedTokenRepository;

    @Test
    void deleteExpired_shouldRemoveOnlyTokensPastTheirOwnExpiry() {
        LocalDateTime now = LocalDateTime.now();
        persistRevocation("jti-expired", now.minusMinutes(1));
        persistRevocation("jti-active", now.plusMinutes(15));

        int removed = revokedTokenRepository.deleteExpired(now);
        entityManager.clear();

        assertThat(removed).isEqualTo(1);
        assertThat(revokedTokenRepository.existsByTokenId("jti-active")).isTrue();
        assertThat(revokedTokenRepository.existsByTokenId("jti-expired")).isFalse();
    }

    /** Token wygasajacy dokladnie teraz jeszcze zostaje -- warunek jest ostry. */
    @Test
    void deleteExpired_shouldKeepTokenExpiringExactlyNow() {
        // Obciecie, bo kolumna TIMESTAMP ma mniejsza rozdzielczosc niz LocalDateTime.
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);
        persistRevocation("jti-border", now);

        assertThat(revokedTokenRepository.deleteExpired(now)).isZero();
    }

    @Test
    void tokenId_shouldBeUnique() {
        LocalDateTime now = LocalDateTime.now();
        persistRevocation("jti-1", now.plusMinutes(15));

        assertThatThrownBy(() -> revokedTokenRepository.saveAndFlush(RevokedToken.builder()
                .tokenId("jti-1")
                .username("admin")
                .expiresAt(now.plusMinutes(15))
                .build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void revokedAt_shouldBeSetOnPersistWhenNotGiven() {
        RevokedToken saved = revokedTokenRepository.saveAndFlush(RevokedToken.builder()
                .tokenId("jti-1")
                .username("admin")
                .expiresAt(LocalDateTime.now().plusMinutes(15))
                .build());

        assertThat(saved.getRevokedAt()).isNotNull();
    }

    private void persistRevocation(String tokenId, LocalDateTime expiresAt) {
        entityManager.persistAndFlush(RevokedToken.builder()
                .tokenId(tokenId)
                .username("admin")
                .expiresAt(expiresAt)
                .build());
    }
}
