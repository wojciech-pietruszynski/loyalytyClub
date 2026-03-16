package pl.pietruszynski.loyaltyclub.api.admin.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "transactions_seq")
    @SequenceGenerator(name = "transactions_seq", sequenceName = "transactions_id_seq", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(nullable = false)
    private Integer points;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal pointsPerCurrency;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false, length = 3)
    private String country;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionState state;

    @Column(name = "source_transaction_number", unique = true)
    private String sourceTransactionNumber;

    @Column(nullable = false)
    private LocalDateTime purchaseTimestamp;

    @Column(nullable = false)
    private LocalDateTime availableFrom;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_transaction_id")
    private Transaction sourceTransaction;

    @PrePersist
    protected void onCreate() {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
        if (purchaseTimestamp == null) {
            purchaseTimestamp = timestamp;
        }
        if (availableFrom == null) {
            availableFrom = purchaseTimestamp;
        }
        if (expiresAt == null) {
            expiresAt = purchaseTimestamp.plusDays(365);
        }
        if (type == null) {
            type = TransactionType.MANUAL_ADJUSTMENT;
        }
        if (state == null) {
            state = TransactionState.AVAILABLE;
        }
        if (amount == null) {
            amount = BigDecimal.ZERO;
        }
        if (pointsPerCurrency == null) {
            pointsPerCurrency = BigDecimal.ONE;
        }
    }
}


