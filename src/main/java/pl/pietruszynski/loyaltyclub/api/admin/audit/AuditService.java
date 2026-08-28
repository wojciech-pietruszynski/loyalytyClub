package pl.pietruszynski.loyaltyclub.api.admin.audit;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.pietruszynski.loyaltyclub.api.admin.dto.AdminAuditLogDto;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuditService {

    private final AdminAuditLogRepository adminAuditLogRepository;

    public List<AdminAuditLogDto> getLatestLogs() {
        return adminAuditLogRepository.findTop200ByOrderByOccurredAtDesc().stream()
                .map(this::mapToDto)
                .toList();
    }

    private AdminAuditLogDto mapToDto(AdminAuditLog log) {
        return new AdminAuditLogDto(
                log.getId(),
                log.getOccurredAt(),
                log.getUsername(),
                log.getRole() == null ? "" : log.getRole(),
                log.getAction(),
                log.getResourceType(),
                log.getResourceId()
        );
    }
}
