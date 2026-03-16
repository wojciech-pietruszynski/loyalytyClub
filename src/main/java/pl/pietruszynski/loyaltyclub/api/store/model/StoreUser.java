package pl.pietruszynski.loyaltyclub.api.store.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "store_users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StoreUser {

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


