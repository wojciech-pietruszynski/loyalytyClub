package pl.pietruszynski.loyaltyclub.api.admin.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "customers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "customers_seq")
    @SequenceGenerator(name = "customers_seq", sequenceName = "customers_id_seq", allocationSize = 1)
    private Long id;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false, unique = true)
    private String customerNumber;

    @Column(nullable = false)
    private String phoneNumber;

    @Column(nullable = false, length = 3)
    private String country;

    /** Biezace saldo -- punkty dostepne do wykorzystania tu i teraz. */
    @Column(nullable = false)
    @Builder.Default
    private Integer loyaltyPoints = 0;

    /**
     * Dorobek punktowy uczestnika: suma punktow faktycznie zdobytych, nigdy
     * nie pomniejszana przez wymiane punktow na kupon ani przez wygasniecie.
     * To z niej wyznaczany jest poziom lojalnosciowy -- korzystanie z programu
     * nie moze obnizac statusu klienta.
     */
    @Column(name = "lifetime_points", nullable = false)
    @Builder.Default
    private Integer lifetimePoints = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private CustomerStatus status = CustomerStatus.ACTIVE;

    @Column(name = "status_changed_at")
    private LocalDateTime statusChangedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(length = 64)
    private String referralCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "referred_by_customer_id")
    private Customer referredBy;

    @OneToMany(mappedBy = "customer", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Transaction> transactions = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = CustomerStatus.ACTIVE;
        }
        if (lifetimePoints == null) {
            lifetimePoints = 0;
        }
    }
}
