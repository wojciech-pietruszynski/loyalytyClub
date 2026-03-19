# Prezentacja – Praca Inżynierska
## „System lojalnościowy z zautomatyzowanym procesem CI/CD i analizą jakości kodu"

> **Cel dokumentu:** Szkic prezentacji na obronę pracy inżynierskiej.
> Każdy slajd zawiera: tytuł, punkty do omówienia oraz wskazówki dla prezentera.

---

## SLAJD 1 – Strona tytułowa

**Tytuł:** System zarządzania programem lojalnościowym

**Podtytuł:** Projekt i implementacja aplikacji fullstack z wykorzystaniem Spring Boot, React oraz zautomatyzowanego pipeline CI/CD

**Elementy:**
- Imię i nazwisko autora
- Promotor
- Uczelnia, kierunek, rok
- Logo uczelni

---

## SLAJD 2 – Agenda

1. Opis biznesowy – po co ten system?
2. Architektura systemu
3. Stack technologiczny
4. Model bezpieczeństwa i wielodostęp
5. Baza danych i migracje
6. API – integracje i endpointy
7. Strategia testowania
8. Pipeline CI/CD i analiza jakości kodu
9. Wyniki i wnioski

---

## SLAJD 3 – Opis biznesowy: Problem i cel

### Problem biznesowy
Sklepy detaliczne i platformy e-commerce tracą klientów z powodu braku mechanizmów nagradzania lojalności. Zarządzanie punktami, kuponami i promocjami w wielu krajach jednocześnie wymaga dedykowanego systemu.

### Cel systemu
Zbudowanie **wielodostępnego (multi-tenant) systemu lojalnościowego**, który umożliwia:
- Naliczanie i śledzenie punktów lojalnościowych klientów
- Generowanie i obsługę kuponów rabatowych
- Integrację z systemami kasowymi (POS) i platformami e-commerce
- Zarządzanie promocjami punktowymi w czasie rzeczywistym

### Słowa kluczowe dla komisji
> Wielodostępność · Integracja B2B · Program lojalnościowy · REST API

---

## SLAJD 4 – Opis biznesowy: Grupy użytkowników i ich role

| Rola | Kim jest? | Co może? |
|------|-----------|---------|
| **ADMIN** | Administrator systemu | Pełny dostęp do wszystkich krajów – zarządzanie klientami, kuponami, kontami |
| **TECHNICAL** | Operator kraju | Dostęp tylko do swojego kraju – zarządzanie klientami i promocjami |
| **STORE** | System kasowy (POS) | Rejestracja sprzedaży i zwrotów, sprawdzanie punktów klienta |
| **ECOM** | Platforma e-commerce | Realizacja kuponów, walidacja kodów rabatowych |

### Wskazówka dla prezentera
> Podkreśl, że jeden system obsługuje 4 typy klientów z różnymi uprawnieniami – to realizacja wzorca **RBAC (Role-Based Access Control)**.

---

## SLAJD 5 – Opis biznesowy: Główne funkcje systemu

### Panel administracyjny (frontend)
- Zarządzanie klientami (CRUD + import CSV)
- Przeglądanie historii transakcji i sald punktowych
- Wydawanie kuponów rabatowych ręcznie i automatycznie
- Tworzenie szablonów kuponów (wartość, punkty, ważność, prefix)
- Zarządzanie promocjami sklepowymi (mnożniki punktów, daty obowiązywania)
- Zarządzanie kontami technicznymi dla integracji zewnętrznych

### Integracja z systemami zewnętrznymi (API)
- **POS → STORE API**: rejestracja transakcji sprzedaży/zwrotu → automatyczne naliczanie punktów
- **E-commerce → COUPON API**: wymiana punktów na kupon + walidacja kodu rabatowego

### Mechanizm punktów
```
Sprzedaż (POS)
  └─► naliczenie punktów (stawka: points_per_currency)
       └─► stan PENDING (30 dni od zakupu jako zabezpieczenie przed zwrotami)
            └─► stan AVAILABLE (dostępne do wymiany)
                 └─► wymiana na kupon (ECOM)
                      └─► stan USED
```

---

## SLAJD 6 – Architektura systemu: Widok ogólny

