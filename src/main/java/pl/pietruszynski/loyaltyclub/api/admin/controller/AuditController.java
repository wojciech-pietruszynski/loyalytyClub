package pl.pietruszynski.loyaltyclub.api.admin.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.pietruszynski.loyaltyclub.api.admin.audit.AuditService;
import pl.pietruszynski.loyaltyclub.api.admin.dto.AdminAuditLogDto;

import java.util.List;

@RestController
@RequestMapping("/api/admin/audit-logs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AuditController {

    private final AuditService auditService;

    @GetMapping
    public List<AdminAuditLogDto> getLogs() {
        return auditService.getLatestLogs();
    }
}
