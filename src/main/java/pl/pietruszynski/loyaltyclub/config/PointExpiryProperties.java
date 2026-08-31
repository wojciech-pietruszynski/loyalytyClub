package pl.pietruszynski.loyaltyclub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Parametry powiadomien o wygasajacych punktach.
 *
 * @param noticeDays progi ostrzegawcze w dniach przed wygasnieciem; dla kazdego
 *                   progu powstaje osobne powiadomienie, wiec uczestnik moze
 *                   dostac przypomnienie z wyprzedzeniem i tuz przed terminem
 */
@ConfigurationProperties(prefix = "app.points-expiry")
public record PointExpiryProperties(
        Boolean enabled,
        List<Integer> noticeDays,
        Integer minimumPoints
) {

    public PointExpiryProperties {
        enabled = enabled == null || enabled;
        noticeDays = noticeDays == null || noticeDays.isEmpty() ? List.of(30, 7) : List.copyOf(noticeDays);
        minimumPoints = minimumPoints == null ? 1 : minimumPoints;
    }
}
