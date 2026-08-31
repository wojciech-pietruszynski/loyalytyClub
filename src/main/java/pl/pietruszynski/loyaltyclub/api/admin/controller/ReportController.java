package pl.pietruszynski.loyaltyclub.api.admin.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.pietruszynski.loyaltyclub.api.admin.dto.ExpiringPointsDto;
import pl.pietruszynski.loyaltyclub.api.admin.dto.ReportsSummaryDto;
import pl.pietruszynski.loyaltyclub.api.admin.service.ReportService;
import pl.pietruszynski.loyaltyclub.api.admin.service.TechnicalUserService;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@RestController
@RequestMapping("/api/admin/reports")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'TECHNICAL')")
public class ReportController {

    private static final MediaType TEXT_CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);
    private static final LocalDate EXPORT_MIN_DATE = LocalDate.of(1970, 1, 1);

    private final ReportService reportService;
    private final TechnicalUserService technicalUserService;

    @GetMapping("/summary")
    public ReportsSummaryDto getSummary(Authentication authentication) {
        return reportService.getSummary(getCountryScope(authentication));
    }

    /**
     * Punkty, ktore wygasna w zadanym oknie. Program nie mial dotad zadnego widoku
     * na zblizajace sie wygasniecia -- ani dla operatora, ani dla uczestnika.
     *
     * @param days horyzont w dniach; domyslnie 30
     */
    @GetMapping("/expiring-points")
    public List<ExpiringPointsDto> getExpiringPoints(
            @RequestParam(defaultValue = "30") int days,
            Authentication authentication) {
        return reportService.getExpiringPoints(days, getCountryScope(authentication));
    }

    @GetMapping("/export/customers")
    public ResponseEntity<byte[]> exportCustomers(Authentication authentication) {
        String csv = reportService.exportCustomersCsv(getCountryScope(authentication));
        return csvResponse(csv, "customers.csv");
    }

    /** Zakres dat jest opcjonalny — panel wysyla tylko te pola, ktore uzytkownik wypelnil. */
    @GetMapping("/export/transactions")
    public ResponseEntity<byte[]> exportTransactions(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Authentication authentication) {

        LocalDateTime fromDateTime = (from == null ? EXPORT_MIN_DATE : from).atStartOfDay();
        LocalDateTime toDateTime = to == null ? LocalDateTime.now() : to.atTime(LocalTime.MAX);

        String csv = reportService.exportTransactionsCsv(fromDateTime, toDateTime, getCountryScope(authentication));
        return csvResponse(csv, "transactions.csv");
    }

    private ResponseEntity<byte[]> csvResponse(String csv, String fileName) {
        return ResponseEntity.ok()
                .contentType(TEXT_CSV)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }

    private String getCountryScope(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities() == null) {
            return null;
        }
        boolean technical = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_TECHNICAL"::equals);
        if (!technical) {
            return null;
        }
        return technicalUserService.resolveTechnicalUserCountry(authentication.getName());
    }
}
