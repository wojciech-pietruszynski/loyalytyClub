package pl.pietruszynski.loyaltyclub.api.admin.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class TransactionDto {
    private Long id;
    private Integer points;
    private String description;
    private LocalDateTime timestamp;
}
