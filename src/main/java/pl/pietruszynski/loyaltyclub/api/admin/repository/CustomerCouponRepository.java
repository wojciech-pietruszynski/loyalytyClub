package pl.pietruszynski.loyaltyclub.api.admin.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import pl.pietruszynski.loyaltyclub.api.admin.model.CustomerCoupon;

import java.util.List;
import java.util.Optional;

public interface CustomerCouponRepository extends JpaRepository<CustomerCoupon, Long> {
    boolean existsByCouponCode(String couponCode);
    @EntityGraph(attributePaths = {"customer", "couponTemplate"})
    Optional<CustomerCoupon> findByCouponCode(String couponCode);
    @EntityGraph(attributePaths = {"customer", "couponTemplate"})
    List<CustomerCoupon> findAllByOrderByIssuedAtDesc();
    @EntityGraph(attributePaths = {"customer", "couponTemplate"})
    List<CustomerCoupon> findAllByCustomerIdOrderByIssuedAtDesc(Long customerId);
}
