package pl.pietruszynski.loyaltyclub.api.admin.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import pl.pietruszynski.loyaltyclub.PersistenceTest;
import pl.pietruszynski.loyaltyclub.api.admin.model.Customer;
import pl.pietruszynski.loyaltyclub.api.admin.model.Transaction;
import pl.pietruszynski.loyaltyclub.api.admin.model.TransactionState;
import pl.pietruszynski.loyaltyclub.api.admin.model.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Agregaty punktowe stojace za zestawieniem zbiorczym i powiadomieniami
 * o wygasajacych punktach. Zapytania powtarzaja w JPQL te sama regule cyklu
 * zycia punktu, ktora aplikacja stosuje w kodzie -- rozjazd miedzy tymi dwoma
 * miejscami jest bledem, ktorego nie wykryje zaden test z mockiem repozytorium.
 */
@PersistenceTest
class TransactionRepositoryTest {

    private static final List<TransactionType> IMMEDIATE_TYPES = List.of(
            TransactionType.MANUAL_ADJUSTMENT,
            TransactionType.POINTS_REDEMPTION,
            TransactionType.POINTS_REFUND
    );

    @Autowired private TestEntityManager entityManager;
    @Autowired private TransactionRepository transactionRepository;

    private LocalDateTime now;
    private Customer polishCustomer;
    private Customer germanCustomer;

