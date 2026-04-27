package pl.pietruszynski.loyaltyclub.api.admin.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pl.pietruszynski.loyaltyclub.api.admin.model.Transaction;
import pl.pietruszynski.loyaltyclub.api.admin.model.TransactionType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findAllByCustomerIdOrderByTimestampAsc(Long customerId);
    List<Transaction> findAllByCustomerIdOrderByPurchaseTimestampAsc(Long customerId);
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
    java.math.BigDecimal sumAmountBySourceTransactionIdAndType(Long sourceTransactionId, TransactionType type);

    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.timestamp >= :since")
    long countSince(LocalDateTime since);

    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.timestamp >= :since AND t.country = :country")
    long countSinceForCountry(LocalDateTime since, String country);

    @Query("""
            SELECT t FROM Transaction t
            WHERE t.timestamp >= :from AND t.timestamp <= :to
              AND (:country IS NULL OR t.country = :country)
            ORDER BY t.timestamp DESC
            """)
    List<Transaction> findForExport(LocalDateTime from, LocalDateTime to, String country);
}


