package pl.pietruszynski.loyaltyclub.notification;

/**
 * Port wyjscia dla powiadomien wysylanych do uczestnika.
 *
 * <p>System nie ma wlasnej infrastruktury pocztowej ani bramki SMS, a dobranie jej
 * jest decyzja wdrozeniowa, nie dziedzinowa. Interfejs pozwala podlaczyc dowolny
 * kanal bez zmiany reguly wyznaczajacej, kogo i kiedy powiadomic; domyslna
 * implementacja zapisuje powiadomienie do logu aplikacji.
 */
public interface NotificationSender {

    /** Symboliczna nazwa kanalu zapisywana w rejestrze powiadomien. */
    String channel();

    void send(PointExpiryNotification notification);
}
