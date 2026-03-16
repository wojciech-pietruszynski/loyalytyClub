package pl.pietruszynski.loyaltyclub.api.store.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.pietruszynski.loyaltyclub.api.store.dto.StorePointsBalanceResponse;
import pl.pietruszynski.loyaltyclub.api.store.dto.StoreReturnRequest;
import pl.pietruszynski.loyaltyclub.api.store.dto.StoreSaleRequest;
import pl.pietruszynski.loyaltyclub.api.store.dto.StoreTransactionResponse;
import pl.pietruszynski.loyaltyclub.api.store.service.StoreTransactionService;

import java.util.Map;

@RestController
@RequestMapping("/api/store")
@RequiredArgsConstructor
public class StoreController {

    private final StoreTransactionService storeTransactionService;

    @GetMapping
    public Map<String, String> info() {
        return Map.of(
                "name", "store",
                "status", "ready"
        );
    }

    @PostMapping("/transactions/sale")
    public StoreTransactionResponse registerSale(
            @RequestHeader("X-CountryCode") String countryCode,
            @Valid @RequestBody StoreSaleRequest request
    ) {
        return storeTransactionService.registerSale(countryCode, request);
    }

    @PostMapping("/transactions/return")
    public StoreTransactionResponse registerReturn(
            @RequestHeader("X-CountryCode") String countryCode,
            @Valid @RequestBody StoreReturnRequest request
    ) {
        return storeTransactionService.registerReturn(countryCode, request);
    }

    @GetMapping("/customers/{customerNumber}/points")
    public StorePointsBalanceResponse getPointsBalance(@PathVariable String customerNumber) {
        return storeTransactionService.getPointsBalance(customerNumber);
    }
}


