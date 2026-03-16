package pl.pietruszynski.loyaltyclub.api.ecom.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.pietruszynski.loyaltyclub.api.ecom.model.EcomUser;

import java.util.Optional;

public interface EcomUserRepository extends JpaRepository<EcomUser, Long> {
    Optional<EcomUser> findByUsername(String username);
}


