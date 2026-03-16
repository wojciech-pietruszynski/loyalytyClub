# LoyaltyClub 💎 - System Programu Lojalnościowego

Kompleksowa aplikacja klasy **Loyalty Program**, zbudowana w nowoczesnym stosie technologicznym. Pozwala na zarządzanie bazą klientów, naliczanie punktów lojalnościowych oraz ich wymianę na zdefiniowane nagrody.

## 🛠 Stos Technologiczny

### Backend:
- **Java 21 (Amazon Corretto)**
- **Spring Boot 3.2** (Web, Data JPA)
- **PostgreSQL 15** (Baza danych)
- **Lombok** (Generowanie kodu)
- **Maven** (Zarządzanie buildem)

### Frontend:
- **React 18** + **TypeScript**
- **Vite** (Build tool)
- **Lucide React** (Ikony)
- **Axios** (Klient HTTP)
- **Vanilla CSS** (Custom theme)

---

## 🚀 Jak uruchomić projekt?

Projekt jest w pełni zintegrowany. Backend serwuje Frontend jako zasoby statyczne.

### 1. Uruchomienie bazy danych (Docker)
Wymagany zainstalowany Docker Desktop. W katalogu głównym projektu wykonaj:
```bash
docker-compose up -d
```

### 2. Budowa całego projektu (Maven)
Polecenie to zainstaluje Node.js, pobierze zależności frontendu, uruchomi testy frontendu, zbuduje go, skopiuje pliki do backendu i skompiluje aplikację Java:
```bash
mvn -Pbuild-frontend clean test package
```

### 3. Osobne kroki build

Backend:
```bash
mvn -Pbuild-backend clean test package
```
Ten krok kompiluje i pakuje aplikację Spring Boot oraz uruchamia testy jednostkowe backendu.

Frontend:
```bash
mvn -Pbuild-frontend clean test package
```
Ten krok instaluje zależności frontendu, uruchamia testy jednostkowe frontendu, buduje aplikację frontendową i kopiuje wynik do zasobów statycznych backendu.
Wynik buildu trafia do `src/main/resources/static`, a następnie do `target/classes/static` na potrzeby pakowania artefaktu Spring Boot.

### 4. Uruchomienie aplikacji
Po zakończeniu buildu uruchom plik JAR:
```bash
java -jar target/loyalty-club-0.0.1-SNAPSHOT.jar
```

Aplikacja będzie dostępna pod adresem: **[http://localhost:8080](http://localhost:8080)**

---

## 📝 Funkcjonalności

- **Zarządzanie Klientami**: Dodawanie nowych osób do programu.
- **System Punktowy**: Manualne dodawanie punktów z opisem (np. za konkretny zakup).
- **Katalog Nagród**: Przeglądanie dostępnych profitów.
- **Redeem System**: Automatyczna weryfikacja salda i wymiana punktów na nagrody.
- **Historia Transakcji**: Każda zmiana salda jest zapisywana jako rekord w bazie danych.

---

## 📂 Struktura Projektu

- `/src/main/java/...` - Kod źródłowy Spring Boot (Moel, Controller, Service).
- `/src/main/resources/static` - (Generowane) Skompilowane pliki Frontendu używane przez backend.
- `/frontend` - Kod źródłowy React + TS.
- `/ai_tool` - Dokumentacja procesowa i changelog.

---

## 💡 Informacje dla dewelopera
Podczas developmentu frontendu można uruchomić serwer Vite niezależnie:
```bash
cd frontend
npm run dev
```
HMR (Hot Module Replacement) będzie działał, a zapytania do API będą przesyłane przez proxy na port 8080.
