---
name: changelog-manager
description: >
  Zarządzaj zapisywaniem changelogów projektu. Używaj tej umiejętności zawsze gdy:
  użytkownik wspomina o zmianach w kodzie, prosi o zapisanie changeloga, opisuje
  co zostało zmodyfikowane w projekcie, lub kończy sesję pracy nad kodem.
  Triggeruj również gdy padają słowa: "zapisz zmiany", "changelog", "co zmieniłem",
  "podsumuj zmiany", "nowa wersja", "commit", "aktualizacja".
---

# Changelog Manager

Skill do automatycznego tworzenia i zarządzania plikami changelog w projekcie.

---

## Struktura katalogów projektu

Skill zakłada następującą strukturę katalogów, w których śledzone są zmiany:

```
projekt/
├── tool/changelog/          ← tutaj zapisywane są pliki changelog
├── backend/
├── frontend/
├── testy/
└── opis_biznesowy/
```

> Jeśli projekt ma inną strukturę, zapytaj użytkownika o potwierdzenie ścieżek przed zapisem.

---

## Krok 1 — Ustal numer sekwencji

Przed zapisaniem nowego pliku **zawsze** sprawdź zawartość katalogu `tool/changelog/`:

```bash
ls tool/changelog/
```

- Jeśli katalog jest **pusty lub nie istnieje** → numer sekwencji = `1`
- Jeśli istnieją poprzednie pliki → weź najwyższy numer i dodaj `1`
- Pliki mają format: `{numer}_{YYYY-MM-DD}_{HH-MM-SS}_changelog.md`

Przykłady poprawnych nazw:
```
1_2025-03-19_14-30-00_changelog.md
2_2025-03-19_17-45-12_changelog.md
```

> Użyj separatora `-` w czasie (nie `:`) dla kompatybilności z systemami plików.

---

## Krok 2 — Pobierz datę i czas

Użyj aktualnej daty i czasu w strefie czasowej projektu (domyślnie UTC jeśli nieznana).

Format:
- data: `YYYY-MM-DD`
- czas: `HH-MM-SS`

---

## Krok 3 — Utwórz plik changelog

Utwórz plik w `tool/changelog/` o nazwie zgodnej ze schematem z Kroku 1.

Wypełnij go według poniższego szablonu:

```markdown
# Changelog #{numer_sekwencji}

**Data:** {YYYY-MM-DD}  
**Czas:** {HH:MM:SS}  
**Autor:** {autor lub "nieznany" jeśli brak info}  
**Wersja:** {numer wersji jeśli podany, np. 1.2.3 lub "-" jeśli brak}

---

## Opis biznesowy

> Zmiany w katalogu `opis_biznesowy/`

<!-- Co zmieniło się z perspektywy użytkownika / produktu? -->
<!-- Jakie wymagania biznesowe zostały zrealizowane? -->

- brak zmian  ← usuń jeśli są zmiany

---

## Backend

> Zmiany w katalogu `backend/`

<!-- Jakie pliki, endpointy, modele, serwisy zostały zmienione? -->
<!-- Czy zmiany są breaking changes? -->

- brak zmian  ← usuń jeśli są zmiany

---

## Frontend

> Zmiany w katalogu `frontend/`

<!-- Jakie komponenty, widoki, style zostały zmienione? -->
<!-- Czy zmiany wpływają na UX? -->

- brak zmian  ← usuń jeśli są zmiany

---

## Testy

> Zmiany w katalogu `testy/`

<!-- Jakie testy zostały dodane, zmienione lub usunięte? -->
<!-- Czy pokrycie testami wzrosło / zmalało? -->

- brak zmian  ← usuń jeśli są zmiany

---

## Zależności i konfiguracja

<!-- Czy zmieniły się paczki, wersje bibliotek, zmienne środowiskowe, pliki konfiguracyjne? -->
<!-- Wpisz "brak zmian" jeśli nic się nie zmieniło -->

- brak zmian

---

## Migracje i bazy danych

<!-- Czy wymagane są migracje bazy danych? -->
<!-- Wpisz "brak" jeśli nie dotyczy -->

- brak

---

## Znane problemy / TODO

<!-- Czy po tej zmianie pozostały otwarte kwestie do rozwiązania? -->

- brak

---

## Powiązane tickety / PR

<!-- Numery zadań w Jira, GitHub Issues, PR itp. -->

- brak

```

---

## Krok 4 — Walidacja przed zapisem

Przed finałowym zapisem sprawdź:

- [ ] Numer sekwencji jest unikalny (nie nadpisuje istniejącego pliku)
- [ ] Data i czas są aktualne (nie skopiowane z poprzedniego changelog)
- [ ] Sekcje bez zmian mają wpis `- brak zmian` (nie są puste)
- [ ] Sekcje ze zmianami mają konkretne opisy — nie ogólniki jak "poprawki"
- [ ] Plik jest zapisany w `tool/changelog/`, nie gdzieś indziej

---

## Krok 5 — Potwierdź użytkownikowi

Po zapisaniu poinformuj użytkownika:

```
✅ Changelog zapisany: tool/changelog/{nazwa_pliku}
   Numer sekwencji: {n}
   Sekcje z zmianami: {lista sekcji które miały zmiany}
   Sekcje bez zmian: {lista sekcji które były puste}
```

---

## Zasady ogólne

**Zawartość opisów:**
- Każda zmiana opisana jako akcja: `Dodano`, `Usunięto`, `Zmieniono`, `Naprawiono`, `Zrefaktorowano`
- Opis powinien być zrozumiały dla osoby, która nie widziała kodu
- Jeśli zmiana jest breaking change, oznacz ją: `⚠️ BREAKING:`

**Priorytety gdy brak informacji:**
- Nie wymyślaj zmian — zapytaj użytkownika o szczegóły
- Jeśli użytkownik nie podał autora / wersji, wpisz `-`
- Jeśli nie wiesz do której sekcji należy zmiana, wpisz ją w najbardziej zbliżonej i dodaj komentarz

**Obsługa błędów:**
- Jeśli katalog `tool/changelog/` nie istnieje — utwórz go przed zapisem
- Jeśli nie możesz odczytać poprzednich plików — poinformuj użytkownika i zacznij od numeru 1 z adnotacją