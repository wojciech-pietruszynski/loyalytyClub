# Expert Java 21 & Spring Boot Review Guidelines

Ten dokument definiuje standard review dla projektow opartych o Java 21, Spring Boot 3.x i Lombok.
Cel: wysoka jakosc kodu, stabilnosc i przewidywalne API.

---

## 1. Java 21

### Rekomendacje
- Uzywaj `switch` expressions i pattern matching tam, gdzie poprawia to czytelnosc.
- Uzywaj `SequencedCollection` API (`getFirst`, `getLast`) zamiast operowania na indeksach, jesli kolekcja to wspiera.
- Dla workload I/O sprawdzaj konfiguracje virtual threads: `spring.threads.virtual.enabled=true`.
- Wybieraj `record` dla prostych DTO i response modeli.

### Uwagi
- Nie wymagamy funkcji preview jezyka. Przypisz kod do stabilnych funkcji Java 21.

---

## 2. Lombok

### Zakaz `@Data` w encjach JPA
- `@Data` generuje `equals/hashCode` po wszystkich polach i moze niekontrolowanie dotykac relacji LAZY.
- Dla encji: `@Getter`, `@Setter`, oraz swiadome `equals/hashCode` (na kluczu biznesowym lub ID).

### Rekomendacje
- DTO: preferuj `record`.
- Wstrzykiwanie zaleznosci: `@RequiredArgsConstructor`.
- Jesli uzywasz `@Builder` i pola domyslne, dodaj `@Builder.Default`.

---

## 3. Spring Boot 3.x

### Serwis i transakcje
- Klasa serwisu: domyslnie `@Transactional(readOnly = true)`.
- Metody modyfikujace dane oznaczaj `@Transactional`.
- Nie trzymaj transakcji podczas dlugich wywolan do systemow zewnetrznych.

### API i walidacja
- Waliduj wejscie na DTO (`jakarta.validation.constraints`).
- Uzywaj `ProblemDetail` (RFC 7807) do spojnej obslugi bledow.
- Nie rzucaj surowych, niesprecyzowanych wyjatkow tam, gdzie mozna zwrocic precyzyjny blad domenowy.

---

## 4. Persystencja (JPA / Hibernate)

- W review zawsze sprawdzaj N+1 (`EntityGraph`, `JOIN FETCH`).
- Dla list i wyszukiwarek filtruj/sortuj po stronie DB, nie w pamieci.
- Uzywaj projekcji, gdy endpoint potrzebuje tylko czesci kolumn.

---

## 5. Clean Code i testy

- Preferuj metody male i spójne odpowiedzialnoscia.
- Nie uzywaj `Optional.get()`, preferuj `orElseThrow(...)`.
- Nie uzywaj `e.printStackTrace()`; loguj przez logger.
- Testy integracyjne: preferowany Testcontainers na silniku zgodnym z produkcja.

---

## Checklist reviewera

1. [ ] Czy DTO moze byc `record`?
2. [ ] Czy odczyty maja `@Transactional(readOnly = true)`?
3. [ ] Czy mutacje maja `@Transactional`?
4. [ ] Czy encje JPA nie uzywaja `@Data`?
5. [ ] Czy nie ma `Optional.get()`?
6. [ ] Czy walidacja wejscia jest na DTO?
7. [ ] Czy API zwraca spojne bledy (`ProblemDetail`)?

---

## Raport review

Raport zapisuj jako:
- `review_report_YYYY-MM-DD_HH-mm.md`
- katalog: `ai_tool/backend/review/reports/`
