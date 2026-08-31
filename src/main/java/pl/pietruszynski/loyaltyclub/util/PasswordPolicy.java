package pl.pietruszynski.loyaltyclub.util;

import pl.pietruszynski.loyaltyclub.exception.BusinessException;

/**
 * Minimalne wymagania dla hasel ustawianych recznie. Hasla generowane przez
 * {@link PasswordGenerator} spelniaja je z konstrukcji.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 12;
    public static final int MAX_LENGTH = 128;

    private PasswordPolicy() {
    }

    public static void validate(String password) {
        if (password == null || password.isBlank()) {
            throw new BusinessException("Password is required");
        }
        if (password.length() < MIN_LENGTH || password.length() > MAX_LENGTH) {
            throw new BusinessException(
                    "Password must have between " + MIN_LENGTH + " and " + MAX_LENGTH + " characters");
        }
        if (password.chars().noneMatch(Character::isUpperCase)
                || password.chars().noneMatch(Character::isLowerCase)
                || password.chars().noneMatch(Character::isDigit)) {
            throw new BusinessException("Password must contain an upper case letter, a lower case letter and a digit");
        }
    }
}