```
┌─────────────────────────────────────────────────────────────────┐
│                        KLIENT / INTEGRACJA                      │
│   ┌──────────────┐  ┌──────────────┐  ┌────────────────────┐   │
│   │ Przeglądarka │  │  System POS  │  │  Platforma e-comm  │   │
│   │  (React SPA) │  │  (STORE API) │  │   (COUPON API)     │   │
│   └──────┬───────┘  └──────┬───────┘  └─────────┬──────────┘   │
└──────────┼─────────────────┼──────────────────── ┼─────────────┘
           │                 │                      │
           ▼                 ▼                      ▼
┌──────────────────────────────────────────────────────────────────┐
│                    SPRING BOOT APPLICATION                        │
│                                                                   │
│  ┌────────────────────────────────────────────────────────────┐  │
│  │  SECURITY LAYER: JwtAuthFilter + SecurityConfig            │  │
│  └────────────────────────────────────────────────────────────┘  │
│                                                                   │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐   │
│  │   /api/admin │  │  /api/store  │  │ /api/coupon /api/ecom│   │
│  │  CONTROLLERS │  │  CONTROLLERS │  │     CONTROLLERS      │   │
│  └──────┬───────┘  └──────┬───────┘  └──────────┬───────────┘   │
│         │                 │                      │                │
│  ┌──────▼─────────────────▼──────────────────────▼──────────┐    │
│  │                    SERVICE LAYER                          │    │
│  │  LoyaltyService │ TechnicalUserService │ CouponService   │    │
│  │  StoreTransactionService │ StorePromotionService         │    │
│  └──────────────────────────┬────────────────────────────────┘   │
│                             │                                     │
│  ┌──────────────────────────▼────────────────────────────────┐   │
│  │              REPOSITORY LAYER (Spring Data JPA)           │   │
│  └──────────────────────────┬────────────────────────────────┘   │
└─────────────────────────────┼────────────────────────────────────┘
                              │
                    ┌─────────▼──────────┐
                    │     PostgreSQL      │
                    │   (11 tabel)       │
                    └────────────────────┘
```

---

## SLAJD 7 – Architektura systemu: Warstwy backendu

### 6 warstw z wyraźnym podziałem odpowiedzialności

| # | Warstwa | Klasy | Odpowiedzialność |
|---|---------|-------|-----------------|
| 1 | **Presentation** | 7 kontrolerów | Obsługa HTTP, walidacja wejścia, mapowanie DTO |
| 2 | **DTO** | ~20 klas/rekordów | Kontrakt API, adnotacje walidacyjne (`@Valid`) |
| 3 | **Business Logic** | 5 serwisów | Logika domenowa, reguły biznesowe, transakcje |
| 4 | **Security** | JwtService + Filter + 3 UserDetailsService | Autentykacja, autoryzacja, wielodostęp |
| 5 | **Data Access** | 11 repozytoriów | Dostęp do danych (Spring Data JPA) |
| 6 | **Domain Model** | 11 encji + 4 enumy | Schemat dziedziny, mapowanie ORM |

### Zasada: Clean Architecture
> Każda warstwa zna tylko warstwę poniżej. Serwisy nie znają HTTP. Kontrolery nie znają SQL.

---

## SLAJD 8 – Architektura systemu: Frontend (SPA)

```
App.tsx  (główny komponent, orkiestrator)
├── Stan globalny: useAuth() · useCustomers() · useCoupons()
│                 usePromotions() · useTechnicalUsers()
│
├── Nawigacja: zakładki dynamiczne (zależne od roli JWT)
│   ├── Customers           ← wszyscy zalogowani
│   ├── Add Customer        ← wszyscy zalogowani
│   ├── Add Points          ← tylko ADMIN
│   ├── Coupons             ← wszyscy zalogowani
│   ├── Store Promotions    ← ADMIN + TECHNICAL
│   ├── Tools (CSV import)  ← wszyscy zalogowani
│   └── Technical Accounts  ← tylko ADMIN
│
└── Komunikacja: Axios → /api/* (Spring Boot)
    ├── Bearer token w nagłówku każdego żądania
    └── Auto-refresh tokenu (60 s przed wygaśnięciem)
```

**Wzorzec separacji:**
- `hooks/` → logika biznesowa i stan
- `components/` → prezentacja (tylko props, bez wywołań API)
- `api/client.ts` → warstwa komunikacji HTTP

