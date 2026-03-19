# LoyaltyClub — Loyalty Program System

A full-stack **enterprise loyalty program** application built with Java 21 + Spring Boot 3.2 and React 19 + TypeScript. It enables customer management, loyalty point accrual via POS transactions, coupon issuance and redemption, and a multi-role admin panel with country-scoped access control.

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

### Frontend
| Technology | Version | Role |
|------------|---------|------|
| React | 19.2.0 | UI framework |
| TypeScript | strict | Type safety |
| Vite | 7.3.1 | Build tool / Dev server |
| Ant Design | 5.27.6 | Component library |
| Axios | 1.13.5 | HTTP client |
| Vitest | 3.2.4 | Unit test runner |
| @testing-library/react | 16.3.2 | Component testing |
| lucide-react | — | Icons |

---

## Architecture

The application is a **monorepo** — the Maven build compiles the React frontend and embeds it as static resources inside the Spring Boot JAR. In production, a single process serves both the API and the SPA.

```
Browser ──► Spring Boot (port 8089)
               ├── /api/admin/**     Admin panel REST API
               ├── /api/store/**     POS terminal REST API
               ├── /api/ecom/**      E-commerce REST API
               ├── /api/coupon/**    Coupon redemption REST API
               └── /**              React SPA (static resources)
```

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

---

## Getting Started

### Prerequisites
- Docker Desktop
- JDK 21
- Maven 3.x
- Node.js 20 *(installed automatically by Maven build)*

### 1. Start the database
```bash
docker-compose up -d
```
This starts PostgreSQL 15 on port **5433** (container port 5432).
Credentials: `user / password`, database: `loyalty_db`.

### 2. Build & run (full stack)
```bash
mvn clean package -P build-frontend
java -jar target/loyalty-club-0.0.1-SNAPSHOT.jar
```
The application will be available at **http://localhost:8089**

### 3. Frontend dev server (hot reload)
```bash
cd frontend
npm run dev
```
Vite starts on port **5173** and proxies all `/api` requests to `http://localhost:8089`.

---

## Build Profiles

| Profile | Command | What it does |
|---------|---------|-------------|
| `build-backend` | `mvn test -P build-backend` | Compile + unit test backend only |
| `build-frontend` | `mvn clean package -P build-frontend -DskipTests` | Install Node 20, run frontend tests, build React app, copy to `src/main/resources/static/`, compile and package the Spring Boot JAR |

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

Migrations are **additive and idempotent** — never modify existing changesets.

---

## Testing

### Backend
18 test classes covering controllers, services, security filters, and exception handling.

```bash
mvn test -P build-backend
```

| Layer | Framework |
|-------|-----------|
| Service unit tests | JUnit 5 + Mockito + AssertJ |
| Controller tests | `@WebMvcTest` + MockMvc (Security auto-config excluded) |
| Coverage report | JaCoCo XML → SonarQube |

Coverage target: **≥ 90%** line coverage for service and controller classes.
Excluded from coverage: `**/config/**`, `LoyaltyClubApplication.java`.

### Frontend
17 test files using Vitest + @testing-library/react.

```bash
cd frontend && npm test
```

---

## CI/CD Pipeline

Jenkins declarative pipeline at `jenkins/build.jenkinsfile`.

