package pl.pietruszynski.loyaltyclub.api.admin.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.pietruszynski.loyaltyclub.api.admin.dto.ExpiringPointsDto;
import pl.pietruszynski.loyaltyclub.api.admin.dto.ReportsSummaryDto;
import pl.pietruszynski.loyaltyclub.api.admin.security.AdminUserDetailsService;
import pl.pietruszynski.loyaltyclub.api.admin.security.JwtService;
import pl.pietruszynski.loyaltyclub.api.admin.service.ReportService;
import pl.pietruszynski.loyaltyclub.api.admin.service.TechnicalUserService;
import pl.pietruszynski.loyaltyclub.api.ecom.security.EcomUserDetailsService;
import pl.pietruszynski.loyaltyclub.api.store.security.StoreUserDetailsService;
import pl.pietruszynski.loyaltyclub.security.TokenRevocationService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kontrakt HTTP raportow.
 *
 * <p>Kontroler odpowiada za jedna decyzje merytoryczna: wyznaczenie zakresu
 * krajowego z tozsamosci wywolujacego. Rola ADMIN widzi caly program, rola
 * TECHNICAL wylacznie kraj przypisany do konta. Blad w tym miejscu oznaczalby,
 * ze konto integracyjne jednego kraju eksportuje kartoteke pozostalych.
 */
@WebMvcTest(value = ReportController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class,
                SecurityFilterAutoConfiguration.class,
                UserDetailsServiceAutoConfiguration.class})
class ReportControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean ReportService reportService;
    @MockitoBean TechnicalUserService technicalUserService;
    @MockitoBean JwtService jwtService;
    @MockitoBean AdminUserDetailsService adminUserDetailsService;
    @MockitoBean StoreUserDetailsService storeUserDetailsService;
    @MockitoBean EcomUserDetailsService ecomUserDetailsService;
    @MockitoBean TokenRevocationService tokenRevocationService;

    // ------------------------------------------------------------------- zestawienie

    @Test
    void getSummary_forAdmin_shouldReportWholeProgram() throws Exception {
        when(reportService.getSummary(isNull()))
                .thenReturn(ReportsSummaryDto.of("", 12, 5, 1000, 200, 30));

        mockMvc.perform(get("/api/admin/reports/summary").principal(adminUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope").value(""))
                .andExpect(jsonPath("$.customerCount").value(12))
                .andExpect(jsonPath("$.availablePoints").value(1000))
                .andExpect(jsonPath("$.pendingPoints").value(200))
                .andExpect(jsonPath("$.expiredPoints").value(30));

        verifyNoInteractions(technicalUserService);
    }

    @Test
    void getSummary_forTechnicalUser_shouldBeLimitedToTheAccountCountry() throws Exception {
        when(technicalUserService.resolveTechnicalUserCountry("integracja")).thenReturn("PL");
        when(reportService.getSummary("PL")).thenReturn(ReportsSummaryDto.of("PL", 3, 1, 100, 0, 0));

        mockMvc.perform(get("/api/admin/reports/summary").principal(technicalUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope").value("PL"));

        verify(reportService).getSummary("PL");
    }

    // ------------------------------------------------------------ wygasajace punkty

    @Test
    void getExpiringPoints_shouldDefaultToThirtyDayHorizon() throws Exception {
        when(reportService.getExpiringPoints(anyInt(), isNull())).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/reports/expiring-points").principal(adminUser()))
                .andExpect(status().isOk());

        verify(reportService).getExpiringPoints(30, null);
    }

    @Test
    void getExpiringPoints_shouldAcceptExplicitHorizon() throws Exception {
        when(reportService.getExpiringPoints(eq(7), isNull())).thenReturn(List.of(
                new ExpiringPointsDto(1L, 2L, "C001", "PL", 120, LocalDateTime.now().plusDays(3))));

        mockMvc.perform(get("/api/admin/reports/expiring-points").param("days", "7").principal(adminUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].customerNumber").value("C001"))
                .andExpect(jsonPath("$[0].points").value(120));
    }

    // -------------------------------------------------------------------- eksport CSV

    /**
     * Panel pobiera plik przez XHR i odczytuje nazwe z {@code Content-Disposition},
     * wiec naglowek i typ tresci sa czescia kontraktu, a nie szczegolem.
     */
    @Test
    void exportCustomers_shouldReturnCsvAttachment() throws Exception {
        when(reportService.exportCustomersCsv(isNull())).thenReturn("customerNumber\nC001\n");

        mockMvc.perform(get("/api/admin/reports/export/customers").principal(adminUser()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"customers.csv\""))
                .andExpect(content().string("customerNumber\nC001\n"));
    }

    @Test
    void exportCustomers_forTechnicalUser_shouldBeLimitedToTheAccountCountry() throws Exception {
        when(technicalUserService.resolveTechnicalUserCountry("integracja")).thenReturn("PL");
        when(reportService.exportCustomersCsv("PL")).thenReturn("customerNumber\n");

        mockMvc.perform(get("/api/admin/reports/export/customers").principal(technicalUser()))
                .andExpect(status().isOk());

        verify(reportService).exportCustomersCsv("PL");
    }

    @Test
    void exportTransactions_shouldUseGivenDateRange() throws Exception {
        when(reportService.exportTransactionsCsv(any(), any(), isNull())).thenReturn("id\n");

        mockMvc.perform(get("/api/admin/reports/export/transactions")
                        .param("from", "2026-01-01")
                        .param("to", "2026-01-31")
                        .principal(adminUser()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"transactions.csv\""));

        verify(reportService).exportTransactionsCsv(
                LocalDate.of(2026, 1, 1).atStartOfDay(),
                LocalDate.of(2026, 1, 31).atTime(java.time.LocalTime.MAX),
                null);
    }

    /**
     * Panel wysyla tylko te pola, ktore uzytkownik wypelnil. Brak daty poczatkowej
     * ma oznaczac "od zawsze", a brak koncowej "do teraz" -- nie pusty zakres.
     */
    @Test
    void exportTransactions_withoutDates_shouldCoverTheWholeHistory() throws Exception {
        when(reportService.exportTransactionsCsv(any(), any(), isNull())).thenReturn("id\n");

        mockMvc.perform(get("/api/admin/reports/export/transactions").principal(adminUser()))
                .andExpect(status().isOk());

        verify(reportService).exportTransactionsCsv(
                eq(LocalDate.of(1970, 1, 1).atStartOfDay()),
                any(LocalDateTime.class),
                isNull());
    }

    /** Data w zlym formacie to bledne zadanie, a nie awaria serwera. */
    @Test
    void exportTransactions_withMalformedDate_shouldReturn400() throws Exception {
        mockMvc.perform(get("/api/admin/reports/export/transactions")
                        .param("from", "01-01-2026")
                        .principal(adminUser()))
                .andExpect(status().isBadRequest());
    }

    /*
     * Kontroler pobiera tozsamosc z parametru typu Authentication, ktory Spring MVC
     * rozwiazuje z request.getUserPrincipal(). Filtry Spring Security sa tu wylaczone,
     * wiec principal ustawiamy wprost na zadaniu -- bez tego kazde wywolanie
     * wygladaloby jak anonimowe i test zakresu krajowego nie mialby czego sprawdzic.
     */
    private UsernamePasswordAuthenticationToken adminUser() {
        return new UsernamePasswordAuthenticationToken("admin", "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    private UsernamePasswordAuthenticationToken technicalUser() {
        return new UsernamePasswordAuthenticationToken("integracja", "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_TECHNICAL")));
    }
}
