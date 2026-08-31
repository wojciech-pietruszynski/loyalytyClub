package pl.pietruszynski.loyaltyclub.security;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Granica czasowa dla konta: kazdy token wydany przed {@code notBefore} jest
 * odrzucany. Jeden wiersz uniewaznia wszystkie sesje uzytkownika naraz --
 * uzywane przy zmianie i resecie hasla oraz przy dezaktywacji konta.
 */
@Entity
@Table(name = "user_token_cutoffs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserTokenCutoff {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(name = "not_before", nullable = false)
    private LocalDateTime notBefore;

    @Column(nullable = false, length = 100)
    private String reason;
}
