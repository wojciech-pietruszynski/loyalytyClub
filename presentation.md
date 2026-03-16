# Prezentacja Projektu: LoyaltyClub 💎

## 1. Zarys Biznesowy
**LoyaltyClub** to nowoczesny system lojalnościowy klasy Enterprise, zaprojektowany w celu budowania trwałych relacji z klientami poprzez mechanizmy grywalizacji i nagradzania zakupów.

### Cele Projektu:
- **Zwiększenie Retencji**: Zachęcanie klientów do powrotu poprzez system zbierania punktów.
- **Personalizacja Oferty**: Zarządzanie promocjami dedykowanymi dla konkretnych rynków (krajów).
- **Automatyzacja Nagród**: Prosty proces wymiany punktów na wymierne korzyści (kupony rabatowe).
- **Wsparcie Wielonarodowe**: Architektura przygotowana pod obsługę różnych krajów z zachowaniem separacji danych i lokalizacji (PL, EN, DE).

---

## 2. Funkcjonalności Systemu

### Panel Administracyjny:
- **Zarządzanie Klientami**: Dodawanie, edycja oraz wgląd w szczegółową historię aktywności klienta.
- **System Promocji**: Definiowanie czasowych mnożników punktowych (np. "Happy Hours") dla wybranych krajów.
- **Katalog Kuponów**: Tworzenie szablonów nagród z określonymi warunkami (minimalna kwota zakupu, wymagane punkty).
- **Analityka**: Wizualizacja historii zakupów i salda punktowego klienta w formie wykresów.
- **Zarządzanie Kontami Technicznymi**: Obsługa użytkowników API dla systemów zewnętrznych (np. kas POS).
- **Narzędzia**: Masowy import bazy klientów z plików CSV.

### Silnik Lojalnościowy (API):
- **Naliczanie Punktów**: Automatyczne obliczanie punktów na podstawie kwoty transakcji i aktywnych promocji.
- **Mechanizm Oczekiwania (Pending)**: Punkty stają się dostępne po określonym czasie (np. 30 dni - okres na zwrot towaru).
- **Obsługa Zwrotów**: Automatyczne korygowanie salda punktowego przy zwrotach produktów.
- **Weryfikacja Kuponów**: System zapobiegający użyciu przeterminowanych lub nieaktywnych nagród.

---

## 3. Stos Technologiczny

### Backend: Solidna Architektura
- **Java 21**: Wykorzystanie najnowszych funkcji języka dla wysokiej wydajności.
- **Spring Boot 3.2**: Fundament aplikacji zapewniający bezpieczeństwo i skalowalność.
- **Spring Security + JWT**: Bezpieczna komunikacja między frontendem a backendem.
- **PostgreSQL 15**: Niezawodna relacyjna baza danych.
- **Maven**: Zarządzanie cyklem życia projektu i zależnościami.

### Frontend: Nowoczesny Interfejs
- **React 18 + TypeScript**: Silnie typowany kod zapewniający wysoką jakość i łatwość utrzymania.
- **Vite**: Ultra-szybkie narzędzie do budowania aplikacji.
- **Ant Design (antd)**: Profesjonalna biblioteka komponentów UI.
- **Lucide React**: Lekki i estetyczny zestaw ikon.
- **i18next**: Pełne wsparcie dla wielojęzyczności (PL, EN, DE).

### Infrastruktura i Narzędzia:
- **Docker**: Konteneryzacja bazy danych dla powtarzalności środowiska.
- **Frontend Maven Plugin**: Integracja budowy frontendu z procesem CI/CD backendu.

---

## 4. Architektura i Bezpieczeństwo
- **Separacja Krajowa**: System ról pozwala administratorom zarządzać tylko klientami ze swojego regionu.
- **Dwuetapowe API**: 
  - `Admin API`: Zarządzanie systemem (JWT).
  - `Store API`: Szybka rejestracja transakcji z systemów POS (Basic Auth / JWT).
- **Integracja**: Backend serwuje frontend jako zasoby statyczne, co upraszcza wdrożenie (jeden plik JAR).

---

## 5. Podsumowanie Techniczne dla Wykładowców
Projekt demonstruje zastosowanie:
1. **Wzorców projektowych** w ekosystemie Spring (Service Layer, DTO, Repository).
2. **Reaktywnego interfejsu** użytkownika z obsługą stanu i efektów (React Hooks).
3. **Pełnego cyklu automatyzacji** (od builda frontendu po pakowanie aplikacji Java).
4. **Logiki biznesowej czasu rzeczywistego** (dynamiczne naliczanie punktów, walidacja promocji).