---

## SLAJD 9 – Stack technologiczny: Backend

| Kategoria | Technologia | Wersja | Rola |
|-----------|-------------|--------|------|
| Język | Java | **21** (LTS) | Virtual threads, records, sealed classes |
| Framework | Spring Boot | **3.2.3** | Autokonfiguracja, IoC, REST |
| Bezpieczeństwo | Spring Security + JJWT | 3.2.3 / **0.12.6** | JWT HS512, RBAC |
| ORM | Spring Data JPA / Hibernate | 3.2.3 | Mapowanie obiektowo-relacyjne |
| Baza danych | PostgreSQL | 15 | RDBMS, ACID |
| Migracje | Liquibase | 4.x | Wersjonowanie schematu bazy |
| Budowanie | Apache Maven | 3.x | Zarządzanie zależnościami, build, profile |
| Pokrycie kodu | JaCoCo | **0.8.11** | Raport XML → SonarQube |
| Jakość kodu | SonarQube | 10.x | Statyczna analiza kodu |
| Konteneryzacja frontendu | frontend-maven-plugin | **1.15.0** | Node.js v20 zarządzany przez Maven |

**Dlaczego Java 21?**
> Wirtualne wątki (`spring.threads.virtual.enabled=true`) – skalowalność bez zmiany kodu aplikacji.

---

## SLAJD 10 – Stack technologiczny: Frontend

| Kategoria | Technologia | Wersja | Rola |
|-----------|-------------|--------|------|
| Język | TypeScript | **5.9.3** | Statyczne typowanie, strict mode |
| Framework UI | React | **19.2.0** | Funkcyjne komponenty, hooks |
| Build tool | Vite | **7.3.1** | HMR, bundling, proxy deweloperski |
| Komponenty | Ant Design | **5.27.6** | Gotowe komponenty UI (dark/light theme) |
| HTTP | Axios | **1.13.5** | Klient REST, interceptory, Bearer token |
| Testy | Vitest | **3.2.4** | Unit testy, kompatybilny z Jest API |
| Testy DOM | @testing-library/react | **16.3.2** | Renderowanie komponentów w testach |
| Ikony | lucide-react | **0.575.0** | SVG ikony |

**Kluczowe decyzje projektowe:**
- **Brak Redux** – stan zarządzany hookami (wystarczający dla tej skali)
- **Brak React Router** – nawigacja zakładkowa (SPA, jeden widok)
- **CSS Variables** – dynamiczne motywy jasny/ciemny bez bibliotek

---

## SLAJD 11 – Model bezpieczeństwa: JWT i role

### Przepływ autentykacji

```
1. Login
   Client ──POST /api/admin/auth/login {username, password}──► AuthController
          ◄──{token, expiresAt, role, country}──────────────── JWT podpisany HS512

2. Każde żądanie
   Client ──Authorization: Bearer {token}──► JwtAuthFilter
          ──────────────────────────────────► Walidacja podpisu
          ──────────────────────────────────► Wyciągnięcie roli (claim "role")
          ──────────────────────────────────► Załadowanie UserDetails
          ──────────────────────────────────► Ustawienie SecurityContext
          ──────────────────────────────────► Kontroler obsługuje żądanie

3. Auto-refresh (Frontend)
   Timer sprawdza co 60 s: jeśli token wygasa < 60 s → POST /auth/refresh
```

### Zawartość tokenu JWT
```json
{
  "sub": "techpl",
  "iat": 1711000000,
  "exp": 1711000900,
  "role": "TECHNICAL"
}
```

**Czas życia tokenu:** 900 000 ms = **15 minut** (konfigurowalny przez `security.jwt.expiration-ms`)

---

## SLAJD 12 – Model bezpieczeństwa: Wielodostęp (Multi-tenancy)

### Problem: jeden system, wiele krajów

```
ADMIN  ──── widzi WSZYSTKICH klientów (PL + DE + CZ + SK + LT)
TECHNICAL (PL) ──── widzi TYLKO klientów z country = 'PL'
TECHNICAL (DE) ──── widzi TYLKO klientów z country = 'DE'
```

### Implementacja – „Country Scope"

