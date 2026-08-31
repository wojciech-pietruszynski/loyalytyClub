package pl.pietruszynski.loyaltyclub.api.admin.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pl.pietruszynski.loyaltyclub.api.admin.dto.CouponIssueRequest;
import pl.pietruszynski.loyaltyclub.api.admin.dto.CouponTemplateCreateRequest;
import pl.pietruszynski.loyaltyclub.api.admin.dto.CustomerCouponDto;
import pl.pietruszynski.loyaltyclub.api.admin.dto.CustomerDto;
import pl.pietruszynski.loyaltyclub.api.admin.dto.CustomerStatusRequest;
import pl.pietruszynski.loyaltyclub.api.admin.dto.CouponTemplateDto;
import pl.pietruszynski.loyaltyclub.api.admin.dto.HierarchyPromotionCreateRequest;
import pl.pietruszynski.loyaltyclub.api.admin.dto.HierarchyPromotionDto;
import pl.pietruszynski.loyaltyclub.api.admin.dto.PersonalDataExportDto;
import pl.pietruszynski.loyaltyclub.api.admin.dto.PointsRequest;
import pl.pietruszynski.loyaltyclub.api.admin.dto.PurchaseHistoryDto;
import pl.pietruszynski.loyaltyclub.api.admin.dto.ReferralRewardDto;
import pl.pietruszynski.loyaltyclub.api.admin.dto.StorePromotionCreateRequest;
import pl.pietruszynski.loyaltyclub.api.admin.dto.StorePromotionDto;
import pl.pietruszynski.loyaltyclub.api.admin.dto.StorePromotionStatusRequest;
import pl.pietruszynski.loyaltyclub.api.admin.dto.TransactionDto;
import pl.pietruszynski.loyaltyclub.api.admin.model.CouponTemplate;
import pl.pietruszynski.loyaltyclub.api.admin.model.Customer;
import pl.pietruszynski.loyaltyclub.api.admin.model.CustomerCoupon;
import pl.pietruszynski.loyaltyclub.api.admin.model.CustomerStatus;
import pl.pietruszynski.loyaltyclub.api.admin.model.ReferralReward;
import pl.pietruszynski.loyaltyclub.api.admin.model.Transaction;
import pl.pietruszynski.loyaltyclub.api.common.dto.PageRequests;
import pl.pietruszynski.loyaltyclub.api.common.dto.PageResponse;
import pl.pietruszynski.loyaltyclub.api.store.model.HierarchyPromotion;
import pl.pietruszynski.loyaltyclub.api.store.model.StorePointsPromotion;
import pl.pietruszynski.loyaltyclub.api.admin.audit.Auditable;
import pl.pietruszynski.loyaltyclub.api.admin.service.CustomerPrivacyService;
import pl.pietruszynski.loyaltyclub.api.admin.service.LoyaltyService;
import pl.pietruszynski.loyaltyclub.api.admin.service.LoyaltyTierService;
import pl.pietruszynski.loyaltyclub.api.admin.service.TechnicalUserService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class LoyaltyController {

    private final LoyaltyService loyaltyService;
    private final CustomerPrivacyService customerPrivacyService;
    private final TechnicalUserService technicalUserService;
    private final LoyaltyTierService loyaltyTierService;

    /** Pelna kolekcja -- kontrakt niezmieniony. Do duzych kartotek sluzy {@code /customers/paged}. */
    @GetMapping("/customers")
    public List<CustomerDto> getAllCustomers(Authentication authentication) {
        String countryScope = getCountryScope(authentication);
        return loyaltyService.getAllCustomers(countryScope).stream()
                .map(this::mapToCustomerDto)
                .toList();
    }

    /**
     * Stronicowany i wyszukiwany odczyt kartoteki.
     *
     * <p>Punkt koncowy jest nowy, a nie zmieniony: dotychczasowe {@code /customers}
     * nadal zwraca pelna kolekcje, wiec wdrozony frontend i wydane biblioteki SDK
     * dzialaja bez zmian. Wersjonowanie calego API dla jednej zmiany ksztaltu
     * odpowiedzi byloby nieproporcjonalne; sasiadujaca sciezka jest opisywalna
     * w OpenAPI i mozliwa do wygenerowania w SDK bez wariantow typu.
     */
    @GetMapping("/customers/paged")
    public PageResponse<CustomerDto> getCustomersPage(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            Authentication authentication) {

        Pageable pageable = PageRequests.of(page, size, Sort.by(Sort.Direction.ASC, "lastName", "id"));
        return PageResponse.of(
                loyaltyService.searchCustomers(query, getCountryScope(authentication), pageable),
                this::mapToCustomerDto);
    }

    @GetMapping("/config/countries")
    public List<String> getAvailableCountries(Authentication authentication) {
        return loyaltyService.getAvailableCountryCodes(getCountryScope(authentication));
    }

    @GetMapping("/config/coupon-prefixes")
    public List<String> getCouponPrefixes() {
        return loyaltyService.getCouponPrefixes();
    }

    @GetMapping("/store-promotions")
    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICAL')")
    public List<StorePromotionDto> getStorePromotions(Authentication authentication) {
        return loyaltyService.getStorePromotions(getCountryScope(authentication)).stream()
                .map(this::mapToStorePromotionDto)
                .toList();
    }

    @PostMapping("/store-promotions")
    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICAL')")
    @Auditable(value = "CREATE_STORE_PROMOTION", resourceType = "STORE_PROMOTION")
    public StorePromotionDto createStorePromotion(@Valid @RequestBody StorePromotionCreateRequest request, Authentication authentication) {
        return mapToStorePromotionDto(loyaltyService.createStorePromotion(request, getCountryScope(authentication)));
    }

    @PutMapping("/store-promotions/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICAL')")
    @Auditable(value = "UPDATE_STORE_PROMOTION", resourceType = "STORE_PROMOTION", capturePathId = true)
    public StorePromotionDto updateStorePromotion(@PathVariable Long id, @Valid @RequestBody StorePromotionCreateRequest request, Authentication authentication) {
        return mapToStorePromotionDto(loyaltyService.updateStorePromotion(id, request, getCountryScope(authentication)));
    }

    @PatchMapping("/store-promotions/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICAL')")
    @Auditable(value = "SET_STORE_PROMOTION_STATUS", resourceType = "STORE_PROMOTION", capturePathId = true)
    public StorePromotionDto setStorePromotionStatus(@PathVariable Long id, @Valid @RequestBody StorePromotionStatusRequest request, Authentication authentication) {
        return mapToStorePromotionDto(loyaltyService.setStorePromotionEnabled(id, request.enabled(), getCountryScope(authentication)));
    }

    @PostMapping("/customers")
    @Auditable(value = "CREATE_CUSTOMER", resourceType = "CUSTOMER")
    public CustomerDto createCustomer(@Valid @RequestBody CustomerDto dto, Authentication authentication) {
        Customer customer = Customer.builder()
                .firstName(dto.getFirstName())
                .lastName(dto.getLastName())
                .email(dto.getEmail())
                .customerNumber(dto.getCustomerNumber())
                .phoneNumber(dto.getPhoneNumber())
                .country(dto.getCountry())
                .loyaltyPoints(dto.getLoyaltyPoints() != null ? dto.getLoyaltyPoints() : 0)
                .build();
        return mapToCustomerDto(loyaltyService.createCustomer(customer, getCountryScope(authentication), dto.getReferrerCustomerNumber()));
    }

    @GetMapping("/customers/{id}")
    public CustomerDto getCustomer(@PathVariable Long id, Authentication authentication) {
        return mapToCustomerDto(loyaltyService.getCustomerById(id, getCountryScope(authentication)));
    }

    @PutMapping("/customers/{id}")
    @Auditable(value = "UPDATE_CUSTOMER", resourceType = "CUSTOMER", capturePathId = true)
    public CustomerDto updateCustomer(@PathVariable Long id, @Valid @RequestBody CustomerDto dto, Authentication authentication) {
        Customer updates = Customer.builder()
                .firstName(dto.getFirstName())
                .lastName(dto.getLastName())
                .email(dto.getEmail())
                .customerNumber(dto.getCustomerNumber())
                .phoneNumber(dto.getPhoneNumber())
                .country(dto.getCountry())
                .build();
        return mapToCustomerDto(loyaltyService.updateCustomer(id, updates, getCountryScope(authentication)));
    }

    /**
     * Zawieszenie i przywrocenie konta uczestnika. Kartoteka nie usuwa rekordow --
     * historia transakcji i log audytowy musza pozostac spojne.
     */
    @PatchMapping("/customers/{id}/status")
    @Auditable(value = "SET_CUSTOMER_STATUS", resourceType = "CUSTOMER", capturePathId = true)
    public CustomerDto setCustomerStatus(@PathVariable Long id,
                                         @Valid @RequestBody CustomerStatusRequest request,
                                         Authentication authentication) {
        return mapToCustomerDto(loyaltyService.setCustomerStatus(id, request.status(), getCountryScope(authentication)));
    }

    /** RODO art. 20 -- komplet danych uczestnika w postaci przenoszalnej. */
    @GetMapping("/customers/{id}/personal-data")
    @PreAuthorize("hasRole('ADMIN')")
    @Auditable(value = "EXPORT_PERSONAL_DATA", resourceType = "CUSTOMER", capturePathId = true)
    public PersonalDataExportDto exportPersonalData(@PathVariable Long id, Authentication authentication) {
        return customerPrivacyService.exportPersonalData(id, getCountryScope(authentication));
    }

    /** RODO art. 17 -- nieodwracalna anonimizacja danych osobowych uczestnika. */
    @PostMapping("/customers/{id}/anonymize")
    @PreAuthorize("hasRole('ADMIN')")
    @Auditable(value = "ANONYMIZE_CUSTOMER", resourceType = "CUSTOMER", capturePathId = true)
    public CustomerDto anonymizeCustomer(@PathVariable Long id, Authentication authentication) {
        return mapToCustomerDto(customerPrivacyService.anonymize(id, getCountryScope(authentication)));
    }

    @GetMapping("/customers/{id}/transactions")
    public List<TransactionDto> getCustomerTransactions(@PathVariable Long id, Authentication authentication) {
        return loyaltyService.getTransactionsForCustomer(id, getCountryScope(authentication)).stream()
                .map(this::mapToTransactionDto)
                .toList();
    }

    @GetMapping("/customers/{id}/transactions/paged")
    public PageResponse<TransactionDto> getCustomerTransactionsPage(
            @PathVariable Long id,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            Authentication authentication) {

        Pageable pageable = PageRequests.of(page, size, Sort.by(Sort.Direction.DESC, "timestamp", "id"));
        return PageResponse.of(
                loyaltyService.getTransactionsForCustomer(id, getCountryScope(authentication), pageable),
                this::mapToTransactionDto);
    }

    @GetMapping("/customers/{id}/purchase-history")
    public PurchaseHistoryDto getCustomerPurchaseHistory(@PathVariable Long id, Authentication authentication) {
        return loyaltyService.getPurchaseHistory(id, getCountryScope(authentication));
    }

    @GetMapping("/customers/{id}/coupons")
    public List<CustomerCouponDto> getCustomerCoupons(@PathVariable Long id, Authentication authentication) {
        return loyaltyService.getCouponsForCustomer(id, getCountryScope(authentication)).stream()
                .map(this::mapToCustomerCouponDto)
                .toList();
    }

    @GetMapping("/customers/{id}/coupons/paged")
    public PageResponse<CustomerCouponDto> getCustomerCouponsPage(
            @PathVariable Long id,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            Authentication authentication) {

        Pageable pageable = PageRequests.of(page, size, Sort.by(Sort.Direction.DESC, "issuedAt", "id"));
        return PageResponse.of(
                loyaltyService.getCouponsForCustomer(id, getCountryScope(authentication), pageable),
                this::mapToCustomerCouponDto);
    }

    /** Polecenia rozliczone na rzecz tego uczestnika. */
    @GetMapping("/customers/{id}/referrals")
    public List<ReferralRewardDto> getCustomerReferrals(@PathVariable Long id, Authentication authentication) {
        return loyaltyService.getReferralRewards(id, getCountryScope(authentication)).stream()
                .map(this::mapToReferralRewardDto)
                .toList();
    }

    /**
     * Reczna korekta salda punktow.
     *
     * <p>Naglowek {@code Idempotency-Key} jest wymagany: korekta nie ma numeru
     * dokumentu kasowego, wiec bez klucza powtorzone zadanie zapisaloby sie drugi
     * raz. Powtorzenie z tym samym kluczem zwraca pierwotna transakcje.
     */
    @PostMapping("/customers/{id}/add-points")
    @PreAuthorize("hasRole('ADMIN')")
    @Auditable(value = "ADD_POINTS", resourceType = "CUSTOMER", capturePathId = true)
    public TransactionDto addPoints(@PathVariable Long id,
                                    @RequestHeader("Idempotency-Key") String idempotencyKey,
                                    @Valid @RequestBody PointsRequest request) {
        return mapToTransactionDto(
                loyaltyService.addPoints(id, request.points(), request.description(), idempotencyKey));
    }

    @GetMapping("/coupons")
    public List<CustomerCouponDto> getIssuedCoupons(Authentication authentication) {
        return loyaltyService.getIssuedCoupons(getCountryScope(authentication)).stream()
                .map(this::mapToCustomerCouponDto)
                .toList();
    }

    @GetMapping("/coupons/paged")
    public PageResponse<CustomerCouponDto> getIssuedCouponsPage(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            Authentication authentication) {

        Pageable pageable = PageRequests.of(page, size, Sort.by(Sort.Direction.DESC, "issuedAt", "id"));
        return PageResponse.of(
                loyaltyService.getIssuedCouponsPage(getCountryScope(authentication), pageable),
                this::mapToCustomerCouponDto);
    }

    @GetMapping("/coupon-templates")
    public List<CouponTemplateDto> getCouponTemplates(Authentication authentication) {
        return loyaltyService.getCouponTemplates(getCountryScope(authentication)).stream()
                .map(this::mapToCouponTemplateDto)
                .toList();
    }

    @PostMapping("/coupon-templates")
    @Auditable(value = "CREATE_COUPON_TEMPLATE", resourceType = "COUPON_TEMPLATE")
    public CouponTemplateDto createCouponTemplate(@Valid @RequestBody CouponTemplateCreateRequest request, Authentication authentication) {
        return mapToCouponTemplateDto(loyaltyService.createCouponTemplate(request, getCountryScope(authentication)));
    }

    @PostMapping("/coupons/issue")
    @Auditable(value = "ISSUE_COUPON", resourceType = "CUSTOMER_COUPON")
    public CustomerCouponDto issueCoupon(@Valid @RequestBody CouponIssueRequest request, Authentication authentication) {
        return mapToCustomerCouponDto(loyaltyService.issueCoupon(request, getCountryScope(authentication)));
    }

    /** Wycofanie wydanego kuponu -- pomylka operatora albo reklamacja. */
    @PostMapping("/coupons/{id}/cancel")
    @Auditable(value = "CANCEL_COUPON", resourceType = "CUSTOMER_COUPON", capturePathId = true)
    public CustomerCouponDto cancelCoupon(@PathVariable Long id, Authentication authentication) {
        return mapToCustomerCouponDto(loyaltyService.cancelCoupon(id, getCountryScope(authentication)));
    }

    @GetMapping("/hierarchy-promotions")
    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICAL')")
    public List<HierarchyPromotionDto> getHierarchyPromotions(Authentication authentication) {
        return loyaltyService.getHierarchyPromotions(getCountryScope(authentication)).stream()
                .map(this::mapToHierarchyPromotionDto)
                .toList();
    }

    @PostMapping("/hierarchy-promotions")
    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICAL')")
    @Auditable(value = "CREATE_HIERARCHY_PROMOTION", resourceType = "HIERARCHY_PROMOTION")
    public HierarchyPromotionDto createHierarchyPromotion(@Valid @RequestBody HierarchyPromotionCreateRequest request, Authentication authentication) {
        return mapToHierarchyPromotionDto(loyaltyService.createHierarchyPromotion(request, getCountryScope(authentication)));
    }

    @PutMapping("/hierarchy-promotions/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICAL')")
    @Auditable(value = "UPDATE_HIERARCHY_PROMOTION", resourceType = "HIERARCHY_PROMOTION", capturePathId = true)
    public HierarchyPromotionDto updateHierarchyPromotion(@PathVariable Long id, @Valid @RequestBody HierarchyPromotionCreateRequest request, Authentication authentication) {
        return mapToHierarchyPromotionDto(loyaltyService.updateHierarchyPromotion(id, request, getCountryScope(authentication)));
    }

    @PatchMapping("/hierarchy-promotions/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICAL')")
    @Auditable(value = "SET_HIERARCHY_PROMOTION_STATUS", resourceType = "HIERARCHY_PROMOTION", capturePathId = true)
    public HierarchyPromotionDto setHierarchyPromotionStatus(@PathVariable Long id, @Valid @RequestBody StorePromotionStatusRequest request, Authentication authentication) {
        return mapToHierarchyPromotionDto(loyaltyService.setHierarchyPromotionEnabled(id, request.enabled(), getCountryScope(authentication)));
    }

    @PostMapping(value = "/tools/import-customers", consumes = "multipart/form-data")
    @Auditable(value = "IMPORT_CUSTOMERS_CSV", resourceType = "CUSTOMER")
    public ResponseEntity<Map<String, Object>> importCustomersCsv(@RequestPart("file") MultipartFile file, Authentication authentication) {
        int importedCount = loyaltyService.importCustomersFromCsv(file, getCountryScope(authentication));
        return ResponseEntity.ok(Map.of("importedCount", importedCount));
    }

    private CustomerDto mapToCustomerDto(Customer customer) {
        return CustomerDto.builder()
                .id(customer.getId())
                .firstName(customer.getFirstName())
                .lastName(customer.getLastName())
                .email(customer.getEmail())
                .customerNumber(customer.getCustomerNumber())
                .phoneNumber(customer.getPhoneNumber())
                .country(customer.getCountry())
                .loyaltyPoints(customer.getLoyaltyPoints())
                .lifetimePoints(customer.getLifetimePoints())
                .status((customer.getStatus() == null ? CustomerStatus.ACTIVE : customer.getStatus()).name())
                .createdAt(customer.getCreatedAt())
                .referrerCustomerNumber(null)
                .referralCode(customer.getReferralCode())
                .loyaltyTierCode(loyaltyTierService.resolveTierCode(customer))
                .build();
    }

    private CustomerCouponDto mapToCustomerCouponDto(CustomerCoupon customerCoupon) {
        Customer customer = customerCoupon.getCustomer();
        return CustomerCouponDto.builder()
                .id(customerCoupon.getId())
                .couponCode(customerCoupon.getCouponCode())
                .customerId(customer.getId())
                .customerName(customer.getFirstName() + " " + customer.getLastName())
                .country(customerCoupon.getCountry())
                .couponValue(customerCoupon.getCouponTemplate().getCouponValue())
                .minimumPurchaseValue(customerCoupon.getCouponTemplate().getMinimumPurchaseValue())
                .requiredPoints(customerCoupon.getCouponTemplate().getRequiredPoints())
                .validityDays(customerCoupon.getCouponTemplate().getValidityDays())
                .couponPrefix(customerCoupon.getCouponTemplate().getCouponPrefix())
                .reason(customerCoupon.getReason() == null ? "POINTS_EXCHANGE" : customerCoupon.getReason().name())
                // Stan wyliczany z dat: kupon po terminie jest raportowany jako
                // wygasly takze wtedy, gdy nikt nie probowal go zwalidowac.
                .status(customerCoupon.effectiveStatus(LocalDateTime.now()).name())
                .issuedAt(customerCoupon.getIssuedAt())
                .expiresAt(customerCoupon.getExpiresAt())
                .build();
    }

    private CouponTemplateDto mapToCouponTemplateDto(CouponTemplate template) {
        return CouponTemplateDto.builder()
                .id(template.getId())
                .couponValue(template.getCouponValue())
                .minimumPurchaseValue(template.getMinimumPurchaseValue())
                .requiredPoints(template.getRequiredPoints())
                .country(template.getCountry())
                .validityDays(template.getValidityDays())
                .couponPrefix(template.getCouponPrefix())
                .build();
    }

    private TransactionDto mapToTransactionDto(Transaction transaction) {
        return TransactionDto.builder()
                .id(transaction.getId())
                .points(transaction.getPoints())
                .description(transaction.getDescription())
                .timestamp(transaction.getTimestamp())
                .availableFrom(transaction.getAvailableFrom())
                .expiresAt(transaction.getExpiresAt())
                .amount(transaction.getAmount())
                .type(transaction.getType() == null ? null : transaction.getType().name())
                .state(transaction.getState() == null ? null : transaction.getState().name())
                .build();
    }

    private ReferralRewardDto mapToReferralRewardDto(ReferralReward reward) {
        Customer referred = reward.getReferred();
        return new ReferralRewardDto(
                reward.getId(),
                referred.getId(),
                referred.getCustomerNumber(),
                referred.getFirstName() + " " + referred.getLastName(),
                reward.getReferrerPoints(),
                reward.getReferredPoints(),
                reward.getCountry(),
                reward.getAwardedAt()
        );
    }

    private HierarchyPromotionDto mapToHierarchyPromotionDto(HierarchyPromotion promotion) {
        return HierarchyPromotionDto.builder()
                .id(promotion.getId())
                .name(promotion.getName())
                .country(promotion.getCountry())
                .hierarchy(promotion.getHierarchy())
                .productClass(promotion.getProductClass())
                .subclass(promotion.getSubclass())
                .type(promotion.getType())
                .multiplier(promotion.getMultiplier())
                .startsAt(promotion.getStartsAt())
                .endsAt(promotion.getEndsAt())
                .enabled(promotion.isEnabled())
                .build();
    }

    private StorePromotionDto mapToStorePromotionDto(StorePointsPromotion promotion) {
        return StorePromotionDto.builder()
                .id(promotion.getId())
                .name(promotion.getName())
                .country(promotion.getCountry())
                .pointsPerCurrency(promotion.getPointsPerCurrency())
                .startsAt(promotion.getStartsAt())
                .endsAt(promotion.getEndsAt())
                .enabled(promotion.isEnabled())
                .build();
    }

    private String getCountryScope(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities() == null) {
            return null;
        }
        boolean technical = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_TECHNICAL"::equals);
        if (!technical) {
            return null;
        }
        return technicalUserService.resolveTechnicalUserCountry(authentication.getName());
    }
}
