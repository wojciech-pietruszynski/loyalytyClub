package pl.pietruszynski.loyaltyclub.idempotency;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import pl.pietruszynski.loyaltyclub.PersistenceTest;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Ochrona przed podwojnym wykonaniem operacji punktowej opiera sie na ograniczeniu
 * bazy, a nie na sprawdzeniu w kodzie: dwa rownolegle watki moga jednoczesnie nie
 * znalezc rezerwacji i oba probowac ja zalozyc. Test sprawdza, ze to zalozenie
 * faktycznie trzyma schemat.
 */
@PersistenceTest
class IdempotentOperationRepositoryTest {

    @Autowired private TestEntityManager entityManager;
    @Autowired private IdempotentOperationRepository idempotentOperationRepository;

    @Test
    void sameOperationAndKey_shouldBeRejectedByDatabase() {
        idempotentOperationRepository.saveAndFlush(reservation("ADJUST_POINTS", "key-1", "fp-1"));

        assertThatThrownBy(() -> idempotentOperationRepository
                .saveAndFlush(reservation("ADJUST_POINTS", "key-1", "fp-2")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /** Klucz jest jednoznaczny w obrebie nazwy operacji, a nie globalnie. */
    @Test
    void sameKeyUnderDifferentOperation_shouldBeAllowed() {
        idempotentOperationRepository.saveAndFlush(reservation("ADJUST_POINTS", "key-1", "fp-1"));
        idempotentOperationRepository.saveAndFlush(reservation("REDEEM_COUPON", "key-1", "fp-1"));

        assertThat(idempotentOperationRepository.count()).isEqualTo(2);
    }

    @Test
    void findByOperationAndIdempotencyKey_shouldNotMatchAcrossOperations() {
        idempotentOperationRepository.saveAndFlush(reservation("ADJUST_POINTS", "key-1", "fp-1"));

        assertThat(idempotentOperationRepository
                .findByOperationAndIdempotencyKey("ADJUST_POINTS", "key-1")).isPresent();
        assertThat(idempotentOperationRepository
                .findByOperationAndIdempotencyKey("REDEEM_COUPON", "key-1")).isEmpty();
    }

    @Test
    void createdAt_shouldBeSetOnPersistWhenNotGiven() {
        IdempotentOperation saved =
                idempotentOperationRepository.saveAndFlush(reservation("ADJUST_POINTS", "key-1", "fp-1"));

        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void deleteCreatedBefore_shouldRemoveOnlyReservationsOlderThanCutoff() {
        LocalDateTime now = LocalDateTime.now();
        persistWithCreatedAt("ADJUST_POINTS", "old", now.minusDays(40));
        persistWithCreatedAt("ADJUST_POINTS", "fresh", now.minusDays(2));

        int removed = idempotentOperationRepository.deleteCreatedBefore(now.minusDays(30));
        entityManager.clear();

        assertThat(removed).isEqualTo(1);
        assertThat(idempotentOperationRepository.findByOperationAndIdempotencyKey("ADJUST_POINTS", "fresh"))
                .isPresent();
        assertThat(idempotentOperationRepository.findByOperationAndIdempotencyKey("ADJUST_POINTS", "old"))
                .isEmpty();
    }

    @Test
    void deleteCreatedBefore_shouldReportZeroWhenNothingToPurge() {
        persistWithCreatedAt("ADJUST_POINTS", "fresh", LocalDateTime.now());

        assertThat(idempotentOperationRepository.deleteCreatedBefore(LocalDateTime.now().minusDays(30))).isZero();
    }

    private void persistWithCreatedAt(String operation, String key, LocalDateTime createdAt) {
        IdempotentOperation reservation = reservation(operation, key, "fp");
        reservation.setCreatedAt(createdAt);
        entityManager.persistAndFlush(reservation);
    }

    private IdempotentOperation reservation(String operation, String key, String fingerprint) {
        return IdempotentOperation.builder()
                .operation(operation)
                .idempotencyKey(key)
                .requestFingerprint(fingerprint)
                .build();
    }
}
