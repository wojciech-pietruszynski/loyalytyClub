package pl.pietruszynski.loyaltyclub.api.admin.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import pl.pietruszynski.loyaltyclub.api.admin.dto.CouponIssueRequest;
import pl.pietruszynski.loyaltyclub.api.admin.dto.CouponTemplateCreateRequest;
import pl.pietruszynski.loyaltyclub.api.admin.dto.StorePromotionCreateRequest;
import pl.pietruszynski.loyaltyclub.api.admin.model.CouponReason;
import pl.pietruszynski.loyaltyclub.api.admin.model.CouponPrefix;
import pl.pietruszynski.loyaltyclub.api.admin.model.CouponStatus;
import pl.pietruszynski.loyaltyclub.api.admin.model.CouponTemplate;
import pl.pietruszynski.loyaltyclub.api.admin.model.Customer;
import pl.pietruszynski.loyaltyclub.api.admin.model.CustomerCoupon;
import pl.pietruszynski.loyaltyclub.api.admin.model.Transaction;
import pl.pietruszynski.loyaltyclub.api.admin.model.TransactionState;
import pl.pietruszynski.loyaltyclub.api.admin.model.TransactionType;
import pl.pietruszynski.loyaltyclub.api.admin.repository.CouponTemplateRepository;
import pl.pietruszynski.loyaltyclub.api.admin.repository.CouponPrefixRepository;
import pl.pietruszynski.loyaltyclub.api.admin.repository.CustomerCouponRepository;
import pl.pietruszynski.loyaltyclub.api.admin.repository.CustomerRepository;
import pl.pietruszynski.loyaltyclub.api.admin.repository.TransactionRepository;
import pl.pietruszynski.loyaltyclub.api.store.model.StorePointsPromotion;
import pl.pietruszynski.loyaltyclub.api.store.repository.StorePointsPromotionRepository;
import pl.pietruszynski.loyaltyclub.exception.ResourceNotFoundException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LoyaltyService {

    private final CustomerRepository customerRepository;
    private final TransactionRepository transactionRepository;
    private final CouponTemplateRepository couponTemplateRepository;
    private final CouponPrefixRepository couponPrefixRepository;
    private final CustomerCouponRepository customerCouponRepository;
    private final StorePointsPromotionRepository storePointsPromotionRepository;

    @Value("${app.available-country-codes:PL}")
    private String availableCountryCodesConfig;

    public List<Customer> getAllCustomers() {
        return getAllCustomers(null);
    }

    public List<Customer> getAllCustomers(String countryScope) {
        if (countryScope != null && !countryScope.isBlank()) {
            return customerRepository.findAllByCountry(normalizeCountryCode(countryScope));
        }
        return customerRepository.findAll();
    }

    public Customer getCustomerById(Long id) {
        return getCustomerById(id, null);
    }

    public Customer getCustomerById(Long id, String countryScope) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with id: " + id));
        ensureInScope(customer.getCountry(), countryScope);
        return customer;
    }

    @Transactional
    public Customer createCustomer(Customer customer) {
        return createCustomer(customer, null);
    }

    @Transactional
    public Customer createCustomer(Customer customer, String countryScope) {
        validateCustomerForCreate(customer);
        String normalizedCountry = normalizeCountryCode(customer.getCountry());
        ensureInScope(normalizedCountry, countryScope);

        if (customerRepository.existsByEmail(customer.getEmail())) {
            throw new RuntimeException("Customer with this email already exists");
        }

        if (customerRepository.existsByCustomerNumber(customer.getCustomerNumber())) {
            throw new RuntimeException("Customer with this customer number already exists");
        }

        if (!getAvailableCountryCodesSet().contains(normalizedCountry)) {
            throw new RuntimeException("Country code is not allowed");
        }

        if (customer.getLoyaltyPoints() == null) {
            customer.setLoyaltyPoints(0);
        }
        customer.setCountry(normalizedCountry);
        return customerRepository.save(customer);
    }

    @Transactional
    public Customer updateCustomer(Long id, Customer updates) {
        return updateCustomer(id, updates, null);
    }

    @Transactional
    public Customer updateCustomer(Long id, Customer updates, String countryScope) {
        validateCustomerForUpdate(updates);
        Customer customer = getCustomerById(id, countryScope);
        String normalizedCountry = normalizeCountryCode(updates.getCountry());
        ensureInScope(normalizedCountry, countryScope);

        if (!getAvailableCountryCodesSet().contains(normalizedCountry)) {
            throw new RuntimeException("Country code is not allowed");
        }
        if (customerRepository.existsByEmailAndIdNot(updates.getEmail(), id)) {
            throw new RuntimeException("Customer with this email already exists");
        }
        if (customerRepository.existsByCustomerNumberAndIdNot(updates.getCustomerNumber(), id)) {
            throw new RuntimeException("Customer with this customer number already exists");
        }

        customer.setFirstName(updates.getFirstName());
        customer.setLastName(updates.getLastName());
        customer.setEmail(updates.getEmail());
        customer.setCustomerNumber(updates.getCustomerNumber());
        customer.setPhoneNumber(updates.getPhoneNumber());
        customer.setCountry(normalizedCountry);

        return customerRepository.save(customer);
    }

    public List<String> getAvailableCountryCodes() {
        return getAvailableCountryCodes(null);
    }

    public List<String> getAvailableCountryCodes(String countryScope) {
        if (countryScope != null && !countryScope.isBlank()) {
            return List.of(normalizeCountryCode(countryScope));
        }
        return List.copyOf(getAvailableCountryCodesSet());
    }

    public List<CustomerCoupon> getIssuedCoupons() {
        return getIssuedCoupons(null);
    }

    public List<CustomerCoupon> getIssuedCoupons(String countryScope) {
        if (countryScope != null && !countryScope.isBlank()) {
            return customerCouponRepository.findAllByCountryOrderByIssuedAtDesc(normalizeCountryCode(countryScope));
        }
        return customerCouponRepository.findAllByOrderByIssuedAtDesc();
    }

    public List<CustomerCoupon> getCouponsForCustomer(Long customerId) {
        return getCouponsForCustomer(customerId, null);
    }

    public List<CustomerCoupon> getCouponsForCustomer(Long customerId, String countryScope) {
        getCustomerById(customerId, countryScope);
        return customerCouponRepository.findAllByCustomerIdOrderByIssuedAtDesc(customerId);
    }

    public List<Transaction> getTransactionsForCustomer(Long customerId) {
        return getTransactionsForCustomer(customerId, null);
    }

    public List<Transaction> getTransactionsForCustomer(Long customerId, String countryScope) {
        getCustomerById(customerId, countryScope);
        return transactionRepository.findAllByCustomerIdOrderByTimestampAsc(customerId);
    }

    public List<CouponTemplate> getCouponTemplates() {
        return getCouponTemplates(null);
    }

    public List<CouponTemplate> getCouponTemplates(String countryScope) {
        if (countryScope != null && !countryScope.isBlank()) {
            return couponTemplateRepository.findAllByCountry(normalizeCountryCode(countryScope));
        }
        return couponTemplateRepository.findAll();
    }

    public List<String> getCouponPrefixes() {
        return couponPrefixRepository.findAllByOrderByValueAsc()
                .stream()
                .map(CouponPrefix::getValue)
                .toList();
    }

    public List<StorePointsPromotion> getStorePromotions(String countryScope) {
        if (countryScope == null || countryScope.isBlank()) {
            return storePointsPromotionRepository.findAllByOrderByStartsAtDesc();
        }
        String normalizedCountryScope = normalizeCountryCode(countryScope);
        return storePointsPromotionRepository.findAllByCountryOrderByStartsAtDesc(normalizedCountryScope);
    }

    @Transactional
    public StorePointsPromotion createStorePromotion(StorePromotionCreateRequest request, String countryScope) {
        validateStorePromotionRequest(request);
        String normalizedCountry = normalizeCountryCode(request.country());
        ensureInScope(normalizedCountry, countryScope);
        if (!getAvailableCountryCodesSet().contains(normalizedCountry)) {
            throw new RuntimeException("Country code is not allowed");
        }

        return storePointsPromotionRepository.save(StorePointsPromotion.builder()
                .name(request.name().trim())
                .country(normalizedCountry)
                .pointsPerCurrency(request.pointsPerCurrency())
                .startsAt(request.startsAt())
                .endsAt(request.endsAt())
                .enabled(request.enabled() == null || request.enabled())
                .build());
    }

    @Transactional
    public StorePointsPromotion updateStorePromotion(Long id, StorePromotionCreateRequest request, String countryScope) {
        validateStorePromotionRequest(request);
        StorePointsPromotion promotion = storePointsPromotionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Store promotion not found with id: " + id));

        ensureInScope(promotion.getCountry(), countryScope);
        String normalizedCountry = normalizeCountryCode(request.country());
        ensureInScope(normalizedCountry, countryScope);
        if (!getAvailableCountryCodesSet().contains(normalizedCountry)) {
            throw new RuntimeException("Country code is not allowed");
        }

        promotion.setName(request.name().trim());
        promotion.setCountry(normalizedCountry);
        promotion.setPointsPerCurrency(request.pointsPerCurrency());
        promotion.setStartsAt(request.startsAt());
        promotion.setEndsAt(request.endsAt());
        promotion.setEnabled(request.enabled() == null || request.enabled());
        return storePointsPromotionRepository.save(promotion);
    }

    @Transactional
    public StorePointsPromotion setStorePromotionEnabled(Long id, boolean enabled, String countryScope) {
        StorePointsPromotion promotion = storePointsPromotionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Store promotion not found with id: " + id));
        ensureInScope(promotion.getCountry(), countryScope);
        promotion.setEnabled(enabled);
        return storePointsPromotionRepository.save(promotion);
    }

    public CouponTemplate createCouponTemplate(CouponTemplateCreateRequest request) {
        return createCouponTemplate(request, null);
    }

    @Transactional
    public CouponTemplate createCouponTemplate(CouponTemplateCreateRequest request, String countryScope) {
        validateCouponTemplateCreateRequest(request);

        String normalizedCountry = normalizeCountryCode(request.country());
        String normalizedPrefix = request.couponPrefix().trim().toUpperCase(Locale.ROOT);
        ensureInScope(normalizedCountry, countryScope);

        if (!getAvailableCountryCodesSet().contains(normalizedCountry)) {
            throw new RuntimeException("Country code is not allowed");
        }
        if (!couponPrefixRepository.existsByValue(normalizedPrefix)) {
            throw new RuntimeException("Coupon prefix is not allowed");
        }

        boolean exists = couponTemplateRepository.existsByCouponValueAndMinimumPurchaseValueAndRequiredPointsAndCountryAndValidityDaysAndCouponPrefix(
                request.couponValue(),
                request.minimumPurchaseValue(),
                request.requiredPoints(),
                normalizedCountry,
                request.validityDays(),
                normalizedPrefix
        );
        if (exists) {
            throw new RuntimeException("Coupon template already exists");
        }

        return couponTemplateRepository.save(CouponTemplate.builder()
                .couponValue(request.couponValue())
                .minimumPurchaseValue(request.minimumPurchaseValue())
                .requiredPoints(request.requiredPoints())
                .country(normalizedCountry)
                .validityDays(request.validityDays())
                .couponPrefix(normalizedPrefix)
                .build());
    }

    @Transactional
    public CustomerCoupon issueCoupon(CouponIssueRequest request) {
        return issueCoupon(request, null);
    }

    @Transactional
    public CustomerCoupon issueCoupon(CouponIssueRequest request, String countryScope) {
        validateCouponIssueRequest(request);

        Customer customer = getCustomerById(request.customerId(), countryScope);
        CouponTemplate template = couponTemplateRepository.findById(request.couponTemplateId())
                .orElseThrow(() -> new ResourceNotFoundException("Coupon template not found"));
        String normalizedCountry = normalizeCountryCode(template.getCountry());
        CouponReason reason = request.reason();
        ensureInScope(normalizedCountry, countryScope);

        if (!customer.getCountry().equals(normalizedCountry)) {
            throw new RuntimeException("Customer country does not match coupon country");
        }

        if (reason == CouponReason.POINTS_EXCHANGE) {
            if (customer.getLoyaltyPoints() < template.getRequiredPoints()) {
                throw new RuntimeException("Not enough points to issue this coupon");
            }

            customer.setLoyaltyPoints(customer.getLoyaltyPoints() - template.getRequiredPoints());
            customerRepository.save(customer);

            Transaction transaction = Transaction.builder()
                    .customer(customer)
                    .points(-template.getRequiredPoints())
                    .amount(BigDecimal.ZERO)
                    .pointsPerCurrency(BigDecimal.ONE)
                    .description("Issued coupon (points exchange): " + template.getCouponPrefix())
                    .country(customer.getCountry())
                    .type(TransactionType.MANUAL_ADJUSTMENT)
                    .state(TransactionState.AVAILABLE)
                    .build();
            transactionRepository.save(transaction);
        }

        LocalDateTime issuedAt = LocalDateTime.now();
        String couponCode = generateUniqueCouponCode(template.getCouponPrefix());

        return customerCouponRepository.save(CustomerCoupon.builder()
                .couponCode(couponCode)
                .country(normalizedCountry)
                .customer(customer)
                .couponTemplate(template)
                .reason(reason)
                .status(CouponStatus.ACTIVE)
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plusDays(template.getValidityDays()))
                .build());
    }

    @Transactional
    public int importCustomersFromCsv(MultipartFile file) {
        return importCustomersFromCsv(file, null);
    }

    @Transactional
    public int importCustomersFromCsv(MultipartFile file, String countryScope) {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("CSV file is empty");
        }

        int imported = 0;
        int lineNumber = 0;
        Character delimiter = null;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.trim().isEmpty()) {
                    continue;
                }

                if (delimiter == null) {
                    delimiter = detectDelimiter(line);
                }

                List<String> values = splitCsvLine(line, delimiter);
                if (imported == 0 && isHeaderRow(values)) {
                    continue;
                }

                if (values.size() != 6) {
                    throw new RuntimeException("Invalid CSV format at line " + lineNumber + ". Expected 6 columns.");
                }

                try {
                    Customer customer = Customer.builder()
                            .firstName(values.get(0).trim())
                            .lastName(values.get(1).trim())
                            .email(values.get(2).trim())
                            .customerNumber(values.get(3).trim())
                            .phoneNumber(values.get(4).trim())
                            .country(values.get(5).trim())
                            .build();
                    createCustomer(customer, countryScope);
                    imported++;
                } catch (RuntimeException ex) {
                    throw new RuntimeException("CSV import error at line " + lineNumber + ": " + ex.getMessage());
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Cannot read CSV file", e);
        }

        if (imported == 0) {
            throw new RuntimeException("No customers imported from CSV");
        }

        return imported;
    }

    @Transactional
    public void addPoints(Long customerId, Integer points, String description) {
        Customer customer = getCustomerById(customerId);
        customer.setLoyaltyPoints(customer.getLoyaltyPoints() + points);

        Transaction transaction = Transaction.builder()
                .customer(customer)
                .points(points)
                .amount(BigDecimal.ZERO)
                .pointsPerCurrency(BigDecimal.ONE)
                .description(description)
                .country(customer.getCountry())
                .type(TransactionType.MANUAL_ADJUSTMENT)
                .state(TransactionState.AVAILABLE)
                .build();

        transactionRepository.save(transaction);
        customerRepository.save(customer);
    }

    private void validateCustomerForCreate(Customer customer) {
        if (isBlank(customer.getFirstName())
                || isBlank(customer.getLastName())
                || isBlank(customer.getEmail())
                || isBlank(customer.getCustomerNumber())
                || isBlank(customer.getPhoneNumber())
                || isBlank(customer.getCountry())) {
            throw new RuntimeException("All customer fields are required");
        }
    }

    private void validateCustomerForUpdate(Customer customer) {
        if (isBlank(customer.getFirstName())
                || isBlank(customer.getLastName())
                || isBlank(customer.getEmail())
                || isBlank(customer.getCustomerNumber())
                || isBlank(customer.getPhoneNumber())
                || isBlank(customer.getCountry())) {
            throw new RuntimeException("All customer fields are required");
        }
    }

    private void validateCouponIssueRequest(CouponIssueRequest request) {
        if (request.customerId() == null || request.couponTemplateId() == null || request.reason() == null) {
            throw new RuntimeException("All coupon fields are required");
        }
    }

    private void validateStorePromotionRequest(StorePromotionCreateRequest request) {
        if (isBlank(request.name())
                || isBlank(request.country())
                || request.pointsPerCurrency() == null
                || request.startsAt() == null) {
            throw new RuntimeException("All store promotion fields are required");
        }
        if (request.pointsPerCurrency().signum() <= 0) {
            throw new RuntimeException("Points per currency must be greater than zero");
        }
        if (request.endsAt() != null && request.endsAt().isBefore(request.startsAt())) {
            throw new RuntimeException("Promotion end date must be after start date");
        }
    }

    private void validateCouponTemplateCreateRequest(CouponTemplateCreateRequest request) {
        if (request.couponValue() == null
                || request.minimumPurchaseValue() == null
                || request.requiredPoints() == null
                || request.validityDays() == null
                || isBlank(request.country())
                || isBlank(request.couponPrefix())) {
            throw new RuntimeException("All coupon template fields are required");
        }

        if (request.couponValue().signum() <= 0
                || request.minimumPurchaseValue().signum() < 0
                || request.requiredPoints() <= 0
                || request.validityDays() <= 0) {
            throw new RuntimeException("Coupon template values must be greater than zero");
        }
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

    private boolean isInScope(String country, String countryScope) {
        if (countryScope == null || countryScope.isBlank()) {
            return true;
        }
        return normalizeCountryCode(country).equals(normalizeCountryCode(countryScope));
    }

    private void ensureInScope(String country, String countryScope) {
        if (!isInScope(country, countryScope)) {
            throw new RuntimeException("Access denied for this country");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private Character detectDelimiter(String line) {
        long semicolonCount = line.chars().filter(ch -> ch == ';').count();
        long commaCount = line.chars().filter(ch -> ch == ',').count();
        return semicolonCount > commaCount ? ';' : ',';
    }

    private boolean isHeaderRow(List<String> values) {
        if (values.size() != 6) {
            return false;
        }
        return normalizeHeader(values.get(0)).equals("imie")
                && normalizeHeader(values.get(1)).equals("nazwisko")
                && normalizeHeader(values.get(2)).equals("email")
                && normalizeHeader(values.get(3)).equals("numerklienta")
                && normalizeHeader(values.get(4)).equals("numertelefonu")
                && normalizeHeader(values.get(5)).equals("kraj");
    }

    private String normalizeHeader(String value) {
        return value == null
                ? ""
                : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private List<String> splitCsvLine(String line, char delimiter) {
        List<String> values = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                inQuotes = !inQuotes;
                continue;
            }
            if (ch == delimiter && !inQuotes) {
                values.add(current.toString());
                current.setLength(0);
                continue;
            }
            current.append(ch);
        }
        values.add(current.toString());
        return values;
    }

    private String generateUniqueCouponCode(String prefix) {
        String normalizedPrefix = prefix.trim().toUpperCase(Locale.ROOT);
        for (int i = 0; i < 100; i++) {
            String code = normalizedPrefix + generateElevenDigits();
            if (!customerCouponRepository.existsByCouponCode(code)) {
                return code;
            }
        }
        throw new RuntimeException("Cannot generate unique coupon code");
    }

    private String generateElevenDigits() {
        StringBuilder digits = new StringBuilder(11);
        for (int i = 0; i < 11; i++) {
            digits.append(ThreadLocalRandom.current().nextInt(10));
        }
        return digits.toString();
    }

}
