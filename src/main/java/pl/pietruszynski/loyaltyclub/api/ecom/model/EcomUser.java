package pl.pietruszynski.loyaltyclub.api.ecom.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "ecom_users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EcomUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;
}


