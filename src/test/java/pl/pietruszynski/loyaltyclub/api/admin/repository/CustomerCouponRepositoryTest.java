package pl.pietruszynski.loyaltyclub.api.admin.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import pl.pietruszynski.loyaltyclub.PersistenceTest;
import pl.pietruszynski.loyaltyclub.api.admin.model.CouponReason;
import pl.pietruszynski.loyaltyclub.api.admin.model.CouponStatus;
import pl.pietruszynski.loyaltyclub.api.admin.model.CouponTemplate;
import pl.pietruszynski.loyaltyclub.api.admin.model.Customer;
import pl.pietruszynski.loyaltyclub.api.admin.model.CustomerCoupon;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@PersistenceTest
class CustomerCouponRepositoryTest {

    @Autowired private TestEntityManager entityManager;
    @Autowired private CustomerCouponRepository customerCouponRepository;

    private LocalDateTime now;
    private Customer polishCustomer;
    private Customer germanCustomer;
    private CouponTemplate template;

    @BeforeEach
    void setUp() {
        // Kolumna TIMESTAMP ma mniejsza rozdzielczosc niz LocalDateTime.now(); bez
        // obciecia porownania na granicy okna sprawdzalyby blad zaokraglenia,
        // a nie regule.
        now = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);
        polishCustomer = persistCustomer("C001", "pl@example.com", "PL");
        germanCustomer = persistCustomer("C002", "de@example.com", "DE");
        template = entityManager.persistAndFlush(CouponTemplate.builder()
                .couponValue(new BigDecimal("10.00"))
                .minimumPurchaseValue(new BigDecimal("50.00"))
                .requiredPoints(300)
                .country("PL")
                .validityDays(7)
                .couponPrefix("KUPPL")
                .build());
    }

    /**
     * Zadanie cykliczne utrwala stan {@code EXPIRED} tylko dla kuponow, ktore
     * nadal maja zapisane {@code ACTIVE}. Stany koncowe ({@code USED},
     * {@code CANCELLED}) sa nietykalne, a kupon z data waznosci przed nami
     * nie moze zostac dotkniety.
     */
    @Test
    void findLapsedActiveCoupons_shouldMatchOnlyActiveCouponsPastTheirExpiry() {
        CustomerCoupon lapsed =
                persistCoupon("KUPPL1", polishCustomer, CouponStatus.ACTIVE, now.minusDays(1));
        persistCoupon("KUPPL2", polishCustomer, CouponStatus.ACTIVE, now.plusDays(1));
        persistCoupon("KUPPL3", polishCustomer, CouponStatus.USED, now.minusDays(1));
        persistCoupon("KUPPL4", polishCustomer, CouponStatus.CANCELLED, now.minusDays(1));
        persistCoupon("KUPPL5", polishCustomer, CouponStatus.EXPIRED, now.minusDays(1));

        List<CustomerCoupon> result =
                customerCouponRepository.findLapsedActiveCoupons(CouponStatus.ACTIVE, now);

        assertThat(result).extracting(CustomerCoupon::getId).containsExactly(lapsed.getId());
    }

    /** Granica jest domknieta: kupon wygasajacy dokladnie teraz juz przepadl. */
    @Test
    void findLapsedActiveCoupons_shouldIncludeCouponExpiringExactlyNow() {
        persistCoupon("KUPPL1", polishCustomer, CouponStatus.ACTIVE, now);

        assertThat(customerCouponRepository.findLapsedActiveCoupons(CouponStatus.ACTIVE, now)).hasSize(1);
    }

    @Test
    void findPage_shouldReturnAllCountriesWhenScopeIsNull() {
        persistCoupon("KUPPL1", polishCustomer, CouponStatus.ACTIVE, now.plusDays(7));
        persistCoupon("KUPDE1", germanCustomer, CouponStatus.ACTIVE, now.plusDays(7));

        Page<CustomerCoupon> page = customerCouponRepository.findPage(null,
                PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "issuedAt", "id")));

        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    @Test
    void findPage_shouldLimitToCountryScope() {
        persistCoupon("KUPPL1", polishCustomer, CouponStatus.ACTIVE, now.plusDays(7));
        persistCoupon("KUPDE1", germanCustomer, CouponStatus.ACTIVE, now.plusDays(7));

        Page<CustomerCoupon> page = customerCouponRepository.findPage("DE",
                PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "issuedAt", "id")));

        assertThat(page.getContent()).extracting(CustomerCoupon::getCouponCode).containsExactly("KUPDE1");
    }

    @Test
    void findAllByCustomerId_shouldReturnOnlyOwnCouponsNewestFirst() {
        CustomerCoupon older = persistCoupon("KUPPL1", polishCustomer, CouponStatus.ACTIVE,
                now.plusDays(7), now.minusDays(5));
        CustomerCoupon newer = persistCoupon("KUPPL2", polishCustomer, CouponStatus.ACTIVE,
                now.plusDays(7), now.minusDays(1));
        persistCoupon("KUPDE1", germanCustomer, CouponStatus.ACTIVE, now.plusDays(7));

        assertThat(customerCouponRepository.findAllByCustomerIdOrderByIssuedAtDesc(polishCustomer.getId()))
                .extracting(CustomerCoupon::getId)
                .containsExactly(newer.getId(), older.getId());
    }

    @Test
    void findByCouponCode_shouldLoadCustomerAndTemplateEagerly() {
        persistCoupon("KUPPL1", polishCustomer, CouponStatus.ACTIVE, now.plusDays(7));
        entityManager.clear();

        CustomerCoupon coupon = customerCouponRepository.findByCouponCode("KUPPL1").orElseThrow();

        // @EntityGraph ma zdjac problem N+1 przy walidacji kuponu; sprawdzamy,
        // ze powiazania sa dostepne bez dodatkowego zapytania w kolejnej sesji.
        assertThat(coupon.getCustomer().getCustomerNumber()).isEqualTo("C001");
        assertThat(coupon.getCouponTemplate().getRequiredPoints()).isEqualTo(300);
        assertThat(customerCouponRepository.existsByCouponCode("KUPPL1")).isTrue();
        assertThat(customerCouponRepository.existsByCouponCode("BRAK")).isFalse();
    }

    private Customer persistCustomer(String customerNumber, String email, String country) {
        return entityManager.persistAndFlush(Customer.builder()
                .firstName("Jan")
                .lastName("Kowalski")
                .email(email)
                .customerNumber(customerNumber)
                .phoneNumber("123456789")
                .country(country)
                .build());
    }

    private CustomerCoupon persistCoupon(String code,
                                         Customer customer,
                                         CouponStatus status,
                                         LocalDateTime expiresAt) {
        return persistCoupon(code, customer, status, expiresAt, now);
    }

    private CustomerCoupon persistCoupon(String code,
                                         Customer customer,
                                         CouponStatus status,
                                         LocalDateTime expiresAt,
                                         LocalDateTime issuedAt) {
        return entityManager.persistAndFlush(CustomerCoupon.builder()
                .couponCode(code)
                .country(customer.getCountry())
                .customer(customer)
                .couponTemplate(template)
                .reason(CouponReason.POINTS_EXCHANGE)
                .status(status)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .build());
    }
}
