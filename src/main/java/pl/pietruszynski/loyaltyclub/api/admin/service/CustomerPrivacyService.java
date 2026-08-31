package pl.pietruszynski.loyaltyclub.api.admin.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.pietruszynski.loyaltyclub.api.admin.dto.PersonalDataExportDto;
import pl.pietruszynski.loyaltyclub.api.admin.model.Customer;
import pl.pietruszynski.loyaltyclub.api.admin.model.CustomerCoupon;
import pl.pietruszynski.loyaltyclub.api.admin.model.CustomerStatus;
import pl.pietruszynski.loyaltyclub.api.admin.model.ReferralReward;
import pl.pietruszynski.loyaltyclub.api.admin.model.Transaction;
import pl.pietruszynski.loyaltyclub.api.admin.repository.CustomerRepository;
import pl.pietruszynski.loyaltyclub.exception.BusinessException;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Realizacja praw uczestnika wynikajacych z RODO. Program dziala w kilku krajach
 * Unii i przetwarza dane osobowe, wiec prawo do przenoszenia danych (art. 20)
 * i do bycia zapomnianym (art. 17) musi miec odwzorowanie w API.
 *
 * <p>Prawo do usuniecia jest zrealizowane <b>anonimizacja, nie kasowaniem rekordow</b>.
 * Skasowanie uczestnika rozspojnilo by historie transakcji (wymagana rachunkowo
 * i do rozliczen ze sklepami) oraz log audytowy, ktorego sensem jest niezmiennosc.
 * Anonimizacja usuwa dane pozwalajace zidentyfikowac osobe, zachowujac zdarzenia
 * gospodarcze w postaci pozbawionej tozsamosci.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomerPrivacyService {

    private static final String ANONYMIZED_VALUE = "ANONIMIZOWANY";
    private static final String ANONYMIZED_PHONE = "000000000";

    private final CustomerRepository customerRepository;
    private final LoyaltyService loyaltyService;
    private final LoyaltyTierService loyaltyTierService;

    public PersonalDataExportDto exportPersonalData(Long customerId, String countryScope) {
        Customer customer = loyaltyService.getCustomerById(customerId, countryScope);
        List<Transaction> transactions = loyaltyService.getTransactionsForCustomer(customerId, countryScope);
        List<CustomerCoupon> coupons = loyaltyService.getCouponsForCustomer(customerId, countryScope);
        List<ReferralReward> referrals = loyaltyService.getReferralRewards(customerId, countryScope);

        return new PersonalDataExportDto(
                LocalDateTime.now(),
                toProfile(customer),
                transactions.stream().map(this::toTransactionEntry).toList(),
                coupons.stream().map(this::toCouponEntry).toList(),
                referrals.stream().map(this::toReferralEntry).toList()
        );
    }

    /**
     * Nieodwracalnie usuwa dane osobowe uczestnika. Numer klienta i adres poczty
     * musza pozostac jednoznaczne, dlatego zamiast pustych wartosci wstawiane sa
     * wartosci zastepcze z identyfikatorem rekordu. Kod polecajacy jest kasowany,
     * a powiazanie "polecony przez" -- zrywane, bo wskazuje na inna osobe.
     */
    @Transactional
    public Customer anonymize(Long customerId, String countryScope) {
        Customer customer = loyaltyService.getCustomerById(customerId, countryScope);
        if (customer.getStatus() == CustomerStatus.ANONYMIZED) {
            throw new BusinessException("Customer is already anonymized");
        }

        String marker = ANONYMIZED_VALUE + "-" + customer.getId();
        customer.setFirstName(ANONYMIZED_VALUE);
        customer.setLastName(marker);
        customer.setEmail("anonymized+" + customer.getId() + "@invalid.example");
        customer.setPhoneNumber(ANONYMIZED_PHONE);
        customer.setReferralCode(null);
        customer.setReferredBy(null);
        customer.setStatus(CustomerStatus.ANONYMIZED);
        customer.setStatusChangedAt(LocalDateTime.now());

        return customerRepository.save(customer);
    }

    private PersonalDataExportDto.Profile toProfile(Customer customer) {
        return new PersonalDataExportDto.Profile(
                customer.getId(),
                customer.getCustomerNumber(),
                customer.getFirstName(),
                customer.getLastName(),
                customer.getEmail(),
                customer.getPhoneNumber(),
                customer.getCountry(),
                customer.getStatus() == null ? CustomerStatus.ACTIVE.name() : customer.getStatus().name(),
                customer.getLoyaltyPoints(),
                customer.getLifetimePoints(),
                loyaltyTierService.resolveTierCode(customer),
                customer.getReferralCode(),
                customer.getReferredBy() == null ? null : customer.getReferredBy().getCustomerNumber(),
                customer.getCreatedAt()
        );
    }

    private PersonalDataExportDto.TransactionEntry toTransactionEntry(Transaction transaction) {
        return new PersonalDataExportDto.TransactionEntry(
                transaction.getId(),
                transaction.getTimestamp(),
                transaction.getPurchaseTimestamp(),
                transaction.getType() == null ? null : transaction.getType().name(),
                transaction.getState() == null ? null : transaction.getState().name(),
                transaction.getPoints(),
                transaction.getAmount(),
                transaction.getCountry(),
                transaction.getDescription(),
                transaction.getSourceTransactionNumber(),
                transaction.getAvailableFrom(),
                transaction.getExpiresAt()
        );
    }

    private PersonalDataExportDto.CouponEntry toCouponEntry(CustomerCoupon coupon) {
        return new PersonalDataExportDto.CouponEntry(
                coupon.getId(),
                coupon.getCouponCode(),
                coupon.effectiveStatus(LocalDateTime.now()).name(),
                coupon.getReason() == null ? null : coupon.getReason().name(),
                coupon.getCouponTemplate().getCouponValue(),
                coupon.getCouponTemplate().getRequiredPoints(),
                coupon.getCountry(),
                coupon.getIssuedAt(),
                coupon.getExpiresAt()
        );
    }

    private PersonalDataExportDto.ReferralEntry toReferralEntry(ReferralReward reward) {
        return new PersonalDataExportDto.ReferralEntry(
                reward.getId(),
                reward.getReferred().getCustomerNumber(),
                reward.getReferrerPoints(),
                reward.getReferredPoints(),
                reward.getAwardedAt()
        );
    }
}
