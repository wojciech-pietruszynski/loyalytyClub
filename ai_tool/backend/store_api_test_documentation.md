# Store API - dokumentacja testow

## 1. Wymagania
- aplikacja uruchomiona na `http://localhost:8080`
- konto store (Basic Auth):
  - login: `store`
  - haslo: `8b7929d8-f588-4c1d-a3da-8aaf5b0b05c7`
- konto admin (JWT):
  - login: `admin`
  - haslo: `admin`

## 2. Przygotowanie danych testowych
### 2.1. Pobranie tokena admin
```bash
curl -X POST http://localhost:8080/api/admin/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin"}'
```
Z odpowiedzi skopiuj `token`.

### 2.2. Utworzenie klienta
```bash
curl -X POST http://localhost:8080/api/admin/customers \
  -H "Authorization: Bearer <TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "firstName":"Jan",
    "lastName":"Kowalski",
    "email":"jan.kowalski.store@test.pl",
    "customerNumber":"CUST-1001",
    "phoneNumber":"500600700",
    "country":"PL",
    "loyaltyPoints":0
  }'
```

## 3. API store
Wszystkie endpointy `store` wymagaja Basic Auth.

### 3.0. Logowanie store (JWT)
```bash
curl -X POST http://localhost:8080/api/store/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username":"store",
    "password":"8b7929d8-f588-4c1d-a3da-8aaf5b0b05c7"
  }'
```
Z odpowiedzi skopiuj `token`. Token mozesz uzyc jako:
`-H "Authorization: Bearer <TOKEN>"`.

### 3.1. Rejestracja sprzedazy
```bash
curl -X POST http://localhost:8080/api/store/transactions/sale \
  -H "Authorization: Bearer <TOKEN>" \
  -H "Content-Type: application/json" \
  -H "X-CountryCode: PL" \
  -d '{
    "customerNumber":"CUST-1001",
    "items":[
      {
        "cartPosition":"1",
        "ean":"5901234123457",
        "name":"Buty biegowe",
        "category":"SPORT",
        "price":{
          "amount":120.50,
          "currency":"PLN"
        }
      }
    ],
    "totalAmount":120.50,
    "sourceTransactionNumber":"POS/1001",
    "purchaseTimestamp":"2026-03-03T10:15:00"
  }'
```
Odpowiedz zawiera m.in. `transactionId`, `points`, `state`, `availableFrom`, `expiresAt`.

### 3.2. Podglad salda punktow klienta
```bash
curl -X GET http://localhost:8080/api/store/customers/CUST-1001/points \
  -H "Authorization: Bearer <TOKEN>"
```
Pola odpowiedzi:
- `pendingPoints`
- `availablePoints`
- `expiredPoints`

### 3.3. Rejestracja zwrotu (pelnego lub czesciowego)
Uzyj `saleTransactionNumber` ze sprzedazy.
```bash
curl -X POST http://localhost:8080/api/store/transactions/return \
  -H "Authorization: Bearer <TOKEN>" \
  -H "Content-Type: application/json" \
  -H "X-CountryCode: PL" \
  -d '{
    "customerNumber":"CUST-1001",
    "items":[
      {
        "cartPosition":"2",
        "ean":"5901234123458",
        "name":"Skarpety sportowe",
        "category":"SPORT",
        "price":{
          "amount":20.00,
          "currency":"PLN"
        }
      }
    ],
    "totalAmount":20.00,
    "sourceTransactionNumber":"RET/1001",
    "saleTransactionNumber":"POS/1001",
    "purchaseTimestamp":"2026-03-04T12:10:00"
  }'
```

## 4. Test promocji (mnoznik punktow)
### 4.1. Dodanie promocji z panelu admin (API)
```bash
curl -X POST http://localhost:8080/api/admin/store-promotions \
  -H "Authorization: Bearer <TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "name":"Happy Hours",
    "country":"PL",
    "pointsPerCurrency":4.00,
    "startsAt":"2026-03-03T09:00:00",
    "endsAt":"2026-03-03T23:00:00",
    "enabled":true
  }'
```

### 4.2. Lista promocji
```bash
curl -X GET http://localhost:8080/api/admin/store-promotions \
  -H "Authorization: Bearer <TOKEN>"
```

### 4.3. Sprzedaz w oknie promocji
Dla transakcji w czasie promocji `pointsPerCurrency` powinno przyjac wartosc `4.00`.

## 5. Scenariusze weryfikacyjne
1. Sprzedaz teraz -> punkty w `pendingPoints` (do 30 dni od `purchaseTimestamp`).
2. Sprzedaz starsza niz 30 dni (ustaw `purchaseTimestamp` w przeszlosci) -> punkty od razu w `availablePoints`.
3. Zwrot czesciowy -> punkty odejmowane proporcjonalnie i nie przekraczaja punktow z transakcji sprzedazy.
4. Zwrot po wygasnieciu punktow (po 365 dniach od zakupu) -> API zwraca blad.
5. Sprzedaz w aktywnej promocji -> wyzszy przelicznik punktow niz domyslne `1.00`.

## 6. Kody bledow
- `400` walidacja biznesowa (np. za duzy zwrot)
- `401` brak/niepoprawne uwierzytelnienie
- `403` brak roli
- `404` brak klienta lub transakcji