| Stage | Description |
|-------|-------------|
| Checkout | Clone repository |
| Backend Tests | `mvn test -P build-backend`, publishes JUnit XML |
| SonarQube Analysis | `mvn sonar:sonar` using Jenkins credential `loyalty-club` |
| Full-Stack Build | `mvn clean package -P build-frontend -DskipTests` |
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
│   │       ├── db/                              ← Liquibase migrations
│   │       └── static/                          ← Built React app (generated)
│   └── test/java/...                            ← JUnit test classes
├── frontend/
│   ├── src/
│   │   ├── api/client.ts                        ← Axios + JWT interceptors
│   │   ├── components/                          ← Presentational components
│   │   ├── hooks/                               ← Business logic hooks
│   │   ├── types/                               ← TypeScript domain types
│   │   └── i18n/                                ← PL / EN / DE translations
│   ├── package.json
│   └── vite.config.ts
├── jenkins/build.jenkinsfile                    ← CI/CD pipeline
├── tool/
│   ├── backend_rules.md                         ← Backend coding standards
│   ├── frontend_rules.md                        ← Frontend coding standards
│   └── information/                             ← Presentation materials
├── docker-compose.yml                           ← PostgreSQL 15 service
└── pom.xml
```

---

---

# LoyaltyClub — System Programu Lojalnościowego

Pełnostackowa aplikacja **enterprise loyalty program** zbudowana w technologii Java 21 + Spring Boot 3.2 oraz React 19 + TypeScript. Umożliwia zarządzanie klientami, naliczanie punktów lojalnościowych przez transakcje kasowe, emisję i realizację kuponów oraz wielorolowy panel administracyjny z kontrolą dostępu ograniczoną do krajów.

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

### Frontend
| Technologia | Wersja | Rola |
|-------------|--------|------|
| React | 19.2.0 | Framework UI |
| TypeScript | strict | Typowanie statyczne |
| Vite | 7.3.1 | Build tool / serwer deweloperski |
| Ant Design | 5.27.6 | Biblioteka komponentów |
| Axios | 1.13.5 | Klient HTTP |
| Vitest | 3.2.4 | Framework testów jednostkowych |
| @testing-library/react | 16.3.2 | Testowanie komponentów |
| lucide-react | — | Ikony |

---

## Architektura

Projekt jest **monorepo** — Maven kompiluje frontend React i osadza go jako zasoby statyczne wewnątrz pliku JAR Spring Boot. W środowisku produkcyjnym jeden proces obsługuje zarówno API, jak i SPA.

```
Przeglądarka ──► Spring Boot (port 8089)
                    ├── /api/admin/**     API panelu administracyjnego
                    ├── /api/store/**     API terminala kasowego
                    ├── /api/ecom/**      API integracji e-commerce
                    ├── /api/coupon/**    API realizacji kuponów
                    └── /**              React SPA (zasoby statyczne)
```

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
- Node.js 20 *(instalowany automatycznie przez Maven)*

### 1. Uruchomienie bazy danych
```bash
docker-compose up -d
```
Startuje PostgreSQL 15 na porcie **5433** (port kontenera: 5432).
Poświadczenia: `user / password`, baza: `loyalty_db`.

### 2. Budowa i uruchomienie (pełny stack)
```bash
mvn clean package -P build-frontend
java -jar target/loyalty-club-0.0.1-SNAPSHOT.jar
```
Aplikacja dostępna pod adresem **http://localhost:8089**

### 3. Serwer deweloperski frontendu (hot reload)
```bash
cd frontend
npm run dev
```
Vite startuje na porcie **5173** i przekierowuje wszystkie żądania `/api` na `http://localhost:8089`.

---

## Profile Maven

| Profil | Komenda | Co robi |
|--------|---------|---------|
| `build-backend` | `mvn test -P build-backend` | Kompilacja i testy jednostkowe wyłącznie backendu |
| `build-frontend` | `mvn clean package -P build-frontend -DskipTests` | Instalacja Node 20, testy frontendu, budowa aplikacji React, kopiowanie do `src/main/resources/static/`, kompilacja i pakowanie JAR Spring Boot |

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

Migracje są **addytywne i idempotentne** — nie modyfikuj istniejących changesetów.

---

## Testy

### Backend
18 klas testowych obejmujących kontrolery, serwisy, filtry bezpieczeństwa i obsługę wyjątków.

```bash
mvn test -P build-backend
```

| Warstwa | Framework |
|---------|-----------|
| Testy jednostkowe serwisów | JUnit 5 + Mockito + AssertJ |
| Testy kontrolerów | `@WebMvcTest` + MockMvc (wyłączona autokonfiguracja Security) |
| Raport pokrycia | JaCoCo XML → SonarQube |

Docelowe pokrycie: **≥ 90%** linii kodu dla klas serwisów i kontrolerów.
Wykluczone z pokrycia: `**/config/**`, `LoyaltyClubApplication.java`.

### Frontend
17 plików testowych z użyciem Vitest + @testing-library/react.

```bash
cd frontend && npm test
```

---

## Potok CI/CD

Deklaratywny potok Jenkinsa w pliku `jenkins/build.jenkinsfile`.

| Etap | Opis |
|------|------|
| Pobranie kodu | Klonowanie repozytorium |
| Testy jednostkowe (Backend) | `mvn test -P build-backend`, publikacja raportów JUnit |
| Analiza SonarQube | `mvn sonar:sonar` z użyciem credentiala Jenkinsa `loyalty-club` |
| Budowanie Fullstack | `mvn clean package -P build-frontend -DskipTests` |
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
│   │       ├── db/                              ← Migracje Liquibase
│   │       └── static/                          ← Zbudowana aplikacja React (generowane)
│   └── test/java/...                            ← Klasy testów JUnit
├── frontend/
│   ├── src/
│   │   ├── api/client.ts                        ← Axios + interceptory JWT
│   │   ├── components/                          ← Komponenty prezentacyjne
│   │   ├── hooks/                               ← Hooki z logiką biznesową
│   │   ├── types/                               ← Typy domenowe TypeScript
│   │   └── i18n/                                ← Tłumaczenia PL / EN / DE
│   ├── package.json
│   └── vite.config.ts
├── jenkins/build.jenkinsfile                    ← Potok CI/CD
├── tool/
│   ├── backend_rules.md                         ← Standardy kodowania backendu
│   ├── frontend_rules.md                        ← Standardy kodowania frontendu
│   └── information/                             ← Materiały prezentacyjne
├── docker-compose.yml                           ← Serwis PostgreSQL 15
└── pom.xml
```
