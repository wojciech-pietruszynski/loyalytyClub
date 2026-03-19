package pl.pietruszynski.loyaltyclub.api.coupon.service;

import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.pietruszynski.loyaltyclub.api.admin.model.CouponReason;
import pl.pietruszynski.loyaltyclub.api.admin.model.CouponStatus;
import pl.pietruszynski.loyaltyclub.api.admin.model.CouponTemplate;
import pl.pietruszynski.loyaltyclub.api.admin.model.Customer;
import pl.pietruszynski.loyaltyclub.api.admin.model.CustomerCoupon;
import pl.pietruszynski.loyaltyclub.api.admin.model.Transaction;
import pl.pietruszynski.loyaltyclub.api.admin.model.TransactionState;
import pl.pietruszynski.loyaltyclub.api.admin.model.TransactionType;
import pl.pietruszynski.loyaltyclub.api.admin.repository.CouponTemplateRepository;
import pl.pietruszynski.loyaltyclub.api.admin.repository.CustomerCouponRepository;
import pl.pietruszynski.loyaltyclub.api.admin.repository.CustomerRepository;
import pl.pietruszynski.loyaltyclub.api.admin.repository.TransactionRepository;
import pl.pietruszynski.loyaltyclub.api.coupon.dto.CouponDefinitionResponse;
import pl.pietruszynski.loyaltyclub.api.coupon.dto.CouponRedeemRequest;
import pl.pietruszynski.loyaltyclub.api.coupon.dto.CouponRedeemResponse;
import pl.pietruszynski.loyaltyclub.api.coupon.dto.CouponValidationResponse;
import pl.pietruszynski.loyaltyclub.api.coupon.model.CouponRedemptionRequest;
import pl.pietruszynski.loyaltyclub.api.coupon.model.CouponValidationStatus;
import pl.pietruszynski.loyaltyclub.api.coupon.repository.CouponRedemptionRequestRepository;
import pl.pietruszynski.loyaltyclub.exception.ResourceNotFoundException;
import pl.pietruszynski.loyaltyclub.util.CouponCodeGenerator;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CouponService {

    private final CouponCodeGenerator couponCodeGenerator;

    private final CustomerRepository customerRepository;
    private final CouponTemplateRepository couponTemplateRepository;
    private final CustomerCouponRepository customerCouponRepository;
    private final TransactionRepository transactionRepository;
    private final CouponRedemptionRequestRepository couponRedemptionRequestRepository;

    @Transactional
    public CouponRedeemResponse redeemPointsForCoupon(CouponRedeemRequest request, String idempotencyKey) {
        String normalizedCustomerNumber = normalizeRequiredField(request.customerNumber(), "customerNumber");
        String normalizedIdempotencyKey = normalizeIdempotencyKey(idempotencyKey);

        CouponRedemptionRequest existingRequest = couponRedemptionRequestRepository.findByIdempotencyKey(normalizedIdempotencyKey).orElse(null);
        if (existingRequest != null) {
            return handleExistingIdempotencyRequest(existingRequest, request, normalizedCustomerNumber);
        }

        CouponRedemptionRequest redemptionRequest = reserveIdempotencyKey(
                normalizedIdempotencyKey,
                normalizedCustomerNumber,
                request.couponTemplateId()
        );
        if (redemptionRequest == null) {
            CouponRedemptionRequest racedRequest = couponRedemptionRequestRepository.findByIdempotencyKey(normalizedIdempotencyKey)
                    .orElseThrow(() -> new IllegalStateException("Idempotency state is inconsistent"));
            return handleExistingIdempotencyRequest(racedRequest, request, normalizedCustomerNumber);
        }

        Customer customer = customerRepository.findByCustomerNumberForUpdate(normalizedCustomerNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found for customerNumber: " + normalizedCustomerNumber));

        CouponTemplate couponTemplate = couponTemplateRepository.findById(request.couponTemplateId())
                .orElseThrow(() -> new ResourceNotFoundException("Coupon template not found with id: " + request.couponTemplateId()));

        if (!customer.getCountry().equalsIgnoreCase(couponTemplate.getCountry())) {
            throw new IllegalArgumentException("Customer country does not match coupon template country");
        }

        if (customer.getLoyaltyPoints() < couponTemplate.getRequiredPoints()) {
            throw new IllegalArgumentException("Not enough points to exchange for this coupon");
        }

        customer.setLoyaltyPoints(customer.getLoyaltyPoints() - couponTemplate.getRequiredPoints());
        customerRepository.save(customer);

        transactionRepository.save(Transaction.builder()
                .customer(customer)
                .points(-couponTemplate.getRequiredPoints())
                .amount(BigDecimal.ZERO)
                .pointsPerCurrency(BigDecimal.ONE)
                .description("Issued coupon (points exchange): " + couponTemplate.getCouponPrefix())
                .country(customer.getCountry())
                .type(TransactionType.MANUAL_ADJUSTMENT)
                .state(TransactionState.AVAILABLE)
                .build());

        LocalDateTime issuedAt = LocalDateTime.now();
        CustomerCoupon coupon = createCouponWithRetry(customer, couponTemplate, issuedAt);
        redemptionRequest.setCustomerCoupon(coupon);
        couponRedemptionRequestRepository.save(redemptionRequest);

        return toRedeemResponse(coupon);
    }

    @Transactional
    public CouponValidationResponse validateCoupon(String couponCode, String customerNumber) {
        String normalizedCouponCode = normalizeRequiredField(couponCode, "couponCode").toUpperCase(Locale.ROOT);
        String normalizedCustomerNumber = normalizeRequiredField(customerNumber, "customerNumber");

        Customer customer = customerRepository.findByCustomerNumber(normalizedCustomerNumber).orElse(null);
        if (customer == null) {
            return new CouponValidationResponse(
                    CouponValidationStatus.CUSTOMER_NOT_FOUND,
                    normalizedCouponCode,
                    normalizedCustomerNumber,
                    null,
                    null,
                    null,
                    null
            );
        }

        CustomerCoupon coupon = customerCouponRepository.findByCouponCode(normalizedCouponCode).orElse(null);
        if (coupon == null) {
            return new CouponValidationResponse(
                    CouponValidationStatus.COUPON_NOT_FOUND,
                    normalizedCouponCode,
                    normalizedCustomerNumber,
                    null,
                    null,
                    null,
                    null
            );
        }

        if (!coupon.getCustomer().getId().equals(customer.getId())) {
            return new CouponValidationResponse(
                    CouponValidationStatus.COUPON_BELONGS_TO_ANOTHER_ACCOUNT,
                    normalizedCouponCode,
                    normalizedCustomerNumber,
                    null,
                    null,
                    null,
                    null
            );
        }

        if (coupon.getStatus() == CouponStatus.USED) {
            return new CouponValidationResponse(
                    CouponValidationStatus.COUPON_ALREADY_USED,
                    normalizedCouponCode,
                    normalizedCustomerNumber,
                    coupon.getStatus().name(),
                    coupon.getIssuedAt(),
                    coupon.getExpiresAt(),
                    mapDefinition(coupon.getCouponTemplate())
            );
        }

        if (isExpired(coupon)) {
            coupon.setStatus(CouponStatus.EXPIRED);
            customerCouponRepository.save(coupon);
            return new CouponValidationResponse(
                    CouponValidationStatus.COUPON_EXPIRED,
                    normalizedCouponCode,
                    normalizedCustomerNumber,
                    coupon.getStatus().name(),
                    coupon.getIssuedAt(),
                    coupon.getExpiresAt(),
                    mapDefinition(coupon.getCouponTemplate())
            );
        }

        return new CouponValidationResponse(
                CouponValidationStatus.VALID,
                normalizedCouponCode,
                normalizedCustomerNumber,
                coupon.getStatus().name(),
                coupon.getIssuedAt(),
                coupon.getExpiresAt(),
                mapDefinition(coupon.getCouponTemplate())
        );
    }

    private boolean isExpired(CustomerCoupon coupon) {
        return coupon.getStatus() == CouponStatus.EXPIRED || !LocalDateTime.now().isBefore(coupon.getExpiresAt());
    }

    private CouponDefinitionResponse mapDefinition(CouponTemplate couponTemplate) {
        return new CouponDefinitionResponse(
                couponTemplate.getId(),
                couponTemplate.getCouponValue(),
                couponTemplate.getMinimumPurchaseValue(),
                couponTemplate.getRequiredPoints(),
                couponTemplate.getValidityDays(),
                couponTemplate.getCouponPrefix(),
                couponTemplate.getCountry()
        );
    }

    private String normalizeRequiredField(String value, String fieldName) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return normalized;
    }

    private String generateUniqueCouponCode(String prefix) {
        String normalizedPrefix = prefix.trim().toUpperCase(Locale.ROOT);
        return normalizedPrefix + generateElevenDigits();
    }

    private String generateElevenDigits() {
        return couponCodeGenerator.generateElevenDigits();
    }

    private CustomerCoupon createCouponWithRetry(Customer customer, CouponTemplate couponTemplate, LocalDateTime issuedAt) {
        for (int i = 0; i < 200; i++) {
            try {
                return customerCouponRepository.save(CustomerCoupon.builder()
                        .couponCode(generateUniqueCouponCode(couponTemplate.getCouponPrefix()))
                        .country(couponTemplate.getCountry())
                        .customer(customer)
                        .couponTemplate(couponTemplate)
                        .reason(CouponReason.POINTS_EXCHANGE)
                        .status(CouponStatus.ACTIVE)
                        .issuedAt(issuedAt)
                        .expiresAt(issuedAt.plusDays(couponTemplate.getValidityDays()))
                        .build());
            } catch (DataIntegrityViolationException ex) {
                if (!isCouponCodeConstraintViolation(ex)) {
                    throw ex;
                }
            }
        }
        throw new IllegalStateException("Cannot generate unique coupon code after multiple retries");
    }

    private CouponRedeemResponse toRedeemResponse(CustomerCoupon coupon) {
        return new CouponRedeemResponse(
                coupon.getCouponCode(),
                coupon.getCustomer().getCustomerNumber(),
                coupon.getStatus().name(),
                coupon.getIssuedAt(),
                coupon.getExpiresAt(),
                mapDefinition(coupon.getCouponTemplate())
        );
    }

    private CouponRedeemResponse handleExistingIdempotencyRequest(
            CouponRedemptionRequest existingRequest,
            CouponRedeemRequest request,
            String normalizedCustomerNumber
    ) {
        if (!existingRequest.getCustomerNumber().equals(normalizedCustomerNumber)
                || !existingRequest.getCouponTemplateId().equals(request.couponTemplateId())) {
            throw new IllegalArgumentException("Idempotency-Key already used with different payload");
        }

        if (existingRequest.getCustomerCoupon() == null) {
            throw new IllegalStateException("Request with this Idempotency-Key is in progress. Retry later");
        }

        return toRedeemResponse(existingRequest.getCustomerCoupon());
    }

    private CouponRedemptionRequest reserveIdempotencyKey(
            String idempotencyKey,
            String customerNumber,
            Long couponTemplateId
    ) {
        try {
            return couponRedemptionRequestRepository.saveAndFlush(CouponRedemptionRequest.builder()
                    .idempotencyKey(idempotencyKey)
                    .customerNumber(customerNumber)
                    .couponTemplateId(couponTemplateId)
                    .build());
        } catch (DataIntegrityViolationException ex) {
            if (isIdempotencyKeyConstraintViolation(ex)) {
                return null;
            }
            throw ex;
        }
    }

    private String normalizeIdempotencyKey(String idempotencyKey) {
        String normalized = normalizeRequiredField(idempotencyKey, "Idempotency-Key");
        if (normalized.length() > 100) {
            throw new IllegalArgumentException("Idempotency-Key must have at most 100 characters");
        }
        return normalized;
    }

    private boolean isCouponCodeConstraintViolation(DataIntegrityViolationException ex) {
        return containsConstraint(ex, "coupon_code");
    }

    private boolean isIdempotencyKeyConstraintViolation(DataIntegrityViolationException ex) {
        return containsConstraint(ex, "idempotency_key");
    }

    private boolean containsConstraint(Throwable throwable, String fragment) {
        String normalizedFragment = fragment.toLowerCase(Locale.ROOT);
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof ConstraintViolationException constraintViolationException) {
                String constraintName = constraintViolationException.getConstraintName();
                if (constraintName != null && constraintName.toLowerCase(Locale.ROOT).contains(normalizedFragment)) {
                    return true;
                }
            }
            String message = current.getMessage();
            if (message != null && message.toLowerCase(Locale.ROOT).contains(normalizedFragment)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
