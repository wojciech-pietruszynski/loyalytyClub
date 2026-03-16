package pl.pietruszynski.loyaltyclub.api.admin.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.pietruszynski.loyaltyclub.api.admin.model.TechnicalUser;

import java.util.Optional;

public interface TechnicalUserRepository extends JpaRepository<TechnicalUser, Long> {
    Optional<TechnicalUser> findByUsername(String username);
    boolean existsByUsername(String username);
}
