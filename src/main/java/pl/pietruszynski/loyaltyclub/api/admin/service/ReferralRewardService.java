package pl.pietruszynski.loyaltyclub.api.admin.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.pietruszynski.loyaltyclub.api.admin.model.Customer;
import pl.pietruszynski.loyaltyclub.api.admin.model.CustomerStatus;
import pl.pietruszynski.loyaltyclub.api.admin.model.ReferralReward;
import pl.pietruszynski.loyaltyclub.api.admin.model.Transaction;
import pl.pietruszynski.loyaltyclub.api.admin.model.TransactionState;
import pl.pietruszynski.loyaltyclub.api.admin.model.TransactionType;
import pl.pietruszynski.loyaltyclub.api.admin.repository.ReferralRewardRepository;
import pl.pietruszynski.loyaltyclub.api.admin.repository.TransactionRepository;
import pl.pietruszynski.loyaltyclub.config.ReferralProperties;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Regula premiowania polecen.
 *
 * <p>Kod polecajacy byl generowany przy rejestracji, a relacja "kto kogo polecil"
 * utrwalana -- ale nic za to nie przyznawalo punktow. Premia jest naliczana przy
 * pierwszym zakupie poleconego, ktory spelnia prog kwotowy; obie strony dostaja
 * osobna transakcje typu {@code REFERRAL}.
 *
 * <p>Naliczenie nie moze wywrocic sprzedazy: gdy premia sie nie nalezy albo nie
 * uda sie jej zapisac (wyscig o ten sam wiersz), transakcja sprzedazy pozostaje
 * poprawna. Dlatego wynik jest opcjonalny, a kolizja jednoznacznosci obslugiwana
 * jako stan oczekiwany.
 *
 * @see ReferralProperties opis czterech rozstrzygnietych decyzji
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ReferralRewardService {

    private final ReferralRewardRepository referralRewardRepository;
    private final TransactionRepository transactionRepository;
    private final CustomerPointsService customerPointsService;
    private final ReferralProperties referralProperties;

    /**
     * Nalicza premie za polecenie, jesli zakup ja uruchamia.
     *
     * @param referred poleconty uczestnik, ktory wlasnie dokonal zakupu
     * @param sale     transakcja sprzedazy uruchamiajaca sprawdzenie
     * @return zapisana premia albo pusty wynik, gdy warunki nie zostaly spelnione
     */
    @Transactional
    public Optional<ReferralReward> awardIfEligible(Customer referred, Transaction sale) {
        if (!Boolean.TRUE.equals(referralProperties.enabled())) {
            return Optional.empty();
        }
        Customer referrer = referred.getReferredBy();
        if (referrer == null || referrer.getId().equals(referred.getId())) {
            return Optional.empty();
        }
        if (!isEligible(referred, referrer, sale)) {
            return Optional.empty();
        }

        LocalDateTime now = LocalDateTime.now();
        Transaction referrerTransaction = saveBonus(referrer, referralProperties.referrerPoints(),
                "Referral bonus for referring customer " + referred.getCustomerNumber(), now);
        Transaction referredTransaction = saveBonus(referred, referralProperties.referredPoints(),
                "Referral bonus for being referred by " + referrer.getCustomerNumber(), now);

        ReferralReward reward;
        try {
            reward = referralRewardRepository.saveAndFlush(ReferralReward.builder()
                    .referrer(referrer)
                    .referred(referred)
                    .qualifyingTransaction(sale)
                    .referrerTransaction(referrerTransaction)
                    .referredTransaction(referredTransaction)
                    .referrerPoints(referralProperties.referrerPoints())
                    .referredPoints(referralProperties.referredPoints())
                    .country(referred.getCountry())
                    .awardedAt(now)
                    .build());
        } catch (DataIntegrityViolationException ex) {
            // Rownolegly zapis zdazyl rozliczyc to samo polecenie. Wycofujemy
            // wlasne transakcje premiowe, zeby nie powstala podwojna premia.
            transactionRepository.deleteAll(List.of(referrerTransaction, referredTransaction));
            log.debug("Referral reward for customer {} was already recorded", referred.getId());
            return Optional.empty();
        }

        // Polecajacy nie bierze udzialu w tym zakupie, wiec nikt inny nie przeliczy
        // jego sald. Bez tego premia istnialaby jako transakcja, ale panel
        // pokazywalby ja dopiero po jego wlasnej operacji punktowej.
        customerPointsService.refresh(referrer);
        return Optional.of(reward);
    }

    public List<ReferralReward> getRewardsGrantedTo(Long referrerCustomerId) {
        return referralRewardRepository.findAllByReferrerIdOrderByAwardedAtDesc(referrerCustomerId);
    }

    private boolean isEligible(Customer referred, Customer referrer, Transaction sale) {
        if (referralRewardRepository.existsByReferredId(referred.getId())) {
            return false;
        }
        if (referred.getStatus() != CustomerStatus.ACTIVE || referrer.getStatus() != CustomerStatus.ACTIVE) {
            return false;
        }
        if (sale.getType() != TransactionType.SALE) {
            return false;
        }
        BigDecimal amount = sale.getAmount() == null ? BigDecimal.ZERO : sale.getAmount();
        if (amount.compareTo(referralProperties.minimumPurchaseAmount()) < 0) {
            return false;
        }
        if (isOutsideQualifyingWindow(referred, sale)) {
            return false;
        }
        return referralRewardRepository.countByReferrerId(referrer.getId()) < referralProperties.maxRewardsPerReferrer();
    }

    private boolean isOutsideQualifyingWindow(Customer referred, Transaction sale) {
        if (referred.getCreatedAt() == null) {
            return false;
        }
        LocalDateTime deadline = referred.getCreatedAt().plusDays(referralProperties.qualifyingWindowDays());
        LocalDateTime purchasedAt = sale.getPurchaseTimestamp() == null ? sale.getTimestamp() : sale.getPurchaseTimestamp();
        return purchasedAt != null && purchasedAt.isAfter(deadline);
    }

    /** Punkty premiowe sa dostepne od razu -- karencja dotyczy tylko zakupow. */
    private Transaction saveBonus(Customer customer, int points, String description, LocalDateTime now) {
        return transactionRepository.save(Transaction.builder()
                .customer(customer)
                .points(points)
                .amount(BigDecimal.ZERO)
                .pointsPerCurrency(BigDecimal.ONE)
                .description(description)
                .country(customer.getCountry())
                .type(TransactionType.REFERRAL)
                .state(TransactionState.AVAILABLE)
                .purchaseTimestamp(now)
                .availableFrom(now)
                .expiresAt(now.plusDays(referralProperties.validityDays()))
                .build());
    }
}
