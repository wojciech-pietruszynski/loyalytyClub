# Coupon API - dokumentacja

## Cel
API `coupon` obsluguje:
- wymiane punktow klienta na kupon,
- walidacje kuponu po kodzie wraz ze sprawdzeniem, czy kupon nalezy do wskazanego klienta,
- zwrot definicji kuponu (template), gdy kupon jest poprawny lub istnieje.

## Security
- Base path: `/api/coupon`
- Wymagana rola: `ROLE_ECOM` (Basic Auth / mechanizm zgodny z aktualna konfiguracja aplikacji)

---

## 1) Wymiana punktow na kupon

### Endpoint
- `POST /api/coupon/redeem-points`
- wymagany naglowek: `Idempotency-Key`

### Request body
```json
{
  "customerNumber": "CUST-10001",
  "couponTemplateId": 1
}
```
Przyklad naglowkow:
```http
Idempotency-Key: redeem-CUST-10001-20260304-001
```

### Walidacje i reguly biznesowe
- `customerNumber` jest wymagany.
- `couponTemplateId` jest wymagane.
- klient musi istniec.
- template kuponu musi istniec.
- kraj klienta musi byc zgodny z krajem template kuponu.
- klient musi miec co najmniej `requiredPoints` z template.
- ten sam `Idempotency-Key` i ten sam payload zwraca ten sam wynik (bez ponownego odjecia punktow).
- ten sam `Idempotency-Key` z innym payloadem zwraca blad biznesowy.

### Efekty uboczne
- punkty klienta sa pomniejszane o `requiredPoints`.
- tworzona jest transakcja `MANUAL_ADJUSTMENT` z ujemna liczba punktow.
- tworzony jest kupon z:
  - `reason=POINTS_EXCHANGE`
  - `status=ACTIVE`
  - `issuedAt=now`
  - `expiresAt=issuedAt + validityDays`

### Response 200
```json
{
  "couponCode": "RAB12345678901",
  "customerNumber": "CUST-10001",
  "status": "ACTIVE",
  "issuedAt": "2026-03-04T12:10:00",
  "expiresAt": "2026-04-03T12:10:00",
  "definition": {
    "couponTemplateId": 1,
    "couponValue": 20.00,
    "minimumPurchaseValue": 100.00,
    "requiredPoints": 250,
    "validityDays": 30,
    "couponPrefix": "RAB",
    "country": "PL"
  }
}
```

---

## 2) Walidacja kuponu

### Endpoint
- `GET /api/coupon/validate?couponCode={code}&customerNumber={customerNumber}`

### Wymagania
- `couponCode` wymagane.
- `customerNumber` wymagane.

### Enum statusow walidacji
- `VALID`
- `COUPON_NOT_FOUND`
- `CUSTOMER_NOT_FOUND`
- `COUPON_BELONGS_TO_ANOTHER_ACCOUNT`
- `COUPON_ALREADY_USED`
- `COUPON_EXPIRED`

### Zasady
- jesli klient nie istnieje -> `CUSTOMER_NOT_FOUND`
- jesli kupon nie istnieje -> `COUPON_NOT_FOUND`
- jesli kupon istnieje, ale inny wlasciciel -> `COUPON_BELONGS_TO_ANOTHER_ACCOUNT`
  - odpowiedz nie zwraca metadanych kuponu (brak `couponStatus`, `issuedAt`, `expiresAt`, `definition`)
- jesli kupon ma status `USED` -> `COUPON_ALREADY_USED`
- jesli kupon przekroczyl `expiresAt` lub ma status `EXPIRED` -> `COUPON_EXPIRED`
- gdy kupon byl `ACTIVE` i podczas walidacji okazal sie po czasie -> status encji aktualizowany do `EXPIRED`

### Response 200 (przyklad `VALID`)
```json
{
  "status": "VALID",
  "couponCode": "RAB12345678901",
  "customerNumber": "CUST-10001",
  "couponStatus": "ACTIVE",
  "issuedAt": "2026-03-04T12:10:00",
  "expiresAt": "2026-04-03T12:10:00",
  "definition": {
    "couponTemplateId": 1,
    "couponValue": 20.00,
    "minimumPurchaseValue": 100.00,
    "requiredPoints": 250,
    "validityDays": 30,
    "couponPrefix": "RAB",
    "country": "PL"
  }
}
```

---

## Edge cases i aktualne zachowanie
- Rownolegla wymiana punktow dla jednego klienta:
  - obsluzone przez `PESSIMISTIC_WRITE` lock na rekordzie klienta podczas `redeem-points`.
- Kupon wygasa dokladnie w chwili zapytania (`expiresAt`):
  - traktowany jako wygasly (`COUPON_EXPIRED`).
- Niespojnosc kraju klient/template:
  - wymiana blokowana bledem biznesowym.
- Kolizje kodu kuponu:
  - zapis kuponu ma retry do 200 prob; po wyczerpaniu zwracany jest blad serwera.
- Klient ma za malo punktow:
  - wymiana blokowana bledem biznesowym.
- Reuzycie kuponu ze statusem `USED`:
  - walidacja zwraca `COUPON_ALREADY_USED`.

## Co jeszcze moze byc potrzebne biznesowo
- endpoint do oznaczenia kuponu jako `USED` (atomowo z numerem zamowienia).
- blokada anty-fraud (limity wymian per klient/dzien, velocity checks).
- audyt i historia zmian statusow kuponu.
- idempotency key dla `redeem-points`, zeby unikac duplikatow przy retrach.
- paginacja/filtrowanie listy kuponow klienta.
- webhook/event po utworzeniu kuponu (CRM, marketing automation).
