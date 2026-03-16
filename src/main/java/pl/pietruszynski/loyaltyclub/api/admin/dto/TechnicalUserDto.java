package pl.pietruszynski.loyaltyclub.api.admin.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TechnicalUserDto {
    private Long id;
    private String username;
    private String passwordPreview;
    private String country;
    private boolean enabled;
}
