package pl.pietruszynski.loyaltyclub.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Macierz uprawnien z {@link SecurityConfig}, sprawdzana na pelnym lancuchu filtrow.
 *
 * <p>Testy kontrolerow ({@code @WebMvcTest}) wylaczaja Spring Security, zeby
 * skupic sie na kontrakcie HTTP -- w efekcie zadna reguła dostepu nie byla dotad
 * wykonana w tescie. Rozdzial na trzy role (panel, kasa, sklep internetowy) jest
 * podstawowa granica bezpieczenstwa programu: token kasy nie moze siegac do
 * kartoteki klientow, a token sklepu internetowego do panelu administracyjnego.
 *
 * <p>Dla zadan dopuszczonych sprawdzamy wylacznie, ze reguła ich nie odrzucila --
 * to, co zwraca sam punkt koncowy, nalezy do testow kontrolerow.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityAuthorizationIntegrationTest {

    @Autowired private MockMvc mockMvc;

    // ---------------------------------------------------------------- brak poswiadczen

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/admin/customers",
            "/api/admin/reports/summary",
            "/api/admin/audit-logs",
            "/api/admin/technical-users",
            "/api/store/customers/C001/points",
            "/api/ecom/customers/C001/profile",
            "/api/coupon/validate"
    })
    void protectedEndpoint_withoutToken_shouldReturn401(String path) throws Exception {
        mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ sciezki otwarte

    @ParameterizedTest
    @ValueSource(strings = {
            "/actuator/health",
            "/v3/api-docs",
            "/swagger-ui/index.html"
    })
    void openEndpoint_shouldBeReachableWithoutToken(String path) throws Exception {
        assertNotRejected(mockMvc.perform(get(path)).andReturn());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/admin/auth/login",
            "/api/store/auth/login",
            "/api/ecom/auth/login"
    })
    void login_shouldBeReachableWithoutToken(String path) throws Exception {
        assertNotRejected(mockMvc.perform(post(path)).andReturn());
    }

    /** Wylogowanie musi dzialac takze wtedy, gdy token zdazyl wygasnac. */
    @ParameterizedTest
    @ValueSource(strings = {
            "/api/admin/auth/logout",
            "/api/store/auth/logout",
            "/api/ecom/auth/logout"
    })
    void logout_shouldBeReachableWithoutToken(String path) throws Exception {
        assertNotRejected(mockMvc.perform(post(path)).andReturn());
    }

    /**
     * Zapytanie wstepne CORS nie niesie naglowka {@code Authorization}, wiec
     * odrzucenie go zablokowaloby frontend wdrozony pod innym adresem.
     */
    @Test
    void corsPreflight_shouldBeAllowedWithoutToken() throws Exception {
        mockMvc.perform(options("/api/admin/customers")
                        .header(HttpHeaders.ORIGIN, "https://panel.example.com")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://panel.example.com"));
    }

    /** Dopuszczenie zapytania wstepnego nie moze oznaczac dopuszczenia dowolnego originu. */
    @Test
    void corsPreflight_fromUnknownOrigin_shouldBeRejected() throws Exception {
        mockMvc.perform(options("/api/admin/customers")
                        .header(HttpHeaders.ORIGIN, "https://evil.example.com")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden());
    }

    /**
     * Panel pobiera eksport CSV przez XHR, wiec bez wystawionego
     * {@code Content-Disposition} przegladarka nie odczyta nazwy pliku.
     */
    @Test
    void corsConfiguration_shouldExposeContentDispositionHeader() throws Exception {
        mockMvc.perform(options("/api/admin/reports/export/customers")
                        .header(HttpHeaders.ORIGIN, "https://panel.example.com")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS,
                        org.hamcrest.Matchers.containsString(HttpHeaders.CONTENT_DISPOSITION)));
    }

    // ------------------------------------------------------------- rozdzial rol

    @ParameterizedTest
    @CsvSource({
            "/api/admin/customers,          ADMIN",
            "/api/admin/customers,          TECHNICAL",
            "/api/admin/reports/summary,    ADMIN",
            "/api/admin/reports/summary,    TECHNICAL",
            "/api/store/customers/C001/points, STORE",
            "/api/ecom/customers/C001/profile, ECOM",
            "/api/coupon/validate,          ECOM"
    })
    void endpoint_withMatchingRole_shouldNotBeRejected(String path, String role) throws Exception {
        assertNotRejected(mockMvc.perform(get(path).with(user("tester").roles(role))).andReturn());
    }

    @ParameterizedTest
    @CsvSource({
            // Kasa i sklep internetowy nie moga siegac do panelu.
            "/api/admin/customers,            STORE",
            "/api/admin/customers,            ECOM",
            "/api/admin/reports/summary,      STORE",
            "/api/admin/audit-logs,           STORE",
            // Panel nie uzywa API kasy ani sklepu internetowego.
            "/api/store/customers/C001/points, ADMIN",
            "/api/store/customers/C001/points, ECOM",
            "/api/ecom/customers/C001/profile, ADMIN",
            "/api/ecom/customers/C001/profile, STORE",
            // Realizacja kuponu jest zastrzezona dla integracji sklepu internetowego.
            "/api/coupon/validate,            ADMIN",
            "/api/coupon/validate,            STORE"
    })
    void endpoint_withForeignRole_shouldReturn403(String path, String role) throws Exception {
        mockMvc.perform(get(path).with(user("tester").roles(role)))
                .andExpect(status().isForbidden());
    }

    /**
     * Konto techniczne obsluguje integracje i raportowanie, ale nie moze zarzadzac
     * innymi kontami technicznymi -- to reguła {@code @PreAuthorize} na kontrolerze,
     * a nie dopasowanie sciezki, wiec wymaga wlaczonego {@code @EnableMethodSecurity}.
     */
    @Test
    void technicalUserManagement_shouldBeRestrictedToAdmin() throws Exception {
        mockMvc.perform(get("/api/admin/technical-users").with(user("tech").roles("TECHNICAL")))
                .andExpect(status().isForbidden());

        assertNotRejected(mockMvc.perform(
                get("/api/admin/technical-users").with(user("admin").roles("ADMIN"))).andReturn());
    }

    @Test
    void auditLog_shouldBeRestrictedToAdmin() throws Exception {
        mockMvc.perform(get("/api/admin/audit-logs").with(user("tech").roles("TECHNICAL")))
                .andExpect(status().isForbidden());
    }

    /** Odswiezenie tokenu obowiazuje w obrebie wlasnego API. */
    @ParameterizedTest
    @CsvSource({
            "/api/store/auth/refresh, ADMIN",
            "/api/store/auth/refresh, ECOM",
            "/api/ecom/auth/refresh,  ADMIN",
            "/api/ecom/auth/refresh,  STORE"
    })
    void refresh_withForeignRole_shouldReturn403(String path, String role) throws Exception {
        mockMvc.perform(post(path).with(user("tester").roles(role)))
                .andExpect(status().isForbidden());
    }

    /**
     * Domyslna reguła to {@code denyAll}: sciezka spoza opisanych obszarow nie moze
     * stac sie dostepna przez samo dodanie kontrolera.
     */
    @Test
    void unmappedPath_shouldBeDeniedByDefault() throws Exception {
        mockMvc.perform(get("/api/nieznane")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/nieznane").with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());
    }

    /** Zadanie dopuszczone przez reguły dostepu -- niezaleznie od wyniku samego punktu koncowego. */
    private void assertNotRejected(MvcResult result) {
        assertThat(result.getResponse().getStatus())
                .describedAs("zadanie odrzucone przez reguły dostepu")
                .isNotIn(401, 403);
    }
}
