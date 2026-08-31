package pl.pietruszynski.loyaltyclub.util;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class ReferralCodeGenerator {

    /** Bez I, O, 0 i 1 — kod bywa przepisywany recznie przez klienta. */
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final SecureRandom secureRandom = new SecureRandom();

    public String generate(int length) {
        if (length <= 0) {
            throw new IllegalArgumentException("Referral code length must be greater than zero");
        }
        StringBuilder code = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            code.append(ALPHABET.charAt(secureRandom.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }
}