```java
// 1. Kontroler wyciąga scope z tokenu JWT
String countryScope = technicalUserService
    .resolveTechnicalUserCountry(authentication.getName());

// 2. Serwis filtruje dane po kraju
if (countryScope != null) {
    return customerRepository.findAllByCountry(countryScope);  // tylko jeden kraj
}
return customerRepository.findAll();  // ADMIN: wszystkie kraje
```

### Zabezpieczenie w bazie danych
- Kolumna `country VARCHAR(3)` we wszystkich tabelach (customers, transactions, customer_coupons, store_points_promotions)
- Indeksy na kolumnie `country` → wydajność filtrowania

---

## SLAJD 13 – Baza danych: Schemat i ewolucja

### 11 tabel w finalnym schemacie

```
auth_users group:
  admin_users        store_users        ecom_users
  technical_users    (+ country, password_preview)

loyalty core:
  customers ──────── transactions (self-ref: source_transaction_id)
       │                   │ type: SALE / RETURN / MANUAL_ADJUSTMENT
       │                   │ state: PENDING → AVAILABLE → EXPIRED
       │
       └──── customer_coupons ──── coupon_templates
                   │                   │ (value, min_purchase, required_points)
                   └──── coupon_redemption_requests (idempotency_key)

promotions:
  store_points_promotions (country, points_per_currency, starts_at, ends_at)
  coupon_prefixes
```

### Cykl życia transakcji

```
Sprzedaż ──► PENDING (blokada 30 dni)
         ──► AVAILABLE (do wymiany na kupon)
         ──► EXPIRED (po 365 dniach)
Zwrot    ──► ujemna transakcja RETURN (odejmuje punkty ze sprzedaży)
```

---

## SLAJD 14 – Baza danych: Migracje Liquibase

### Wersjonowanie schematu – 7 migracji, 8 changesetów

| Migracja | Co wprowadza |
|----------|-------------|
| `001_schema_init_auth.sql` | Tabele użytkowników (admin, store, ecom, technical) |
| `002_schema_init_core.sql` | Tabele biznesowe (customers, transactions, coupons) |
| `003_indexes_and_base_constraints.sql` | Indeksy + klucze obce |
| `005_backfill_missing_columns.sql` | Uzupełnienie kolumny `country` |
| `006_store_transaction_lifecycle_and_promotions.sql` | Cykl życia transakcji + tabela promocji |
| `007_store_transaction_source_number.sql` | Śledzenie źródłowej transakcji (numer zewnętrzny) |
| `008_coupon_redemption_idempotency.sql` | Idempotentność realizacji kuponów |

### Dlaczego Liquibase?
> Każda zmiana schematu jest zarejestrowana, wersjonowana w Git i **automatycznie aplikowana** przy starcie aplikacji. Zero ręcznych skryptów SQL na serwerze.

---

## SLAJD 15 – API: Mapa endpointów

### 27 endpointów w 5 domenach

**Admin API** (`/api/admin`) – panel zarządzania
```
GET  /customers                   Lista klientów (scope kraju)
POST /customers                   Nowy klient
PUT  /customers/{id}              Aktualizacja
GET  /customers/{id}/transactions Historia transakcji
POST /customers/{id}/add-points   Manualne punkty (ADMIN)
POST /tools/import-customers      Import CSV
GET/POST /coupon-templates        Szablony kuponów
POST /coupons/issue               Wydanie kuponu
GET/POST/PUT/PATCH /store-promotions  Zarządzanie promocjami
GET/POST/PATCH /technical-users   Konta techniczne
POST /auth/login · POST /auth/refresh
```

**Store API** (`/api/store`) – integracja POS
```
POST /transactions/sale    Rejestracja sprzedaży → naliczenie punktów
POST /transactions/return  Zwrot → odjęcie punktów ze sprzedaży
GET  /customers/{nr}/points  Saldo punktowe klienta
POST /auth/login
```

**Coupon API** (`/api/coupon`) – integracja e-commerce
```
POST /redeem-points   Wymiana punktów na kupon (Idempotency-Key!)
GET  /validate        Walidacja kodu kuponu przy zakupie
```

