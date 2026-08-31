package pl.pietruszynski.loyaltyclub.api.admin.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import pl.pietruszynski.loyaltyclub.api.admin.model.Customer;
import pl.pietruszynski.loyaltyclub.api.admin.model.CustomerStatus;
import pl.pietruszynski.loyaltyclub.api.admin.model.ReferralReward;
import pl.pietruszynski.loyaltyclub.api.admin.model.Transaction;
import pl.pietruszynski.loyaltyclub.api.admin.model.TransactionType;
import pl.pietruszynski.loyaltyclub.api.admin.repository.ReferralRewardRepository;
import pl.pietruszynski.loyaltyclub.api.admin.repository.TransactionRepository;
import pl.pietruszynski.loyaltyclub.config.ReferralProperties;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regula premiowania polecen. Kod polecajacy byl generowany, a relacja "kto kogo
 * polecil" utrwalana -- ale nic za to nie przyznawalo punktow.
 */
@ExtendWith(MockitoExtension.class)
class ReferralRewardServiceTest {

    private static final BigDecimal THRESHOLD = new BigDecimal("100.00");

    @Mock private ReferralRewardRepository referralRewardRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private CustomerPointsService customerPointsService;

    private ReferralRewardService referralRewardService;
    private final List<Transaction> savedTransactions = new ArrayList<>();

