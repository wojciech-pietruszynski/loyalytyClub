package pl.pietruszynski.loyaltyclub.api.admin.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

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

    /**
     * Wylacznie skrot BCrypt. Haslo w postaci jawnej nie jest nigdzie utrwalane --
     * jest prezentowane jednorazowo w odpowiedzi na utworzenie konta albo na reset.
     */
    @Column(nullable = false)
    private String password;

    @Column(name = "password_changed_at")
    private LocalDateTime passwordChangedAt;

    @Column(nullable = false, length = 3)
    private String country;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    @PrePersist
    protected void onCreate() {
        if (passwordChangedAt == null) {
            passwordChangedAt = LocalDateTime.now();
        }
    }
}
