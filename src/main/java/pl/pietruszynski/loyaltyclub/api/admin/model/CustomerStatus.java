package pl.pietruszynski.loyaltyclub.api.admin.model;

public enum CustomerStatus {

    /** Konto czynne -- naliczanie i wymiana punktow dozwolone. */
    ACTIVE,

    /** Konto zawieszone -- historia zachowana, operacje punktowe odrzucane. */
    INACTIVE,

    /**
     * Dane osobowe usuniete na zadanie uczestnika (RODO, art. 17). Rekord pozostaje,
     * bo historia transakcji i log audytowy musza sie bilansowac, ale nie zawiera juz
     * danych pozwalajacych zidentyfikowac osobe. Stan nieodwracalny.
     */
    ANONYMIZED;

    public boolean allowsPointOperations() {
        return this == ACTIVE;
    }
}
