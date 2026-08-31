package pl.pietruszynski.loyaltyclub.notification;

import jakarta.persistence.*;
import lombok.*;
import pl.pietruszynski.loyaltyclub.api.admin.model.Customer;
import pl.pietruszynski.loyaltyclub.api.admin.model.Transaction;

import java.time.LocalDateTime;

/**
 * Rejestr powiadomien o wygasajacych punktach. Jednoznaczna para
 * (transakcja, prog ostrzegawczy) sprawia, ze powtorne uruchomienie zadania
 * cyklicznego nie tworzy drugiego powiadomienia o tym samym.
 */
@Entity
@Table(name = "point_expiry_notifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PointExpiryNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id", nullable = false)
    private Transaction transaction;

    /** Prog ostrzegawczy w dniach, dla ktorego powstalo powiadomienie. */
    @Column(name = "notice_days", nullable = false)
    private Integer noticeDays;

    @Column(nullable = false)
    private Integer points;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @Column(nullable = false, length = 30)
    private String channel;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
