package pl.pietruszynski.loyaltyclub.api.admin.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import pl.pietruszynski.loyaltyclub.api.admin.model.CouponPrefix;
import pl.pietruszynski.loyaltyclub.api.admin.repository.CouponPrefixRepository;

import java.util.Locale;

@Component
@RequiredArgsConstructor
public class CouponPrefixSeeder implements CommandLineRunner {

    private final CouponPrefixRepository couponPrefixRepository;

    @Override
    public void run(String... args) {
        seedPrefix("KUPPL");
        seedPrefix("KUPSK");
        seedPrefix("KUPDE");
    }

    private void seedPrefix(String prefix) {
        String normalized = prefix.trim().toUpperCase(Locale.ROOT);
        if (!couponPrefixRepository.existsByValue(normalized)) {
            couponPrefixRepository.save(CouponPrefix.builder().value(normalized).build());
        }
    }
}
