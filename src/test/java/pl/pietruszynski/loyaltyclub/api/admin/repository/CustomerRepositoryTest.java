package pl.pietruszynski.loyaltyclub.api.admin.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import pl.pietruszynski.loyaltyclub.PersistenceTest;
import pl.pietruszynski.loyaltyclub.api.admin.model.Customer;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Wyszukiwanie kartoteki. Zapytanie {@code search} laczy dwa niezalezne
 * ograniczenia -- zakres krajowy roli TECHNICAL i fraze wpisana przez operatora --
 * przy czym {@code null} w kazdym z nich znaczy "bez ograniczenia". Ta konwencja
 * jest wyrazona wylacznie w tresci JPQL, wiec sprawdza ja tylko test na bazie.
 */
@PersistenceTest
class CustomerRepositoryTest {

    @Autowired private TestEntityManager entityManager;
    @Autowired private CustomerRepository customerRepository;

    @Test
    void search_shouldReturnEverythingWhenNoScopeAndNoQuery() {
        persistCustomer("C001", "jan.kowalski@example.com", "Jan", "Kowalski", "PL", 10);
        persistCustomer("C002", "anna.nowak@example.com", "Anna", "Nowak", "DE", 20);

        assertThat(searchAll(null, null)).hasSize(2);
    }

    @Test
    void search_shouldLimitToCountryScope() {
        persistCustomer("C001", "jan.kowalski@example.com", "Jan", "Kowalski", "PL", 10);
        persistCustomer("C002", "anna.nowak@example.com", "Anna", "Nowak", "DE", 20);

        assertThat(searchAll("PL", null))
                .extracting(Customer::getCustomerNumber)
                .containsExactly("C001");
    }

    /** Fraza przekazywana jest juz w postaci wzorca LIKE, malymi literami. */
    @Test
    void search_shouldMatchLastNameFirstNameEmailAndCustomerNumber() {
        persistCustomer("C001", "jan.kowalski@example.com", "Jan", "Kowalski", "PL", 10);
        persistCustomer("C002", "anna.nowak@example.com", "Anna", "Nowak", "PL", 20);

        assertThat(searchAll(null, "%kowalski%")).extracting(Customer::getCustomerNumber).containsExactly("C001");
        assertThat(searchAll(null, "%anna%")).extracting(Customer::getCustomerNumber).containsExactly("C002");
        assertThat(searchAll(null, "%nowak@example%")).extracting(Customer::getCustomerNumber).containsExactly("C002");
        assertThat(searchAll(null, "%c001%")).extracting(Customer::getCustomerNumber).containsExactly("C001");
        assertThat(searchAll(null, "%brak%")).isEmpty();
    }

    /** Fraza dziala wewnatrz zakresu krajowego, a nie zamiast niego. */
    @Test
    void search_shouldCombineCountryScopeWithQuery() {
        persistCustomer("C001", "jan.kowalski@example.com", "Jan", "Kowalski", "PL", 10);
        persistCustomer("C002", "jan.kowalski.de@example.com", "Jan", "Kowalski", "DE", 20);

        assertThat(searchAll("PL", "%kowalski%"))
                .extracting(Customer::getCustomerNumber)
                .containsExactly("C001");
    }

    @Test
    void search_shouldPageAndSort() {
        persistCustomer("C001", "a@example.com", "Jan", "Abacki", "PL", 10);
        persistCustomer("C002", "b@example.com", "Jan", "Babacki", "PL", 20);
        persistCustomer("C003", "c@example.com", "Jan", "Cabacki", "PL", 30);

        Page<Customer> firstPage = customerRepository.search(null, null,
                PageRequest.of(0, 2, Sort.by(Sort.Direction.ASC, "lastName")));

        assertThat(firstPage.getTotalElements()).isEqualTo(3);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);
        assertThat(firstPage.getContent()).extracting(Customer::getLastName).containsExactly("Abacki", "Babacki");
    }

    @Test
    void sumLoyaltyPoints_shouldAggregateGloballyAndPerCountry() {
        persistCustomer("C001", "a@example.com", "Jan", "Kowalski", "PL", 10);
        persistCustomer("C002", "b@example.com", "Anna", "Nowak", "PL", 20);
        persistCustomer("C003", "c@example.com", "Tom", "Schmidt", "DE", 5);

        assertThat(customerRepository.sumLoyaltyPoints()).isEqualTo(35);
        assertThat(customerRepository.sumLoyaltyPointsByCountry("PL")).isEqualTo(30);
        assertThat(customerRepository.sumLoyaltyPointsByCountry("LT")).isZero();
        assertThat(customerRepository.countByCountry("PL")).isEqualTo(2);
    }

    @Test
    void findAllByReferredById_shouldReturnReferredCustomersInStableOrder() {
        Customer referrer = persistCustomer("C001", "a@example.com", "Jan", "Kowalski", "PL", 0);
        Customer first = persistCustomer("C002", "b@example.com", "Anna", "Nowak", "PL", 0);
        Customer second = persistCustomer("C003", "c@example.com", "Tom", "Schmidt", "PL", 0);
        first.setReferredBy(referrer);
        second.setReferredBy(referrer);
        entityManager.flush();

        assertThat(customerRepository.findAllByReferredByIdOrderByIdAsc(referrer.getId()))
                .extracting(Customer::getCustomerNumber)
                .containsExactly("C002", "C003");
    }

    /**
     * Jednoznacznosc numeru klienta i adresu poczty jest zalozeniem calej logiki
     * wyszukiwania i anonimizacji, wiec musi trzymac ja baza, a nie tylko kod.
     */
    @Test
    void customerNumberAndEmail_shouldBeUnique() {
        persistCustomer("C001", "jan@example.com", "Jan", "Kowalski", "PL", 0);

        assertThatThrownBy(() ->
                persistCustomer("C001", "inny@example.com", "Anna", "Nowak", "PL", 0))
                .isInstanceOf(Exception.class);
    }

    @Test
    void existsChecks_shouldIgnoreTheEditedCustomer() {
        Customer customer = persistCustomer("C001", "jan@example.com", "Jan", "Kowalski", "PL", 0);

        assertThat(customerRepository.existsByEmail("jan@example.com")).isTrue();
        assertThat(customerRepository.existsByEmailAndIdNot("jan@example.com", customer.getId())).isFalse();
        assertThat(customerRepository.existsByCustomerNumberAndIdNot("C001", customer.getId())).isFalse();
        assertThat(customerRepository.existsByCustomerNumberAndIdNot("C001", customer.getId() + 1)).isTrue();
    }

    private List<Customer> searchAll(String country, String query) {
        return customerRepository.search(country, query, PageRequest.of(0, 50)).getContent();
    }

    private Customer persistCustomer(String customerNumber,
                                     String email,
                                     String firstName,
                                     String lastName,
                                     String country,
                                     int loyaltyPoints) {
        return entityManager.persistAndFlush(Customer.builder()
                .firstName(firstName)
                .lastName(lastName)
                .email(email)
                .customerNumber(customerNumber)
                .phoneNumber("123456789")
                .country(country)
                .loyaltyPoints(loyaltyPoints)
                .build());
    }
}
