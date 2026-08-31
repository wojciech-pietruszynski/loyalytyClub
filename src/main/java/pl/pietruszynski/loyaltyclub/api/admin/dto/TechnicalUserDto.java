package pl.pietruszynski.loyaltyclub.api.admin.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class TechnicalUserDto {
    private Long id;
    private String username;
    private String country;
    private boolean enabled;
    private LocalDateTime passwordChangedAt;

    /**
     * Haslo jednorazowe. Wypelniane wylacznie w odpowiedzi na utworzenie konta
     * albo na jego reset -- nigdzie nie jest utrwalane i nie da sie go odczytac
     * ponownie. Przy pozostalych odczytach pole nie pojawia sie w odpowiedzi.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String oneTimePassword;
}
