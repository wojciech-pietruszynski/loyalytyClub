package pl.pietruszynski.loyaltyclub.api.store.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.pietruszynski.loyaltyclub.api.admin.model.Customer;
import pl.pietruszynski.loyaltyclub.api.admin.model.Transaction;
import pl.pietruszynski.loyaltyclub.api.admin.model.TransactionState;
import pl.pietruszynski.loyaltyclub.api.admin.model.TransactionType;
import pl.pietruszynski.loyaltyclub.api.admin.repository.CustomerRepository;
import pl.pietruszynski.loyaltyclub.api.admin.repository.TransactionRepository;
import pl.pietruszynski.loyaltyclub.api.store.dto.StorePointsBalanceResponse;
import pl.pietruszynski.loyaltyclub.api.store.dto.StoreReturnRequest;
import pl.pietruszynski.loyaltyclub.api.store.dto.StoreSaleRequest;
import pl.pietruszynski.loyaltyclub.api.store.dto.StoreTransactionItemRequest;
import pl.pietruszynski.loyaltyclub.api.store.dto.StoreTransactionResponse;
import pl.pietruszynski.loyaltyclub.api.store.model.HierarchyPromotion;
import pl.pietruszynski.loyaltyclub.exception.ResourceNotFoundException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StoreTransactionService {

    private static final BigDecimal ZERO = new BigDecimal("0.00");

    private final CustomerRepository customerRepository;
    private final TransactionRepository transactionRepository;
    private final StorePromotionService storePromotionService;
    private final HierarchyPromotionService hierarchyPromotionService;

    @Transactional
    public StoreTransactionResponse registerSale(String countryCode, StoreSaleRequest request) {
        Customer customer = findCustomerByNumber(request.customerNumber());
        LocalDateTime purchaseTimestamp = request.purchaseTimestamp() == null ? LocalDateTime.now() : request.purchaseTimestamp();
        String normalizedCountryCode = normalizeCountryCode(countryCode);
        String normalizedSourceTransactionNumber = normalizeSourceTransactionNumber(request.sourceTransactionNumber(), "sourceTransactionNumber");
        validateUniqueSourceTransactionNumber(normalizedSourceTransactionNumber);
        validateTotalAmountAgainstItems(request.items(), request.totalAmount());

        BigDecimal pointsPerCurrency = storePromotionService.resolvePointsPerCurrency(normalizedCountryCode, purchaseTimestamp);
        List<HierarchyPromotion> activeHierarchyPromotions = hierarchyPromotionService.getActivePromotions(normalizedCountryCode, purchaseTimestamp);
        int points = calculatePointsWithHierarchy(request.items(), pointsPerCurrency, activeHierarchyPromotions);

        Transaction transaction = transactionRepository.save(Transaction.builder()
                .customer(customer)
                .points(points)
                .amount(request.totalAmount().setScale(2, RoundingMode.HALF_UP))
                .pointsPerCurrency(pointsPerCurrency)
                .description("Store sale: " + normalizedSourceTransactionNumber)
                .country(normalizedCountryCode)
                .type(TransactionType.SALE)
                .state(TransactionState.PENDING)
                .purchaseTimestamp(purchaseTimestamp)
                .availableFrom(purchaseTimestamp.plusDays(30))
                .expiresAt(purchaseTimestamp.plusDays(365))
                .sourceTransactionNumber(normalizedSourceTransactionNumber)
                .build());

        refreshCustomerPoints(customer);
        return toResponse(transaction);
    }

    @Transactional
    public StoreTransactionResponse registerReturn(String countryCode, StoreReturnRequest request) {
        Customer customer = findCustomerByNumber(request.customerNumber());
        String normalizedCountryCode = normalizeCountryCode(countryCode);
        String normalizedSourceTransactionNumber = normalizeSourceTransactionNumber(request.sourceTransactionNumber(), "sourceTransactionNumber");
        String normalizedSaleTransactionNumber = normalizeSourceTransactionNumber(request.saleTransactionNumber(), "saleTransactionNumber");
        validateUniqueSourceTransactionNumber(normalizedSourceTransactionNumber);
        validateTotalAmountAgainstItems(request.items(), request.totalAmount());

        Transaction saleTransaction = transactionRepository.findBySourceTransactionNumberAndCustomerId(
                        normalizedSaleTransactionNumber,
                        customer.getId()
                )
                .orElseThrow(() -> new ResourceNotFoundException("Sale transaction not found"));

        if (saleTransaction.getType() != TransactionType.SALE) {
            throw new IllegalArgumentException("Return can be created only for SALE transaction");
        }
        if (!normalizedCountryCode.equalsIgnoreCase(saleTransaction.getCountry())) {
            throw new IllegalArgumentException("Return country must match sale transaction country");
        }

        TransactionState currentSaleState = resolveState(saleTransaction, LocalDateTime.now());
        if (currentSaleState == TransactionState.EXPIRED) {
            throw new IllegalArgumentException("Cannot process return for expired points");
        }

        BigDecimal alreadyReturnedAmount = transactionRepository.sumAmountBySourceTransactionIdAndType(saleTransaction.getId(), TransactionType.RETURN);
        BigDecimal remainingAmount = saleTransaction.getAmount().subtract(alreadyReturnedAmount);
        if (request.totalAmount().compareTo(remainingAmount) > 0) {
            throw new IllegalArgumentException("Return amount exceeds remaining sale amount");
        }

        int alreadyReturnedPoints = Math.abs(transactionRepository.sumPointsBySourceTransactionIdAndType(saleTransaction.getId(), TransactionType.RETURN));
        int maxReversiblePoints = saleTransaction.getPoints() - alreadyReturnedPoints;

        int safePointsToReverse;
        if (saleTransaction.getPoints() == 0) {
            safePointsToReverse = 0;
        } else {
            if (maxReversiblePoints <= 0) {
                throw new IllegalArgumentException("No points left to reverse for this sale");
            }
            int pointsToReverse = calculatePoints(request.totalAmount(), saleTransaction.getPointsPerCurrency());
            safePointsToReverse = Math.min(pointsToReverse, maxReversiblePoints);
            if (safePointsToReverse <= 0) {
                throw new IllegalArgumentException("Return amount is too small to reverse any points");
            }
        }

        Transaction returnTransaction = transactionRepository.save(Transaction.builder()
                .customer(customer)
                .points(-safePointsToReverse)
                .amount(request.totalAmount().setScale(2, RoundingMode.HALF_UP))
                .pointsPerCurrency(saleTransaction.getPointsPerCurrency())
                .description("Store return: " + normalizedSourceTransactionNumber)
                .country(normalizedCountryCode)
                .type(TransactionType.RETURN)
                .state(currentSaleState)
                .purchaseTimestamp(saleTransaction.getPurchaseTimestamp())
                .availableFrom(saleTransaction.getAvailableFrom())
                .expiresAt(saleTransaction.getExpiresAt())
                .sourceTransaction(saleTransaction)
                .sourceTransactionNumber(normalizedSourceTransactionNumber)
                .build());

        refreshCustomerPoints(customer);
        return toResponse(returnTransaction);
    }

    @Transactional
    public StorePointsBalanceResponse getPointsBalance(String customerNumber) {
        Customer customer = findCustomerByNumber(customerNumber);
        List<Transaction> transactions = refreshCustomerPoints(customer);

        int pending = 0;
        int available = 0;
        int expired = 0;
        for (Transaction transaction : transactions) {
            switch (transaction.getState()) {
                case PENDING -> pending += transaction.getPoints();
                case AVAILABLE -> available += transaction.getPoints();
                case EXPIRED -> expired += transaction.getPoints();
            }
        }

        return new StorePointsBalanceResponse(
                customer.getId(),
                customer.getCustomerNumber(),
                pending,
                available,
                expired
        );
    }

    private Customer findCustomerByNumber(String customerNumber) {
        return customerRepository.findByCustomerNumber(customerNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found for customerNumber: " + customerNumber));
    }

    private void validateTotalAmountAgainstItems(List<StoreTransactionItemRequest> items, BigDecimal totalAmount) {
        BigDecimal itemTotal = items.stream()
                .map(item -> item.price().amount())
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal normalizedTotal = totalAmount.setScale(2, RoundingMode.HALF_UP);
        if (normalizedTotal.compareTo(itemTotal) != 0) {
            throw new IllegalArgumentException("Total amount must match sum of item prices");
        }
    }

    private void validateUniqueSourceTransactionNumber(String sourceTransactionNumber) {
        if (transactionRepository.existsBySourceTransactionNumber(sourceTransactionNumber)) {
            throw new IllegalArgumentException("sourceTransactionNumber must be unique");
        }
    }

    private String normalizeCountryCode(String countryCode) {
        if (countryCode == null || countryCode.isBlank()) {
            throw new IllegalArgumentException("X-CountryCode header is required");
        }
        String normalizedCountryCode = countryCode.trim().toUpperCase(Locale.ROOT);
        if (normalizedCountryCode.length() > 3) {
            throw new IllegalArgumentException("X-CountryCode must have at most 3 characters");
        }
        return normalizedCountryCode;
    }

    private String normalizeSourceTransactionNumber(String transactionNumber, String fieldName) {
        String normalizedTransactionNumber = transactionNumber == null ? "" : transactionNumber.trim();
        if (normalizedTransactionNumber.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return normalizedTransactionNumber;
    }

    private int calculatePoints(BigDecimal amount, BigDecimal pointsPerCurrency) {
        if (amount == null || pointsPerCurrency == null) {
            throw new IllegalArgumentException("Amount and pointsPerCurrency are required");
        }
        if (amount.compareTo(ZERO) <= 0 || pointsPerCurrency.compareTo(ZERO) <= 0) {
            throw new IllegalArgumentException("Amount and pointsPerCurrency must be greater than zero");
        }
        BigDecimal points = amount.multiply(pointsPerCurrency);
        return points.setScale(0, RoundingMode.DOWN).intValueExact();
    }

    private List<Transaction> refreshCustomerPoints(Customer customer) {
        LocalDateTime now = LocalDateTime.now();
        List<Transaction> transactions = transactionRepository.findAllByCustomerIdOrderByPurchaseTimestampAsc(customer.getId());
        boolean changed = false;

        for (Transaction transaction : transactions) {
            TransactionState resolvedState = resolveState(transaction, now);
            if (transaction.getState() != resolvedState) {
                transaction.setState(resolvedState);
                changed = true;
            }
        }

        if (changed) {
            transactionRepository.saveAll(transactions);
        }

        int availablePoints = transactions.stream()
                .filter(transaction -> transaction.getState() == TransactionState.AVAILABLE)
                .mapToInt(Transaction::getPoints)
                .sum();

        customer.setLoyaltyPoints(availablePoints);
        customerRepository.save(customer);

        return transactions;
    }

    private TransactionState resolveState(Transaction transaction, LocalDateTime now) {
        if (transaction.getType() == TransactionType.MANUAL_ADJUSTMENT) {
            return TransactionState.AVAILABLE;
        }
        if (now.isBefore(transaction.getAvailableFrom())) {
            return TransactionState.PENDING;
        }
        if (now.isAfter(transaction.getExpiresAt())) {
            return TransactionState.EXPIRED;
        }
        return TransactionState.AVAILABLE;
    }

    private int calculatePointsWithHierarchy(List<StoreTransactionItemRequest> items, BigDecimal baseRate, List<HierarchyPromotion> promotions) {
        BigDecimal totalPoints = BigDecimal.ZERO;
        for (StoreTransactionItemRequest item : items) {
            if (hierarchyPromotionService.isItemExcluded(item.hierarchy(), promotions)) {
                continue;
            }
            BigDecimal multiplier = hierarchyPromotionService.resolveItemMultiplier(item.hierarchy(), promotions);
            totalPoints = totalPoints.add(item.price().amount().multiply(baseRate).multiply(multiplier));
        }
        return totalPoints.setScale(0, RoundingMode.DOWN).intValueExact();
    }

    private StoreTransactionResponse toResponse(Transaction transaction) {
        return new StoreTransactionResponse(
                transaction.getId(),
                transaction.getCustomer().getId(),
                transaction.getCustomer().getCustomerNumber(),
                transaction.getType(),
                transaction.getState(),
                transaction.getPoints(),
                transaction.getAmount(),
                transaction.getPointsPerCurrency(),
                transaction.getPurchaseTimestamp(),
                transaction.getAvailableFrom(),
                transaction.getExpiresAt()
        );
    }
}
