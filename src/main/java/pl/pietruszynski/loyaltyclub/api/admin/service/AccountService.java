package pl.pietruszynski.loyaltyclub.api.admin.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.pietruszynski.loyaltyclub.api.admin.model.AdminUser;
import pl.pietruszynski.loyaltyclub.api.admin.model.TechnicalUser;
import pl.pietruszynski.loyaltyclub.api.admin.repository.AdminUserRepository;
import pl.pietruszynski.loyaltyclub.api.admin.repository.TechnicalUserRepository;
import pl.pietruszynski.loyaltyclub.exception.BusinessException;
import pl.pietruszynski.loyaltyclub.exception.ResourceNotFoundException;
import pl.pietruszynski.loyaltyclub.security.TokenRevocationService;
import pl.pietruszynski.loyaltyclub.util.PasswordPolicy;

import java.time.LocalDateTime;

/**
 * Samoobsługowa zmiana hasla uzytkownika panelu. Do tej pory zadne konto -- ani
 * administracyjne, ani techniczne -- nie mialo sposobu na zmiane wlasnego hasla:
 * konta administracyjne zakladal seeder, a techniczne mogl zmienic wylacznie
 * administrator.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountService {

    private final AdminUserRepository adminUserRepository;
    private final TechnicalUserRepository technicalUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenRevocationService tokenRevocationService;

    /**
     * Zmienia haslo zalogowanego uzytkownika po weryfikacji dotychczasowego.
     * Zmiana uniewaznia wszystkie wydane wczesniej tokeny konta -- takze ten,
     * ktorym wykonano samo zadanie.
     */
    @Transactional
    public void changeOwnPassword(String username, String currentPassword, String newPassword) {
        PasswordPolicy.validate(newPassword);
        if (newPassword.equals(currentPassword)) {
            throw new BusinessException("New password must be different from the current one");
        }

        AdminUser adminUser = adminUserRepository.findByUsername(username).orElse(null);
        if (adminUser != null) {
            verifyCurrentPassword(currentPassword, adminUser.getPassword());
            adminUser.setPassword(passwordEncoder.encode(newPassword));
            adminUser.setPasswordChangedAt(LocalDateTime.now());
            adminUserRepository.save(adminUser);
            tokenRevocationService.revokeAllTokensFor(username, "PASSWORD_CHANGED");
            return;
        }

        TechnicalUser technicalUser = technicalUserRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
        verifyCurrentPassword(currentPassword, technicalUser.getPassword());
        technicalUser.setPassword(passwordEncoder.encode(newPassword));
        technicalUser.setPasswordChangedAt(LocalDateTime.now());
        technicalUserRepository.save(technicalUser);
        tokenRevocationService.revokeAllTokensFor(username, "PASSWORD_CHANGED");
    }

    private void verifyCurrentPassword(String provided, String storedHash) {
        if (!passwordEncoder.matches(provided, storedHash)) {
            throw new BusinessException("Current password is incorrect");
        }
    }
}
