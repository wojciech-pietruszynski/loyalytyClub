package pl.pietruszynski.loyaltyclub.idempotency;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.pietruszynski.loyaltyclub.exception.BusinessException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.function.LongFunction;
import java.util.function.Supplier;

/**
 * Wykonuje operacje dokladnie raz dla danego klucza idempotencji.
 *
 * <p>Schemat jest ten sam, ktory sprawdzil sie przy wymianie punktow na kupon
 * (migracja 008): najpierw rezerwujemy klucz osobnym wierszem, opierajac sie na
 * ograniczeniu jednoznacznosci w bazie zamiast na sprawdzeniu "czy istnieje"
 * (to ostatnie przegrywa z rownoleglym zadaniem), a dopiero potem wykonujemy
 * wlasciwa prace. W odroznieniu od tamtego rozwiazania mechanizm nie jest zwiazany
 * z jednym przypadkiem uzycia -- klucze sa unikalne w obrebie nazwy operacji.
 */
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private static final int MAX_KEY_LENGTH = 100;

    private final IdempotencyReservationService reservationService;
    private final IdempotentOperationRepository idempotentOperationRepository;

    /**
     * @param operation   nazwa operacji, np. {@code ADD_POINTS}; klucze sa unikalne w jej obrebie
     * @param key         wartosc naglowka {@code Idempotency-Key}
     * @param canonicalRequest kanoniczna postac tresci zadania, np. {@code "12|500|korekta"}
     * @param action      wlasciwa operacja; zwraca identyfikator utrwalonego wyniku wraz z odpowiedzia
     * @param replay      odtworzenie odpowiedzi na podstawie zapisanego identyfikatora wyniku
     */
    public <T> T execute(String operation,
                         String key,
                         String canonicalRequest,
                         Supplier<IdempotentResult<T>> action,
                         LongFunction<T> replay) {

        String normalizedKey = normalizeKey(key);
        String fingerprint = fingerprint(canonicalRequest);

        IdempotentOperation existing = reservationService.find(operation, normalizedKey).orElse(null);
        if (existing != null) {
            return replay(existing, fingerprint, replay);
        }

        IdempotentOperation reservation = reservationService.reserve(operation, normalizedKey, fingerprint)
                .orElse(null);
        if (reservation == null) {
            IdempotentOperation raced = reservationService.find(operation, normalizedKey)
                    .orElseThrow(() -> new BusinessException("Idempotency state is inconsistent"));
            return replay(raced, fingerprint, replay);
        }

        IdempotentResult<T> result;
        try {
            result = action.get();
        } catch (RuntimeException ex) {
            reservationService.release(reservation.getId());
            throw ex;
        }

        reservationService.complete(reservation.getId(), result.resultId());
        return result.value();
    }

    /** Usuwa zuzyte rezerwacje; wywolywane przez zadanie porzadkowe. */
    @Transactional
    public int purgeOlderThan(LocalDateTime before) {
        return idempotentOperationRepository.deleteCreatedBefore(before);
    }

    private <T> T replay(IdempotentOperation existing, String fingerprint, LongFunction<T> replay) {
        if (!existing.getRequestFingerprint().equals(fingerprint)) {
            throw new BusinessException("Idempotency-Key already used with a different request payload");
        }
        if (existing.getResultId() == null) {
            throw new BusinessException("Request with this Idempotency-Key is in progress. Retry later");
        }
        return replay.apply(existing.getResultId());
    }

    private String normalizeKey(String key) {
        String normalized = key == null ? "" : key.trim();
        if (normalized.isEmpty()) {
            throw new BusinessException("Idempotency-Key header is required");
        }
        if (normalized.length() > MAX_KEY_LENGTH) {
            throw new BusinessException("Idempotency-Key must have at most " + MAX_KEY_LENGTH + " characters");
        }
        return normalized;
    }

    private String fingerprint(String canonicalRequest) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonicalRequest.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required to compute an idempotency fingerprint", ex);
        }
    }

    /** Wynik operacji wraz z identyfikatorem, ktorym zostanie odtworzony przy powtorzeniu. */
    public record IdempotentResult<T>(long resultId, T value) {
    }
}