    @BeforeEach
    void setUp() {
        // Kolumna TIMESTAMP ma mniejsza rozdzielczosc niz LocalDateTime.now(); bez
        // obciecia porownania na granicy okna sprawdzalyby blad zaokraglenia,
        // a nie regule.
        now = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);
        polishCustomer = persistCustomer("C001", "pl@example.com", "PL");
        germanCustomer = persistCustomer("C002", "de@example.com", "DE");
    }

    @Test
    void sumAvailablePoints_shouldCountOnlyPointsUsableRightNow() {
        // Dostepna: karencja minela, data waznosci przed nami.
        persistTransaction(polishCustomer, "PL", TransactionType.SALE, 100,
                now.minusDays(10), now.plusDays(300));
        // Oczekujaca: karencja jeszcze trwa.
        persistTransaction(polishCustomer, "PL", TransactionType.SALE, 50,
                now.plusDays(2), now.plusDays(300));
        // Wygasla.
        persistTransaction(polishCustomer, "PL", TransactionType.SALE, 70,
                now.minusDays(400), now.minusDays(1));

        long available = transactionRepository.sumAvailablePoints(now, null, IMMEDIATE_TYPES);

        assertThat(available).isEqualTo(100);
    }

    /**
     * Korekty reczne i operacje kuponowe nie maja karencji ani waznosci, wiec
     * musza sie liczyc jako dostepne niezaleznie od dat zapisanych w wierszu.
     */
    @Test
    void sumAvailablePoints_shouldAlwaysCountImmediateTypes() {
        persistTransaction(polishCustomer, "PL", TransactionType.MANUAL_ADJUSTMENT, 30,
                now.plusDays(5), now.minusDays(5));
        persistTransaction(polishCustomer, "PL", TransactionType.POINTS_REDEMPTION, -20,
                now.plusDays(5), now.minusDays(5));

        long available = transactionRepository.sumAvailablePoints(now, null, IMMEDIATE_TYPES);

        assertThat(available).isEqualTo(10);
    }

    @Test
    void sumAvailablePoints_shouldLimitToCountryWhenGiven() {
        persistTransaction(polishCustomer, "PL", TransactionType.SALE, 100,
                now.minusDays(1), now.plusDays(300));
        persistTransaction(germanCustomer, "DE", TransactionType.SALE, 40,
                now.minusDays(1), now.plusDays(300));

        assertThat(transactionRepository.sumAvailablePoints(now, "PL", IMMEDIATE_TYPES)).isEqualTo(100);
        assertThat(transactionRepository.sumAvailablePoints(now, "DE", IMMEDIATE_TYPES)).isEqualTo(40);
        assertThat(transactionRepository.sumAvailablePoints(now, null, IMMEDIATE_TYPES)).isEqualTo(140);
    }

    @Test
    void sumPendingPoints_shouldCountOnlyPointsStillInGracePeriod() {
        persistTransaction(polishCustomer, "PL", TransactionType.SALE, 100,
                now.minusDays(1), now.plusDays(300));
        persistTransaction(polishCustomer, "PL", TransactionType.SALE, 50,
                now.plusDays(3), now.plusDays(300));
        // Korekta reczna nie ma karencji, wiec nigdy nie jest oczekujaca.
        persistTransaction(polishCustomer, "PL", TransactionType.MANUAL_ADJUSTMENT, 999,
                now.plusDays(3), now.plusDays(300));

        assertThat(transactionRepository.sumPendingPoints(now, null, IMMEDIATE_TYPES)).isEqualTo(50);
    }

    @Test
    void sumExpiredPoints_shouldCountOnlyLapsedEarnings() {
        persistTransaction(polishCustomer, "PL", TransactionType.SALE, 70,
                now.minusDays(400), now.minusDays(1));
        persistTransaction(polishCustomer, "PL", TransactionType.SALE, 100,
                now.minusDays(1), now.plusDays(300));
        // Korekta reczna nie wygasa, mimo daty w przeszlosci.
        persistTransaction(polishCustomer, "PL", TransactionType.MANUAL_ADJUSTMENT, 999,
                now.minusDays(400), now.minusDays(1));

        assertThat(transactionRepository.sumExpiredPoints(now, null, IMMEDIATE_TYPES)).isEqualTo(70);
    }

    /** Brak wierszy musi dawac zero, a nie {@code null} -- stad COALESCE w zapytaniach. */
    @Test
    void sums_shouldReturnZeroWhenNoTransactionsMatch() {
        assertThat(transactionRepository.sumAvailablePoints(now, "LT", IMMEDIATE_TYPES)).isZero();
        assertThat(transactionRepository.sumPendingPoints(now, "LT", IMMEDIATE_TYPES)).isZero();
        assertThat(transactionRepository.sumExpiredPoints(now, "LT", IMMEDIATE_TYPES)).isZero();
    }

    @Test
    void countSince_shouldCountTransactionsInsideWindowOnly() {
        persistTransaction(polishCustomer, "PL", TransactionType.SALE, 10,
                now.minusDays(1), now.plusDays(300), now.minusDays(5));
        persistTransaction(polishCustomer, "PL", TransactionType.SALE, 10,
                now.minusDays(1), now.plusDays(300), now.minusDays(40));
        persistTransaction(germanCustomer, "DE", TransactionType.SALE, 10,
                now.minusDays(1), now.plusDays(300), now.minusDays(5));

        LocalDateTime since = now.minusDays(30);

        assertThat(transactionRepository.countSince(since)).isEqualTo(2);
        assertThat(transactionRepository.countSinceForCountry(since, "PL")).isEqualTo(1);
    }

    @Test
    void findExpiringBetween_shouldReturnOnlyAvailableEarningsInsideWindowOrderedByExpiry() {
        Transaction later = persistTransaction(polishCustomer, "PL", TransactionType.SALE, 10,
                now.minusDays(1), now.plusDays(20));
        Transaction sooner = persistTransaction(polishCustomer, "PL", TransactionType.SALE, 20,
                now.minusDays(1), now.plusDays(5));
        // Poza oknem.
        persistTransaction(polishCustomer, "PL", TransactionType.SALE, 30,
                now.minusDays(1), now.plusDays(60));
        // Jeszcze niedostepna.
        persistTransaction(polishCustomer, "PL", TransactionType.SALE, 40,
                now.plusDays(2), now.plusDays(10));
        // Typ bez daty waznosci.
        persistTransaction(polishCustomer, "PL", TransactionType.MANUAL_ADJUSTMENT, 50,
                now.minusDays(1), now.plusDays(10));
        // Obciazenie punktowe, nie zarobek.
        persistTransaction(polishCustomer, "PL", TransactionType.RETURN, -60,
                now.minusDays(1), now.plusDays(10));

        List<Transaction> expiring =
                transactionRepository.findExpiringBetween(now, now.plusDays(30), null, IMMEDIATE_TYPES);

        assertThat(expiring).extracting(Transaction::getId)
                .containsExactly(sooner.getId(), later.getId());
    }

    @Test
    void findExpiringBetween_shouldLimitToCountryWhenGiven() {
        persistTransaction(polishCustomer, "PL", TransactionType.SALE, 10,
                now.minusDays(1), now.plusDays(5));
        persistTransaction(germanCustomer, "DE", TransactionType.SALE, 10,
                now.minusDays(1), now.plusDays(5));

        assertThat(transactionRepository.findExpiringBetween(now, now.plusDays(30), "DE", IMMEDIATE_TYPES))
                .singleElement()
                .extracting(Transaction::getCountry)
                .isEqualTo("DE");
    }

    @Test
    void findForExport_shouldFilterByDateRangeAndCountryAndSortNewestFirst() {
        Transaction newest = persistTransaction(polishCustomer, "PL", TransactionType.SALE, 10,
                now.minusDays(1), now.plusDays(300), now.minusDays(2));
        Transaction oldest = persistTransaction(polishCustomer, "PL", TransactionType.SALE, 20,
                now.minusDays(1), now.plusDays(300), now.minusDays(9));
        // Poza zakresem dat.
        persistTransaction(polishCustomer, "PL", TransactionType.SALE, 30,
                now.minusDays(1), now.plusDays(300), now.minusDays(90));
        // Inny kraj.
        persistTransaction(germanCustomer, "DE", TransactionType.SALE, 40,
                now.minusDays(1), now.plusDays(300), now.minusDays(3));

        List<Transaction> exported =
                transactionRepository.findForExport(now.minusDays(10), now, "PL");

        assertThat(exported).extracting(Transaction::getId)
                .containsExactly(newest.getId(), oldest.getId());
    }

    @Test
    void findForExport_shouldCoverAllCountriesWhenScopeIsNull() {
        persistTransaction(polishCustomer, "PL", TransactionType.SALE, 10,
                now.minusDays(1), now.plusDays(300), now.minusDays(2));
        persistTransaction(germanCustomer, "DE", TransactionType.SALE, 20,
                now.minusDays(1), now.plusDays(300), now.minusDays(3));

        assertThat(transactionRepository.findForExport(now.minusDays(10), now, null)).hasSize(2);
    }

    @Test
    void sumBySourceTransaction_shouldAggregateOnlyLinkedRowsOfGivenType() {
        Transaction sale = persistTransaction(polishCustomer, "PL", TransactionType.SALE, 100,
                now.minusDays(5), now.plusDays(300));
        persistReturn(polishCustomer, sale, -30, new BigDecimal("30.00"));
        persistReturn(polishCustomer, sale, -20, new BigDecimal("20.00"));

        assertThat(transactionRepository
                .sumPointsBySourceTransactionIdAndType(sale.getId(), TransactionType.RETURN)).isEqualTo(-50);
        assertThat(transactionRepository
                .sumAmountBySourceTransactionIdAndType(sale.getId(), TransactionType.RETURN))
                .isEqualByComparingTo("50.00");
    }

    /** Brak powiazanych zwrotow ma dawac zero, bo wynik wchodzi wprost do arytmetyki. */
    @Test
    void sumBySourceTransaction_shouldReturnZeroWhenNothingLinked() {
        Transaction sale = persistTransaction(polishCustomer, "PL", TransactionType.SALE, 100,
                now.minusDays(5), now.plusDays(300));

        assertThat(transactionRepository
                .sumPointsBySourceTransactionIdAndType(sale.getId(), TransactionType.RETURN)).isZero();
        assertThat(transactionRepository
                .sumAmountBySourceTransactionIdAndType(sale.getId(), TransactionType.RETURN))
                .isEqualByComparingTo("0");
    }

    @Test
    void findAllByCustomerIdOrderByTimestampAsc_shouldReturnOnlyOwnHistoryOldestFirst() {
        Transaction older = persistTransaction(polishCustomer, "PL", TransactionType.SALE, 10,
                now.minusDays(1), now.plusDays(300), now.minusDays(9));
        Transaction newer = persistTransaction(polishCustomer, "PL", TransactionType.SALE, 20,
                now.minusDays(1), now.plusDays(300), now.minusDays(2));
        persistTransaction(germanCustomer, "DE", TransactionType.SALE, 30,
                now.minusDays(1), now.plusDays(300), now.minusDays(5));

        assertThat(transactionRepository.findAllByCustomerIdOrderByTimestampAsc(polishCustomer.getId()))
                .extracting(Transaction::getId)
                .containsExactly(older.getId(), newer.getId());
    }

    @Test
    void existsBySourceTransactionNumber_shouldDetectAlreadyRegisteredSale() {
        Transaction sale = persistTransaction(polishCustomer, "PL", TransactionType.SALE, 10,
                now.minusDays(1), now.plusDays(300));
        sale.setSourceTransactionNumber("TXN-001");
        entityManager.persistAndFlush(sale);

        assertThat(transactionRepository.existsBySourceTransactionNumber("TXN-001")).isTrue();
        assertThat(transactionRepository.existsBySourceTransactionNumber("TXN-002")).isFalse();
        assertThat(transactionRepository
                .findBySourceTransactionNumberAndCustomerId("TXN-001", polishCustomer.getId())).isPresent();
        assertThat(transactionRepository
                .findBySourceTransactionNumberAndCustomerId("TXN-001", germanCustomer.getId())).isEmpty();
    }

    private Customer persistCustomer(String customerNumber, String email, String country) {
        return entityManager.persistAndFlush(Customer.builder()
                .firstName("Jan")
                .lastName("Kowalski")
                .email(email)
                .customerNumber(customerNumber)
                .phoneNumber("123456789")
                .country(country)
                .build());
    }

    private Transaction persistTransaction(Customer customer,
                                           String country,
                                           TransactionType type,
                                           int points,
                                           LocalDateTime availableFrom,
                                           LocalDateTime expiresAt) {
        return persistTransaction(customer, country, type, points, availableFrom, expiresAt, now);
    }

    private Transaction persistTransaction(Customer customer,
                                           String country,
                                           TransactionType type,
                                           int points,
                                           LocalDateTime availableFrom,
                                           LocalDateTime expiresAt,
                                           LocalDateTime timestamp) {
        return entityManager.persistAndFlush(Transaction.builder()
                .customer(customer)
                .points(points)
                .amount(BigDecimal.ZERO)
                .pointsPerCurrency(BigDecimal.ONE)
                .description(type.name())
                .country(country)
                .type(type)
                .state(TransactionState.AVAILABLE)
                .purchaseTimestamp(timestamp)
                .availableFrom(availableFrom)
                .expiresAt(expiresAt)
                .timestamp(timestamp)
                .build());
    }

    private void persistReturn(Customer customer, Transaction source, int points, BigDecimal amount) {
        entityManager.persistAndFlush(Transaction.builder()
                .customer(customer)
                .points(points)
                .amount(amount)
                .pointsPerCurrency(BigDecimal.ONE)
                .description("RETURN")
                .country(source.getCountry())
                .type(TransactionType.RETURN)
                .state(TransactionState.AVAILABLE)
                .purchaseTimestamp(now)
                .availableFrom(now)
                .expiresAt(now.plusDays(300))
                .timestamp(now)
                .sourceTransaction(source)
                .build());
    }
}
