package pl.pietruszynski.loyaltyclub.idempotency;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.pietruszynski.loyaltyclub.exception.BusinessException;
import pl.pietruszynski.loyaltyclub.idempotency.IdempotencyService.IdempotentResult;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Ochrona przed podwojnym wykonaniem operacji zmieniajacej saldo. Reczna korekta
 * punktow nie ma numeru dokumentu kasowego, na ktorym opiera sie czesciowy indeks
 * unikalny z migracji 007 -- klucz idempotencji jest dla niej jedynym zabezpieczeniem.
 */
@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    private static final String OPERATION = "ADD_POINTS";

    @Mock private IdempotencyReservationService reservationService;
    @Mock private IdempotentOperationRepository idempotentOperationRepository;

    @InjectMocks
    private IdempotencyService idempotencyService;

    @Test
    void execute_freshKey_shouldRunActionAndRecordResult() {
        when(reservationService.find(OPERATION, "key-1")).thenReturn(Optional.empty());
        when(reservationService.reserve(eq(OPERATION), eq("key-1"), anyString()))
                .thenReturn(Optional.of(reservation(7L, null)));

        String result = idempotencyService.execute(OPERATION, "key-1", "1|50|bonus",
                () -> new IdempotentResult<>(42L, "created"),
                resultId -> "replayed");

        assertThat(result).isEqualTo("created");
        verify(reservationService).complete(7L, 42L);
    }

    @Test
    void execute_repeatedKeySamePayload_shouldReplayWithoutRunningAction() {
        AtomicInteger invocations = new AtomicInteger();
        when(reservationService.find(OPERATION, "key-1"))
                .thenReturn(Optional.of(reservation(7L, 42L, fingerprintOf("1|50|bonus"))));

        String result = idempotencyService.execute(OPERATION, "key-1", "1|50|bonus",
                () -> {
                    invocations.incrementAndGet();
                    return new IdempotentResult<>(99L, "created-again");
                },
                resultId -> "replayed-" + resultId);

        assertThat(result).isEqualTo("replayed-42");
        assertThat(invocations).hasValue(0);
        verify(reservationService, never()).reserve(anyString(), anyString(), anyString());
    }

    @Test
    void execute_repeatedKeyDifferentPayload_shouldReject() {
        when(reservationService.find(OPERATION, "key-1"))
                .thenReturn(Optional.of(reservation(7L, 42L, fingerprintOf("1|50|bonus"))));

        assertThatThrownBy(() -> idempotencyService.execute(OPERATION, "key-1", "1|9999|inne",
                () -> new IdempotentResult<>(1L, "x"),
                resultId -> "replayed"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("different request payload");
    }

    /** Rownolegle zadanie zdazylo zarezerwowac klucz, ale jeszcze nie skonczylo pracy. */
    @Test
    void execute_requestInProgress_shouldReject() {
        when(reservationService.find(OPERATION, "key-1"))
                .thenReturn(Optional.of(reservation(7L, null, fingerprintOf("1|50|bonus"))));

        assertThatThrownBy(() -> idempotencyService.execute(OPERATION, "key-1", "1|50|bonus",
                () -> new IdempotentResult<>(1L, "x"),
                resultId -> "replayed"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("in progress");
    }

    /** Przegrana o rezerwacje oznacza, ze operacje wykonuje juz ktos inny. */
    @Test
    void execute_lostReservationRace_shouldReplayWinnerResult() {
        when(reservationService.find(OPERATION, "key-1"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(reservation(7L, 42L, fingerprintOf("1|50|bonus"))));
        when(reservationService.reserve(eq(OPERATION), eq("key-1"), anyString())).thenReturn(Optional.empty());

        String result = idempotencyService.execute(OPERATION, "key-1", "1|50|bonus",
                () -> new IdempotentResult<>(1L, "created"),
                resultId -> "replayed-" + resultId);

        assertThat(result).isEqualTo("replayed-42");
    }

    /**
     * Nieudana operacja musi zwolnic klucz -- inaczej po naprawieniu przyczyny
     * nie dalo by sie ponowic zadania z tym samym kluczem.
     */
    @Test
    void execute_failingAction_shouldReleaseReservation() {
        when(reservationService.find(OPERATION, "key-1")).thenReturn(Optional.empty());
        when(reservationService.reserve(eq(OPERATION), eq("key-1"), anyString()))
                .thenReturn(Optional.of(reservation(7L, null)));

        assertThatThrownBy(() -> idempotencyService.execute(OPERATION, "key-1", "1|50|bonus",
                () -> {
                    throw new IllegalStateException("boom");
                },
                resultId -> "replayed"))
                .isInstanceOf(IllegalStateException.class);

        verify(reservationService).release(7L);
        verify(reservationService, never()).complete(any(), any());
    }

    @Test
    void execute_missingKey_shouldReject() {
        assertThatThrownBy(() -> idempotencyService.execute(OPERATION, "  ", "1|50|bonus",
                () -> new IdempotentResult<>(1L, "x"),
                resultId -> "replayed"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Idempotency-Key header is required");
    }

    @Test
    void execute_overlongKey_shouldReject() {
        assertThatThrownBy(() -> idempotencyService.execute(OPERATION, "k".repeat(101), "1|50|bonus",
                () -> new IdempotentResult<>(1L, "x"),
                resultId -> "replayed"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("at most 100 characters");
    }

    private IdempotentOperation reservation(Long id, Long resultId) {
        return reservation(id, resultId, "irrelevant");
    }

    private IdempotentOperation reservation(Long id, Long resultId, String fingerprint) {
        IdempotentOperation operation = IdempotentOperation.builder()
                .operation(OPERATION)
                .idempotencyKey("key-1")
                .requestFingerprint(fingerprint)
                .resultId(resultId)
                .build();
        operation.setId(id);
        return operation;
    }

    /** Odwzorowanie skrotu liczonego przez serwis, zeby test nie zalezal od jego wnetrza. */
    private String fingerprintOf(String canonicalRequest) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of()
                    .formatHex(digest.digest(canonicalRequest.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
