package pl.pietruszynski.loyaltyclub.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Domyslny adapter kanalu powiadomien. Zapisuje powiadomienie do logu aplikacji,
 * dzieki czemu regula wyznaczania odbiorcow dziala i jest testowalna, zanim
 * zostanie podlaczona docelowa bramka pocztowa albo SMS. Wlasny adapter wystarczy
 * zarejestrowac jako {@code @Primary} -- reszta kodu pozostaje bez zmian.
 */
@Component
@Slf4j
public class LoggingNotificationSender implements NotificationSender {

    @Override
    public String channel() {
        return "LOG";
    }

    @Override
    public void send(PointExpiryNotification notification) {
        log.info("Points expiring: customer={} points={} expiresAt={} noticeDays={}",
                notification.getCustomer().getCustomerNumber(),
                notification.getPoints(),
                notification.getExpiresAt(),
                notification.getNoticeDays());
    }
}
