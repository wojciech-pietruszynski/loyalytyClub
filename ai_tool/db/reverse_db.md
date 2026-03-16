# Instrukcja dla Agenta AI: Refaktoryzacja Liquibase YAML do SQL

**Cel:** Przekształcenie istniejącego pliku konfiguracyjnego Liquibase w formacie YAML na strukturę opartą o główny plik XML (Master) oraz odseparowane, czyste pliki SQL.

---

## 1. Dane wejściowe
Przeanalizuj dostarczony plik: `db.changelog-master.yaml`.
Zidentyfikuj wszystkie `changeSets`, ich autorów, ID oraz zawarte w nich operacje SQL lub deklaratywne zmiany (np. `createTable`).

## 2. Docelowa Struktura Katalogów
Wygeneruj i zorganizuj pliki w następujący sposób:

```text
src/main/resources/db/
├── changelog-master.xml      <-- Główny punkt wejścia
└── migrations/               <-- Logicznie podzielone pliki SQL
    ├── 001_schema_init.sql
    ├── 002_indexes.sql
    └── 003_constraints.sql