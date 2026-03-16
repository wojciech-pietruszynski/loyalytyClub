# Backend Code Review Report (2026-03-03) - Store Promotions API

## Findings (ordered by severity)

1. High: Brak pe³nego API do zarz¹dzania promocjami punktowymi (tylko create/list)
   - Risk: panel administracyjny nie móg³ edytowaæ kampanii ani zmieniaæ statusu bez tworzenia nowych rekordów.
   - Fix applied:
     - `PUT /api/admin/store-promotions/{id}` (edycja promocji)
     - `PATCH /api/admin/store-promotions/{id}/status` (aktywacja/dezaktywacja)
     - walidacja biznesowa i kontrola scope kraju w serwisie.

2. Medium: Endpoint listuj¹cy promocje dostêpny bez ograniczenia do roli admin
   - Risk: dane konfiguracyjne panelu administracyjnego mog³y byæ dostêpne dla roli technicznej.
   - Fix applied:
     - dodano `@PreAuthorize("hasRole('ADMIN')")` dla `GET /api/admin/store-promotions`.

3. Medium: Niespójna semantyka i wydajnoœæ pobrania listy promocji
   - Risk: brak deterministycznego sortowania i pobieranie wszystkich rekordów do filtrowania w pamiêci.
   - Fix applied:
     - dodane metody repozytorium:
       - `findAllByOrderByStartsAtDesc()`
       - `findAllByCountryOrderByStartsAtDesc(String country)`
     - serwis u¿ywa teraz zapytañ sortowanych po stronie bazy.

## Standards Check (review_backend.md)

- `@Transactional(readOnly = true)` utrzymane dla operacji odczytu.
- mutacje oznaczone `@Transactional`.
- brak `@Data` w encjach JPA.
- `Optional.get()` nieu¿ywane; zastosowano `orElseThrow(...)`.
- obs³uga b³êdów API pozostaje oparta o `ProblemDetail`.

## Verification

- backend compile: `mvn -q -s .mvn-settings.xml -DskipTests compiler:compile` -> SUCCESS

## Residual risks

- brak testów integracyjnych dla CRUD promocji i walidacji okien czasowych.