### Wzorzec idempotentności
```
POST /api/coupon/redeem-points
Headers: Idempotency-Key: {uuid}
```
> To samo żądanie z tym samym kluczem zawsze zwróci ten sam wynik – bezpieczne retry przy awariach sieciowych.

---

## SLAJD 16 – Strategia testowania: Piramida testów

```
              ╔═══════════════╗
              ║  E2E / Manual ║  ← weryfikacja wdrożenia (pgrep)
              ╚═══════════════╝
         ╔═════════════════════════╗
         ║   Integracyjne (MockMvc)║  ← 8 klas kontrolerów
         ╚═════════════════════════╝
    ╔═══════════════════════════════════════╗
    ║   Jednostkowe (JUnit + Vitest)        ║  ← 10 klas serwisów + 17 plików TS
    ╚═══════════════════════════════════════╝
```

### Liczby

| Warstwa | Narzędzie | Klas testowych | Metod testowych |
|---------|-----------|---------------|-----------------|
| Backend – unit | JUnit 5 + Mockito | 10 | ~150 |
| Backend – integration | Spring MockMvc | 8 | ~40 |
| Frontend – komponenty | Vitest + RTL | 10 | ~40 |
| Frontend – hooki | Vitest | 5 | ~20 |
| Frontend – API | Vitest | 2 | ~4 |
| **RAZEM** | | **35** | **~254** |

---

## SLAJD 17 – Strategia testowania: Typy testów

### 1. Testy jednostkowe serwisów (JUnit 5 + Mockito)

```java
@ExtendWith(MockitoExtension.class)
class TechnicalUserServiceTest {
    @Mock TechnicalUserRepository technicalUserRepository;
    @InjectMocks TechnicalUserService technicalUserService;

    @Test
    void createTechnicalUser_disallowedCountry_shouldThrow() {
        assertThatThrownBy(() -> technicalUserService.createTechnicalUser(req))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Country code is not allowed");
    }
}
```
> **Co izolujemy?** Repozytoria mockowane → testujemy tylko logikę biznesową serwisu.

---

### 2. Testy kontrolerów (Spring @WebMvcTest)

```java
@WebMvcTest(value = AuthController.class,
    excludeAutoConfiguration = {SecurityAutoConfiguration.class, ...})
class AuthControllerTest {
    @Autowired MockMvc mockMvc;
    @MockBean JwtService jwtService;

    @Test
    void login_adminUser_shouldReturnTokenAndAdminRole() throws Exception {
        mockMvc.perform(post("/api/admin/auth/login")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value("ADMIN"));
    }
}
```
> **Kluczowy trick:** Security wyłączane przez `excludeAutoConfiguration` – testujemy tylko routing i mapowanie JSON.

---

### 3. Testy frontendu (Vitest + React Testing Library)

```typescript
describe('LoginView', () => {
  it('calls setUsername on input change', () => {
    render(<LoginView {...mockProps} />);
    fireEvent.change(screen.getByDisplayValue('admin'),
                     { target: { value: 'newuser' } });
    expect(mockProps.setUsername).toHaveBeenCalledWith('newuser');
  });
});
```
> **Zasada:** Testujemy zachowanie z perspektywy użytkownika, nie implementację.

---

### 4. Pokrycie kodu (JaCoCo)

**Cel: ≥ 90% pokrycia linii dla klas biznesowych**

| Klasa | Pokrycie linii |
|-------|---------------|
| `TechnicalUserService` | **97%** |
| `LoyaltyService` | **96%** |
| `CouponService` | **95%** |
| `StoreTransactionService` | **94%** |
| `LoyaltyController` | **94%** |
| `JwtAuthFilter` | **97%** |
| `AuthController` | **100%** |
| `BusinessException` | **100%** |

> Klasy wykluczone z analizy: `**/config/**`, `LoyaltyClubApplication.java` (infrastruktura, nie logika)

---

## SLAJD 18 – Pipeline CI/CD: Architektura

