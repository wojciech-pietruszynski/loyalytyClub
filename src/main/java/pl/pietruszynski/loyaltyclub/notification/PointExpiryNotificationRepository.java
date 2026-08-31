package pl.pietruszynski.loyaltyclub.notification;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PointExpiryNotificationRepository extends JpaRepository<PointExpiryNotification, Long> {

    boolean existsByTransactionIdAndNoticeDays(Long transactionId, Integer noticeDays);
}
