package pl.pietruszynski.loyaltyclub.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.Optional;

public interface IdempotentOperationRepository extends JpaRepository<IdempotentOperation, Long> {

    Optional<IdempotentOperation> findByOperationAndIdempotencyKey(String operation, String idempotencyKey);

    @Modifying
    @Query("DELETE FROM IdempotentOperation o WHERE o.createdAt < :before")
    int deleteCreatedBefore(LocalDateTime before);
}
