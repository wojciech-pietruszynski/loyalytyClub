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

    @Value("${app.available-country-codes:PL}")
    private String availableCountryCodesConfig;

    public List<TechnicalUser> getAllTechnicalUsers() {
        return technicalUserRepository.findAll().stream()
                .sorted((a, b) -> a.getUsername().compareToIgnoreCase(b.getUsername()))
                .toList();
    }

    @Transactional
    public TechnicalUser createTechnicalUser(TechnicalUserCreateRequest request) {
        if (isBlank(request.username()) || isBlank(request.password()) || isBlank(request.country())) {
            throw new BusinessException("All technical user fields are required");
        }
        String normalizedCountry = normalizeCountryCode(request.country());
        if (!getAvailableCountryCodesSet().contains(normalizedCountry)) {
            throw new BusinessException("Country code is not allowed");
        }
        if (technicalUserRepository.existsByUsername(request.username().trim())) {
            throw new BusinessException("Technical user with this username already exists");
        }

        TechnicalUser user = TechnicalUser.builder()
                .username(request.username().trim())
                .password(passwordEncoder.encode(request.password()))
                .passwordPreview(request.password())
                .country(normalizedCountry)
                .enabled(request.enabled() == null || request.enabled())
                .build();
        return technicalUserRepository.save(user);
    }

    @Transactional
    public TechnicalUser setTechnicalUserEnabled(Long id, boolean enabled) {
        TechnicalUser user = technicalUserRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Technical user not found with id: " + id));
        user.setEnabled(enabled);
        return technicalUserRepository.save(user);
    }

    @Transactional
    public TechnicalUser updateTechnicalUserPassword(Long id, String password) {
        if (isBlank(password)) {
            throw new BusinessException("Password is required");
        }
        TechnicalUser user = technicalUserRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Technical user not found with id: " + id));
        user.setPassword(passwordEncoder.encode(password));
        user.setPasswordPreview(password);
        return technicalUserRepository.save(user);
    }

    public String resolveTechnicalUserCountry(String username) {
        TechnicalUser user = technicalUserRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Technical user not found: " + username));
        if (!user.isEnabled()) {
            throw new RuntimeException("Technical user is disabled");
        }
        return normalizeCountryCode(user.getCountry());
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
}
