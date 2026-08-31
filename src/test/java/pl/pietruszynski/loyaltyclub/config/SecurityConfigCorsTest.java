package pl.pietruszynski.loyaltyclub.config;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Frontend jest osobnym repozytorium i wdrozeniem, wiec wariant z rozdzielonymi
 * adresami SPA i API musi byc realnie obslugiwany i sprawdzony, a nie tylko
 * mozliwy do ustawienia zmienna srodowiskowa.
 */
class SecurityConfigCorsTest {

    private final SecurityConfig securityConfig = new SecurityConfig(null, null, null, null);

    @Test
    void emptyConfiguration_shouldRegisterNoMappings() {
        CorsConfigurationSource source = securityConfig.corsConfigurationSource(List.of());

        assertThat(source.getCorsConfiguration(request("/api/admin/customers"))).isNull();
    }

    /** Puste wpisy z rozwinietej zmiennej srodowiskowej nie moga wlaczac CORS. */
    @Test
    void blankEntriesOnly_shouldRegisterNoMappings() {
        CorsConfigurationSource source = securityConfig.corsConfigurationSource(List.of("", "   "));

        assertThat(source.getCorsConfiguration(request("/api/admin/customers"))).isNull();
    }

    @Test
    void configuredOrigin_shouldBeAllowedForApiPaths() {
        CorsConfigurationSource source =
                securityConfig.corsConfigurationSource(List.of("https://panel.example.com"));

        CorsConfiguration configuration = source.getCorsConfiguration(request("/api/admin/customers"));

        assertThat(configuration).isNotNull();
        assertThat(configuration.checkOrigin("https://panel.example.com")).isEqualTo("https://panel.example.com");
        assertThat(configuration.checkOrigin("https://evil.example.com")).isNull();
    }

    @Test
    void wildcardPattern_shouldBeSupported() {
        CorsConfigurationSource source =
                securityConfig.corsConfigurationSource(List.of("https://*.example.com"));

        CorsConfiguration configuration = source.getCorsConfiguration(request("/api/ecom/customers/C001/profile"));

        assertThat(configuration.checkOrigin("https://panel.example.com")).isEqualTo("https://panel.example.com");
        assertThat(configuration.checkOrigin("https://panel.other.com")).isNull();
    }

    /**
     * Bez wystawienia naglowka Content-Disposition przegladarka nie odczyta nazwy
     * pliku przy eksporcie raportow z innego origin -- eksport CSV bylby zepsuty
     * dokladnie w wariancie z rozdzielonymi adresami.
     */
    @Test
    void configuredOrigin_shouldExposeContentDispositionHeader() {
        CorsConfigurationSource source =
                securityConfig.corsConfigurationSource(List.of("https://panel.example.com"));

        CorsConfiguration configuration = source.getCorsConfiguration(request("/api/admin/reports/export/customers"));

        assertThat(configuration.getExposedHeaders()).contains(HttpHeaders.CONTENT_DISPOSITION);
    }

    @Test
    void configuredOrigin_shouldAllowModifyingMethodsAndNotUseCredentials() {
        CorsConfigurationSource source =
                securityConfig.corsConfigurationSource(List.of("https://panel.example.com"));

        CorsConfiguration configuration = source.getCorsConfiguration(request("/api/admin/customers"));

        assertThat(configuration.getAllowedMethods()).contains("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
        // Token wedruje naglowkiem Authorization, nie ciasteczkiem.
        assertThat(configuration.getAllowCredentials()).isNotEqualTo(Boolean.TRUE);
    }

    @Test
    void nonApiPath_shouldNotBeCorsEnabled() {
        CorsConfigurationSource source =
                securityConfig.corsConfigurationSource(List.of("https://panel.example.com"));

        assertThat(source.getCorsConfiguration(request("/actuator/health"))).isNull();
    }

    private HttpServletRequest request(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setRequestURI(uri);
        return request;
    }
}
