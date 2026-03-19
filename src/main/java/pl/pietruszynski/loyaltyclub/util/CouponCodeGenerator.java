package pl.pietruszynski.loyaltyclub.util;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class CouponCodeGenerator {

    private final SecureRandom secureRandom = new SecureRandom();

    public String generateElevenDigits() {
        StringBuilder digits = new StringBuilder(11);
        for (int i = 0; i < 11; i++) {
            digits.append(secureRandom.nextInt(10));
        }
        return digits.toString();
    }
}
