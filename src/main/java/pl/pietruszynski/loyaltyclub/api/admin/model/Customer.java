package pl.pietruszynski.loyaltyclub.api.admin.model;

import jakarta.persistence.*;
import lombok.*;

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

    @Column(nullable = false)
    @Builder.Default
    private Integer loyaltyPoints = 0;

    @Column(length = 64)
    private String referralCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "referred_by_customer_id")
    private Customer referredBy;

    @OneToMany(mappedBy = "customer", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Transaction> transactions = new ArrayList<>();
}