```
GitHub (master branch)
        │
        ▼ (webhook / ręczne uruchomienie)
  ╔═══════════════╗
  ║    JENKINS    ║
  ╠═══════════════╣
  ║ 1. Checkout   ║  git clone
  ║ 2. Testy      ║  mvn test -P build-backend
  ║ 3. SonarQube  ║  mvn sonar:sonar
  ║ 4. Build      ║  mvn package -P build-frontend
  ║ 5. Stop       ║  pkill -f loyaltyClub.jar
  ║ 6. Archive    ║  mv loyaltyClub.jar → archive/
  ║ 7. Deploy     ║  cp target/*.jar → /home/wojciech/
  ║ 8. Start      ║  nohup java -jar loyaltyClub.jar &
  ╚═══════════════╝
        │
        ▼
  ┌─────────────────────┐     ┌───────────────┐
  │ loyalty-club-builds │     │   SonarQube   │
  │  loyaltyClub.jar    │     │ 192.168.x.x   │
  │  application.log    │     │ Raport jakości│
  │  archive/           │     └───────────────┘
  └─────────────────────┘
```

---

## SLAJD 19 – Pipeline CI/CD: Szczegóły etapów

### Etap 2: Testy jednostkowe
```groovy
stage('Testy jednostkowe (Backend)') {
    steps { sh 'mvn test -P build-backend' }
    post { always { junit 'target/surefire-reports/*.xml' } }
}
```
> Wyniki testów archiwizowane w Jenkins – widoczne w historii buildów.

### Etap 3: Analiza SonarQube
```groovy
withCredentials([string(credentialsId: 'loyalty-club', variable: 'SONAR_TOKEN')]) {
    sh 'mvn sonar:sonar -Dsonar.login=${SONAR_TOKEN}'
}
```
> Token w Jenkins Credentials Store – **nigdy nie w kodzie źródłowym**.

### Etap 4: Build fullstack
```groovy
sh 'mvn clean package -P build-frontend -DskipTests'
```
> Maven plugin pobiera Node.js v20, instaluje npm, buduje React SPA, kopiuje do `static/` i pakuje jako jeden JAR.

### Etap 8: Weryfikacja startu
```bash
sleep 15
if pgrep -f "java.*loyaltyClub.jar" > /dev/null; then
    echo "SUKCES"
else
    exit 1  # Fail pipeline jeśli aplikacja nie wystartowała
fi
```
> Pipeline nie przejdzie jeśli aplikacja crashuje przy starcie – prosta forma health check.

---

## SLAJD 20 – Analiza jakości kodu: SonarQube

### Rozwiązane problemy SonarQube (Code Smells)

| Typ problemu | Reguła | Rozwiązanie zastosowane |
|---|---|---|
| Niebezpieczny PRNG | `S2245` | `ThreadLocalRandom` → `SecureRandom` |
| Generyczny wyjątek | `S112` | `RuntimeException` → `BusinessException` |
| Zbędny collect | `S6204` | `collect(toList())` → `.toList()` |
| Duplikacja kodu | `S1192` | Wydzielenie `CouponCodeGenerator` i `BaseUser` |
| Zbyt duża złożoność | `S3776` | Ekstrakcja metody `importCustomerLine()` |
| Security Hotspot | `S2245` | Analiza i potwierdzenie bezpieczeństwa `SecureRandom` |

### Architektoniczne refaktoringi dla eliminacji duplikacji

**Przed:**
```
AdminUser  { id, username, password, enabled }  ← 28 linii
EcomUser   { id, username, password, enabled }  ← 28 linii
StoreUser  { id, username, password, enabled }  ← 28 linii
```

**Po:**
```
BaseUser (@MappedSuperclass) { id, username, password, enabled }
AdminUser extends BaseUser   ← 5 linii
EcomUser  extends BaseUser   ← 5 linii
StoreUser extends BaseUser   ← 5 linii
```

---

## SLAJD 21 – Kluczowe wzorce projektowe

| Wzorzec | Gdzie zastosowany | Korzyść |
|---------|------------------|---------|
| **Repository** | Spring Data JPA | Izolacja dostępu do danych |
| **DTO (Data Transfer Object)** | Kontrakty API | Odsprzężenie modelu wewnętrznego od API |
| **Builder** | Encje + DTO (Lombok) | Czytelne tworzenie obiektów |
| **Filter Chain** | JwtAuthFilter | Przechwycenie każdego żądania HTTP |
| **Template Method** | BaseUser + @MappedSuperclass | Eliminacja duplikacji encji |
| **Idempotency Key** | CouponRedemptionRequest | Bezpieczne retry operacji finansowych |
| **Command** | CommandLineRunner (Seeder) | Inicjalizacja danych przy starcie |
| **Multi-tenancy (Shared DB)** | Country Scope | Izolacja danych między krajami |

