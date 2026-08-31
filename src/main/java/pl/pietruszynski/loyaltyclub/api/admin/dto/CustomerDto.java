package pl.pietruszynski.loyaltyclub.api.admin.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerDto {
    private Long id;
    @NotBlank(message = "First name is required")
    private String firstName;
    @NotBlank(message = "Last name is required")
    private String lastName;
    @NotBlank(message = "Email is required")
    @Email(message = "Email format is invalid")
    private String email;
    @NotBlank(message = "Customer number is required")
    private String customerNumber;
    @NotBlank(message = "Phone number is required")
    private String phoneNumber;
    @NotBlank(message = "Country is required")
    @Size(min = 2, max = 3, message = "Country code must have 2 or 3 characters")
    private String country;
    @PositiveOrZero(message = "Loyalty points cannot be negative")
    private Integer loyaltyPoints;

    /**
     * Dorobek punktowy uczestnika (tylko do odczytu). Nie maleje przy wymianie
     * punktow na kupon ani przy wygasnieciu -- to z niego wynika poziom lojalnosciowy.
     */
    private Integer lifetimePoints;

    /** Populated on read responses: ACTIVE / INACTIVE / ANONYMIZED. */
    private String status;

    /** Populated on read responses. */
    private LocalDateTime createdAt;

    /** Optional: existing customer's number who referred this registration (create only). */
    private String referrerCustomerNumber;

    /** Populated on read responses. */
    private String referralCode;

    /** Populated on read responses — derived from tier definitions and lifetime points. */
    private String loyaltyTierCode;
}
