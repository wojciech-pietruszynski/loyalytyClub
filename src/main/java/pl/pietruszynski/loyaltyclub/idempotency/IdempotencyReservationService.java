package pl.pietruszynski.loyaltyclub.idempotency;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Rezerwacje kluczy idempotencji prowadzone w osobnych transakcjach.
 *
 * <p>{@code REQUIRES_NEW} jest tu istotne z dwoch powodow. Po pierwsze naruszenie
 * ograniczenia jednoznacznosci -- normalna sciezka przy rownoleglym zadaniu z tym
 * samym kluczem -- uniewaznia sesje Hibernate; izolacja w osobnej transakcji
 * chroni transakcje wywolujacego. Po drugie rezerwacja musi byc widoczna dla
 * innych watkow, zanim wlasciwa operacja sie zakonczy, bo to ona daje odpowiedz
 * "zadanie w toku" zamiast drugiego wykonania.
 */
@Service
@RequiredArgsConstructor
public class IdempotencyReservationService {

    private final IdempotentOperationRepository idempotentOperationRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<IdempotentOperation> find(String operation, String idempotencyKey) {
        return idempotentOperationRepository.findByOperationAndIdempotencyKey(operation, idempotencyKey);
    }

    /** @return pusty wynik, gdy klucz zdazyl zarezerwowac ktos inny */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<IdempotentOperation> reserve(String operation, String idempotencyKey, String requestFingerprint) {
        try {
            return Optional.of(idempotentOperationRepository.saveAndFlush(IdempotentOperation.builder()
                    .operation(operation)
                    .idempotencyKey(idempotencyKey)
                    .requestFingerprint(requestFingerprint)
                    .build()));
        } catch (DataIntegrityViolationException ex) {
            return Optional.empty();
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(Long reservationId, Long resultId) {
        idempotentOperationRepository.findById(reservationId).ifPresent(reservation -> {
            reservation.setResultId(resultId);
            idempotentOperationRepository.save(reservation);
        });
    }

    /**
     * Zwalnia rezerwacje po nieudanej operacji. Bez tego klucz zostalby na stale
     * w stanie "w toku" i uniemozliwil ponowienie zadania po naprawieniu przyczyny.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void release(Long reservationId) {
        idempotentOperationRepository.deleteById(reservationId);
    }
}
