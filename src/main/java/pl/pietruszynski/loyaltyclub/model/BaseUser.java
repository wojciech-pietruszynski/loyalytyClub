package pl.pietruszynski.loyaltyclub.model;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

@MappedSuperclass
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public abstract class BaseUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(name = "password_changed_at")
    private LocalDateTime passwordChangedAt;

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
