package pl.pietruszynski.loyaltyclub.api.admin.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "technical_users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TechnicalUser {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "technical_users_seq")
    @SequenceGenerator(name = "technical_users_seq", sequenceName = "technical_users_id_seq", allocationSize = 1)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(name = "password_preview", nullable = false)
    private String passwordPreview;

    @Column(nullable = false, length = 3)
    private String country;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;
}
