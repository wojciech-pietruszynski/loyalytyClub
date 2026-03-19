package pl.pietruszynski.loyaltyclub.api.admin.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
import pl.pietruszynski.loyaltyclub.api.admin.dto.CouponTemplateDto;
import pl.pietruszynski.loyaltyclub.api.admin.dto.HierarchyPromotionCreateRequest;
import pl.pietruszynski.loyaltyclub.api.admin.dto.HierarchyPromotionDto;
import pl.pietruszynski.loyaltyclub.api.admin.dto.PointsRequest;
import pl.pietruszynski.loyaltyclub.api.admin.dto.PurchaseHistoryDto;
import pl.pietruszynski.loyaltyclub.api.admin.dto.StorePromotionCreateRequest;
import pl.pietruszynski.loyaltyclub.api.admin.dto.StorePromotionDto;
import pl.pietruszynski.loyaltyclub.api.admin.dto.StorePromotionStatusRequest;
import pl.pietruszynski.loyaltyclub.api.admin.dto.TransactionDto;
import pl.pietruszynski.loyaltyclub.api.admin.model.CouponTemplate;
import pl.pietruszynski.loyaltyclub.api.admin.model.Customer;
import pl.pietruszynski.loyaltyclub.api.admin.model.CustomerCoupon;
import pl.pietruszynski.loyaltyclub.api.admin.model.Transaction;
import pl.pietruszynski.loyaltyclub.api.store.model.HierarchyPromotion;
import pl.pietruszynski.loyaltyclub.api.store.model.StorePointsPromotion;
import pl.pietruszynski.loyaltyclub.api.admin.service.LoyaltyService;
import pl.pietruszynski.loyaltyclub.api.admin.service.TechnicalUserService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class LoyaltyController {

    private final LoyaltyService loyaltyService;
    private final TechnicalUserService technicalUserService;

    @GetMapping("/customers")
    public List<CustomerDto> getAllCustomers(Authentication authentication) {
        String countryScope = getCountryScope(authentication);
        return loyaltyService.getAllCustomers(countryScope).stream()
                .map(this::mapToCustomerDto)
                .toList();
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
    public StorePromotionDto createStorePromotion(@Valid @RequestBody StorePromotionCreateRequest request, Authentication authentication) {
        return mapToStorePromotionDto(loyaltyService.createStorePromotion(request, getCountryScope(authentication)));
    }

    @PutMapping("/store-promotions/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICAL')")
    public StorePromotionDto updateStorePromotion(@PathVariable Long id, @Valid @RequestBody StorePromotionCreateRequest request, Authentication authentication) {
        return mapToStorePromotionDto(loyaltyService.updateStorePromotion(id, request, getCountryScope(authentication)));
    }

    @PatchMapping("/store-promotions/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICAL')")
    public StorePromotionDto setStorePromotionStatus(@PathVariable Long id, @Valid @RequestBody StorePromotionStatusRequest request, Authentication authentication) {
        return mapToStorePromotionDto(loyaltyService.setStorePromotionEnabled(id, request.enabled(), getCountryScope(authentication)));
    }

    @PostMapping("/customers")
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
        return mapToCustomerDto(loyaltyService.createCustomer(customer, getCountryScope(authentication)));
    }

    @GetMapping("/customers/{id}")
    public CustomerDto getCustomer(@PathVariable Long id, Authentication authentication) {
        return mapToCustomerDto(loyaltyService.getCustomerById(id, getCountryScope(authentication)));
    }

    @PutMapping("/customers/{id}")
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

    @GetMapping("/customers/{id}/transactions")
    public List<TransactionDto> getCustomerTransactions(@PathVariable Long id, Authentication authentication) {
        return loyaltyService.getTransactionsForCustomer(id, getCountryScope(authentication)).stream()
                .map(this::mapToTransactionDto)
                .toList();
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

    @PostMapping("/customers/{id}/add-points")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> addPoints(@PathVariable Long id, @Valid @RequestBody PointsRequest request) {
        loyaltyService.addPoints(id, request.points(), request.description());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/coupons")
    public List<CustomerCouponDto> getIssuedCoupons(Authentication authentication) {
        return loyaltyService.getIssuedCoupons(getCountryScope(authentication)).stream()
                .map(this::mapToCustomerCouponDto)
                .toList();
    }

    @GetMapping("/coupon-templates")
    public List<CouponTemplateDto> getCouponTemplates(Authentication authentication) {
        return loyaltyService.getCouponTemplates(getCountryScope(authentication)).stream()
                .map(this::mapToCouponTemplateDto)
                .toList();
    }

    @PostMapping("/coupon-templates")
    public CouponTemplateDto createCouponTemplate(@Valid @RequestBody CouponTemplateCreateRequest request, Authentication authentication) {
        return mapToCouponTemplateDto(loyaltyService.createCouponTemplate(request, getCountryScope(authentication)));
    }

    @PostMapping("/coupons/issue")
    public CustomerCouponDto issueCoupon(@Valid @RequestBody CouponIssueRequest request, Authentication authentication) {
        return mapToCustomerCouponDto(loyaltyService.issueCoupon(request, getCountryScope(authentication)));
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
    public HierarchyPromotionDto createHierarchyPromotion(@Valid @RequestBody HierarchyPromotionCreateRequest request, Authentication authentication) {
        return mapToHierarchyPromotionDto(loyaltyService.createHierarchyPromotion(request, getCountryScope(authentication)));
    }

    @PutMapping("/hierarchy-promotions/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICAL')")
    public HierarchyPromotionDto updateHierarchyPromotion(@PathVariable Long id, @Valid @RequestBody HierarchyPromotionCreateRequest request, Authentication authentication) {
        return mapToHierarchyPromotionDto(loyaltyService.updateHierarchyPromotion(id, request, getCountryScope(authentication)));
    }

    @PatchMapping("/hierarchy-promotions/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICAL')")
    public HierarchyPromotionDto setHierarchyPromotionStatus(@PathVariable Long id, @Valid @RequestBody StorePromotionStatusRequest request, Authentication authentication) {
        return mapToHierarchyPromotionDto(loyaltyService.setHierarchyPromotionEnabled(id, request.enabled(), getCountryScope(authentication)));
    }

    @PostMapping(value = "/tools/import-customers", consumes = "multipart/form-data")
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
                .status(customerCoupon.getStatus() == null ? "ACTIVE" : customerCoupon.getStatus().name())
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
                .build();
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
