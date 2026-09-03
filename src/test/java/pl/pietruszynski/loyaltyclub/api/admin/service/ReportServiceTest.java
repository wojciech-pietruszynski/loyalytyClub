package pl.pietruszynski.loyaltyclub.api.admin.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.pietruszynski.loyaltyclub.api.admin.dto.ExpiringPointsDto;
import pl.pietruszynski.loyaltyclub.api.admin.dto.ReportsSummaryDto;
import pl.pietruszynski.loyaltyclub.api.admin.model.Customer;
import pl.pietruszynski.loyaltyclub.api.admin.model.CustomerStatus;
import pl.pietruszynski.loyaltyclub.api.admin.model.Transaction;
import pl.pietruszynski.loyaltyclub.api.admin.model.TransactionState;
import pl.pietruszynski.loyaltyclub.api.admin.model.TransactionType;
import pl.pietruszynski.loyaltyclub.api.admin.repository.CustomerRepository;
import pl.pietruszynski.loyaltyclub.api.admin.repository.TransactionRepository;
import pl.pietruszynski.loyaltyclub.notification.PointExpiryNotificationService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Raporty i eksport CSV.
 *
 * <p>Dwie rzeczy decyduja tu o poprawnosci wyniku. Pierwsza to zakres krajowy:
 * rola TECHNICAL widzi wylacznie swoj kraj, rola ADMIN caly program, a "brak
 * zakresu" ma byc reprezentowany przez {@code null}, nie przez pusty ciag --
 * inaczej zapytanie porownaloby kod kraju z pustym lancuchem i nie zwrocilo nic.
 * Druga to skladnia CSV: plik trafia do arkusza kalkulacyjnego, wiec przecinek
 * albo cudzyslow w nazwisku nie moze rozjechac kolumn.
 */
