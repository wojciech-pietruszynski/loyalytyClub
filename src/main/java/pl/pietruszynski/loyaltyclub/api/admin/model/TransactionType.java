package pl.pietruszynski.loyaltyclub.api.admin.model;

public enum TransactionType {

    /** Sprzedaz w kasie lub w sklepie internetowym. */
    SALE,

    /** Zwrot towaru; cofa punkty naliczone przy sprzedazy zrodlowej. */
    RETURN,

    /** Reczna korekta salda wykonana przez administratora. */
    MANUAL_ADJUSTMENT,

    /** Premia za polecenie -- dla polecajacego i dla poleconego. */
    REFERRAL,

    /** Pobranie punktow przy wydaniu kuponu. */
    POINTS_REDEMPTION,

    /** Zwrot punktow przy anulowaniu wydanego kuponu. */
    POINTS_REFUND;

    /**
     * Operacje, ktore nie maja okresu karencji ani daty wygasniecia -- ich punkty
     * sa dostepne od chwili zapisu i pozostaja dostepne. Dotyczy korekt recznych
     * i operacji kuponowych; sprzedaz, zwroty i premie za polecenia podlegaja
     * cyklowi zycia wyznaczanemu datami.
     */
    public boolean isImmediatelyAvailable() {
        return this == MANUAL_ADJUSTMENT || this == POINTS_REDEMPTION || this == POINTS_REFUND;
    }

    /**
     * Czy operacja wchodzi do dorobku punktowego wyznaczajacego poziom lojalnosciowy.
     * Wymiana punktow na kupon i zwrot punktow przy anulowaniu kuponu sa neutralne:
     * klient nie traci statusu za korzystanie z programu ani go nie zyskuje za
     * anulowanie wlasnej wymiany.
     */
    public boolean countsTowardsLifetimePoints() {
        return this == SALE || this == RETURN || this == MANUAL_ADJUSTMENT || this == REFERRAL;
    }
}
