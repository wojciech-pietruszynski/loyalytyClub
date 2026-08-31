package pl.pietruszynski.loyaltyclub.api.admin.audit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long> {

    /** Panel pokazuje najnowsze wpisy; limit chroni przed zaciagnieciem calej tabeli. */
    List<AdminAuditLog> findTop200ByOrderByOccurredAtDesc();
}