---

## SLAJD 22 – Podsumowanie: Metryki projektu

### Backend
| Metryka | Wartość |
|---------|---------|
| Java klasy źródłowe | **83** |
| API endpointy | **27** |
| Tabele bazy danych | **11** |
| Migracje Liquibase | **7** |
| Klasy testowe | **18** |
| Metody testowe | **190** |
| Pokrycie linii (serwisy) | **≥ 94%** |
| Etapy CI/CD | **8** |

### Frontend
| Metryka | Wartość |
|---------|---------|
| Pliki TSX/TS | **40+** |
| Komponenty | **21** |
| Custom hooks | **5** |
| Klasy testowe | **17** |
| Metody testowe | **64** |
| Obsługiwane języki | **3** (PL, EN, DE) |
| Motywy | **2** (jasny, ciemny) |

---

## SLAJD 23 – Wnioski i podsumowanie

### Co zostało osiągnięte?

1. **Kompletny system produkcyjny** – frontend + backend + baza danych w jednym deploymencie
2. **Bezpieczeństwo klasy enterprise** – JWT HS512, RBAC, BCrypt, SecureRandom, HTTPS-ready
3. **Wielodostępność** – jeden system dla wielu krajów z izolacją danych
4. **Automatyzacja** – pipeline Jenkins eliminuje ręczne wdrożenia
5. **Jakość kodu** – SonarQube monitoruje dług techniczny, ≥ 94% pokrycie testami klas biznesowych
6. **Skalowalność** – wirtualne wątki Java 21, stateless JWT, optymalne indeksy DB

### Czego się nauczyłem?
- Projektowanie wielowarstwowej architektury aplikacji biznesowej
- Implementacja bezpieczeństwa opartego na tokenach JWT
- Budowanie pipeline CI/CD od zera (Jenkins + SonarQube + Maven)
- Strategia testowania na poziomie jednostkowym i integracyjnym
- Zarządzanie jakością kodu i eliminacja długu technicznego

### Możliwe rozszerzenia
- Dockeryzacja + Kubernetes (orchestracja kontenerów)
- OAuth2 / OpenID Connect (federacja tożsamości)
- Apache Kafka (asynchroniczne zdarzenia transakcyjne)
- Redis (cache sald punktowych, rate limiting)

---

## SLAJD 24 – Pytania i dyskusja

**Dziękuję za uwagę!**

---
---

## APPENDIX A – Potencjalne pytania komisji i sugerowane odpowiedzi

**P: Dlaczego Spring Boot, a nie np. Quarkus lub Micronaut?**
> Spring Boot ma największą adopcję w przemyśle i bogatszy ekosystem. Spring Security oferuje gotowe integracje, które w Quarkus wymagałyby więcej konfiguracji. Dla pracy inżynierskiej ważna była stabilność dokumentacji.

**P: Dlaczego PostgreSQL, a nie MySQL lub H2?**
> PostgreSQL oferuje JSONB, pesymistyczne blokady (`FOR UPDATE`), sekwencje z precyzyjną kontrolą i lepszą zgodność ze standardem SQL. H2 byłoby tylko do testów, nie produkcji.

**P: Co oznacza „multi-tenancy" w tym systemie?**
> Jeden deployment obsługuje wiele krajów (PL, DE, CZ, SK, LT). Izolacja danych jest logiczna (kolumna `country`), nie fizyczna (osobne bazy). Użytkownik TECHNICAL ma scope tylko do swojego kraju – widzi i modyfikuje tylko dane z `country = 'PL'`.

**P: Jak działa idempotentność przy realizacji kuponów?**
> Klient wysyła nagłówek `Idempotency-Key: {uuid}`. Backend przechowuje key w tabeli `coupon_redemption_requests`. Przy ponownym żądaniu z tym samym kluczem (retry po awarii sieci) system zwraca pierwotny wynik bez tworzenia drugiego kuponu.

