package pl.pietruszynski.loyaltyclub.api.admin.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    @Value("${security.jwt.secret}")
    private String jwtSecret;

    @Value("${security.jwt.expiration-ms}")
    private long jwtExpirationMs;

    public String generateToken(String username) {
        return generateToken(username, null);
    }

    /**
     * Kazdy token dostaje wlasny identyfikator {@code jti} oraz znacznik wydania
     * {@code iat}. Bez nich token bezstanowy jest nieuniewaznialny: {@code jti}
     * pozwala wycofac pojedyncza sesje przy wylogowaniu, a {@code iat} -- wszystkie
     * sesje konta naraz przy zmianie hasla.
     */
    public String generateToken(String username, String role) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtExpirationMs);
        io.jsonwebtoken.JwtBuilder builder = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(username)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(getSigningKey());

        if (role != null && !role.isBlank()) {
            builder.claim("role", role);
        }

        return builder.compact();
    }

    public String extractUsername(String token) {
        return extractClaims(token).getSubject();
    }

    public String extractTokenId(String token) {
        return extractClaims(token).getId();
    }

    public long extractExpirationEpochMillis(String token) {
        return extractClaims(token).getExpiration().getTime();
    }

    public LocalDateTime extractExpiration(String token) {
        return toLocalDateTime(extractClaims(token).getExpiration());
    }

    public LocalDateTime extractIssuedAt(String token) {
        return toLocalDateTime(extractClaims(token).getIssuedAt());
    }

    public String extractRole(String token) {
        Object role = extractClaims(token).get("role");
        return role == null ? null : role.toString();
    }

    public long getExpirationMs() {
        return jwtExpirationMs;
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        String username = extractUsername(token);
        return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
    }

    private boolean isTokenExpired(String token) {
        return extractClaims(token).getExpiration().before(new Date());
    }

    private Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private LocalDateTime toLocalDateTime(Date date) {
        return date == null ? null : LocalDateTime.ofInstant(Instant.ofEpochMilli(date.getTime()), ZoneId.systemDefault());
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
