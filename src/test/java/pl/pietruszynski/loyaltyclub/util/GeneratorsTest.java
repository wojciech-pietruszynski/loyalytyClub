package pl.pietruszynski.loyaltyclub.util;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pl.pietruszynski.loyaltyclub.exception.BusinessException;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Generatory kodow i hasel oraz polityka hasel.
 *
 * <p>Kody kuponow i kody polecen bywaja przepisywane recznie z paragonu, dlatego
 * alfabet nie zawiera znakow mylacych sie przy przepisywaniu. Haslo jednorazowe
 * konta technicznego jest pokazywane raz i musi z konstrukcji spelniac polityke --
 * inaczej administrator dostalby haslo, ktorego system sam by nie przyjal.
 */
class GeneratorsTest {

    private static final int SAMPLE_SIZE = 500;

    @Nested
    class CouponCode {

        private final CouponCodeGenerator generator = new CouponCodeGenerator();

        @Test
        void generateElevenDigits_shouldReturnExactlyElevenDigits() {
            IntStream.range(0, SAMPLE_SIZE).forEach(i ->
                    assertThat(generator.generateElevenDigits()).matches("\\d{11}"));
        }

        /** Kod jest doklejany do prefiksu i musi rozroznic kupony wydane tego samego dnia. */
        @Test
        void generateElevenDigits_shouldNotRepeatItselfInPractice() {
            Set<String> codes = new HashSet<>();
            IntStream.range(0, SAMPLE_SIZE).forEach(i -> codes.add(generator.generateElevenDigits()));

            assertThat(codes).hasSize(SAMPLE_SIZE);
        }
    }

    @Nested
    class ReferralCode {

        private final ReferralCodeGenerator generator = new ReferralCodeGenerator();

        @ParameterizedTest
        @ValueSource(ints = {1, 6, 8, 32})
        void generate_shouldRespectRequestedLength(int length) {
            assertThat(generator.generate(length)).hasSize(length);
        }

        /** Bez I, O, 0 i 1 -- kod bywa przepisywany recznie przez klienta. */
        @Test
        void generate_shouldAvoidCharactersConfusedWhenTranscribed() {
            IntStream.range(0, SAMPLE_SIZE).forEach(i ->
                    assertThat(generator.generate(16)).matches("[A-HJ-NP-Z2-9]{16}"));
        }

        @ParameterizedTest
        @ValueSource(ints = {0, -1})
        void generate_withNonPositiveLength_shouldBeRejected(int length) {
            assertThatThrownBy(() -> generator.generate(length))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class GeneratedPassword {

        private final PasswordGenerator generator = new PasswordGenerator();

        @Test
        void generate_shouldProduceTwentyCharacterPasswordByDefault() {
            assertThat(generator.generate()).hasSize(20);
        }

        /**
         * Wygenerowane haslo musi przejsc walidacje, ktorej podlega haslo ustawione
         * recznie -- inaczej reset hasla dawalby haslo nie do przyjecia przez system.
         */
        @Test
        void generate_shouldAlwaysSatisfyThePasswordPolicy() {
            IntStream.range(0, SAMPLE_SIZE).forEach(i ->
                    PasswordPolicy.validate(generator.generate()));
        }

        @Test
        void generate_shouldContainEveryRequiredCharacterClass() {
            IntStream.range(0, SAMPLE_SIZE).forEach(i -> {
                String password = generator.generate(PasswordPolicy.MIN_LENGTH);
                assertThat(password.chars().anyMatch(Character::isUpperCase)).isTrue();
                assertThat(password.chars().anyMatch(Character::isLowerCase)).isTrue();
                assertThat(password.chars().anyMatch(Character::isDigit)).isTrue();
                assertThat(password.chars().anyMatch(c -> "!@#$%^&*-_=+".indexOf(c) >= 0)).isTrue();
            });
        }

        /** Znaki mylace sie przy przepisywaniu (I, l, O, 0, 1) sa wykluczone. */
        @Test
        void generate_shouldAvoidAmbiguousCharacters() {
            IntStream.range(0, SAMPLE_SIZE).forEach(i ->
                    assertThat(generator.generate()).doesNotContainAnyWhitespaces()
                            .matches("[^IlO01]+"));
        }

        @Test
        void generate_belowPolicyMinimum_shouldBeRejected() {
            assertThatThrownBy(() -> generator.generate(PasswordPolicy.MIN_LENGTH - 1))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        /** Kolejnosc klas znakow jest losowa, a nie stala. */
        @Test
        void generate_shouldNotPlaceCharacterClassesInAFixedOrder() {
            Set<String> prefixes = new HashSet<>();
            IntStream.range(0, SAMPLE_SIZE).forEach(i -> prefixes.add(generator.generate().substring(0, 1)));

            assertThat(prefixes).hasSizeGreaterThan(1);
        }
    }

    @Nested
    class Policy {

        @Test
        void validate_shouldAcceptPasswordMeetingEveryRequirement() {
            PasswordPolicy.validate("PoprawneHaslo1");
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "   "})
        void validate_shouldRejectBlankPassword(String password) {
            assertThatThrownBy(() -> PasswordPolicy.validate(password))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("required");
        }

        @Test
        void validate_shouldRejectMissingPassword() {
            assertThatThrownBy(() -> PasswordPolicy.validate(null))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        void validate_shouldRejectPasswordShorterThanMinimum() {
            assertThatThrownBy(() -> PasswordPolicy.validate("Krotkie123"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining(String.valueOf(PasswordPolicy.MIN_LENGTH));
        }

        @Test
        void validate_shouldRejectPasswordLongerThanMaximum() {
            String tooLong = "A1" + "a".repeat(PasswordPolicy.MAX_LENGTH);

            assertThatThrownBy(() -> PasswordPolicy.validate(tooLong))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        void validate_shouldAcceptPasswordsExactlyAtBothLimits() {
            PasswordPolicy.validate("Aa1" + "b".repeat(PasswordPolicy.MIN_LENGTH - 3));
            PasswordPolicy.validate("Aa1" + "b".repeat(PasswordPolicy.MAX_LENGTH - 3));
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "bezwielkichliter1",
                "BEZMALYCHLITER12",
                "BezCyfrWHasleABC"
        })
        void validate_shouldRequireAllThreeCharacterClasses(String password) {
            assertThatThrownBy(() -> PasswordPolicy.validate(password))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("upper case");
        }
    }
}
