package pl.pietruszynski.loyaltyclub.api.admin.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.pietruszynski.loyaltyclub.api.admin.model.CouponPrefix;

import java.util.List;

public interface CouponPrefixRepository extends JpaRepository<CouponPrefix, Long> {
    boolean existsByValue(String value);
    List<CouponPrefix> findAllByOrderByValueAsc();
}