**P: Jak zapewniona jest bezpieczeństwo tokenu JWT?**
> Token podpisany HMAC-SHA512 z 64-bajtowym kluczem z zmiennej środowiskowej. Token wygasa po 15 minutach. Frontend odświeża go automatycznie 60 sekund przed wygaśnięciem. Interceptor Axios wylogowuje użytkownika przy 401.

**P: Czy system jest gotowy na produkcję?**
> Architektonicznie tak: stateless sesja, bezpieczny PRNG, wersjonowane migracje DB, pipeline CI/CD, monitoring jakości kodu. Brakuje: Dockera (deploymentem jest JAR na maszynie wirtualnej), HTTPS (termination na serwerze proxy) i load balancera dla skali.

**P: Dlaczego testy bezpieczeństwa (`@WebMvcTest`) wyłączają Spring Security?**
> Kontrolery testujemy izolując warstwę HTTP od warstwy bezpieczeństwa. Bezpieczeństwo JWT testowane jest osobno w `JwtAuthFilterTest`. Gdybyśmy włączyli pełną Security, testy musiałyby generować prawdziwe tokeny JWT – zwiększyłoby to złożoność i wolniej uruchamiałyby się testy.

**P: Co daje SonarQube ponad standardowy code review?**
> Automatyczna, deterministyczna analiza przy każdym buildzie. Wykrywa: duplikacje kodu, zbyt wysoką złożoność kognitywną, niebezpieczne wzorce (PRNG, SQL injection), nieużywane zmienne, zbyt mało testów. W projekcie naprawiono m.in. użycie `ThreadLocalRandom` w generowaniu kodów kuponów (security hotspot S2245).

---

## APPENDIX B – Diagram przepływu autentykacji (szczegółowy)

```
Frontend                Backend                 Database
   │                       │                       │
   │── POST /auth/login ──►│                       │
   │   {username, password} │                       │
   │                       │── findByUsername() ──►│
   │                       │◄── AdminUser ─────────│
   │                       │                       │
   │                       │── BCrypt.matches() ──►│ (w pamięci)
   │                       │                       │
   │                       │── generateToken() ───►│ (JwtService)
   │                       │   sub=username        │
   │                       │   role=ADMIN          │
   │                       │   exp=+15min          │
   │                       │                       │
   │◄── {token, expiresAt} │                       │
   │                       │                       │
   │── localStorage.setItem(token) ──► (przeglądarka)
   │                       │                       │
   │── GET /customers ─────►│                      │
   │   Authorization:       │                       │
   │   Bearer {token}       │                       │
   │                       │── JwtAuthFilter ──────►│ (walidacja)
   │                       │   extractUsername()    │
   │                       │   extractRole()        │
   │                       │   isTokenValid()       │
   │                       │── SecurityContext.set()│
   │                       │                       │
   │                       │── findAllByCountry() ─►│
   │                       │◄── [Customer, ...] ────│
   │                       │                        │
   │◄── [{customers...}] ──│                        │
```

---

## APPENDIX C – Struktura projektu (drzewo katalogów)

```
loyaltyClub/
├── frontend/                    ← React SPA
│   └── src/
│       ├── api/client.ts        ← Axios + Auth
│       ├── components/          ← 21 komponentów TSX
│       ├── hooks/               ← 5 custom hooks
│       ├── types/               ← TypeScript interfaces
│       └── i18n/                ← PL, EN, DE
│
├── src/main/java/.../
│   ├── api/admin/               ← Panel administracyjny
│   ├── api/store/               ← Integracja POS
│   ├── api/ecom/                ← Integracja e-commerce
│   ├── api/coupon/              ← Realizacja kuponów
│   ├── config/                  ← SecurityConfig
│   ├── exception/               ← GlobalExceptionHandler
│   ├── model/                   ← BaseUser (@MappedSuperclass)
│   └── util/                    ← CouponCodeGenerator
│
├── src/main/resources/
│   ├── application.properties   ← Konfiguracja Spring Boot
│   └── db/migrations/           ← 7 plików SQL (Liquibase)
│
├── src/test/                    ← 18 klas testowych, 190 testów
│
├── jenkins/build.jenkinsfile    ← Pipeline CI/CD (8 etapów)
├── pom.xml                      ← Maven (2 profile)
└── tool/
    ├── backend_rules.md
    ├── frontend_rules.md
    └── information/
        └── presentation_draft.md  ← ten plik
```
