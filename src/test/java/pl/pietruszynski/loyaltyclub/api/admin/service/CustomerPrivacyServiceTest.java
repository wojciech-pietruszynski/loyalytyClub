package pl.pietruszynski.loyaltyclub.api.admin.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.pietruszynski.loyaltyclub.api.admin.dto.PersonalDataExportDto;
import pl.pietruszynski.loyaltyclub.api.admin.model.Customer;
import pl.pietruszynski.loyaltyclub.api.admin.model.CustomerStatus;
import pl.pietruszynski.loyaltyclub.api.admin.model.Transaction;
import pl.pietruszynski.loyaltyclub.api.admin.model.TransactionType;
import pl.pietruszynski.loyaltyclub.api.admin.repository.CustomerRepository;
import pl.pietruszynski.loyaltyclub.exception.BusinessException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Prawa uczestnika wynikajace z RODO. Prawo do usuniecia realizujemy anonimizacja,
 * nie kasowaniem rekordow -- historia transakcji i log audytowy musza sie bilansowac.
 */
@ExtendWith(MockitoExtension.class)
class CustomerPrivacyServiceTest {

    @Mock private CustomerRepository customerRepository;
    @Mock private LoyaltyService loyaltyService;
    @Mock private LoyaltyTierService loyaltyTierService;

    @InjectMocks
    private CustomerPrivacyService customerPrivacyService;

    @Test
    void exportPersonalData_shouldIncludeProfileAndHistory() {
        Customer customer = customer();
        when(loyaltyService.getCustomerById(1L, null)).thenReturn(customer);
        when(loyaltyService.getTransactionsForCustomer(1L, null)).thenReturn(List.of(transaction(customer)));
        when(loyaltyService.getCouponsForCustomer(1L, null)).thenReturn(List.of());
        when(loyaltyService.getReferralRewards(1L, null)).thenReturn(List.of());
        when(loyaltyTierService.resolveTierCode(any(Customer.class))).thenReturn("SILVER");

        PersonalDataExportDto export = customerPrivacyService.exportPersonalData(1L, null);

        assertThat(export.profile().customerNumber()).isEqualTo("C001");
        assertThat(export.profile().email()).isEqualTo("jan@pl.com");
        assertThat(export.profile().loyaltyTierCode()).isEqualTo("SILVER");
        assertThat(export.transactions()).hasSize(1);
        assertThat(export.generatedAt()).isNotNull();
    }

    @Test
    void anonymize_shouldRemovePersonalDataAndKeepRecord() {
        Customer customer = customer();
        customer.setReferralCode("ABC123");
        when(loyaltyService.getCustomerById(1L, null)).thenReturn(customer);
        when(customerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Customer result = customerPrivacyService.anonymize(1L, null);

        assertThat(result.getStatus()).isEqualTo(CustomerStatus.ANONYMIZED);
        assertThat(result.getFirstName()).doesNotContain("Jan");
        assertThat(result.getEmail()).doesNotContain("jan@pl.com");
        assertThat(result.getPhoneNumber()).isEqualTo("000000000");
        assertThat(result.getReferralCode()).isNull();
        assertThat(result.getReferredBy()).isNull();
        // Numer klienta zostaje -- wiaze historie transakcji, ktora musi sie bilansowac.
        assertThat(result.getCustomerNumber()).isEqualTo("C001");
        assertThat(result.getStatusChangedAt()).isNotNull();
    }

    /** Adres poczty i numer klienta pozostaja jednoznaczne po anonimizacji. */
    @Test
    void anonymize_shouldKeepEmailUniquePerCustomer() {
        Customer first = customer();
        Customer second = customer();
        second.setId(2L);
        second.setEmail("anna@pl.com");

        when(loyaltyService.getCustomerById(1L, null)).thenReturn(first);
        when(loyaltyService.getCustomerById(2L, null)).thenReturn(second);
        when(customerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String firstEmail = customerPrivacyService.anonymize(1L, null).getEmail();
        String secondEmail = customerPrivacyService.anonymize(2L, null).getEmail();

        assertThat(firstEmail).isNotEqualTo(secondEmail);
    }

    @Test
    void anonymize_alreadyAnonymized_shouldThrow() {
        Customer customer = customer();
        customer.setStatus(CustomerStatus.ANONYMIZED);
        when(loyaltyService.getCustomerById(1L, null)).thenReturn(customer);

        assertThatThrownBy(() -> customerPrivacyService.anonymize(1L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already anonymized");
    }

    private Customer customer() {
        Customer customer = Customer.builder()
                .firstName("Jan")
                .lastName("Kowalski")
                .email("jan@pl.com")
                .customerNumber("C001")
                .phoneNumber("123456789")
                .country("PL")
                .loyaltyPoints(100)
                .lifetimePoints(1500)
                .build();
        customer.setId(1L);
        customer.setStatus(CustomerStatus.ACTIVE);
        customer.setCreatedAt(LocalDateTime.now().minusDays(30));
        return customer;
    }

    private Transaction transaction(Customer customer) {
        LocalDateTime now = LocalDateTime.now();
        return Transaction.builder()
                .id(1L)
                .customer(customer)
                .points(100)
                .amount(new BigDecimal("100.00"))
                .pointsPerCurrency(BigDecimal.ONE)
                .description("Store sale: TXN-1")
                .country("PL")
                .type(TransactionType.SALE)
                .purchaseTimestamp(now)
                .availableFrom(now.plusDays(30))
                .expiresAt(now.plusDays(365))
                .timestamp(now)
                .build();
    }
}
