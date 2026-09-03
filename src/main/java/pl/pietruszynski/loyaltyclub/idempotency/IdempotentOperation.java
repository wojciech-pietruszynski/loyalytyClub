package pl.pietruszynski.loyaltyclub.idempotency;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Rezerwacja klucza idempotencji dla operacji modyfikujacej stan. Wiersz powstaje
 * przed wykonaniem operacji, a po jej powodzeniu zapisywany jest identyfikator
 * wyniku, ktorym odpowiadamy na powtorzenie zadania.
 */
@Entity
@Table(
        name = "idempotent_operations",
        /*
         * Jednoznacznosc pary (operacja, klucz) jest tym, co realnie blokuje
         * podwojne wykonanie: rownolegle zadanie z tym samym kluczem ma dostac
         * blad zapisu, a nie drugi wiersz. Ograniczenie zaklada migracja 015;
         * powtorzenie go w mapowaniu sprawia, ze zalozenie jest widoczne w kodzie
         * i weryfikowalne testem warstwy persystencji.
         */
        uniqueConstraints = @UniqueConstraint(
                name = "uq_idempotent_operations_operation_key",
                columnNames = {"operation", "idempotency_key"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IdempotentOperation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 60)
    private String operation;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    /** Skrot tresci zadania -- odroznia powtorzenie od kolizji klucza. */
    @Column(name = "request_fingerprint", nullable = false, length = 128)
    private String requestFingerprint;

    @Column(name = "result_id")
    private Long resultId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
