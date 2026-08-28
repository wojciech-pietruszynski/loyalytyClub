package pl.pietruszynski.loyaltyclub.api.admin.dto;

import java.time.LocalDateTime;

public record AdminAuditLogDto(
        Long id,
        LocalDateTime timestamp,
        String username,
        String role,
        String action,
        String resourceType,
        String resourceId
) {}
