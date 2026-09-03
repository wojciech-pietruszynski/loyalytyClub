package pl.pietruszynski.loyaltyclub;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import pl.pietruszynski.loyaltyclub.api.admin.repository.AdminUserRepository;
import pl.pietruszynski.loyaltyclub.api.admin.repository.CouponPrefixRepository;
import pl.pietruszynski.loyaltyclub.api.admin.repository.CouponTemplateRepository;
import pl.pietruszynski.loyaltyclub.config.MaintenanceJobs;
import pl.pietruszynski.loyaltyclub.config.PointExpiryProperties;
import pl.pietruszynski.loyaltyclub.config.ReferralProperties;
import pl.pietruszynski.loyaltyclub.security.AuthenticationTokenService;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test wstania kontekstu.
 *
 * <p>Testy jednostkowe skladaja obiekty recznie, a testy warstwy webowej podnosza
 * wycinek kontekstu z podmienionymi zaleznosciami -- zaden z nich nie sprawdza,
 * czy caly graf komponentow da sie zlozyc. Blad konfiguracji (brakujaca definicja,
 * cykl, zla wlasciwosc) ujawnialby sie dopiero przy uruchomieniu aplikacji.
 */
@SpringBootTest
@ActiveProfiles("test")
class LoyaltyClubApplicationTests {

    @Autowired private ApplicationContext applicationContext;
    @Autowired private AdminUserRepository adminUserRepository;
    @Autowired private CouponPrefixRepository couponPrefixRepository;
    @Autowired private CouponTemplateRepository couponTemplateRepository;

    @Test
    void contextLoads() {
        assertThat(applicationContext).isNotNull();
    }

    /**
     * Komponenty przekrojowe -- aspekt audytu, zadania cykliczne, wystawianie
     * tokenow -- nie sa wstrzykiwane do zadnego kontrolera, wiec brak ich definicji
     * nie przerwalby startu w oczywistym miejscu.
     */
    @Test
    void crossCuttingBeans_shouldBeAvailable() {
        assertThat(applicationContext.getBean(MaintenanceJobs.class)).isNotNull();
        assertThat(applicationContext.getBean(AuthenticationTokenService.class)).isNotNull();
        assertThat(applicationContext.getBeansWithAnnotation(org.aspectj.lang.annotation.Aspect.class))
                .isNotEmpty();
    }

    /** Wlasciwosci z {@code application.properties} maja byc zwiazane, a nie puste. */
    @Test
    void configurationProperties_shouldBeBound() {
        PointExpiryProperties pointExpiry = applicationContext.getBean(PointExpiryProperties.class);
        ReferralProperties referral = applicationContext.getBean(ReferralProperties.class);

        assertThat(pointExpiry.noticeDays()).isNotEmpty();
        assertThat(pointExpiry.minimumPoints()).isNotNull();
        assertThat(referral.referrerPoints()).isPositive();
        assertThat(referral.minimumPurchaseAmount()).isNotNull();
    }

    /**
     * Seedery to {@link org.springframework.boot.CommandLineRunner}, wiec wykonuja
     * sie takze przy podnoszeniu kontekstu w tescie. Pusta baza po ich przebiegu
     * oznaczalaby, ze swiezo postawione srodowisko nie ma konta administracyjnego
     * ani slownikow potrzebnych do wydania kuponu.
     */
    @Test
    void seeders_shouldPrepareUsableEnvironment() {
        assertThat(adminUserRepository.findByUsername("admin")).isPresent();
        assertThat(couponPrefixRepository.count()).isPositive();
        assertThat(couponTemplateRepository.count()).isPositive();
    }
}
