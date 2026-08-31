# LoyaltyClub — Loyalty Program System (Backend)

The **backend** of an enterprise loyalty program, built with Java 21 + Spring Boot 3.2. It exposes the REST API for customer management, loyalty point accrual via POS transactions, coupon issuance and redemption, and a multi-role admin API with country-scoped access control.

> The React admin panel lives in a separate repository: **loyaltyClub-frontend**. This repository no longer builds or embeds it.

---

## Table of Contents (EN)

1. [Tech Stack](#tech-stack)
2. [Architecture](#architecture)
3. [Roles & Access Control](#roles--access-control)
4. [API Overview](#api-overview)
5. [Getting Started](#getting-started)
6. [Build Profiles](#build-profiles)
7. [Configuration](#configuration)
8. [Database Migrations](#database-migrations)
9. [Testing](#testing)
10. [CI/CD Pipeline](#cicd-pipeline)
11. [Project Structure](#project-structure)

---

## Tech Stack

### Backend
| Technology | Version | Role |
|------------|---------|------|
| Java (Amazon Corretto) | 21 | Runtime |
| Spring Boot | 3.2.3 | Web + Data JPA framework |
| PostgreSQL | 15 | Relational database |
| Liquibase | — | Database migrations |
| JJWT | 0.12.6 | JWT auth (HMAC-SHA512) |
| Lombok | — | Boilerplate reduction |
| JaCoCo | 0.8.11 | Code coverage |
| SonarQube / Sonar Maven | 4.0.0 | Static code analysis |
| Maven | — | Build tool |

---

## Architecture

The backend is an **API-only** Spring Boot service. The React SPA is built and deployed independently from its own repository and talks to this service over HTTP.

```
Browser ──► SPA (static hosting / reverse proxy)
               └──► Spring Boot (port 8089)
                       ├── /api/admin/**     Admin panel REST API
                       ├── /api/store/**     POS terminal REST API
                       ├── /api/ecom/**      E-commerce REST API
                       └── /api/coupon/**    Coupon redemption REST API
```

Two supported topologies:
- **Same origin** — a reverse proxy serves the SPA and forwards `/api` here. No CORS needed (default).
- **Separate origins** — the SPA is served from its own host; set `app.cors.allowed-origins` (env `CORS_ALLOWED_ORIGINS`) to that origin.

**Backend domain packages:**
```
pl.pietruszynski.loyaltyclub
├── api/
│   ├── admin/      ← Admin panel (customers, coupons, promotions, technical users)
│   ├── store/      ← POS transactions (sales, returns, points balance)
│   ├── ecom/       ← E-commerce integration
│   └── coupon/     ← Coupon redemption & validation
├── config/         ← Security config (Spring Security, JWT filter)
├── exception/      ← BusinessException, ResourceNotFoundException, GlobalExceptionHandler
├── model/          ← BaseUser (@MappedSuperclass)
└── util/           ← CouponCodeGenerator (SecureRandom)
```

---

## Roles & Access Control

| Role | Login endpoint | Scope | Capabilities |
|------|---------------|-------|--------------|
| `ROLE_ADMIN` | `/api/admin/auth/login` | All countries | Full access: customers, points, coupons, promotions, technical user management |
| `ROLE_TECHNICAL` | `/api/admin/auth/login` | Single country | Customers, coupons, promotions within assigned country; no add-points, no technical user management |
| `ROLE_STORE` | `/api/store/auth/login` | — | Register sales/returns, query customer points balance |
| `ROLE_ECOM` | *(system credential)* | — | Redeem points for coupons, validate coupons |

JWT tokens expire after **15 minutes** and are auto-refreshed by the frontend when fewer than 60 seconds remain.

---

## API Overview

### Admin API — `/api/admin/**`
| Method | Path | Roles | Description |
|--------|------|-------|-------------|
| POST | `/api/admin/auth/login` | public | Login, returns JWT |
| POST | `/api/admin/auth/refresh` | ADMIN, TECHNICAL | Refresh JWT |
| GET | `/api/admin/customers` | ADMIN, TECHNICAL | List customers |
| POST | `/api/admin/customers` | ADMIN, TECHNICAL | Create customer |
| PUT | `/api/admin/customers/{id}` | ADMIN, TECHNICAL | Update customer |
| GET | `/api/admin/customers/{id}/transactions` | ADMIN, TECHNICAL | Customer transaction history |
| GET | `/api/admin/customers/{id}/coupons` | ADMIN, TECHNICAL | Customer coupons |
| POST | `/api/admin/customers/{id}/add-points` | ADMIN | Manually add points |
| GET | `/api/admin/coupons` | ADMIN, TECHNICAL | All issued coupons |
| GET | `/api/admin/coupon-templates` | ADMIN, TECHNICAL | Coupon template list |
| POST | `/api/admin/coupon-templates` | ADMIN | Create coupon template |
| POST | `/api/admin/coupons/issue` | ADMIN | Issue coupon to customer |
| GET | `/api/admin/store-promotions` | ADMIN, TECHNICAL | List promotions |
| POST | `/api/admin/store-promotions` | ADMIN, TECHNICAL | Create promotion |
| PUT | `/api/admin/store-promotions/{id}` | ADMIN, TECHNICAL | Update promotion |
| PATCH | `/api/admin/store-promotions/{id}/status` | ADMIN, TECHNICAL | Toggle promotion status |
| GET | `/api/admin/technical-users` | ADMIN | List technical users |
| POST | `/api/admin/technical-users` | ADMIN | Create technical user |
| PATCH | `/api/admin/technical-users/{id}/status` | ADMIN | Toggle user enabled |
| PATCH | `/api/admin/technical-users/{id}/password` | ADMIN | Change password |
| POST | `/api/admin/tools/import-customers` | ADMIN, TECHNICAL | CSV bulk import |
| GET | `/api/admin/config/countries` | ADMIN, TECHNICAL | Available country codes |
| GET | `/api/admin/config/coupon-prefixes` | ADMIN, TECHNICAL | Coupon prefix config |
| GET | `/api/admin/reports/summary` | ADMIN, TECHNICAL | Summary: customer count, total points, transactions (30d), scoped by country for TECHNICAL |
| GET | `/api/admin/reports/export/customers` | ADMIN, TECHNICAL | CSV export of customers (tier + referral code columns) |
| GET | `/api/admin/reports/export/transactions` | ADMIN, TECHNICAL | CSV export of transactions (optional `from` / `to` ISO date query params) |
| GET | `/api/admin/audit-logs` | ADMIN | Latest 200 admin audit entries (mutating actions from panel) |

### Store API — `/api/store/**`
| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/store/auth/login` | Store user login |
| POST | `/api/store/transactions/sale` | Register sale (header: `X-CountryCode`) |
| POST | `/api/store/transactions/return` | Register return (header: `X-CountryCode`) |
| GET | `/api/store/customers/{customerNumber}/points` | Query points balance |

### Coupon API — `/api/coupon/**`
| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/coupon/redeem-points` | Redeem points for coupon (header: `Idempotency-Key`) |
| GET | `/api/coupon/validate` | Validate coupon (params: `couponCode`, `customerNumber`) |

**E-commerce integration:** use **HTTP Basic** or **JWT** (Bearer) with role `ECOM` for both `/api/coupon/**` and `/api/ecom/**`.

### E-commerce read API — `/api/ecom/**`
| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/ecom` | Integration metadata (`apiVersion`, pointers to profile vs coupon APIs) |
| GET | `/api/ecom/customers/{customerNumber}/points` | Same balance breakdown as store (`pending` / `available` / `expired`) |
| GET | `/api/ecom/customers/{customerNumber}/profile` | Profile + tier + referral fields |
| GET | `/api/ecom/customers/{customerNumber}/transactions` | Transaction history (same shape as admin list) |
| GET | `/api/ecom/customers/{customerNumber}/coupons` | Issued coupons for the customer |

Point accrual and returns remain on **`/api/store`** (POS). Coupon redemption and validation remain on **`/api/coupon`**.

---

## Getting Started

### Prerequisites
- Docker Desktop
- JDK 21
- Maven 3.x

### 1. Start the database
```bash
docker-compose up -d
```
This starts PostgreSQL 15 on port **5433** (container port 5432).
Credentials: `user / password`, database: `loyalty_db`.

### 2. Build & run
```bash
mvn clean package
java -jar target/loyalty-club-0.0.1-SNAPSHOT.jar
```
The API will be available at **http://localhost:8089**

### 3. Admin panel
Clone the frontend repository and follow its README. Its dev server proxies `/api` to `http://localhost:8089` out of the box.

---

## Build Commands

The project is a backend-only service, so the build needs no Maven profiles.

| Command | What it does |
|---------|-------------|
| `mvn test` | Run all unit and controller tests + JaCoCo report |
| `mvn clean package -DskipTests` | Build the runnable JAR |
| `mvn sonar:sonar` | Send the JaCoCo report to SonarQube |

---

## Configuration

Key properties in `src/main/resources/application.properties`:

| Property | Default | Description |
|----------|---------|-------------|
| `server.port` | `8089` | HTTP port |
| `app.jwt.expiration-ms` | `900000` | JWT expiry (15 min) |
| `app.jwt.secret` | *(base64 key)* | HMAC-SHA512 secret — override via `JWT_SECRET` env var in production |
| `app.available-country-codes` | `PL,DE,CZ,SK,LT` | Countries enabled for multi-tenancy |
| `app.default-store-points-rate` | `1.00` | Points earned per currency unit |
| `app.cors.allowed-origins` | *(empty)* | Comma-separated frontend origins allowed for CORS on `/api/**`. Empty disables CORS — override via `CORS_ALLOWED_ORIGINS` |
| `spring.datasource.url` | `jdbc:postgresql://localhost:5433/loyalty_db` | Database URL |

---

## Database Migrations

Managed by **Liquibase**. Master changelog: `src/main/resources/db/changelog-master.xml`.

| File | Description |
|------|-------------|
| `001_schema_init_auth.sql` | Auth tables: admin_users, store_users, ecom_users, technical_users |
| `002_schema_init_core.sql` | Core tables: customers, loyalty_transactions, coupons, coupon_templates |
| `003_indexes_and_base_constraints.sql` | Indexes and unique constraints |
| `005_backfill_missing_columns.sql` | Backfill columns added during development |
| `006_store_transaction_lifecycle_and_promotions.sql` | Transaction state machine, store_promotions table |
| `007_store_transaction_source_number.sql` | Source number field on transactions |
| `008_coupon_redemption_idempotency.sql` | Idempotency key table for coupon redemption |
| `009_hierarchy_promotions.sql` | Hierarchy promotions |
| `010_audit_tiers_referrals.sql` | Admin audit log, tier definitions (BRONZE/SILVER/GOLD), referral columns on customers |

Migrations are **additive and idempotent** — never modify existing changesets.

---

## Testing

### Backend
18 test classes covering controllers, services, security filters, and exception handling.

```bash
mvn test
```

| Layer | Framework |
|-------|-----------|
| Service unit tests | JUnit 5 + Mockito + AssertJ |
| Controller tests | `@WebMvcTest` + MockMvc (Security auto-config excluded) |
| Coverage report | JaCoCo XML → SonarQube |

Coverage target: **≥ 90%** line coverage for service and controller classes.
Excluded from coverage: `**/config/**`, `LoyaltyClubApplication.java`.

Frontend tests live in the frontend repository.

---

## CI/CD Pipeline

Jenkins declarative pipeline at `jenkins/build.jenkinsfile`.

| Stage | Description |
|-------|-------------|
| Checkout | Clone repository |
| Unit Tests | `mvn test`, publishes JUnit XML |
| SonarQube Analysis | `mvn sonar:sonar` using Jenkins credential `loyalty-club` |
| Backend Build | `mvn clean package -DskipTests` |
| Stop & Archive | Kill previous process, archive JAR with timestamp |
| Deploy | Copy JAR to `/home/wojciech/loyalty-club-builds/` |
| Start | Launch with `java -Xms512M -Xmx1G -XX:+UseG1GC -jar ...` |

SonarQube: `http://192.168.100.150:9000`, project key `loyalty-club`.

---

## Project Structure

```
loyaltyclub/
├── src/
│   ├── main/
│   │   ├── java/pl/pietruszynski/loyaltyclub/   ← Spring Boot source
│   │   └── resources/
│   │       ├── application.properties
│   │       └── db/                              ← Liquibase migrations
│   └── test/java/...                            ← JUnit test classes
├── jenkins/build.jenkinsfile                    ← CI/CD pipeline
├── tool/
│   ├── backend_rules.md                         ← Backend coding standards
│   └── information/                             ← Presentation materials
├── docker-compose.yml                           ← PostgreSQL 15 service
└── pom.xml
```

The React admin panel is maintained in its own repository (`loyaltyClub-frontend`), together with its coding standards (`docs/frontend_rules.md`).

---

---

# LoyaltyClub — System Programu Lojalnościowego (Backend)

**Backend** aplikacji enterprise loyalty program zbudowany w technologii Java 21 + Spring Boot 3.2. Udostępnia REST API do zarządzania klientami, naliczania punktów lojalnościowych przez transakcje kasowe, emisji i realizacji kuponów oraz wielorolowe API administracyjne z kontrolą dostępu ograniczoną do krajów.

> Panel administracyjny w React znajduje się w osobnym repozytorium: **loyaltyClub-frontend**. To repozytorium już go nie buduje ani nie osadza w JAR-ze.

---

## Spis treści (PL)

1. [Stos technologiczny](#stos-technologiczny)
2. [Architektura](#architektura)
3. [Role i kontrola dostępu](#role-i-kontrola-dostępu)
4. [Przegląd API](#przegląd-api)
5. [Uruchomienie projektu](#uruchomienie-projektu)
6. [Profile Maven](#profile-maven)
7. [Konfiguracja](#konfiguracja)
8. [Migracje bazy danych](#migracje-bazy-danych)
9. [Testy](#testy)
10. [Potok CI/CD](#potok-cicd)
11. [Struktura projektu](#struktura-projektu)

---

## Stos technologiczny

### Backend
| Technologia | Wersja | Rola |
|-------------|--------|------|
| Java (Amazon Corretto) | 21 | Środowisko uruchomieniowe |
| Spring Boot | 3.2.3 | Framework Web + Data JPA |
| PostgreSQL | 15 | Relacyjna baza danych |
| Liquibase | — | Migracje schematu bazy |
| JJWT | 0.12.6 | Autoryzacja JWT (HMAC-SHA512) |
| Lombok | — | Redukcja kodu szablonowego |
| JaCoCo | 0.8.11 | Pokrycie kodu testami |
| SonarQube / Sonar Maven | 4.0.0 | Statyczna analiza kodu |
| Maven | — | Narzędzie budowania |

---

## Architektura

Backend jest serwisem **wyłącznie API**. SPA w React jest budowana i wdrażana niezależnie, z własnego repozytorium, i komunikuje się z tym serwisem po HTTP.

```
Przeglądarka ──► SPA (hosting statyczny / reverse proxy)
                    └──► Spring Boot (port 8089)
                            ├── /api/admin/**     API panelu administracyjnego
                            ├── /api/store/**     API terminala kasowego
                            ├── /api/ecom/**      API integracji e-commerce
                            └── /api/coupon/**    API realizacji kuponów
```

Dwa wspierane warianty wdrożenia:
- **Ten sam origin** — reverse proxy serwuje SPA i przekazuje `/api` tutaj. CORS niepotrzebny (domyślnie).
- **Osobne originy** — SPA na własnym hoście; ustaw `app.cors.allowed-origins` (env `CORS_ALLOWED_ORIGINS`) na jej origin.

**Pakiety domenowe backendu:**
```
pl.pietruszynski.loyaltyclub
├── api/
│   ├── admin/      ← Panel administracyjny (klienci, kupony, promocje, użytkownicy techniczni)
│   ├── store/      ← Transakcje kasowe (sprzedaż, zwroty, saldo punktów)
│   ├── ecom/       ← Integracja z e-commerce
│   └── coupon/     ← Realizacja i walidacja kuponów
├── config/         ← Konfiguracja bezpieczeństwa (Spring Security, filtr JWT)
├── exception/      ← BusinessException, ResourceNotFoundException, GlobalExceptionHandler
├── model/          ← BaseUser (@MappedSuperclass)
└── util/           ← CouponCodeGenerator (SecureRandom)
```

---

## Role i kontrola dostępu

| Rola | Endpoint logowania | Zakres | Uprawnienia |
|------|-------------------|--------|-------------|
| `ROLE_ADMIN` | `/api/admin/auth/login` | Wszystkie kraje | Pełny dostęp: klienci, punkty, kupony, promocje, zarządzanie kontami technicznymi |
| `ROLE_TECHNICAL` | `/api/admin/auth/login` | Jeden kraj | Klienci, kupony, promocje w przypisanym kraju; brak dodawania punktów i zarządzania kontami technicznymi |
| `ROLE_STORE` | `/api/store/auth/login` | — | Rejestrowanie sprzedaży/zwrotów, sprawdzanie salda punktów klienta |
| `ROLE_ECOM` | *(poświadczenia systemowe)* | — | Wymiana punktów na kupony, walidacja kuponów |

Tokeny JWT wygasają po **15 minutach** i są automatycznie odświeżane przez frontend, gdy pozostało mniej niż 60 sekund.

---

## Przegląd API

### Admin API — `/api/admin/**`
| Metoda | Ścieżka | Role | Opis |
|--------|---------|------|------|
| POST | `/api/admin/auth/login` | publiczny | Logowanie, zwraca JWT |
| POST | `/api/admin/auth/refresh` | ADMIN, TECHNICAL | Odświeżenie JWT |
| GET | `/api/admin/customers` | ADMIN, TECHNICAL | Lista klientów |
| POST | `/api/admin/customers` | ADMIN, TECHNICAL | Tworzenie klienta |
| PUT | `/api/admin/customers/{id}` | ADMIN, TECHNICAL | Aktualizacja klienta |
| GET | `/api/admin/customers/{id}/transactions` | ADMIN, TECHNICAL | Historia transakcji klienta |
| GET | `/api/admin/customers/{id}/coupons` | ADMIN, TECHNICAL | Kupony klienta |
| POST | `/api/admin/customers/{id}/add-points` | ADMIN | Manualne dodanie punktów |
| GET | `/api/admin/coupons` | ADMIN, TECHNICAL | Wszystkie wyemitowane kupony |
| GET | `/api/admin/coupon-templates` | ADMIN, TECHNICAL | Lista szablonów kuponów |
| POST | `/api/admin/coupon-templates` | ADMIN | Tworzenie szablonu kuponu |
| POST | `/api/admin/coupons/issue` | ADMIN | Emisja kuponu do klienta |
| GET | `/api/admin/store-promotions` | ADMIN, TECHNICAL | Lista promocji sklepowych |
| POST | `/api/admin/store-promotions` | ADMIN, TECHNICAL | Tworzenie promocji |
| PUT | `/api/admin/store-promotions/{id}` | ADMIN, TECHNICAL | Aktualizacja promocji |
| PATCH | `/api/admin/store-promotions/{id}/status` | ADMIN, TECHNICAL | Zmiana statusu promocji |
| GET | `/api/admin/technical-users` | ADMIN | Lista użytkowników technicznych |
| POST | `/api/admin/technical-users` | ADMIN | Tworzenie użytkownika technicznego |
| PATCH | `/api/admin/technical-users/{id}/status` | ADMIN | Włączenie/wyłączenie konta |
| PATCH | `/api/admin/technical-users/{id}/password` | ADMIN | Zmiana hasła |
| POST | `/api/admin/tools/import-customers` | ADMIN, TECHNICAL | Masowy import klientów z CSV |
| GET | `/api/admin/config/countries` | ADMIN, TECHNICAL | Dostępne kody krajów |
| GET | `/api/admin/config/coupon-prefixes` | ADMIN, TECHNICAL | Konfiguracja prefiksów kuponów |

### Store API — `/api/store/**`
| Metoda | Ścieżka | Opis |
|--------|---------|------|
| POST | `/api/store/auth/login` | Logowanie użytkownika sklepu |
| POST | `/api/store/transactions/sale` | Rejestracja sprzedaży (nagłówek: `X-CountryCode`) |
| POST | `/api/store/transactions/return` | Rejestracja zwrotu (nagłówek: `X-CountryCode`) |
| GET | `/api/store/customers/{customerNumber}/points` | Saldo punktów klienta |

### Coupon API — `/api/coupon/**`
| Metoda | Ścieżka | Opis |
|--------|---------|------|
| POST | `/api/coupon/redeem-points` | Wymiana punktów na kupon (nagłówek: `Idempotency-Key`) |
| GET | `/api/coupon/validate` | Walidacja kuponu (params: `couponCode`, `customerNumber`) |

---

## Uruchomienie projektu

### Wymagania wstępne
- Docker Desktop
- JDK 21
- Maven 3.x

### 1. Uruchomienie bazy danych
```bash
docker-compose up -d
```
Startuje PostgreSQL 15 na porcie **5433** (port kontenera: 5432).
Poświadczenia: `user / password`, baza: `loyalty_db`.

### 2. Budowa i uruchomienie
```bash
mvn clean package
java -jar target/loyalty-club-0.0.1-SNAPSHOT.jar
```
API dostępne pod adresem **http://localhost:8089**

### 3. Panel administracyjny
Sklonuj repozytorium frontendu i postępuj zgodnie z jego README. Jego serwer deweloperski domyślnie przekierowuje `/api` na `http://localhost:8089`.

---

## Komendy budowania

Projekt jest wyłącznie backendem, więc budowanie nie wymaga profili Mavena.

| Komenda | Co robi |
|---------|---------|
| `mvn test` | Uruchamia wszystkie testy jednostkowe i testy kontrolerów + raport JaCoCo |
| `mvn clean package -DskipTests` | Buduje uruchamialny JAR |
| `mvn sonar:sonar` | Wysyła raport JaCoCo do SonarQube |

---

## Konfiguracja

Kluczowe właściwości w `src/main/resources/application.properties`:

| Właściwość | Domyślna wartość | Opis |
|------------|-----------------|------|
| `server.port` | `8089` | Port HTTP |
| `app.jwt.expiration-ms` | `900000` | Czas życia JWT (15 min) |
| `app.jwt.secret` | *(klucz base64)* | Sekret HMAC-SHA512 — w produkcji nadpisz zmienną env `JWT_SECRET` |
| `app.available-country-codes` | `PL,DE,CZ,SK,LT` | Kraje włączone w multi-tenancy |
| `app.default-store-points-rate` | `1.00` | Punkty naliczane za jednostkę waluty |
| `app.cors.allowed-origins` | *(puste)* | Originy frontendu dopuszczone do CORS na `/api/**`, po przecinku. Puste = CORS wyłączony — nadpisz zmienną `CORS_ALLOWED_ORIGINS` |
| `spring.datasource.url` | `jdbc:postgresql://localhost:5433/loyalty_db` | URL bazy danych |

---

## Migracje bazy danych

Zarządzane przez **Liquibase**. Masterowy changelog: `src/main/resources/db/changelog-master.xml`.

| Plik | Opis |
|------|------|
| `001_schema_init_auth.sql` | Tabele autoryzacji: admin_users, store_users, ecom_users, technical_users |
| `002_schema_init_core.sql` | Tabele główne: customers, loyalty_transactions, coupons, coupon_templates |
| `003_indexes_and_base_constraints.sql` | Indeksy i ograniczenia unikalności |
| `005_backfill_missing_columns.sql` | Uzupełnienie kolumn dodanych w trakcie developmentu |
| `006_store_transaction_lifecycle_and_promotions.sql` | Maszyna stanów transakcji, tabela store_promotions |
| `007_store_transaction_source_number.sql` | Pole numeru źródłowego transakcji |
| `008_coupon_redemption_idempotency.sql` | Tabela kluczy idempotentności dla realizacji kuponów |
| `009_hierarchy_promotions.sql` | Promocje hierarchii produktów |
| `010_audit_tiers_referrals.sql` | Log audytu admina, progi poziomów (BRONZE/SILVER/GOLD), kolumny poleceń przy klientach |

Migracje są **addytywne i idempotentne** — nie modyfikuj istniejących changesetów.

---

## Testy

### Backend
18 klas testowych obejmujących kontrolery, serwisy, filtry bezpieczeństwa i obsługę wyjątków.

```bash
mvn test
```

| Warstwa | Framework |
|---------|-----------|
| Testy jednostkowe serwisów | JUnit 5 + Mockito + AssertJ |
| Testy kontrolerów | `@WebMvcTest` + MockMvc (wyłączona autokonfiguracja Security) |
| Raport pokrycia | JaCoCo XML → SonarQube |

Docelowe pokrycie: **≥ 90%** linii kodu dla klas serwisów i kontrolerów.
Wykluczone z pokrycia: `**/config/**`, `LoyaltyClubApplication.java`.

Testy frontendu znajdują się w repozytorium frontendu.

---

## Potok CI/CD

Deklaratywny potok Jenkinsa w pliku `jenkins/build.jenkinsfile`.

| Etap | Opis |
|------|------|
| Pobranie kodu | Klonowanie repozytorium |
| Testy jednostkowe | `mvn test`, publikacja raportów JUnit |
| Analiza SonarQube | `mvn sonar:sonar` z użyciem credentiala Jenkinsa `loyalty-club` |
| Budowanie Backendu | `mvn clean package -DskipTests` |
| Zatrzymanie i archiwizacja | Zatrzymanie poprzedniego procesu, archiwizacja JAR ze znacznikiem czasu |
| Kopiowanie (Deploy) | Kopiowanie JAR do `/home/wojciech/loyalty-club-builds/` |
| Uruchomienie | Start z `java -Xms512M -Xmx1G -XX:+UseG1GC -jar ...` |

SonarQube: `http://192.168.100.150:9000`, klucz projektu `loyalty-club`.

---

## Struktura projektu

```
loyaltyclub/
├── src/
│   ├── main/
│   │   ├── java/pl/pietruszynski/loyaltyclub/   ← Kod źródłowy Spring Boot
│   │   └── resources/
│   │       ├── application.properties
│   │       └── db/                              ← Migracje Liquibase
│   └── test/java/...                            ← Klasy testów JUnit
├── jenkins/build.jenkinsfile                    ← Potok CI/CD
├── tool/
│   ├── backend_rules.md                         ← Standardy kodowania backendu
│   └── information/                             ← Materiały prezentacyjne
├── docker-compose.yml                           ← Serwis PostgreSQL 15
└── pom.xml
```

Panel administracyjny w React rozwijany jest w osobnym repozytorium (`loyaltyClub-frontend`) wraz ze swoimi standardami kodowania (`docs/frontend_rules.md`).
