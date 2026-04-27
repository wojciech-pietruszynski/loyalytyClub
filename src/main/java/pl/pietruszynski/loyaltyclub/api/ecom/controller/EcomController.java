package pl.pietruszynski.loyaltyclub.api.ecom.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.pietruszynski.loyaltyclub.api.admin.dto.CustomerCouponDto;
import pl.pietruszynski.loyaltyclub.api.admin.dto.TransactionDto;
import pl.pietruszynski.loyaltyclub.api.ecom.dto.EcomCustomerProfileDto;
import pl.pietruszynski.loyaltyclub.api.ecom.service.EcomService;
import pl.pietruszynski.loyaltyclub.api.store.dto.StorePointsBalanceResponse;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ecom")
@RequiredArgsConstructor
public class EcomController {

    private static final String API_VERSION = "1.0.0";

    private final EcomService ecomService;

    /**
     * Integration metadata. Point-of-sale style operations for loyalty remain on {@code /api/store};
     * coupon redemption and validation stay on {@code /api/coupon}. This namespace adds read APIs for e-commerce.
     */
    @GetMapping
    public Map<String, String> info() {
        return Map.of(
                "name", "ecom",
                "status", "ready",
                "apiVersion", API_VERSION,
                "docs", "Use GET /api/ecom/customers/{customerNumber}/profile for loyalty summary; /api/coupon for redeem and validate."
        );
    }

    @GetMapping("/customers/{customerNumber}/points")
    public StorePointsBalanceResponse getPoints(@PathVariable String customerNumber) {
        return ecomService.getPointsBalance(customerNumber);
    }

    @GetMapping("/customers/{customerNumber}/profile")
    public EcomCustomerProfileDto getProfile(@PathVariable String customerNumber) {
        return ecomService.getCustomerProfile(customerNumber);
    }

    @GetMapping("/customers/{customerNumber}/transactions")
    public List<TransactionDto> getTransactions(@PathVariable String customerNumber) {
        return ecomService.getTransactions(customerNumber);
    }

    @GetMapping("/customers/{customerNumber}/coupons")
    public List<CustomerCouponDto> getCoupons(@PathVariable String customerNumber) {
        return ecomService.getCoupons(customerNumber);
    }
}
