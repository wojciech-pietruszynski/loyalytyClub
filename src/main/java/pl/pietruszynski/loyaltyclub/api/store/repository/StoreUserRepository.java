package pl.pietruszynski.loyaltyclub.api.store.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.pietruszynski.loyaltyclub.api.store.model.StoreUser;

import java.util.Optional;

public interface StoreUserRepository extends JpaRepository<StoreUser, Long> {
    Optional<StoreUser> findByUsername(String username);
}