    @BeforeEach
    void setUp() {
        ReferralProperties properties = new ReferralProperties(true, THRESHOLD, 500, 250, 10, 365, 365);
        referralRewardService = new ReferralRewardService(
                referralRewardRepository, transactionRepository, customerPointsService, properties);

        savedTransactions.clear();
        lenient().when(transactionRepository.save(any())).thenAnswer(inv -> {
            Transaction t = inv.getArgument(0);
            t.setId((long) (savedTransactions.size() + 100));
            savedTransactions.add(t);
            return t;
        });
        lenient().when(referralRewardRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void awardIfEligible_qualifyingPurchase_shouldRewardBothSides() {
        Customer referrer = customer(1L, "C001");
        Customer referred = customer(2L, "C002");
        referred.setReferredBy(referrer);

        when(referralRewardRepository.existsByReferredId(2L)).thenReturn(false);
        when(referralRewardRepository.countByReferrerId(1L)).thenReturn(0L);

        Optional<ReferralReward> reward = referralRewardService.awardIfEligible(referred, sale(referred, "150.00"));

        assertThat(reward).isPresent();
        assertThat(reward.get().getReferrerPoints()).isEqualTo(500);
        assertThat(reward.get().getReferredPoints()).isEqualTo(250);
        assertThat(savedTransactions)
                .hasSize(2)
                .allSatisfy(t -> assertThat(t.getType()).isEqualTo(TransactionType.REFERRAL));
        // Polecajacy nie bierze udzialu w tym zakupie -- nikt inny nie przeliczy jego sald.
        verify(customerPointsService).refresh(referrer);
    }

    /** Premiujemy pierwszy kwalifikujacy sie zakup, nie sama rejestracje. */
    @Test
    void awardIfEligible_purchaseBelowThreshold_shouldNotReward() {
        Customer referrer = customer(1L, "C001");
        Customer referred = customer(2L, "C002");
        referred.setReferredBy(referrer);

        when(referralRewardRepository.existsByReferredId(2L)).thenReturn(false);

        assertThat(referralRewardService.awardIfEligible(referred, sale(referred, "99.99"))).isEmpty();
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void awardIfEligible_withoutReferrer_shouldNotReward() {
        Customer referred = customer(2L, "C002");

        assertThat(referralRewardService.awardIfEligible(referred, sale(referred, "500.00"))).isEmpty();
        verify(transactionRepository, never()).save(any());
    }

    /** Jedna premia na poleconego uczestnika, niezaleznie od liczby zakupow. */
    @Test
    void awardIfEligible_alreadyRewarded_shouldNotRewardAgain() {
        Customer referrer = customer(1L, "C001");
        Customer referred = customer(2L, "C002");
        referred.setReferredBy(referrer);

        when(referralRewardRepository.existsByReferredId(2L)).thenReturn(true);

        assertThat(referralRewardService.awardIfEligible(referred, sale(referred, "500.00"))).isEmpty();
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void awardIfEligible_referrerLimitReached_shouldNotReward() {
        Customer referrer = customer(1L, "C001");
        Customer referred = customer(2L, "C002");
        referred.setReferredBy(referrer);

        when(referralRewardRepository.existsByReferredId(2L)).thenReturn(false);
        when(referralRewardRepository.countByReferrerId(1L)).thenReturn(10L);

        assertThat(referralRewardService.awardIfEligible(referred, sale(referred, "500.00"))).isEmpty();
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void awardIfEligible_purchaseOutsideQualifyingWindow_shouldNotReward() {
        Customer referrer = customer(1L, "C001");
        Customer referred = customer(2L, "C002");
        referred.setReferredBy(referrer);
        referred.setCreatedAt(LocalDateTime.now().minusDays(400));

        when(referralRewardRepository.existsByReferredId(2L)).thenReturn(false);

        assertThat(referralRewardService.awardIfEligible(referred, sale(referred, "500.00"))).isEmpty();
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void awardIfEligible_inactiveReferrer_shouldNotReward() {
        Customer referrer = customer(1L, "C001");
        referrer.setStatus(CustomerStatus.INACTIVE);
        Customer referred = customer(2L, "C002");
        referred.setReferredBy(referrer);

        when(referralRewardRepository.existsByReferredId(2L)).thenReturn(false);

        assertThat(referralRewardService.awardIfEligible(referred, sale(referred, "500.00"))).isEmpty();
    }

    /**
     * Rownolegly zapis z drugiej kasy przegrywa z ograniczeniem jednoznacznosci.
     * Wlasne transakcje premiowe musza wtedy zostac wycofane, zeby nie powstala
     * premia bez pokrycia w rejestrze polecen.
     */
    @Test
    void awardIfEligible_concurrentAward_shouldRollBackOwnBonusTransactions() {
        Customer referrer = customer(1L, "C001");
        Customer referred = customer(2L, "C002");
        referred.setReferredBy(referrer);

        when(referralRewardRepository.existsByReferredId(2L)).thenReturn(false);
        when(referralRewardRepository.countByReferrerId(1L)).thenReturn(0L);
        when(referralRewardRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("uq_referral_rewards_referred"));

        assertThat(referralRewardService.awardIfEligible(referred, sale(referred, "500.00"))).isEmpty();
        verify(transactionRepository).deleteAll(any());
    }

    @Test
    void awardIfEligible_disabledByConfiguration_shouldNotReward() {
        ReferralProperties disabled = new ReferralProperties(false, THRESHOLD, 500, 250, 10, 365, 365);
        ReferralRewardService service = new ReferralRewardService(
                referralRewardRepository, transactionRepository, customerPointsService, disabled);

        Customer referrer = customer(1L, "C001");
        Customer referred = customer(2L, "C002");
        referred.setReferredBy(referrer);

        assertThat(service.awardIfEligible(referred, sale(referred, "500.00"))).isEmpty();
        verify(transactionRepository, never()).save(any());
    }

    private Customer customer(Long id, String number) {
        Customer customer = Customer.builder()
                .firstName("Jan")
                .lastName("Kowalski")
                .email(number + "@pl.com")
                .customerNumber(number)
                .phoneNumber("123456789")
                .country("PL")
                .build();
        customer.setId(id);
        customer.setStatus(CustomerStatus.ACTIVE);
        customer.setCreatedAt(LocalDateTime.now().minusDays(10));
        return customer;
    }

    private Transaction sale(Customer customer, String amount) {
        LocalDateTime now = LocalDateTime.now();
        return Transaction.builder()
                .id(1L)
                .customer(customer)
                .points(100)
                .amount(new BigDecimal(amount))
                .pointsPerCurrency(BigDecimal.ONE)
                .description("Store sale")
                .country("PL")
                .type(TransactionType.SALE)
                .purchaseTimestamp(now)
                .availableFrom(now.plusDays(30))
                .expiresAt(now.plusDays(365))
                .build();
    }
}
