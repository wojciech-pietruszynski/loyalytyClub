package pl.pietruszynski.loyaltyclub.api.ecom.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.pietruszynski.loyaltyclub.api.admin.dto.CustomerCouponDto;
import pl.pietruszynski.loyaltyclub.api.admin.dto.TransactionDto;
import pl.pietruszynski.loyaltyclub.api.admin.model.Customer;
import pl.pietruszynski.loyaltyclub.api.admin.model.CustomerCoupon;
import pl.pietruszynski.loyaltyclub.api.admin.model.Transaction;
import pl.pietruszynski.loyaltyclub.api.admin.repository.CustomerCouponRepository;
import pl.pietruszynski.loyaltyclub.api.admin.repository.CustomerRepository;
import pl.pietruszynski.loyaltyclub.api.admin.repository.TransactionRepository;
import pl.pietruszynski.loyaltyclub.api.admin.service.LoyaltyTierService;
import pl.pietruszynski.loyaltyclub.api.ecom.dto.EcomCustomerProfileDto;
import pl.pietruszynski.loyaltyclub.api.store.dto.StorePointsBalanceResponse;
import pl.pietruszynski.loyaltyclub.api.store.service.StoreTransactionService;
import pl.pietruszynski.loyaltyclub.exception.ResourceNotFoundException;

import java.util.List;

/**
 * Odczytowe API dla integracji e-commerce. Naliczanie punktow pozostaje na {@code /api/store},
 * a realizacja kuponow na {@code /api/coupon} — tutaj wystawiamy wylacznie widok klienta.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EcomService {

    private final CustomerRepository customerRepository;
    private final TransactionRepository transactionRepository;
    private final CustomerCouponRepository customerCouponRepository;
    private final StoreTransactionService storeTransactionService;
    private final LoyaltyTierService loyaltyTierService;

    public StorePointsBalanceResponse getPointsBalance(String customerNumber) {
        return storeTransactionService.getPointsBalance(customerNumber);
    }

    public EcomCustomerProfileDto getCustomerProfile(String customerNumber) {
        Customer customer = findCustomer(customerNumber);
        return new EcomCustomerProfileDto(
                customer.getId(),
                customer.getCustomerNumber(),
                customer.getFirstName(),
                customer.getLastName(),
                customer.getEmail(),
                customer.getPhoneNumber(),
                customer.getCountry(),
                customer.getLoyaltyPoints(),
                loyaltyTierService.resolveTierCode(customer.getLoyaltyPoints()),
                customer.getReferralCode()
        );
    }

    public List<TransactionDto> getTransactions(String customerNumber) {
        Customer customer = findCustomer(customerNumber);
        return transactionRepository.findAllByCustomerIdOrderByTimestampAsc(customer.getId()).stream()
                .map(this::mapToTransactionDto)
                .toList();
    }

    public List<CustomerCouponDto> getCoupons(String customerNumber) {
        Customer customer = findCustomer(customerNumber);
        return customerCouponRepository.findAllByCustomerIdOrderByIssuedAtDesc(customer.getId()).stream()
                .map(this::mapToCustomerCouponDto)
                .toList();
    }

    private Customer findCustomer(String customerNumber) {
        return customerRepository.findByCustomerNumber(customerNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with number: " + customerNumber));
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
}
