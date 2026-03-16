package pl.pietruszynski.loyaltyclub.api.admin.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.*;

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
}


