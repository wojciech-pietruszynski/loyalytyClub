package pl.pietruszynski.loyaltyclub.api.admin.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.pietruszynski.loyaltyclub.api.admin.dto.ReportsSummaryDto;
import pl.pietruszynski.loyaltyclub.api.admin.model.Customer;
import pl.pietruszynski.loyaltyclub.api.admin.model.Transaction;
import pl.pietruszynski.loyaltyclub.api.admin.repository.CustomerRepository;
import pl.pietruszynski.loyaltyclub.api.admin.repository.TransactionRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportService {

    private static final int SUMMARY_WINDOW_DAYS = 30;
    private static final String CUSTOMERS_CSV_HEADER =
            "customerNumber,firstName,lastName,email,phoneNumber,country,loyaltyPoints,loyaltyTierCode,referralCode";
    private static final String TRANSACTIONS_CSV_HEADER =
            "id,timestamp,customerNumber,country,type,state,points,amount,description";

    private final CustomerRepository customerRepository;
    private final TransactionRepository transactionRepository;
    private final LoyaltyTierService loyaltyTierService;

    public ReportsSummaryDto getSummary(String countryScope) {
        LocalDateTime since = LocalDateTime.now().minusDays(SUMMARY_WINDOW_DAYS);
        String country = normalizeCountryCode(countryScope);

        if (country == null) {
            return new ReportsSummaryDto(
                    "",
                    customerRepository.count(),
                    customerRepository.sumLoyaltyPoints(),
                    transactionRepository.countSince(since)
            );
        }
        return new ReportsSummaryDto(
                country,
                customerRepository.countByCountry(country),
                customerRepository.sumLoyaltyPointsByCountry(country),
                transactionRepository.countSinceForCountry(since, country)
        );
    }

    public String exportCustomersCsv(String countryScope) {
        String country = normalizeCountryCode(countryScope);
        List<Customer> customers = country == null
                ? customerRepository.findAll()
                : customerRepository.findAllByCountry(country);

        StringBuilder csv = new StringBuilder(CUSTOMERS_CSV_HEADER).append('\n');
        for (Customer customer : customers) {
            csv.append(csvRow(
                    customer.getCustomerNumber(),
                    customer.getFirstName(),
                    customer.getLastName(),
                    customer.getEmail(),
                    customer.getPhoneNumber(),
                    customer.getCountry(),
                    String.valueOf(customer.getLoyaltyPoints()),
                    loyaltyTierService.resolveTierCode(customer.getLoyaltyPoints()),
                    customer.getReferralCode()
            )).append('\n');
        }
        return csv.toString();
    }

    public String exportTransactionsCsv(LocalDateTime from, LocalDateTime to, String countryScope) {
        List<Transaction> transactions =
                transactionRepository.findForExport(from, to, normalizeCountryCode(countryScope));

        StringBuilder csv = new StringBuilder(TRANSACTIONS_CSV_HEADER).append('\n');
        for (Transaction transaction : transactions) {
            csv.append(csvRow(
                    String.valueOf(transaction.getId()),
                    String.valueOf(transaction.getTimestamp()),
                    transaction.getCustomer().getCustomerNumber(),
                    transaction.getCountry(),
                    transaction.getType() == null ? null : transaction.getType().name(),
                    transaction.getState() == null ? null : transaction.getState().name(),
                    String.valueOf(transaction.getPoints()),
                    String.valueOf(transaction.getAmount()),
                    transaction.getDescription()
            )).append('\n');
        }
        return csv.toString();
    }

    private String csvRow(String... values) {
        StringBuilder row = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                row.append(',');
            }
            row.append(escapeCsv(values[i]));
        }
        return row.toString();
    }

    /** RFC 4180: pola z przecinkiem, cudzyslowem lub znakiem nowej linii otaczamy cudzyslowami. */
    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }

    private String normalizeCountryCode(String countryScope) {
        if (countryScope == null || countryScope.isBlank()) {
            return null;
        }
        return countryScope.trim().toUpperCase(Locale.ROOT);
    }
}
