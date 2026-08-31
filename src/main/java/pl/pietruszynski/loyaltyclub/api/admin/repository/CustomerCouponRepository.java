package pl.pietruszynski.loyaltyclub.api.admin.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import pl.pietruszynski.loyaltyclub.api.admin.model.CouponStatus;
import pl.pietruszynski.loyaltyclub.api.admin.model.CustomerCoupon;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CustomerCouponRepository extends JpaRepository<CustomerCoupon, Long> {
    boolean existsByCouponCode(String couponCode);

    @EntityGraph(attributePaths = {"customer", "couponTemplate"})
    Optional<CustomerCoupon> findByCouponCode(String couponCode);

    @EntityGraph(attributePaths = {"customer", "couponTemplate"})
    List<CustomerCoupon> findAllByOrderByIssuedAtDesc();

    @EntityGraph(attributePaths = {"customer", "couponTemplate"})
    List<CustomerCoupon> findAllByCountryOrderByIssuedAtDesc(String country);

    @EntityGraph(attributePaths = {"customer", "couponTemplate"})
    List<CustomerCoupon> findAllByCustomerIdOrderByIssuedAtDesc(Long customerId);

    @EntityGraph(attributePaths = {"customer", "couponTemplate"})
    @Query("SELECT c FROM CustomerCoupon c WHERE (:country IS NULL OR c.country = :country)")
    Page<CustomerCoupon> findPage(String country, Pageable pageable);

    @EntityGraph(attributePaths = {"customer", "couponTemplate"})
    Page<CustomerCoupon> findAllByCustomerId(Long customerId, Pageable pageable);

    /**
     * Kupony wymagajace utrwalenia stanu {@code EXPIRED}. Stan wyliczany jest przy
     * kazdym odczycie, wiec zapytanie sluzy wylacznie porzadkowaniu bazy przez
     * zadanie cykliczne -- prezentacja nie zalezy od tego, czy zdazylo sie wykonac.
     */
    @Query("""
            SELECT c FROM CustomerCoupon c
            WHERE c.status = :activeStatus AND c.expiresAt <= :now
            """)
    List<CustomerCoupon> findLapsedActiveCoupons(CouponStatus activeStatus, LocalDateTime now);
}
