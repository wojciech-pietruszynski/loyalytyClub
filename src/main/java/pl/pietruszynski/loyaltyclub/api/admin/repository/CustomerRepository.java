package pl.pietruszynski.loyaltyclub.api.admin.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pl.pietruszynski.loyaltyclub.api.admin.model.Customer;

import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    List<Customer> findAllByCountry(String country);
    Optional<Customer> findByEmail(String email);
    Optional<Customer> findByCustomerNumber(String customerNumber);
    boolean existsByEmail(String email);
    boolean existsByCustomerNumber(String customerNumber);
    boolean existsByEmailAndIdNot(String email, Long id);
    boolean existsByCustomerNumberAndIdNot(String customerNumber, Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Customer c WHERE c.customerNumber = :customerNumber")
    Optional<Customer> findByCustomerNumberForUpdate(String customerNumber);

    boolean existsByReferralCode(String referralCode);

    long countByCountry(String country);

    @org.springframework.data.jpa.repository.Query("SELECT COALESCE(SUM(c.loyaltyPoints), 0) FROM Customer c")
    long sumLoyaltyPoints();

    @org.springframework.data.jpa.repository.Query("SELECT COALESCE(SUM(c.loyaltyPoints), 0) FROM Customer c WHERE c.country = :country")
    long sumLoyaltyPointsByCountry(String country);
}


