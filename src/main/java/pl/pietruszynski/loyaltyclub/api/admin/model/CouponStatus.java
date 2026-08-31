package pl.pietruszynski.loyaltyclub.api.admin.model;

public enum CouponStatus {

    /** Kupon wydany i mozliwy do realizacji. */
    ACTIVE,

    /** Kupon zrealizowany. */
    USED,

    /** Uplynela data waznosci. */
    EXPIRED,

    /** Kupon wycofany przez operatora -- pomylka przy wydaniu albo reklamacja. */
    CANCELLED;

    /** Stany koncowe: nie zmieniaja sie juz samoczynnie wraz z uplywem czasu. */
    public boolean isFinal() {
        return this == USED || this == CANCELLED;
    }
}
