package pl.pietruszynski.loyaltyclub.util;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Generuje hasla jednorazowe dla kont technicznych. Haslo powstaje po stronie
 * serwera i jest pokazywane dokladnie raz -- w odpowiedzi na utworzenie konta
 * albo na reset -- zamiast byc przechowywane w bazie w postaci jawnej.
 */
@Component
public class PasswordGenerator {

    /** Bez znakow mylacych sie przy przepisywaniu (I, l, O, 0, 1). */
    private static final String UPPERCASE = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String LOWERCASE = "abcdefghijkmnpqrstuvwxyz";
    private static final String DIGITS = "23456789";
    private static final String SPECIAL = "!@#$%^&*-_=+";
    private static final String ALL = UPPERCASE + LOWERCASE + DIGITS + SPECIAL;

    private static final int DEFAULT_LENGTH = 20;

    private final SecureRandom secureRandom = new SecureRandom();

    public String generate() {
        return generate(DEFAULT_LENGTH);
    }

    public String generate(int length) {
        if (length < PasswordPolicy.MIN_LENGTH) {
            throw new IllegalArgumentException("Generated password must have at least " + PasswordPolicy.MIN_LENGTH + " characters");
        }

        // Po jednym znaku z kazdej klasy, reszta losowo -- wynik zawsze spelnia polityke.
        List<Character> characters = new ArrayList<>(length);
        characters.add(randomFrom(UPPERCASE));
        characters.add(randomFrom(LOWERCASE));
        characters.add(randomFrom(DIGITS));
        characters.add(randomFrom(SPECIAL));
        while (characters.size() < length) {
            characters.add(randomFrom(ALL));
        }
        Collections.shuffle(characters, secureRandom);

        StringBuilder password = new StringBuilder(length);
        characters.forEach(password::append);
        return password.toString();
    }

    private char randomFrom(String alphabet) {
        return alphabet.charAt(secureRandom.nextInt(alphabet.length()));
    }
}
