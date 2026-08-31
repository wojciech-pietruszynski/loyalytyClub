package pl.pietruszynski.loyaltyclub.api.admin.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Rozliczone polecenie. Jeden wiersz na poleconego uczestnika -- ograniczenie
 * jednoznacznosci na {@code referred_customer_id} jest wlasciwa ochrona przed
 * podwojnym naliczeniem premii, takze przy rownoleglych zapisach z dwoch kas.
 */
@Entity
@Table(name = "referral_rewards")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReferralReward {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "referrer_customer_id", nullable = false)
    private Customer referrer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "referred_customer_id", nullable = false, unique = true)
    private Customer referred;

    /** Zakup poleconego, ktory spelnil prog kwotowy i uruchomil naliczenie. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "qualifying_transaction_id")
    private Transaction qualifyingTransaction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "referrer_transaction_id")
    private Transaction referrerTransaction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "referred_transaction_id")
    private Transaction referredTransaction;

    @Column(name = "referrer_points", nullable = false)
    private Integer referrerPoints;

    @Column(name = "referred_points", nullable = false)
    private Integer referredPoints;

    @Column(nullable = false, length = 3)
    private String country;

    @Column(name = "awarded_at", nullable = false)
    private LocalDateTime awardedAt;

    @PrePersist
    protected void onCreate() {
        if (awardedAt == null) {
            awardedAt = LocalDateTime.now();
        }
    }
}
