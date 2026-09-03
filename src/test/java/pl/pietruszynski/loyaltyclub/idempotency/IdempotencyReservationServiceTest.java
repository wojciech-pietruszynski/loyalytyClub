package pl.pietruszynski.loyaltyclub.idempotency;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Rezerwacje kluczy idempotencji.
 *
 * <p>Kluczowe zachowanie: kolizja klucza jest normalnym przebiegiem, a nie awaria.
 * Gdy rownolegle zadanie zdazylo zalozyc rezerwacje pierwsze, baza odrzuca zapis,
 * a serwis ma zwrocic pusty wynik -- na tej podstawie wywolujacy odpowiada
 * "zadanie w toku" zamiast wykonac operacje po raz drugi.
 */
@ExtendWith(MockitoExtension.class)
class IdempotencyReservationServiceTest {

    @Mock private IdempotentOperationRepository idempotentOperationRepository;

    @InjectMocks
    private IdempotencyReservationService reservationService;

    @Test
    void reserve_shouldStoreOperationKeyAndRequestFingerprint() {
        when(idempotentOperationRepository.saveAndFlush(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Optional<IdempotentOperation> reservation =
                reservationService.reserve("ADJUST_POINTS", "key-1", "fp-1");

        assertThat(reservation).isPresent();
        ArgumentCaptor<IdempotentOperation> captor = ArgumentCaptor.forClass(IdempotentOperation.class);
        verify(idempotentOperationRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getOperation()).isEqualTo("ADJUST_POINTS");
        assertThat(captor.getValue().getIdempotencyKey()).isEqualTo("key-1");
        assertThat(captor.getValue().getRequestFingerprint()).isEqualTo("fp-1");
    }

    /** Wyscig o ten sam klucz konczy sie pustym wynikiem, a nie wyjatkiem. */
    @Test
    void reserve_whenKeyAlreadyTaken_shouldReturnEmptyInsteadOfFailing() {
        when(idempotentOperationRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("uq_idempotent_operations_operation_key"));

        assertThat(reservationService.reserve("ADJUST_POINTS", "key-1", "fp-1")).isEmpty();
    }

    @Test
    void find_shouldLookUpByOperationAndKey() {
        IdempotentOperation existing = IdempotentOperation.builder()
                .id(1L)
                .operation("ADJUST_POINTS")
                .idempotencyKey("key-1")
                .requestFingerprint("fp-1")
                .build();
        when(idempotentOperationRepository.findByOperationAndIdempotencyKey("ADJUST_POINTS", "key-1"))
                .thenReturn(Optional.of(existing));

        assertThat(reservationService.find("ADJUST_POINTS", "key-1")).contains(existing);
    }

    @Test
    void complete_shouldStoreResultIdOnTheReservation() {
        IdempotentOperation reservation = IdempotentOperation.builder()
                .id(1L)
                .operation("ADJUST_POINTS")
                .idempotencyKey("key-1")
                .requestFingerprint("fp-1")
                .build();
        when(idempotentOperationRepository.findById(1L)).thenReturn(Optional.of(reservation));

        reservationService.complete(1L, 99L);

        assertThat(reservation.getResultId()).isEqualTo(99L);
        verify(idempotentOperationRepository).save(reservation);
    }

    /** Rezerwacja zwolniona w miedzyczasie nie moze wywrocic domkniecia operacji. */
    @Test
    void complete_forMissingReservation_shouldDoNothing() {
        when(idempotentOperationRepository.findById(1L)).thenReturn(Optional.empty());

        reservationService.complete(1L, 99L);

        verify(idempotentOperationRepository, never()).save(any());
    }

    /**
     * Zwolnienie rezerwacji po nieudanej operacji. Bez tego klucz zostalby na stale
     * w stanie "w toku" i blokowal ponowienie zadania po usunieciu przyczyny bledu.
     */
    @Test
    void release_shouldRemoveTheReservation() {
        reservationService.release(1L);

        verify(idempotentOperationRepository).deleteById(1L);
    }
}
