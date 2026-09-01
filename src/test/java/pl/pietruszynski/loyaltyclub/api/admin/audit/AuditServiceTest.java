package pl.pietruszynski.loyaltyclub.api.admin.audit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.pietruszynski.loyaltyclub.api.admin.dto.AdminAuditLogDto;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock private AdminAuditLogRepository adminAuditLogRepository;

    @InjectMocks
    private AuditService auditService;

    @Test
    void getLatestLogs_shouldMapEntriesToDto() {
        LocalDateTime occurredAt = LocalDateTime.now();
        when(adminAuditLogRepository.findTop200ByOrderByOccurredAtDesc()).thenReturn(List.of(
                AdminAuditLog.builder()
                        .id(1L)
                        .occurredAt(occurredAt)
                        .username("admin")
                        .role("ROLE_ADMIN")
                        .action("ADD_POINTS")
                        .resourceType("CUSTOMER")
                        .resourceId("42")
                        .build()));

        List<AdminAuditLogDto> logs = auditService.getLatestLogs();

        assertThat(logs).singleElement().satisfies(dto -> {
            assertThat(dto.id()).isEqualTo(1L);
            assertThat(dto.timestamp()).isEqualTo(occurredAt);
            assertThat(dto.username()).isEqualTo("admin");
            assertThat(dto.role()).isEqualTo("ROLE_ADMIN");
            assertThat(dto.action()).isEqualTo("ADD_POINTS");
            assertThat(dto.resourceType()).isEqualTo("CUSTOMER");
            assertThat(dto.resourceId()).isEqualTo("42");
        });
    }

    /** Wpis bez roli (np. sprzed jej wprowadzenia) pokazujemy jako pusta kolumne, nie jako brak. */
    @Test
    void getLatestLogs_withoutRole_shouldReturnEmptyStringInsteadOfNull() {
        when(adminAuditLogRepository.findTop200ByOrderByOccurredAtDesc()).thenReturn(List.of(
                AdminAuditLog.builder()
                        .id(1L)
                        .occurredAt(LocalDateTime.now())
                        .username("admin")
                        .role(null)
                        .action("LOGIN")
                        .resourceType("SESSION")
                        .build()));

        assertThat(auditService.getLatestLogs().getFirst().role()).isEmpty();
    }

    @Test
    void getLatestLogs_withEmptyLog_shouldReturnEmptyList() {
        when(adminAuditLogRepository.findTop200ByOrderByOccurredAtDesc()).thenReturn(List.of());

        assertThat(auditService.getLatestLogs()).isEmpty();
    }
}
