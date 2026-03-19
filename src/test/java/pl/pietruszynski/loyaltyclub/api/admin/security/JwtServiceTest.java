package pl.pietruszynski.loyaltyclub.api.admin.security;

import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    // Base64-encoded 64-byte key (sufficient for HS512)
    private static final String SECRET = "YWRtaW5Mb3lhbHR5Q2x1YlNlY3JldEtleUFkbWluTG95YWx0eUNsdWJTZWNyZXRLZXk=";
    private static final long EXPIRATION_MS = 60_000L; // 1 minute

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "jwtSecret", SECRET);
        ReflectionTestUtils.setField(jwtService, "jwtExpirationMs", EXPIRATION_MS);
    }

    @Test
    void generateToken_withoutRole_shouldContainSubject() {
        String token = jwtService.generateToken("admin");

        assertThat(jwtService.extractUsername(token)).isEqualTo("admin");
    }

    @Test
    void generateToken_withRole_shouldContainRoleClaim() {
        String token = jwtService.generateToken("techuser", "ROLE_TECHNICAL");

        assertThat(jwtService.extractRole(token)).isEqualTo("ROLE_TECHNICAL");
        assertThat(jwtService.extractUsername(token)).isEqualTo("techuser");
    }

    @Test
    void generateToken_withNullRole_shouldNotIncludeRoleClaim() {
        String token = jwtService.generateToken("admin", null);

        assertThat(jwtService.extractRole(token)).isNull();
    }

    @Test
    void generateToken_withBlankRole_shouldNotIncludeRoleClaim() {
        String token = jwtService.generateToken("admin", "  ");

        assertThat(jwtService.extractRole(token)).isNull();
    }

    @Test
    void extractExpirationEpochMillis_shouldReturnFutureTimestamp() {
        long before = System.currentTimeMillis();
        String token = jwtService.generateToken("admin");
        long expiry = jwtService.extractExpirationEpochMillis(token);

        assertThat(expiry).isGreaterThan(before)
                .isLessThanOrEqualTo(before + EXPIRATION_MS + 1000);
    }

    @Test
    void isTokenValid_withCorrectUser_shouldReturnTrue() {
        String token = jwtService.generateToken("admin");
        UserDetails userDetails = User.withUsername("admin").password("pass").authorities(Collections.emptyList()).build();

        assertThat(jwtService.isTokenValid(token, userDetails)).isTrue();
    }

    @Test
    void isTokenValid_withWrongUsername_shouldReturnFalse() {
        String token = jwtService.generateToken("admin");
        UserDetails userDetails = User.withUsername("other").password("pass").authorities(Collections.emptyList()).build();

        assertThat(jwtService.isTokenValid(token, userDetails)).isFalse();
    }

    @Test
    void isTokenValid_withExpiredToken_shouldThrowExpiredJwtException() {
        ReflectionTestUtils.setField(jwtService, "jwtExpirationMs", -1L);
        String token = jwtService.generateToken("admin");
        UserDetails userDetails = User.withUsername("admin").password("pass").authorities(Collections.emptyList()).build();

        // JJWT throws ExpiredJwtException when parsing an expired token
        assertThatThrownBy(() -> jwtService.isTokenValid(token, userDetails))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void getExpirationMs_shouldReturnConfiguredValue() {
        assertThat(jwtService.getExpirationMs()).isEqualTo(EXPIRATION_MS);
    }
}
