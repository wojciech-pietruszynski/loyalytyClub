package pl.pietruszynski.loyaltyclub.api.admin.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.pietruszynski.loyaltyclub.api.admin.dto.TechnicalUserCreateRequest;
import pl.pietruszynski.loyaltyclub.api.admin.model.TechnicalUser;
import pl.pietruszynski.loyaltyclub.api.admin.repository.TechnicalUserRepository;
import pl.pietruszynski.loyaltyclub.exception.BusinessException;
import pl.pietruszynski.loyaltyclub.exception.ResourceNotFoundException;
import pl.pietruszynski.loyaltyclub.security.TokenRevocationService;
import pl.pietruszynski.loyaltyclub.util.PasswordGenerator;
import pl.pietruszynski.loyaltyclub.util.PasswordPolicy;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TechnicalUserService {

    private final TechnicalUserRepository technicalUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordGenerator passwordGenerator;
    private final TokenRevocationService tokenRevocationService;

    @Value("${app.available-country-codes:PL}")
    private String availableCountryCodesConfig;

    public List<TechnicalUser> getAllTechnicalUsers() {
        return technicalUserRepository.findAll().stream()
                .sorted((a, b) -> a.getUsername().compareToIgnoreCase(b.getUsername()))
                .toList();
    }

    /**
     * Tworzy konto techniczne. Haslo pochodzi z zadania albo -- gdy go nie podano --
     * jest generowane po stronie serwera. W obu przypadkach zwracamy je wraz
     * z kontem i nigdzie nie utrwalamy: to jedyna chwila, w ktorej mozna je odczytac.
     */
    @Transactional
    public TechnicalUserWithPassword createTechnicalUser(TechnicalUserCreateRequest request) {
        if (isBlank(request.username()) || isBlank(request.country())) {
            throw new BusinessException("Username and country are required");
        }
        String normalizedCountry = normalizeCountryCode(request.country());
        if (!getAvailableCountryCodesSet().contains(normalizedCountry)) {
            throw new BusinessException("Country code is not allowed");
        }
        if (technicalUserRepository.existsByUsername(request.username().trim())) {
            throw new BusinessException("Technical user with this username already exists");
        }

        String password = resolvePassword(request.password());

        TechnicalUser user = technicalUserRepository.save(TechnicalUser.builder()
                .username(request.username().trim())
                .password(passwordEncoder.encode(password))
                .passwordChangedAt(LocalDateTime.now())
                .country(normalizedCountry)
                .enabled(request.enabled() == null || request.enabled())
                .build());

        return new TechnicalUserWithPassword(user, password);
    }

    @Transactional
    public TechnicalUser setTechnicalUserEnabled(Long id, boolean enabled) {
        TechnicalUser user = findById(id);
        user.setEnabled(enabled);
        TechnicalUser saved = technicalUserRepository.save(user);
        if (!enabled) {
            // Dezaktywacja bez wycofania tokenow bylaby pozorna: wydany token
            // dzialalby jeszcze do konca swojego okresu waznosci.
            tokenRevocationService.revokeAllTokensFor(saved.getUsername(), "ACCOUNT_DISABLED");
        }
        return saved;
    }

    /** Ustawia haslo podane przez administratora. Odpowiedz nie zawiera hasla. */
    @Transactional
    public TechnicalUser updateTechnicalUserPassword(Long id, String password) {
        PasswordPolicy.validate(password);
        TechnicalUser user = findById(id);
        applyPassword(user, password);
        return technicalUserRepository.save(user);
    }

    /**
     * Reset inicjowany przez role ADMIN: generuje nowe haslo jednorazowe i zwraca
     * je raz. Zastepuje odczyt hasla z bazy, ktory byl mozliwy, dopoki konto
     * przechowywalo haslo w postaci jawnej.
     */
    @Transactional
    public TechnicalUserWithPassword resetTechnicalUserPassword(Long id) {
        TechnicalUser user = findById(id);
        String password = passwordGenerator.generate();
        applyPassword(user, password);
        return new TechnicalUserWithPassword(technicalUserRepository.save(user), password);
    }

    public String resolveTechnicalUserCountry(String username) {
        TechnicalUser user = technicalUserRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Technical user not found: " + username));
        if (!user.isEnabled()) {
            throw new BusinessException("Technical user is disabled");
        }
        return normalizeCountryCode(user.getCountry());
    }

    private void applyPassword(TechnicalUser user, String password) {
        user.setPassword(passwordEncoder.encode(password));
        user.setPasswordChangedAt(LocalDateTime.now());
        tokenRevocationService.revokeAllTokensFor(user.getUsername(), "PASSWORD_CHANGED");
    }

    private String resolvePassword(String requestedPassword) {
        if (isBlank(requestedPassword)) {
            return passwordGenerator.generate();
        }
        PasswordPolicy.validate(requestedPassword);
        return requestedPassword;
    }

    private TechnicalUser findById(Long id) {
        return technicalUserRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Technical user not found with id: " + id));
    }

    private Set<String> getAvailableCountryCodesSet() {
        return Arrays.stream(availableCountryCodesConfig.split(","))
                .map(this::normalizeCountryCode)
                .filter(code -> !code.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String normalizeCountryCode(String code) {
        if (code == null) {
            return "";
        }
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /** Konto wraz z haslem jednorazowym, ktore wolno pokazac dokladnie raz. */
    public record TechnicalUserWithPassword(TechnicalUser user, String oneTimePassword) {
    }
}
