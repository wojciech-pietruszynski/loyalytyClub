package pl.pietruszynski.loyaltyclub.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.pietruszynski.loyaltyclub.api.admin.model.Transaction;
import pl.pietruszynski.loyaltyclub.api.admin.model.TransactionType;
import pl.pietruszynski.loyaltyclub.api.admin.repository.TransactionRepository;
import pl.pietruszynski.loyaltyclub.config.PointExpiryProperties;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Powiadomienia o wygasajacych punktach.
 *
 * <p>Punkty przepadaja po 365 dniach, a system nie mial kanalu, ktory by o tym
 * uprzedzil -- dla programu lojalnosciowego to funkcja produktowa, nie dodatek.
 * Regula jest prosta: dla kazdego progu ostrzegawczego szukamy transakcji,
 * ktorych punkty sa juz dostepne i wygasna w ciagu najblizszych N dni, i dla
 * kazdej tworzymy dokladnie jedno powiadomienie.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PointExpiryNotificationService {

    private static final List<TransactionType> IMMEDIATE_TYPES = List.of(
            TransactionType.MANUAL_ADJUSTMENT,
            TransactionType.POINTS_REDEMPTION,
            TransactionType.POINTS_REFUND
    );

    private final TransactionRepository transactionRepository;
    private final PointExpiryNotificationRepository notificationRepository;
    private final NotificationSender notificationSender;
    private final PointExpiryProperties properties;

    /** @return liczba utworzonych powiadomien */
    @Transactional
    public int notifyUpcomingExpirations() {
        if (!Boolean.TRUE.equals(properties.enabled())) {
            return 0;
        }

        LocalDateTime now = LocalDateTime.now();
        int created = 0;
        for (Integer noticeDays : properties.noticeDays()) {
            created += notifyForThreshold(now, noticeDays);
        }
        return created;
    }

    /** Transakcje wygasajace w zadanym oknie -- podstawa raportu w panelu. */
    @Transactional(readOnly = true)
    public List<Transaction> findExpiringWithin(int days, String country) {
        LocalDateTime now = LocalDateTime.now();
        return transactionRepository.findExpiringBetween(now, now.plusDays(days), country, IMMEDIATE_TYPES);
    }

    private int notifyForThreshold(LocalDateTime now, int noticeDays) {
        int created = 0;
        for (Transaction transaction : transactionRepository.findExpiringBetween(
                now, now.plusDays(noticeDays), null, IMMEDIATE_TYPES)) {

            if (transaction.getPoints() < properties.minimumPoints()
                    || notificationRepository.existsByTransactionIdAndNoticeDays(transaction.getId(), noticeDays)) {
                continue;
            }
            if (createAndSend(transaction, noticeDays)) {
                created++;
            }
        }
        return created;
    }

    private boolean createAndSend(Transaction transaction, int noticeDays) {
        PointExpiryNotification notification = PointExpiryNotification.builder()
                .customer(transaction.getCustomer())
                .transaction(transaction)
                .noticeDays(noticeDays)
                .points(transaction.getPoints())
                .expiresAt(transaction.getExpiresAt())
                .channel(notificationSender.channel())
                .build();

        try {
            notificationRepository.saveAndFlush(notification);
        } catch (DataIntegrityViolationException ex) {
            // Powiadomienie zdazyl utworzyc rownolegly przebieg zadania.
            return false;
        }

        try {
            notificationSender.send(notification);
            notification.setDeliveredAt(LocalDateTime.now());
            notificationRepository.save(notification);
        } catch (RuntimeException ex) {
            // Wpis zostaje bez daty doreczenia -- widac, ze powiadomienie
            // powstalo, ale kanal go nie przyjal.
            log.warn("Cannot deliver point expiry notification for transaction {}", transaction.getId(), ex);
        }
        return true;
    }
}
