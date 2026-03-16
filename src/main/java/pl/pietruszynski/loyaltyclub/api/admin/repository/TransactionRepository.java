package pl.pietruszynski.loyaltyclub.api.admin.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pl.pietruszynski.loyaltyclub.api.admin.model.Transaction;
import pl.pietruszynski.loyaltyclub.api.admin.model.TransactionType;

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
}


