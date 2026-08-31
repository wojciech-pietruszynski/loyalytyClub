package pl.pietruszynski.loyaltyclub.security;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserTokenCutoffRepository extends JpaRepository<UserTokenCutoff, Long> {

    Optional<UserTokenCutoff> findByUsername(String username);
}
