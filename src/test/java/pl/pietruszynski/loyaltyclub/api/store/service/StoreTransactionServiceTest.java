package pl.pietruszynski.loyaltyclub.api.store.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.pietruszynski.loyaltyclub.api.admin.model.Customer;
import pl.pietruszynski.loyaltyclub.api.admin.model.Transaction;
import pl.pietruszynski.loyaltyclub.api.admin.model.TransactionState;
import pl.pietruszynski.loyaltyclub.api.admin.model.TransactionType;
import pl.pietruszynski.loyaltyclub.api.admin.repository.CustomerRepository;
import pl.pietruszynski.loyaltyclub.api.admin.repository.TransactionRepository;
import pl.pietruszynski.loyaltyclub.api.store.dto.*;
import pl.pietruszynski.loyaltyclub.api.store.model.HierarchyPromotion;
import pl.pietruszynski.loyaltyclub.api.store.model.HierarchyPromotionType;
import pl.pietruszynski.loyaltyclub.exception.ResourceNotFoundException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StoreTransactionServiceTest {

    @Mock private CustomerRepository customerRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private StorePromotionService storePromotionService;
    @Mock private HierarchyPromotionService hierarchyPromotionService;

    @InjectMocks
    private StoreTransactionService storeTransactionService;

    // -----------------------------------------------------------------------
    // registerSale
    // -----------------------------------------------------------------------

    @Test
    void registerSale_valid_shouldCreatePendingTransaction() {
        Customer customer = customer("C001", 0);
        StoreSaleRequest request = saleRequest("C001", "TXN-001", new BigDecimal("100.00"));

        when(customerRepository.findByCustomerNumber("C001")).thenReturn(Optional.of(customer));
        when(transactionRepository.existsBySourceTransactionNumber("TXN-001")).thenReturn(false);
        when(storePromotionService.resolvePointsPerCurrency(eq("PL"), any())).thenReturn(new BigDecimal("1.00"));
        when(hierarchyPromotionService.getActivePromotions(eq("PL"), any())).thenReturn(Collections.emptyList());
        when(hierarchyPromotionService.isItemExcluded(any(), any())).thenReturn(false);
        when(hierarchyPromotionService.resolveItemMultiplier(any(), any())).thenReturn(BigDecimal.ONE);
        when(transactionRepository.save(any())).thenAnswer(inv -> withId(inv.getArgument(0), 1L));
        when(transactionRepository.findAllByCustomerIdOrderByPurchaseTimestampAsc(any())).thenReturn(Collections.emptyList());
        when(customerRepository.save(any())).thenReturn(customer);

        StoreTransactionResponse response = storeTransactionService.registerSale("PL", request);

        assertThat(response.points()).isEqualTo(100);
        assertThat(response.type()).isEqualTo(TransactionType.SALE);
        assertThat(response.state()).isEqualTo(TransactionState.PENDING);
    }

    @Test
    void registerSale_customerNotFound_shouldThrowResourceNotFound() {
        StoreSaleRequest request = saleRequest("UNKNOWN", "TXN-002", new BigDecimal("100.00"));
        when(customerRepository.findByCustomerNumber("UNKNOWN")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> storeTransactionService.registerSale("PL", request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void registerSale_duplicateTransactionNumber_shouldThrow() {
        Customer customer = customer("C001", 0);
        StoreSaleRequest request = saleRequest("C001", "TXN-DUP", new BigDecimal("100.00"));

        when(customerRepository.findByCustomerNumber("C001")).thenReturn(Optional.of(customer));
        when(transactionRepository.existsBySourceTransactionNumber("TXN-DUP")).thenReturn(true);

        assertThatThrownBy(() -> storeTransactionService.registerSale("PL", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unique");
    }

    @Test
    void registerSale_totalAmountMismatch_shouldThrow() {
        Customer customer = customer("C001", 0);
        // totalAmount differs from item sum
        List<StoreTransactionItemRequest> items = List.of(
                new StoreTransactionItemRequest("1", "EAN1", "Item1", new HierarchyRequest("42", null, null),
                        new StoreItemPriceRequest(new BigDecimal("30.00"), "PLN"))
        );
        StoreSaleRequest request = new StoreSaleRequest("C001", items, new BigDecimal("100.00"), "TXN-003", null);

        when(customerRepository.findByCustomerNumber("C001")).thenReturn(Optional.of(customer));
        when(transactionRepository.existsBySourceTransactionNumber("TXN-003")).thenReturn(false);

        assertThatThrownBy(() -> storeTransactionService.registerSale("PL", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Total amount must match");
    }

    @Test
    void registerSale_blankCountryCode_shouldThrow() {
        Customer customer = customer("C001", 0);
        StoreSaleRequest request = saleRequest("C001", "TXN-004", new BigDecimal("100.00"));

        // findCustomerByNumber is called before normalizeCountryCode
        when(customerRepository.findByCustomerNumber("C001")).thenReturn(Optional.of(customer));

        assertThatThrownBy(() -> storeTransactionService.registerSale("", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("X-CountryCode header is required");
    }

    @Test
    void registerSale_countryCodeTooLong_shouldThrow() {
        Customer customer = customer("C001", 0);
        StoreSaleRequest request = saleRequest("C001", "TXN-005", new BigDecimal("100.00"));

        // findCustomerByNumber is called before normalizeCountryCode
        when(customerRepository.findByCustomerNumber("C001")).thenReturn(Optional.of(customer));

        assertThatThrownBy(() -> storeTransactionService.registerSale("TOOLONG", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at most 3 characters");
    }

    @Test
    void registerSale_pointsCalculatedByPromotion() {
        Customer customer = customer("C001", 0);
        StoreSaleRequest request = saleRequest("C001", "TXN-006", new BigDecimal("100.00"));

        when(customerRepository.findByCustomerNumber("C001")).thenReturn(Optional.of(customer));
        when(transactionRepository.existsBySourceTransactionNumber("TXN-006")).thenReturn(false);
        when(storePromotionService.resolvePointsPerCurrency(eq("PL"), any())).thenReturn(new BigDecimal("2.50"));
        when(hierarchyPromotionService.getActivePromotions(eq("PL"), any())).thenReturn(Collections.emptyList());
        when(hierarchyPromotionService.isItemExcluded(any(), any())).thenReturn(false);
        when(hierarchyPromotionService.resolveItemMultiplier(any(), any())).thenReturn(BigDecimal.ONE);
        when(transactionRepository.save(any())).thenAnswer(inv -> withId(inv.getArgument(0), 1L));
        when(transactionRepository.findAllByCustomerIdOrderByPurchaseTimestampAsc(any())).thenReturn(Collections.emptyList());
        when(customerRepository.save(any())).thenReturn(customer);

        StoreTransactionResponse response = storeTransactionService.registerSale("PL", request);

        // 100.00 * 2.50 = 250 points (floor)
        assertThat(response.points()).isEqualTo(250);
    }

    // -----------------------------------------------------------------------
    // registerReturn
    // -----------------------------------------------------------------------

    @Test
    void registerReturn_valid_shouldCreateReturnTransaction() {
        Customer customer = customer("C001", 100);
        customer.setId(1L);

        LocalDateTime purchaseTime = LocalDateTime.now().minusDays(5);
        Transaction sale = saleTransaction(customer, "SALE-001", new BigDecimal("100.00"), 100, purchaseTime);

        StoreReturnRequest returnRequest = new StoreReturnRequest(
                "C001",
                List.of(new StoreTransactionItemRequest("1", "EAN1", "Item1", new HierarchyRequest("42", null, null),
                        new StoreItemPriceRequest(new BigDecimal("50.00"), "PLN"))),
                new BigDecimal("50.00"),
                "RET-001",
                "SALE-001",
                null
        );

        when(customerRepository.findByCustomerNumber("C001")).thenReturn(Optional.of(customer));
        when(transactionRepository.existsBySourceTransactionNumber("RET-001")).thenReturn(false);
        when(transactionRepository.findBySourceTransactionNumberAndCustomerId("SALE-001", 1L))
                .thenReturn(Optional.of(sale));
        when(transactionRepository.sumAmountBySourceTransactionIdAndType(any(), eq(TransactionType.RETURN)))
                .thenReturn(BigDecimal.ZERO);
        when(transactionRepository.sumPointsBySourceTransactionIdAndType(any(), eq(TransactionType.RETURN)))
                .thenReturn(0);
        when(transactionRepository.save(any())).thenAnswer(inv -> withId(inv.getArgument(0), 2L));
        when(transactionRepository.findAllByCustomerIdOrderByPurchaseTimestampAsc(any()))
                .thenReturn(Collections.emptyList());
        when(customerRepository.save(any())).thenReturn(customer);

        StoreTransactionResponse response = storeTransactionService.registerReturn("PL", returnRequest);

        assertThat(response.type()).isEqualTo(TransactionType.RETURN);
        assertThat(response.points()).isEqualTo(-50);
    }

    @Test
    void registerReturn_saleNotFound_shouldThrow() {
        Customer customer = customer("C001", 0);
        customer.setId(1L);

        StoreReturnRequest returnRequest = new StoreReturnRequest(
                "C001",
                List.of(new StoreTransactionItemRequest("1", "EAN1", "Item1", new HierarchyRequest("42", null, null),
                        new StoreItemPriceRequest(new BigDecimal("50.00"), "PLN"))),
                new BigDecimal("50.00"),
                "RET-002",
                "MISSING-SALE",
                null
        );

        when(customerRepository.findByCustomerNumber("C001")).thenReturn(Optional.of(customer));
        when(transactionRepository.existsBySourceTransactionNumber("RET-002")).thenReturn(false);
        when(transactionRepository.findBySourceTransactionNumberAndCustomerId("MISSING-SALE", 1L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> storeTransactionService.registerReturn("PL", returnRequest))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void registerReturn_amountExceedsSale_shouldThrow() {
        Customer customer = customer("C001", 100);
        customer.setId(1L);

        LocalDateTime purchaseTime = LocalDateTime.now().minusDays(5);
        Transaction sale = saleTransaction(customer, "SALE-003", new BigDecimal("50.00"), 50, purchaseTime);

        StoreReturnRequest returnRequest = new StoreReturnRequest(
                "C001",
                List.of(new StoreTransactionItemRequest("1", "EAN1", "Item1", new HierarchyRequest("42", null, null),
                        new StoreItemPriceRequest(new BigDecimal("100.00"), "PLN"))),
                new BigDecimal("100.00"),
                "RET-003",
                "SALE-003",
                null
        );

        when(customerRepository.findByCustomerNumber("C001")).thenReturn(Optional.of(customer));
        when(transactionRepository.existsBySourceTransactionNumber("RET-003")).thenReturn(false);
        when(transactionRepository.findBySourceTransactionNumberAndCustomerId("SALE-003", 1L))
                .thenReturn(Optional.of(sale));
        // alreadyReturnedAmount = 0, remainingAmount = 50.00, returnAmount = 100.00 → exceeds
        when(transactionRepository.sumAmountBySourceTransactionIdAndType(any(), eq(TransactionType.RETURN)))
                .thenReturn(BigDecimal.ZERO);

        assertThatThrownBy(() -> storeTransactionService.registerReturn("PL", returnRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Return amount exceeds");
    }

    @Test
    void registerReturn_sourceIsNotSale_shouldThrow() {
        Customer customer = customer("C001", 0);
        customer.setId(1L);

        LocalDateTime purchaseTime = LocalDateTime.now().minusDays(5);
        Transaction returnTx = Transaction.builder()
                .id(10L)
                .customer(customer)
                .points(-30)
                .amount(new BigDecimal("30.00"))
                .pointsPerCurrency(BigDecimal.ONE)
                .description("return")
                .country("PL")
                .type(TransactionType.RETURN) // NOT a SALE
                .state(TransactionState.AVAILABLE)
                .purchaseTimestamp(purchaseTime)
                .availableFrom(purchaseTime.plusDays(30))
                .expiresAt(purchaseTime.plusDays(365))
                .sourceTransactionNumber("RET-SRC-001")
                .build();

        StoreReturnRequest returnRequest = new StoreReturnRequest(
                "C001",
                List.of(new StoreTransactionItemRequest("1", "EAN1", "Item1", new HierarchyRequest("42", null, null),
                        new StoreItemPriceRequest(new BigDecimal("10.00"), "PLN"))),
                new BigDecimal("10.00"),
                "RET-004",
                "RET-SRC-001",
                null
        );

        when(customerRepository.findByCustomerNumber("C001")).thenReturn(Optional.of(customer));
        when(transactionRepository.existsBySourceTransactionNumber("RET-004")).thenReturn(false);
        when(transactionRepository.findBySourceTransactionNumberAndCustomerId("RET-SRC-001", 1L))
                .thenReturn(Optional.of(returnTx));

        assertThatThrownBy(() -> storeTransactionService.registerReturn("PL", returnRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SALE");
    }

    @Test
    void registerReturn_countryMismatch_shouldThrow() {
        Customer customer = customer("C001", 100);
        customer.setId(1L);

        LocalDateTime purchaseTime = LocalDateTime.now().minusDays(5);
        Transaction sale = saleTransaction(customer, "SALE-004", new BigDecimal("100.00"), 100, purchaseTime);
        // sale country is PL, but return uses DE

        StoreReturnRequest returnRequest = new StoreReturnRequest(
                "C001",
                List.of(new StoreTransactionItemRequest("1", "EAN1", "Item1", new HierarchyRequest("42", null, null),
                        new StoreItemPriceRequest(new BigDecimal("50.00"), "PLN"))),
                new BigDecimal("50.00"),
                "RET-005",
                "SALE-004",
                null
        );

        when(customerRepository.findByCustomerNumber("C001")).thenReturn(Optional.of(customer));
        when(transactionRepository.existsBySourceTransactionNumber("RET-005")).thenReturn(false);
        when(transactionRepository.findBySourceTransactionNumberAndCustomerId("SALE-004", 1L))
                .thenReturn(Optional.of(sale));

        assertThatThrownBy(() -> storeTransactionService.registerReturn("DE", returnRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("country");
    }

    @Test
    void registerReturn_expiredSale_shouldThrow() {
        Customer customer = customer("C001", 100);
        customer.setId(1L);

        // Purchase 400 days ago → expires in 365 days → already expired
        LocalDateTime purchaseTime = LocalDateTime.now().minusDays(400);
        Transaction sale = saleTransaction(customer, "SALE-005", new BigDecimal("100.00"), 100, purchaseTime);

        StoreReturnRequest returnRequest = new StoreReturnRequest(
                "C001",
                List.of(new StoreTransactionItemRequest("1", "EAN1", "Item1", new HierarchyRequest("42", null, null),
                        new StoreItemPriceRequest(new BigDecimal("50.00"), "PLN"))),
                new BigDecimal("50.00"),
                "RET-006",
                "SALE-005",
                null
        );

        when(customerRepository.findByCustomerNumber("C001")).thenReturn(Optional.of(customer));
        when(transactionRepository.existsBySourceTransactionNumber("RET-006")).thenReturn(false);
        when(transactionRepository.findBySourceTransactionNumberAndCustomerId("SALE-005", 1L))
                .thenReturn(Optional.of(sale));

        assertThatThrownBy(() -> storeTransactionService.registerReturn("PL", returnRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void registerReturn_noPointsLeftToReverse_shouldThrow() {
        Customer customer = customer("C001", 0);
        customer.setId(1L);

        LocalDateTime purchaseTime = LocalDateTime.now().minusDays(5);
        Transaction sale = saleTransaction(customer, "SALE-006", new BigDecimal("100.00"), 50, purchaseTime);

        StoreReturnRequest returnRequest = new StoreReturnRequest(
                "C001",
                List.of(new StoreTransactionItemRequest("1", "EAN1", "Item1", new HierarchyRequest("42", null, null),
                        new StoreItemPriceRequest(new BigDecimal("10.00"), "PLN"))),
                new BigDecimal("10.00"),
                "RET-007",
                "SALE-006",
                null
        );

        when(customerRepository.findByCustomerNumber("C001")).thenReturn(Optional.of(customer));
        when(transactionRepository.existsBySourceTransactionNumber("RET-007")).thenReturn(false);
        when(transactionRepository.findBySourceTransactionNumberAndCustomerId("SALE-006", 1L))
                .thenReturn(Optional.of(sale));
        when(transactionRepository.sumAmountBySourceTransactionIdAndType(any(), eq(TransactionType.RETURN)))
                .thenReturn(BigDecimal.ZERO);
        // already reversed all 50 points
        when(transactionRepository.sumPointsBySourceTransactionIdAndType(any(), eq(TransactionType.RETURN)))
                .thenReturn(-50);

        assertThatThrownBy(() -> storeTransactionService.registerReturn("PL", returnRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No points left");
    }

    @Test
    void registerSale_manualAdjustmentAlwaysAvailable_resolveState() {
        // Test that MANUAL_ADJUSTMENT transactions are always AVAILABLE in refreshCustomerPoints
        Customer customer = customer("C001", 0);
        customer.setId(1L);

        LocalDateTime future = LocalDateTime.now().plusDays(25);
        Transaction manualTx = Transaction.builder()
                .customer(customer)
                .points(50)
                .amount(BigDecimal.ZERO)
                .pointsPerCurrency(BigDecimal.ONE)
                .description("manual")
                .country("PL")
                .type(TransactionType.MANUAL_ADJUSTMENT)
                .state(TransactionState.PENDING)  // should become AVAILABLE
                .purchaseTimestamp(future)
                .availableFrom(future.plusDays(30))
                .expiresAt(future.plusDays(365))
                .build();

        StoreSaleRequest request = saleRequest("C001", "TXN-MANUAL", new BigDecimal("10.00"));

        when(customerRepository.findByCustomerNumber("C001")).thenReturn(Optional.of(customer));
        when(transactionRepository.existsBySourceTransactionNumber("TXN-MANUAL")).thenReturn(false);
        when(storePromotionService.resolvePointsPerCurrency(eq("PL"), any())).thenReturn(BigDecimal.ONE);
        when(hierarchyPromotionService.getActivePromotions(eq("PL"), any())).thenReturn(Collections.emptyList());
        when(hierarchyPromotionService.isItemExcluded(any(), any())).thenReturn(false);
        when(hierarchyPromotionService.resolveItemMultiplier(any(), any())).thenReturn(BigDecimal.ONE);
        when(transactionRepository.save(any())).thenAnswer(inv -> withId(inv.getArgument(0), 99L));
        when(transactionRepository.findAllByCustomerIdOrderByPurchaseTimestampAsc(any()))
                .thenReturn(List.of(manualTx));
        when(transactionRepository.saveAll(any())).thenReturn(List.of(manualTx));
        when(customerRepository.save(any())).thenReturn(customer);

        storeTransactionService.registerSale("PL", request);

        assertThat(manualTx.getState()).isEqualTo(TransactionState.AVAILABLE);
    }

    @Test
    void getPointsBalance_withExpiredTransaction_shouldReturnExpiredCount() {
        Customer customer = customer("C001", 0);
        customer.setId(1L);

        LocalDateTime veryOld = LocalDateTime.now().minusDays(400);
        Transaction expiredTx = Transaction.builder()
                .customer(customer)
                .points(30)
                .amount(BigDecimal.ZERO)
                .pointsPerCurrency(BigDecimal.ONE)
                .description("old sale")
                .country("PL")
                .type(TransactionType.SALE)
                .state(TransactionState.AVAILABLE)
                .purchaseTimestamp(veryOld)
                .availableFrom(veryOld.plusDays(30))
                .expiresAt(veryOld.plusDays(365)) // expired
                .build();

        when(customerRepository.findByCustomerNumber("C001")).thenReturn(Optional.of(customer));
        when(transactionRepository.findAllByCustomerIdOrderByPurchaseTimestampAsc(1L))
                .thenReturn(List.of(expiredTx));
        when(transactionRepository.saveAll(any())).thenReturn(List.of(expiredTx));
        when(customerRepository.save(any())).thenReturn(customer);

        StorePointsBalanceResponse balance = storeTransactionService.getPointsBalance("C001");

        assertThat(balance.expiredPoints()).isEqualTo(30);
        assertThat(balance.availablePoints()).isZero();
    }

    // -----------------------------------------------------------------------
    // getPointsBalance
    // -----------------------------------------------------------------------

    @Test
    void getPointsBalance_shouldRefreshAndReturnBreakdown() {
        Customer customer = customer("C001", 0);
        customer.setId(1L);

        LocalDateTime past = LocalDateTime.now().minusDays(60);
        Transaction availableTx = Transaction.builder()
                .customer(customer)
                .points(100)
                .amount(BigDecimal.ZERO)
                .pointsPerCurrency(BigDecimal.ONE)
                .description("sale")
                .country("PL")
                .type(TransactionType.SALE)
                .state(TransactionState.AVAILABLE)
                .purchaseTimestamp(past)
                .availableFrom(past.plusDays(30))
                .expiresAt(past.plusDays(365))
                .build();

        when(customerRepository.findByCustomerNumber("C001")).thenReturn(Optional.of(customer));
        when(transactionRepository.findAllByCustomerIdOrderByPurchaseTimestampAsc(1L))
                .thenReturn(List.of(availableTx));
        when(customerRepository.save(any())).thenReturn(customer);

        StorePointsBalanceResponse balance = storeTransactionService.getPointsBalance("C001");

        assertThat(balance.availablePoints()).isEqualTo(100);
        assertThat(balance.pendingPoints()).isZero();
        assertThat(balance.expiredPoints()).isZero();
    }

    @Test
    void getPointsBalance_customerNotFound_shouldThrow() {
        when(customerRepository.findByCustomerNumber("GHOST")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> storeTransactionService.getPointsBalance("GHOST"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // -----------------------------------------------------------------------
    // Hierarchy promotion calculation
    // -----------------------------------------------------------------------

    @Test
    void registerSale_withHierarchyMultiplier_shouldApplyMultiplier() {
        Customer customer = customer("C001", 0);
        StoreSaleRequest request = saleRequest("C001", "TXN-H01", new BigDecimal("100.00"));

        when(customerRepository.findByCustomerNumber("C001")).thenReturn(Optional.of(customer));
        when(transactionRepository.existsBySourceTransactionNumber("TXN-H01")).thenReturn(false);
        when(storePromotionService.resolvePointsPerCurrency(eq("PL"), any())).thenReturn(new BigDecimal("1.00"));
        when(hierarchyPromotionService.getActivePromotions(eq("PL"), any())).thenReturn(Collections.emptyList());
        when(hierarchyPromotionService.isItemExcluded(any(), any())).thenReturn(false);
        when(hierarchyPromotionService.resolveItemMultiplier(any(), any())).thenReturn(new BigDecimal("4.00"));
        when(transactionRepository.save(any())).thenAnswer(inv -> withId(inv.getArgument(0), 10L));
        when(transactionRepository.findAllByCustomerIdOrderByPurchaseTimestampAsc(any())).thenReturn(Collections.emptyList());
        when(customerRepository.save(any())).thenReturn(customer);

        StoreTransactionResponse response = storeTransactionService.registerSale("PL", request);

        // 100.00 * 1.00 * 4.00 = 400 points
        assertThat(response.points()).isEqualTo(400);
    }

    @Test
    void registerSale_withHierarchyExclusion_shouldYieldZeroPoints() {
        Customer customer = customer("C001", 0);
        StoreSaleRequest request = saleRequest("C001", "TXN-H02", new BigDecimal("100.00"));

        when(customerRepository.findByCustomerNumber("C001")).thenReturn(Optional.of(customer));
        when(transactionRepository.existsBySourceTransactionNumber("TXN-H02")).thenReturn(false);
        when(storePromotionService.resolvePointsPerCurrency(eq("PL"), any())).thenReturn(new BigDecimal("1.00"));
        when(hierarchyPromotionService.getActivePromotions(eq("PL"), any())).thenReturn(Collections.emptyList());
        when(hierarchyPromotionService.isItemExcluded(any(), any())).thenReturn(true);
        when(transactionRepository.save(any())).thenAnswer(inv -> withId(inv.getArgument(0), 11L));
        when(transactionRepository.findAllByCustomerIdOrderByPurchaseTimestampAsc(any())).thenReturn(Collections.emptyList());
        when(customerRepository.save(any())).thenReturn(customer);

        StoreTransactionResponse response = storeTransactionService.registerSale("PL", request);

        assertThat(response.points()).isZero();
    }

    @Test
    void registerReturn_saleWithZeroPoints_shouldCreateReturnWithZeroPoints() {
        Customer customer = customer("C001", 0);
        customer.setId(1L);

        LocalDateTime purchaseTime = LocalDateTime.now().minusDays(5);
        Transaction sale = Transaction.builder()
                .id(10L)
                .customer(customer)
                .points(0)
                .amount(new BigDecimal("100.00"))
                .pointsPerCurrency(BigDecimal.ONE)
                .description("Store sale: SALE-ZERO")
                .country("PL")
                .type(TransactionType.SALE)
                .state(TransactionState.AVAILABLE)
                .purchaseTimestamp(purchaseTime)
                .availableFrom(purchaseTime.plusDays(30))
                .expiresAt(purchaseTime.plusDays(365))
                .sourceTransactionNumber("SALE-ZERO")
                .build();

        StoreReturnRequest returnRequest = new StoreReturnRequest(
                "C001",
                List.of(new StoreTransactionItemRequest("1", "EAN1", "Item1", new HierarchyRequest("42", null, null),
                        new StoreItemPriceRequest(new BigDecimal("50.00"), "PLN"))),
                new BigDecimal("50.00"),
                "RET-ZERO",
                "SALE-ZERO",
                null
        );

        when(customerRepository.findByCustomerNumber("C001")).thenReturn(Optional.of(customer));
        when(transactionRepository.existsBySourceTransactionNumber("RET-ZERO")).thenReturn(false);
        when(transactionRepository.findBySourceTransactionNumberAndCustomerId("SALE-ZERO", 1L))
                .thenReturn(Optional.of(sale));
        when(transactionRepository.sumAmountBySourceTransactionIdAndType(any(), eq(TransactionType.RETURN)))
                .thenReturn(BigDecimal.ZERO);
        when(transactionRepository.sumPointsBySourceTransactionIdAndType(any(), eq(TransactionType.RETURN)))
                .thenReturn(0);
        when(transactionRepository.save(any())).thenAnswer(inv -> withId(inv.getArgument(0), 20L));
        when(transactionRepository.findAllByCustomerIdOrderByPurchaseTimestampAsc(any())).thenReturn(Collections.emptyList());
        when(customerRepository.save(any())).thenReturn(customer);

        StoreTransactionResponse response = storeTransactionService.registerReturn("PL", returnRequest);

        assertThat(response.type()).isEqualTo(TransactionType.RETURN);
        assertThat(response.points()).isZero();
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private Customer customer(String number, int points) {
        return Customer.builder()
                .firstName("Jan")
                .lastName("Kowalski")
                .email(number + "@test.com")
                .customerNumber(number)
                .phoneNumber("111222333")
                .country("PL")
                .loyaltyPoints(points)
                .build();
    }

    private StoreSaleRequest saleRequest(String customerNumber, String txnNumber, BigDecimal total) {
        List<StoreTransactionItemRequest> items = List.of(
                new StoreTransactionItemRequest("1", "EAN1", "Item1",
                        new HierarchyRequest("42", null, null),
                        new StoreItemPriceRequest(total, "PLN"))
        );
        return new StoreSaleRequest(customerNumber, items, total, txnNumber, null);
    }

    private Transaction saleTransaction(Customer customer, String sourceNumber, BigDecimal amount, int points, LocalDateTime purchaseTime) {
        return Transaction.builder()
                .id(10L)
                .customer(customer)
                .points(points)
                .amount(amount)
                .pointsPerCurrency(BigDecimal.ONE)
                .description("Store sale: " + sourceNumber)
                .country("PL")
                .type(TransactionType.SALE)
                .state(TransactionState.AVAILABLE)
                .purchaseTimestamp(purchaseTime)
                .availableFrom(purchaseTime.plusDays(30))
                .expiresAt(purchaseTime.plusDays(365))
                .sourceTransactionNumber(sourceNumber)
                .build();
    }

    private Transaction withId(Transaction t, Long id) {
        t.setId(id);
        return t;
    }
}
