package pl.pietruszynski.loyaltyclub.security;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Pojedynczy token uniewazniony wylogowaniem, identyfikowany deklaracja {@code jti}. */
@Entity
@Table(name = "revoked_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RevokedToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "token_id", nullable = false, unique = true, length = 64)
    private String tokenId;

    @Column(nullable = false)
    private String username;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "revoked_at", nullable = false)
    private LocalDateTime revokedAt;

    @PrePersist
    protected void onCreate() {
        if (revokedAt == null) {
            revokedAt = LocalDateTime.now();
        }
    }
}
