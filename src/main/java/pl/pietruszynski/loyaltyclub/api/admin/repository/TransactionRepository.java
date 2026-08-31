package pl.pietruszynski.loyaltyclub.api.admin.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pl.pietruszynski.loyaltyclub.api.admin.model.Transaction;
import pl.pietruszynski.loyaltyclub.api.admin.model.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findAllByCustomerIdOrderByTimestampAsc(Long customerId);
    List<Transaction> findAllByCustomerIdOrderByPurchaseTimestampAsc(Long customerId);
    Page<Transaction> findAllByCustomerId(Long customerId, Pageable pageable);
    Optional<Transaction> findByIdAndCustomerId(Long id, Long customerId);
    Optional<Transaction> findBySourceTransactionNumberAndCustomerId(String sourceTransactionNumber, Long customerId);
    boolean existsBySourceTransactionNumber(String sourceTransactionNumber);

    @Query("""
            SELECT COALESCE(SUM(t.points), 0)
            FROM Transaction t
            WHERE t.sourceTransaction.id = :sourceTransactionId
              AND t.type = :type
            """)
    int sumPointsBySourceTransactionIdAndType(Long sourceTransactionId, TransactionType type);

    @Query("""
            SELECT COALESCE(SUM(t.amount), 0)
            FROM Transaction t
            WHERE t.sourceTransaction.id = :sourceTransactionId
              AND t.type = :type
            """)
    BigDecimal sumAmountBySourceTransactionIdAndType(Long sourceTransactionId, TransactionType type);

    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.timestamp >= :since")
    long countSince(LocalDateTime since);

    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.timestamp >= :since AND t.country = :country")
    long countSinceForCountry(LocalDateTime since, String country);

    /**
     * Suma punktow w zadanym stanie, liczona z dat -- dokladnie tak, jak stan
     * transakcji wyznacza aplikacja. Wczesniej zestawienie zbiorcze sumowalo
     * kolumne {@code customers.loyalty_points}, ktora jest odswiezana dopiero przy
     * operacji dotyczacej danego klienta, wiec raport pokazywal takze punkty
     * niedostepne (oczekujace i wygasle).
     *
     * <p>Korekty reczne i operacje kuponowe nie maja karencji ani daty wygasniecia,
     * dlatego liczone sa zawsze jako dostepne.
     */
    @Query("""
            SELECT COALESCE(SUM(t.points), 0) FROM Transaction t
            WHERE (:country IS NULL OR t.country = :country)
              AND (t.type IN :immediateTypes
                   OR (t.availableFrom <= :now AND t.expiresAt >= :now))
            """)
    long sumAvailablePoints(LocalDateTime now, String country, List<TransactionType> immediateTypes);

    @Query("""
            SELECT COALESCE(SUM(t.points), 0) FROM Transaction t
            WHERE (:country IS NULL OR t.country = :country)
              AND t.type NOT IN :immediateTypes
              AND t.availableFrom > :now
            """)
    long sumPendingPoints(LocalDateTime now, String country, List<TransactionType> immediateTypes);

    @Query("""
            SELECT COALESCE(SUM(t.points), 0) FROM Transaction t
            WHERE (:country IS NULL OR t.country = :country)
              AND t.type NOT IN :immediateTypes
              AND t.expiresAt < :now
            """)
    long sumExpiredPoints(LocalDateTime now, String country, List<TransactionType> immediateTypes);

    /**
     * Transakcje, ktorych punkty sa juz dostepne i wygasna w zadanym oknie --
     * podstawa powiadomien o wygasajacych punktach oraz raportu dla panelu.
     */
    @Query("""
            SELECT t FROM Transaction t
            WHERE t.type NOT IN :immediateTypes
              AND t.points > 0
              AND t.availableFrom <= :now
              AND t.expiresAt > :now
              AND t.expiresAt <= :until
              AND (:country IS NULL OR t.country = :country)
            ORDER BY t.expiresAt ASC
            """)
    List<Transaction> findExpiringBetween(LocalDateTime now,
                                          LocalDateTime until,
                                          String country,
                                          List<TransactionType> immediateTypes);

    @Query("""
            SELECT t FROM Transaction t
            WHERE t.timestamp >= :from AND t.timestamp <= :to
              AND (:country IS NULL OR t.country = :country)
            ORDER BY t.timestamp DESC
            """)
    List<Transaction> findForExport(LocalDateTime from, LocalDateTime to, String country);
}