@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock private CustomerRepository customerRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private LoyaltyTierService loyaltyTierService;
    @Mock private PointExpiryNotificationService pointExpiryNotificationService;

    @InjectMocks
    private ReportService reportService;

    // ------------------------------------------------------------------- zestawienie

    @Test
    void getSummary_withoutScope_shouldCoverAllCountries() {
        when(customerRepository.count()).thenReturn(12L);
        when(transactionRepository.countSince(any())).thenReturn(5L);
        when(transactionRepository.sumAvailablePoints(any(), isNull(), anyList())).thenReturn(1000L);
        when(transactionRepository.sumPendingPoints(any(), isNull(), anyList())).thenReturn(200L);
        when(transactionRepository.sumExpiredPoints(any(), isNull(), anyList())).thenReturn(30L);

        ReportsSummaryDto summary = reportService.getSummary(null);

        assertThat(summary.scope()).isEmpty();
        assertThat(summary.customerCount()).isEqualTo(12);
        assertThat(summary.transactionsLast30Days()).isEqualTo(5);
        assertThat(summary.availablePoints()).isEqualTo(1000);
        assertThat(summary.pendingPoints()).isEqualTo(200);
        assertThat(summary.expiredPoints()).isEqualTo(30);
    }

    /**
     * Nazwa {@code totalLoyaltyPoints} zostala w kontrakcie ze wzgledu na zgodnosc
     * wstecz i ma zawierac punkty dostepne, a nie sume wszystkich naliczen.
     */
    @Test
    void getSummary_shouldReportAvailablePointsUnderTheLegacyFieldName() {
        when(customerRepository.count()).thenReturn(1L);
        when(transactionRepository.countSince(any())).thenReturn(0L);
        when(transactionRepository.sumAvailablePoints(any(), isNull(), anyList())).thenReturn(1000L);
        when(transactionRepository.sumPendingPoints(any(), isNull(), anyList())).thenReturn(200L);
        when(transactionRepository.sumExpiredPoints(any(), isNull(), anyList())).thenReturn(30L);

        assertThat(reportService.getSummary(null).totalLoyaltyPoints()).isEqualTo(1000);
    }

    @Test
    void getSummary_withCountryScope_shouldUseCountryAwareQueries() {
        when(customerRepository.countByCountry("PL")).thenReturn(3L);
        when(transactionRepository.countSinceForCountry(any(), eq("PL"))).thenReturn(2L);
        when(transactionRepository.sumAvailablePoints(any(), eq("PL"), anyList())).thenReturn(100L);
        when(transactionRepository.sumPendingPoints(any(), eq("PL"), anyList())).thenReturn(0L);
        when(transactionRepository.sumExpiredPoints(any(), eq("PL"), anyList())).thenReturn(0L);

        ReportsSummaryDto summary = reportService.getSummary("PL");

        assertThat(summary.scope()).isEqualTo("PL");
        assertThat(summary.customerCount()).isEqualTo(3);
    }

    /** Kod kraju z panelu albo z konta technicznego bywa zapisany roznie. */
    @ParameterizedTest
    @ValueSource(strings = {"pl", " PL ", "Pl"})
    void getSummary_shouldNormalizeCountryCode(String rawScope) {
        when(customerRepository.countByCountry("PL")).thenReturn(3L);
        when(transactionRepository.countSinceForCountry(any(), eq("PL"))).thenReturn(0L);
        when(transactionRepository.sumAvailablePoints(any(), eq("PL"), anyList())).thenReturn(0L);
        when(transactionRepository.sumPendingPoints(any(), eq("PL"), anyList())).thenReturn(0L);
        when(transactionRepository.sumExpiredPoints(any(), eq("PL"), anyList())).thenReturn(0L);

        assertThat(reportService.getSummary(rawScope).scope()).isEqualTo("PL");
    }

    /** Pusty zakres to brak ograniczenia, a nie kraj o pustym kodzie. */
    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void getSummary_blankScope_shouldBeTreatedAsNoRestriction(String blankScope) {
        when(customerRepository.count()).thenReturn(7L);
        when(transactionRepository.countSince(any())).thenReturn(0L);
        when(transactionRepository.sumAvailablePoints(any(), isNull(), anyList())).thenReturn(0L);
        when(transactionRepository.sumPendingPoints(any(), isNull(), anyList())).thenReturn(0L);
        when(transactionRepository.sumExpiredPoints(any(), isNull(), anyList())).thenReturn(0L);

        assertThat(reportService.getSummary(blankScope).customerCount()).isEqualTo(7);
    }

    /** Okno zestawienia to 30 dni wstecz od chwili wywolania. */
    @Test
    void getSummary_shouldUseThirtyDayWindow() {
        when(customerRepository.count()).thenReturn(0L);
        when(transactionRepository.countSince(any())).thenReturn(0L);
        when(transactionRepository.sumAvailablePoints(any(), isNull(), anyList())).thenReturn(0L);
        when(transactionRepository.sumPendingPoints(any(), isNull(), anyList())).thenReturn(0L);
        when(transactionRepository.sumExpiredPoints(any(), isNull(), anyList())).thenReturn(0L);

        LocalDateTime before = LocalDateTime.now();
        reportService.getSummary(null);

        ArgumentCaptor<LocalDateTime> since = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(transactionRepository).countSince(since.capture());
        assertThat(since.getValue()).isBetween(before.minusDays(30).minusMinutes(1), before.minusDays(30).plusMinutes(1));
    }

    // ------------------------------------------------------ punkty przed wygasnieciem

    @Test
    void getExpiringPoints_shouldMapTransactionsToReportRows() {
        Customer customer = customer("C001", "Jan", "Kowalski", "PL");
        Transaction transaction = transaction(customer, 120, LocalDateTime.now().plusDays(10));
        when(pointExpiryNotificationService.findExpiringWithin(30, "PL")).thenReturn(List.of(transaction));

        List<ExpiringPointsDto> rows = reportService.getExpiringPoints(30, "pl");

        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.transactionId()).isEqualTo(transaction.getId());
            assertThat(row.customerNumber()).isEqualTo("C001");
            assertThat(row.country()).isEqualTo("PL");
            assertThat(row.points()).isEqualTo(120);
            assertThat(row.expiresAt()).isEqualTo(transaction.getExpiresAt());
        });
    }

    @Test
    void getExpiringPoints_withoutScope_shouldNotRestrictCountry() {
        when(pointExpiryNotificationService.findExpiringWithin(anyInt(), isNull())).thenReturn(List.of());

        assertThat(reportService.getExpiringPoints(7, "  ")).isEmpty();
        verify(pointExpiryNotificationService).findExpiringWithin(7, null);
    }

    // --------------------------------------------------------------- eksport klientow

    @Test
    void exportCustomersCsv_shouldStartWithHeaderAndOneRowPerCustomer() {
        when(customerRepository.findAll()).thenReturn(List.of(
                customer("C001", "Jan", "Kowalski", "PL"),
                customer("C002", "Anna", "Nowak", "PL")));
        when(loyaltyTierService.resolveTierCode(any(Customer.class))).thenReturn("SILVER");

        String csv = reportService.exportCustomersCsv(null);
        String[] lines = csv.split("\n");

        assertThat(lines[0]).isEqualTo(
                "customerNumber,firstName,lastName,email,phoneNumber,country,status,"
                        + "loyaltyPoints,lifetimePoints,loyaltyTierCode,referralCode");
        assertThat(lines).hasSize(3);
        assertThat(lines[1]).startsWith("C001,Jan,Kowalski,");
        assertThat(lines[1]).contains(",SILVER,");
    }

    @Test
    void exportCustomersCsv_withCountryScope_shouldQueryOnlyThatCountry() {
        when(customerRepository.findAllByCountry("DE")).thenReturn(List.of());

        reportService.exportCustomersCsv("de");

        verify(customerRepository).findAllByCountry("DE");
    }

    /**
     * RFC 4180: pole zawierajace przecinek, cudzyslow albo znak nowej linii jest
     * otoczone cudzyslowami, a cudzyslow wewnatrz podwojony.
     */
    @Test
    void exportCustomersCsv_shouldEscapeSeparatorsInsideFields() {
        Customer customer = customer("C001", "Jan", "Kowalski, Jr.", "PL");
        customer.setEmail("jan\"cudzyslow\"@example.com");
        customer.setPhoneNumber("123\n456");
        when(customerRepository.findAll()).thenReturn(List.of(customer));
        when(loyaltyTierService.resolveTierCode(any(Customer.class))).thenReturn(null);

        String row = reportService.exportCustomersCsv(null).split("\n", 2)[1];

        assertThat(row).contains("\"Kowalski, Jr.\"");
        assertThat(row).contains("\"jan\"\"cudzyslow\"\"@example.com\"");
        assertThat(row).contains("\"123\n456\"");
    }

    /** Brak poziomu albo brak kodu polecajacego zapisujemy jako pole puste. */
    @Test
    void exportCustomersCsv_shouldWriteEmptyFieldForMissingValues() {
        Customer customer = customer("C001", "Jan", "Kowalski", "PL");
        customer.setReferralCode(null);
        customer.setStatus(null);
        when(customerRepository.findAll()).thenReturn(List.of(customer));
        when(loyaltyTierService.resolveTierCode(any(Customer.class))).thenReturn(null);

        // Kazdy wiersz konczy sie znakiem nowej linii, wiec porownujemy sama tresc.
        String row = reportService.exportCustomersCsv(null).split("\n", 2)[1].stripTrailing();

        assertThat(row).endsWith(",,");
    }

    @Test
    void exportCustomersCsv_withNoCustomers_shouldStillReturnHeader() {
        when(customerRepository.findAll()).thenReturn(List.of());

        assertThat(reportService.exportCustomersCsv(null))
                .isEqualTo("customerNumber,firstName,lastName,email,phoneNumber,country,status,"
                        + "loyaltyPoints,lifetimePoints,loyaltyTierCode,referralCode\n");
    }

    // ------------------------------------------------------------ eksport transakcji

    @Test
    void exportTransactionsCsv_shouldWriteHeaderAndRows() {
        Customer customer = customer("C001", "Jan", "Kowalski", "PL");
        when(transactionRepository.findForExport(any(), any(), isNull()))
                .thenReturn(List.of(transaction(customer, 50, LocalDateTime.now().plusDays(300))));

        String[] lines = reportService
                .exportTransactionsCsv(LocalDateTime.now().minusDays(1), LocalDateTime.now(), null)
                .split("\n");

        assertThat(lines[0]).isEqualTo("id,timestamp,customerNumber,country,type,state,points,amount,description");
        assertThat(lines[1]).contains("C001").contains("SALE").contains("AVAILABLE").contains("50");
    }

    @Test
    void exportTransactionsCsv_shouldPassNormalizedCountryToRepository() {
        when(transactionRepository.findForExport(any(), any(), eq("PL"))).thenReturn(List.of());

        LocalDateTime from = LocalDateTime.now().minusDays(7);
        LocalDateTime to = LocalDateTime.now();
        reportService.exportTransactionsCsv(from, to, " pl ");

        verify(transactionRepository).findForExport(from, to, "PL");
    }

    /** Opis transakcji jest tekstem swobodnym, wiec musi przejsc przez to samo cytowanie. */
    @Test
    void exportTransactionsCsv_shouldEscapeDescription() {
        Customer customer = customer("C001", "Jan", "Kowalski", "PL");
        Transaction transaction = transaction(customer, 50, LocalDateTime.now().plusDays(300));
        transaction.setDescription("Korekta, po reklamacji");
        when(transactionRepository.findForExport(any(), any(), isNull())).thenReturn(List.of(transaction));

        String row = reportService
                .exportTransactionsCsv(LocalDateTime.now().minusDays(1), LocalDateTime.now(), null)
                .split("\n", 2)[1].stripTrailing();

        assertThat(row).endsWith("\"Korekta, po reklamacji\"");
    }

    private Customer customer(String customerNumber, String firstName, String lastName, String country) {
        return Customer.builder()
                .id(1L)
                .firstName(firstName)
                .lastName(lastName)
                .email(customerNumber.toLowerCase() + "@example.com")
                .customerNumber(customerNumber)
                .phoneNumber("123456789")
                .country(country)
                .loyaltyPoints(100)
                .lifetimePoints(150)
                .status(CustomerStatus.ACTIVE)
                .referralCode("REF123")
                .build();
    }

    private Transaction transaction(Customer customer, int points, LocalDateTime expiresAt) {
        return Transaction.builder()
                .id(10L)
                .customer(customer)
                .points(points)
                .amount(new BigDecimal("100.00"))
                .pointsPerCurrency(BigDecimal.ONE)
                .description("Zakup")
                .country(customer.getCountry())
                .type(TransactionType.SALE)
                .state(TransactionState.AVAILABLE)
                .timestamp(LocalDateTime.now())
                .purchaseTimestamp(LocalDateTime.now())
                .availableFrom(LocalDateTime.now())
                .expiresAt(expiresAt)
                .build();
    }
}
