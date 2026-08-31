package pl.pietruszynski.loyaltyclub.api.admin.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class TransactionDto {
    private Long id;
    private Integer points;
    private String description;
    private LocalDateTime timestamp;
    private LocalDateTime availableFrom;

    /** Wypelniane przy odczycie -- klient integracji nie musi ich wnioskowac z opisu. */
    private String type;
    private String state;
    private BigDecimal amount;
    private LocalDateTime expiresAt;
}
