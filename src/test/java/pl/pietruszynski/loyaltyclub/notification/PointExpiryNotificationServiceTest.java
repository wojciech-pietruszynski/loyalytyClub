package pl.pietruszynski.loyaltyclub.notification;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.pietruszynski.loyaltyclub.api.admin.model.Customer;
import pl.pietruszynski.loyaltyclub.api.admin.model.Transaction;
import pl.pietruszynski.loyaltyclub.api.admin.model.TransactionType;
import pl.pietruszynski.loyaltyclub.api.admin.repository.TransactionRepository;
import pl.pietruszynski.loyaltyclub.config.PointExpiryProperties;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Punkty przepadaja po 365 dniach, a system nie mial kanalu, ktory by o tym
 * uprzedzil -- dla programu lojalnosciowego to funkcja produktowa, nie dodatek.
 */
@ExtendWith(MockitoExtension.class)
class PointExpiryNotificationServiceTest {

    @Mock private TransactionRepository transactionRepository;
    @Mock private PointExpiryNotificationRepository notificationRepository;
    @Mock private NotificationSender notificationSender;

    @Test
    void notifyUpcomingExpirations_shouldCreateOneNotificationPerThreshold() {
        PointExpiryNotificationService service = service(List.of(30, 7));
        Transaction expiring = expiringTransaction(1L, 200);

        when(notificationSender.channel()).thenReturn("LOG");
        when(transactionRepository.findExpiringBetween(any(), any(), isNull(), any()))
                .thenReturn(List.of(expiring));
        when(notificationRepository.existsByTransactionIdAndNoticeDays(anyLong(), anyInt())).thenReturn(false);
        when(notificationRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        int created = service.notifyUpcomingExpirations();

        assertThat(created).isEqualTo(2);
        verify(notificationSender, org.mockito.Mockito.times(2)).send(any());
    }

    /** Powtorny przebieg zadania nie tworzy drugiego powiadomienia o tym samym. */
    @Test
    void notifyUpcomingExpirations_alreadyNotified_shouldSkip() {
        PointExpiryNotificationService service = service(List.of(30));
        Transaction expiring = expiringTransaction(1L, 200);

        when(transactionRepository.findExpiringBetween(any(), any(), isNull(), any()))
                .thenReturn(List.of(expiring));
        when(notificationRepository.existsByTransactionIdAndNoticeDays(1L, 30)).thenReturn(true);

        assertThat(service.notifyUpcomingExpirations()).isZero();
        verify(notificationRepository, never()).saveAndFlush(any());
    }

    @Test
    void notifyUpcomingExpirations_belowMinimumPoints_shouldSkip() {
        PointExpiryProperties properties = new PointExpiryProperties(true, List.of(30), 100);
        PointExpiryNotificationService service = new PointExpiryNotificationService(
                transactionRepository, notificationRepository, notificationSender, properties);

        when(transactionRepository.findExpiringBetween(any(), any(), isNull(), any()))
                .thenReturn(List.of(expiringTransaction(1L, 50)));

        assertThat(service.notifyUpcomingExpirations()).isZero();
        verify(notificationRepository, never()).saveAndFlush(any());
    }

    @Test
    void notifyUpcomingExpirations_disabled_shouldDoNothing() {
        PointExpiryProperties properties = new PointExpiryProperties(false, List.of(30), 1);
        PointExpiryNotificationService service = new PointExpiryNotificationService(
                transactionRepository, notificationRepository, notificationSender, properties);

        assertThat(service.notifyUpcomingExpirations()).isZero();
        verify(transactionRepository, never()).findExpiringBetween(any(), any(), any(), any());
    }

    /** Nieudane doreczenie zostawia wpis bez daty doreczenia, a nie kasuje go. */
    @Test
    void notifyUpcomingExpirations_senderFails_shouldStillRecordNotification() {
        PointExpiryNotificationService service = service(List.of(30));

        when(notificationSender.channel()).thenReturn("LOG");
        when(transactionRepository.findExpiringBetween(any(), any(), isNull(), any()))
                .thenReturn(List.of(expiringTransaction(1L, 200)));
        when(notificationRepository.existsByTransactionIdAndNoticeDays(1L, 30)).thenReturn(false);
        when(notificationRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        org.mockito.Mockito.doThrow(new IllegalStateException("gateway down"))
                .when(notificationSender).send(any());

        assertThat(service.notifyUpcomingExpirations()).isEqualTo(1);
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void findExpiringWithin_shouldDelegateWithCountryScope() {
        PointExpiryNotificationService service = service(List.of(30));
        when(transactionRepository.findExpiringBetween(any(), any(), eq("PL"), any()))
                .thenReturn(List.of(expiringTransaction(1L, 200)));

        assertThat(service.findExpiringWithin(14, "PL")).hasSize(1);
    }

    private PointExpiryNotificationService service(List<Integer> noticeDays) {
        PointExpiryProperties properties = new PointExpiryProperties(true, noticeDays, 1);
        return new PointExpiryNotificationService(
                transactionRepository, notificationRepository, notificationSender, properties);
    }

    private Transaction expiringTransaction(long id, int points) {
        Customer customer = Customer.builder()
                .firstName("Jan")
                .lastName("Kowalski")
                .email("jan@pl.com")
                .customerNumber("C001")
                .phoneNumber("123456789")
                .country("PL")
                .build();
        customer.setId(1L);

        LocalDateTime purchasedAt = LocalDateTime.now().minusDays(350);
        Transaction transaction = Transaction.builder()
                .customer(customer)
                .points(points)
                .amount(BigDecimal.ZERO)
                .pointsPerCurrency(BigDecimal.ONE)
                .description("Store sale")
                .country("PL")
                .type(TransactionType.SALE)
                .purchaseTimestamp(purchasedAt)
                .availableFrom(purchasedAt.plusDays(30))
                .expiresAt(purchasedAt.plusDays(365))
                .build();
        transaction.setId(id);
        return transaction;
    }
}
