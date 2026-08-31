# LoyaltyClub — Loyalty Program System (Backend)

The **backend** of an enterprise loyalty program, built with Java 25 + Spring Boot 3.5. It exposes the REST API for customer management, loyalty point accrual via POS transactions, coupon issuance and redemption, and a multi-role admin API with country-scoped access control.

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
12. [Java 25 Migration](#java-25-migration)

---

## Tech Stack

### Backend
| Technology | Version | Role |
|------------|---------|------|
| Java | 25 | Language level and runtime (class file version 69) |
| Spring Boot | 3.5.16 | Web + Data JPA framework |
| Spring Framework / Spring Security | 6.2.x / 6.5.x | Managed by Spring Boot |
| Hibernate ORM | 6.6.x | JPA provider, managed by Spring Boot |
| PostgreSQL | 15 (server) / 42.7.x (driver) | Relational database |
| Liquibase | 4.31.x | Database migrations, managed by Spring Boot |
| springdoc-openapi | 2.8.17 | OpenAPI spec + Swagger UI |
| JJWT | 0.13.0 | JWT auth (HMAC-SHA512) |
| Lombok | 1.18.46 | Boilerplate reduction — pinned, see below |
| JUnit 5 / Mockito / Byte Buddy | 5.12.x / 5.23.0 / 1.18.12 | Tests — Mockito and Byte Buddy pinned, see below |
| JaCoCo | 0.8.15 | Code coverage |
| SonarQube / Sonar Maven | 5.7.0 | Static code analysis |
| Maven | 3.9+ | Build tool |

Three versions are pinned in `pom.xml` above what `spring-boot-starter-parent`
manages, because the managed ones do not understand Java 25 class files:

| Pinned | Why |
|--------|-----|
| `lombok.version` | Older Lombok silently stops generating code on a newer JDK; the failure looks like missing methods, not like a build problem. |
| `mockito.version` | Older Mockito cannot instrument classes on JDK 25 — every test with a mock fails with `Could not modify all classes`. |
| `byte-buddy.version` | Raised together with Mockito, otherwise Boot's dependency management pulls Mockito's transitive Byte Buddy back down. |

Two more build details that Java 25 forces:

- `maven-compiler-plugin` declares Lombok in `annotationProcessorPaths`. Since
  JDK 23 `javac` no longer runs annotation processors found on the class path.
- Surefire starts the Mockito agent with `-javaagent` (path resolved by
  `maven-dependency-plugin`) instead of letting Mockito attach it to the running
  JVM. Self-attach warns on JDK 25 and is slated to be disallowed.

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

### A. Fully containerized (recommended)

Only Docker is required — no JDK, no Maven.

```bash
./scripts/stack.sh up          # Linux / macOS
.\scripts\stack.ps1 up          # Windows
```

The script builds the image, starts PostgreSQL and the backend, waits until
the container reports `healthy` (Liquibase migrations included) and runs a
smoke test. The API is then available at **http://localhost:8089**,
Swagger UI at **/swagger-ui.html**.

Settings are read from `.env` (see `.env.example`): ports, database
credentials, `JWT_SECRET`. `./scripts/stack.sh down` stops the stack and keeps
the data; `destroy` also drops the database volume.

> The database port **5433** is published on the host for developer tools only.
> The backend reaches PostgreSQL over the container network as `db:5432`.

### B. Local run with a JDK

#### Prerequisites
- Docker Desktop
- JDK 25
- Maven 3.9+ (must itself run on JDK 25 — check with `mvn -v`)

#### 1. Start the database only
```bash
docker compose up -d db
```
This starts PostgreSQL 15 on port **5433** (container port 5432).
Credentials: `user / password`, database: `loyalty_db`.

#### 2. Build & run
```bash
mvn clean package
java -jar target/loyalty-club-0.0.1-SNAPSHOT.jar
```
The API will be available at **http://localhost:8089**

### Admin panel
Clone the frontend repository and follow its README. In the containerized
setup its nginx forwards `/api` to this backend over the shared
`loyaltyclub-net` network; in local development its Vite dev server proxies
`/api` to `http://localhost:8089` out of the box.

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
| `security.jwt.expiration-ms` | `900000` | JWT expiry (15 min) |
| `security.jwt.secret` | *(base64 key)* | HMAC-SHA512 secret — override via `JWT_SECRET` env var in production |
| `app.available-country-codes` | `PL,DE,CZ,SK,LT` | Countries enabled for multi-tenancy |
| `app.store.default-points-per-currency` | `1.00` | Points earned per currency unit |
| `springdoc.api-docs.version` | `openapi_3_0` | Emitted OpenAPI version. springdoc 2.7+ defaults to 3.1; pinned to 3.0 so generated client SDKs keep reading the same contract. Remove the line to emit 3.1 |
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
29 test classes / 297 tests covering controllers, services, security filters, and exception handling.

```bash
mvn test
```

| Layer | Framework |
|-------|-----------|
| Service unit tests | JUnit 5 + Mockito + AssertJ |
| Controller tests | `@WebMvcTest` + MockMvc (Security auto-config excluded) |
| Bean mocking | `@MockitoBean` — Boot's `@MockBean` is deprecated for removal |
| Coverage report | JaCoCo XML → SonarQube |

Coverage target: **≥ 90%** line coverage for service and controller classes.
Excluded from coverage: `**/config/**`, `LoyaltyClubApplication.java`.

Frontend tests live in the frontend repository.

---

## CI/CD Pipeline

Everything — building, testing, analysis and running — happens inside Docker.
The only requirement on the agent (or on a developer machine) is a working
Docker daemon with BuildKit. No JDK 25, no Maven, no local `~/.m2`.

### Container images

`Dockerfile` is multi-stage; every stage is a separate build target:

| Target | Purpose |
|--------|---------|
| `deps` | Resolves Maven dependencies; own layer, so a source change does not re-download them |
| `test` | `mvn test` — unit tests and the JaCoCo report |
| `test-reports` | Exports `surefire-reports` and `jacoco` out of the image (`--output`) |
| `sonar` | `mvn sonar:sonar` reusing the `test` filesystem, so tests are not run twice |
| `build` | `mvn package` — the executable JAR |
| `runtime` | `eclipse-temurin:25-jre-alpine`, non-root user, `HEALTHCHECK` on `/actuator/health` |

The Sonar token is passed as a BuildKit secret (`--secret`), not a build
argument — a build argument would stay visible in the image history.

### Local commands

`scripts/stack.sh` (Linux/CI) and `scripts/stack.ps1` (Windows) wrap the whole
flow:

| Command | Effect |
|---------|--------|
| `build` | Builds the runtime image |
| `test` | Runs the tests in a container; reports land in `target/docker-reports` |
| `sonar` | SonarQube analysis (requires `SONAR_TOKEN` in the environment) |
| `up` | Builds and starts the stack, waits for `healthy`, runs a smoke test |
| `down` / `destroy` | Stops the stack (`destroy` also drops the database volume) |
| `logs` / `ps` / `smoke` | Diagnostics of a running deployment |

### Jenkins

Declarative pipeline at `jenkins/build.jenkinsfile`:

| Stage | Description |
|-------|-------------|
| Checkout | Clone repository |
| Unit Tests | `scripts/stack.sh test`, publishes JUnit XML and the coverage report |
| SonarQube Analysis | `scripts/stack.sh sonar` with Jenkins credential `loyalty-club` |
| Image Build | `docker build --target runtime`, tagged with the build number and `latest` |
| Deploy | `docker compose up -d --no-build` |
| Smoke Test | Waits for `healthy` and probes the API from inside the container network |

Rollback is the deploy stage re-run with an older tag:
`IMAGE_TAG=<build number> docker compose up -d --no-build`.

SonarQube: `http://192.168.100.150:9000`, project key `loyalty-club`.

The previous pipeline, which deployed a bare JAR onto the agent, is kept at
`jenkins/build-legacy-jar.jenkinsfile` for reference.

### GitHub Actions

`.github/workflows/ci.yml` is the quality gate for `master` and pull requests:
it runs the tests through the `test-reports` target, builds the runtime image,
starts the full stack with Compose, and verifies that the container reports
`healthy` and that the Liquibase migrations apply to an empty database.

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
├── jenkins/
│   ├── build.jenkinsfile                        ← CI/CD pipeline (Docker)
│   └── build-legacy-jar.jenkinsfile             ← Previous JAR-on-agent pipeline
├── .github/workflows/ci.yml                     ← Quality gate (GitHub Actions)
├── scripts/
│   ├── stack.sh                                 ← build / test / sonar / up / down
│   └── stack.ps1                                ← same, for Windows
├── tool/
│   ├── backend_rules.md                         ← Backend coding standards
│   └── information/                             ← Presentation materials
├── Dockerfile                                   ← Multi-stage: deps/test/sonar/build/runtime
├── .dockerignore
├── docker-compose.yml                           ← PostgreSQL 15 + backend, network loyaltyclub-net
├── .env.example                                 ← Deployment settings template
└── pom.xml
```

The React admin panel is maintained in its own repository (`loyaltyClub-frontend`), together with its coding standards (`docs/frontend_rules.md`).


---

## Java 25 Migration

The project moved from Java 21 / Spring Boot 3.2.3 to **Java 25 / Spring Boot 3.5.16**.
Compiled artefacts are class file version 69, so **the whole toolchain — Maven
included — has to run on JDK 25**.

### What changed

| Area | Before | After |
|------|--------|-------|
| Language level and runtime | 21 | 25 |
| Spring Boot | 3.2.3 | 3.5.16 |
| springdoc-openapi | 2.3.0 | 2.8.17 |
| JJWT | 0.12.6 | 0.13.0 (single `jjwt.version` property) |
| Lombok | 1.18.42 | 1.18.46 |
| Byte Buddy | 1.17.7 | 1.18.12 |
| Sonar Maven plugin | 4.0.0.4121 | 5.7.0.6970 |
| Mockito agent | self-attached, silenced with `-XX:+EnableDynamicAgentLoading` | started with `-javaagent` |
| Bean mocking in tests | `@MockBean` | `@MockitoBean` |
| `DaoAuthenticationProvider` | no-arg constructor + `setUserDetailsService` | constructor taking the `UserDetailsService` |

Boot-managed transitive versions moved with the parent: Spring Framework 6.2.x,
Spring Security 6.5.x, Hibernate ORM 6.6.x, Liquibase 4.31.x, PostgreSQL driver
42.7.x, JUnit 5.12.x.

### Deliberately not changed

- **Liquibase stays on the Boot-managed 4.x.** Jumping to 5.x is a major upgrade
  against an existing changelog history and is unrelated to Java 25.
- **PostgreSQL stays at server 15** in `docker-compose.yml`. Bumping the image
  would require a data migration of the existing volume.
- **The emitted OpenAPI version stays at 3.0.** See
  `springdoc.api-docs.version` in [Configuration](#configuration) — the contract
  feeds generated client SDKs, so the migration does not change it silently.
- **Spring Boot 4.x** is a separate migration: it brings Spring Framework 7 and
  Spring Security 7, removes `@MockBean` outright, and needs springdoc 3.x. The
  two deprecation fixes above already remove the blockers this codebase had.

### Verification

`mvn clean verify` builds with no compiler warnings and 297 passing tests. The
packaged JAR was started on JDK 25 against the docker-compose database:
Liquibase applied cleanly, `/v3/api-docs` returned all 59 paths and 46 schemas,
Swagger UI loaded, and an unauthenticated `/api/admin/**` call still returned
401.

---

---

# LoyaltyClub — System Programu Lojalnościowego (Backend)

**Backend** aplikacji enterprise loyalty program zbudowany w technologii Java 25 + Spring Boot 3.5. Udostępnia REST API do zarządzania klientami, naliczania punktów lojalnościowych przez transakcje kasowe, emisji i realizacji kuponów oraz wielorolowe API administracyjne z kontrolą dostępu ograniczoną do krajów.

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
12. [Migracja na Javę 25](#migracja-na-javę-25)

---

## Stos technologiczny

### Backend
| Technologia | Wersja | Rola |
|-------------|--------|------|
| Java | 25 | Poziom języka i środowisko uruchomieniowe (class file 69) |
| Spring Boot | 3.5.16 | Framework Web + Data JPA |
| Spring Framework / Spring Security | 6.2.x / 6.5.x | Wersje zarządzane przez Spring Boot |
| Hibernate ORM | 6.6.x | Dostawca JPA, wersja zarządzana przez Spring Boot |
| PostgreSQL | 15 (serwer) / 42.7.x (sterownik) | Relacyjna baza danych |
| Liquibase | 4.31.x | Migracje schematu, wersja zarządzana przez Spring Boot |
| springdoc-openapi | 2.8.17 | Specyfikacja OpenAPI + Swagger UI |
| JJWT | 0.13.0 | Autoryzacja JWT (HMAC-SHA512) |
| Lombok | 1.18.46 | Redukcja kodu szablonowego — wersja przypięta, patrz niżej |
| JUnit 5 / Mockito / Byte Buddy | 5.12.x / 5.23.0 / 1.18.12 | Testy — Mockito i Byte Buddy przypięte, patrz niżej |
| JaCoCo | 0.8.15 | Pokrycie kodu testami |
| SonarQube / Sonar Maven | 5.7.0 | Statyczna analiza kodu |
| Maven | 3.9+ | Narzędzie budowania |

Trzy wersje są przypięte w `pom.xml` ponad to, czym zarządza
`spring-boot-starter-parent`, bo wersje zarządzane nie znają formatu plików
klas Javy 25:

| Przypięte | Dlaczego |
|-----------|----------|
| `lombok.version` | Starszy Lombok na nowszym JDK cicho przestaje generować kod; błędy wyglądają jak brakujące metody, a nie jak problem z budowaniem. |
| `mockito.version` | Starsze Mockito nie potrafi instrumentować klas na JDK 25 — wszystkie testy z mockami wywalają się na `Could not modify all classes`. |
| `byte-buddy.version` | Podnoszone razem z Mockito, inaczej zarządzanie zależnościami Boota cofa przechodnią wersję Byte Buddy z Mockito. |

Dwa dodatkowe elementy budowania wymuszone przez Javę 25:

- `maven-compiler-plugin` podaje Lombok w `annotationProcessorPaths`. Od JDK 23
  `javac` nie uruchamia już procesorów adnotacji znalezionych na ścieżce klas.
- Surefire startuje agenta Mockito przez `-javaagent` (ścieżkę wystawia
  `maven-dependency-plugin`), zamiast pozwalać Mockito doładować go do
  działającej JVM. Samodoładowanie na JDK 25 wypisuje ostrzeżenie i ma zostać
  zabronione.

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

### A. W całości w kontenerach (zalecane)

Potrzebny jest wyłącznie Docker — bez JDK i bez Mavena.

```bash
./scripts/stack.sh up          # Linux / macOS
.\scripts\stack.ps1 up          # Windows
```

Skrypt buduje obraz, podnosi PostgreSQL i backend, czeka aż kontener zgłosi
`healthy` (czyli także po migracjach Liquibase) i wykonuje test dymny. API jest
wtedy dostępne pod **http://localhost:8089**, Swagger UI pod **/swagger-ui.html**.

Ustawienia czytane są z pliku `.env` (wzorzec: `.env.example`): porty, dane
dostępowe do bazy, `JWT_SECRET`. `./scripts/stack.sh down` zatrzymuje stos
i zostawia dane; `destroy` usuwa też wolumen bazy.

> Port bazy **5433** jest wystawiony na hosta wyłącznie dla narzędzi
> deweloperskich. Backend sięga do PostgreSQL po sieci kontenerów, jako `db:5432`.

### B. Uruchomienie lokalne z JDK

#### Wymagania
- Docker Desktop
- JDK 25
- Maven 3.9+ (sam musi działać na JDK 25 — sprawdź `mvn -v`)

#### 1. Uruchomienie samej bazy
```bash
docker compose up -d db
```
Startuje PostgreSQL 15 na porcie **5433** (port kontenera 5432).
Dane dostępowe: `user / password`, baza: `loyalty_db`.

#### 2. Budowanie i uruchomienie
```bash
mvn clean package
java -jar target/loyalty-club-0.0.1-SNAPSHOT.jar
```
API będzie dostępne pod **http://localhost:8089**

### Panel administracyjny
Sklonuj repozytorium frontendu i postępuj zgodnie z jego README. We wdrożeniu
kontenerowym jego nginx przekazuje `/api` do tego backendu po współdzielonej
sieci `loyaltyclub-net`; przy pracy lokalnej serwer deweloperski Vite
przekierowuje `/api` na `http://localhost:8089` bez dodatkowej konfiguracji.

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
| `security.jwt.expiration-ms` | `900000` | Czas życia JWT (15 min) |
| `security.jwt.secret` | *(klucz base64)* | Sekret HMAC-SHA512 — w produkcji nadpisz zmienną env `JWT_SECRET` |
| `app.available-country-codes` | `PL,DE,CZ,SK,LT` | Kraje włączone w multi-tenancy |
| `app.store.default-points-per-currency` | `1.00` | Punkty naliczane za jednostkę waluty |
| `springdoc.api-docs.version` | `openapi_3_0` | Wersja generowanej specyfikacji OpenAPI. springdoc od 2.7 domyślnie generuje 3.1; przypięte do 3.0, żeby generowane biblioteki klienckie czytały ten sam kontrakt co dotąd. Usunięcie linii przełącza na 3.1 |
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
29 klas testowych / 297 testów obejmujących kontrolery, serwisy, filtry bezpieczeństwa i obsługę wyjątków.

```bash
mvn test
```

| Warstwa | Framework |
|---------|-----------|
| Testy jednostkowe serwisów | JUnit 5 + Mockito + AssertJ |
| Testy kontrolerów | `@WebMvcTest` + MockMvc (wyłączona autokonfiguracja Security) |
| Podmiana beanów na mocki | `@MockitoBean` — `@MockBean` z Boota jest oznaczone jako do usunięcia |
| Raport pokrycia | JaCoCo XML → SonarQube |

Docelowe pokrycie: **≥ 90%** linii kodu dla klas serwisów i kontrolerów.
Wykluczone z pokrycia: `**/config/**`, `LoyaltyClubApplication.java`.

Testy frontendu znajdują się w repozytorium frontendu.

---

## Potok CI/CD

Całość — budowanie, testy, analiza i uruchomienie — dzieje się w Dockerze.
Jedyne, czego wymaga agent (albo maszyna dewelopera), to działający demon
Dockera z BuildKitem. Bez JDK 25, bez Mavena, bez lokalnego `~/.m2`.

### Obrazy kontenerów

`Dockerfile` jest wieloetapowy; każdy etap to osobny cel budowania:

| Cel | Przeznaczenie |
|-----|---------------|
| `deps` | Pobranie zależności Mavena; osobna warstwa, więc zmiana kodu nie pobiera ich ponownie |
| `test` | `mvn test` — testy jednostkowe i raport JaCoCo |
| `test-reports` | Wystawienie `surefire-reports` i `jacoco` poza obraz (`--output`) |
| `sonar` | `mvn sonar:sonar` na systemie plików etapu `test`, więc testy nie lecą drugi raz |
| `build` | `mvn package` — wykonywalny JAR |
| `runtime` | `eclipse-temurin:25-jre-alpine`, użytkownik bez roota, `HEALTHCHECK` na `/actuator/health` |

Token Sonara idzie jako sekret BuildKita (`--secret`), a nie argument
budowania — argument zostałby widoczny w historii obrazu.

### Polecenia lokalne

`scripts/stack.sh` (Linux/CI) oraz `scripts/stack.ps1` (Windows) obsługują
cały przepływ:

| Polecenie | Efekt |
|-----------|-------|
| `build` | Zbudowanie obrazu wykonawczego |
| `test` | Testy w kontenerze; raporty trafiają do `target/docker-reports` |
| `sonar` | Analiza SonarQube (wymaga `SONAR_TOKEN` w środowisku) |
| `up` | Zbudowanie i uruchomienie stosu, czekanie na `healthy`, test dymny |
| `down` / `destroy` | Zatrzymanie stosu (`destroy` usuwa też wolumen bazy) |
| `logs` / `ps` / `smoke` | Diagnostyka działającego wdrożenia |

### Jenkins

Deklaratywny potok w pliku `jenkins/build.jenkinsfile`:

| Etap | Opis |
|------|------|
| Pobranie kodu | Klonowanie repozytorium |
| Testy jednostkowe | `scripts/stack.sh test`, publikacja JUnit i raportu pokrycia |
| Analiza SonarQube | `scripts/stack.sh sonar` z credentialem Jenkinsa `loyalty-club` |
| Budowanie obrazu | `docker build --target runtime`, znaczniki: numer budowania i `latest` |
| Wdrożenie | `docker compose up -d --no-build` |
| Test dymny | Czekanie na `healthy` i odpytanie API z wnętrza sieci kontenerów |

Wycofanie zmiany to powtórzenie etapu wdrożenia ze starszym znacznikiem:
`IMAGE_TAG=<numer budowania> docker compose up -d --no-build`.

SonarQube: `http://192.168.100.150:9000`, klucz projektu `loyalty-club`.

Poprzedni potok, który wdrażał goły JAR na agenta, został zachowany
w `jenkins/build-legacy-jar.jenkinsfile`.

### GitHub Actions

`.github/workflows/ci.yml` to bramka jakości dla `master` i zadań scalenia:
uruchamia testy celem `test-reports`, buduje obraz wykonawczy, podnosi pełny
stos przez Compose i sprawdza, czy kontener zgłasza `healthy`, a migracje
Liquibase przechodzą na pustej bazie.

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
├── jenkins/
│   ├── build.jenkinsfile                        ← Potok CI/CD (Docker)
│   └── build-legacy-jar.jenkinsfile             ← Poprzedni potok wdrażający JAR na agenta
├── .github/workflows/ci.yml                     ← Bramka jakości (GitHub Actions)
├── scripts/
│   ├── stack.sh                                 ← build / test / sonar / up / down
│   └── stack.ps1                                ← to samo, dla Windowsa
├── tool/
│   ├── backend_rules.md                         ← Standardy kodowania backendu
│   └── information/                             ← Materiały prezentacyjne
├── Dockerfile                                   ← Wieloetapowy: deps/test/sonar/build/runtime
├── .dockerignore
├── docker-compose.yml                           ← PostgreSQL 15 + backend, sieć loyaltyclub-net
├── .env.example                                 ← Wzorzec ustawień wdrożenia
└── pom.xml
```

Panel administracyjny w React rozwijany jest w osobnym repozytorium (`loyaltyClub-frontend`) wraz ze swoimi standardami kodowania (`docs/frontend_rules.md`).

---

## Migracja na Javę 25

Projekt przeszedł z Javy 21 / Spring Boota 3.2.3 na **Javę 25 / Spring Boota 3.5.16**.
Artefakty kompilują się do formatu class file 69, więc **cały zestaw narzędzi —
łącznie z Mavenem — musi działać na JDK 25**.

### Co się zmieniło

| Obszar | Przed | Po |
|--------|-------|-----|
| Poziom języka i środowisko | 21 | 25 |
| Spring Boot | 3.2.3 | 3.5.16 |
| springdoc-openapi | 2.3.0 | 2.8.17 |
| JJWT | 0.12.6 | 0.13.0 (jedna właściwość `jjwt.version`) |
| Lombok | 1.18.42 | 1.18.46 |
| Byte Buddy | 1.17.7 | 1.18.12 |
| Wtyczka Sonar Maven | 4.0.0.4121 | 5.7.0.6970 |
| Agent Mockito | doładowywany w locie, wyciszony przez `-XX:+EnableDynamicAgentLoading` | startowany przez `-javaagent` |
| Podmiana beanów w testach | `@MockBean` | `@MockitoBean` |
| `DaoAuthenticationProvider` | konstruktor bezargumentowy + `setUserDetailsService` | konstruktor przyjmujący `UserDetailsService` |

Wersje przechodnie zarządzane przez Boota poszły w górę razem z rodzicem:
Spring Framework 6.2.x, Spring Security 6.5.x, Hibernate ORM 6.6.x,
Liquibase 4.31.x, sterownik PostgreSQL 42.7.x, JUnit 5.12.x.

### Świadomie niezmienione

- **Liquibase zostaje na zarządzanej przez Boota wersji 4.x.** Przejście na 5.x
  to zmiana major na istniejącej historii changelogów i nie ma związku z Javą 25.
- **PostgreSQL zostaje na serwerze 15** w `docker-compose.yml`. Podniesienie
  obrazu wymagałoby migracji danych z istniejącego wolumenu.
- **Generowana specyfikacja OpenAPI zostaje w wersji 3.0.** Patrz
  `springdoc.api-docs.version` w [Konfiguracji](#konfiguracja) — kontrakt jest
  źródłem generowanych bibliotek klienckich, więc migracja nie zmienia go po cichu.
- **Spring Boot 4.x** to osobna migracja: przynosi Spring Framework 7
  i Spring Security 7, całkiem usuwa `@MockBean` i wymaga springdoc 3.x. Dwie
  powyższe poprawki deprecacji zdejmują blokady, które miał ten kod.

### Weryfikacja

`mvn clean verify` buduje się bez ostrzeżeń kompilatora i przechodzi 297 testów.
Spakowany JAR został uruchomiony na JDK 25 na bazie z docker-compose:
Liquibase zaaplikował zmiany poprawnie, `/v3/api-docs` zwróciło wszystkie
59 ścieżek i 46 schematów, Swagger UI się załadował, a niezalogowane wywołanie
`/api/admin/**` nadal zwróciło 401.
