# Analiza technologii frontend

## Biblioteki runtime (używane przez aplikację)

- **react** (`^19.2.0`) - główny framework UI; używany m.in. w `src/main.tsx` i `src/App.tsx`.
- **react-dom** (`^19.2.0`) - renderowanie React do DOM (`createRoot` w `src/main.tsx`).
- **axios** (`^1.13.5`) - komunikacja HTTP z backendem (`src/api/client.ts`).
- **lucide-react** (`^0.575.0`) - zestaw ikon używanych w interfejsie (`src/App.tsx`).

## Biblioteki developerskie (build/tooling/lint/typy)

- **vite** (`^7.3.1`) - bundler i dev server.
- **@vitejs/plugin-react** (`^5.1.1`) - obsługa React w Vite.
- **typescript** (`~5.9.3`) - statyczne typowanie.
- **eslint** (`^9.39.1`) - linting kodu.
- **@eslint/js** (`^9.39.1`) - bazowe reguły ESLint dla JS.
- **typescript-eslint** (`^8.48.0`) - reguły ESLint dla TypeScript.
- **eslint-plugin-react-hooks** (`^7.0.1`) - reguły dla hooków React.
- **eslint-plugin-react-refresh** (`^0.4.24`) - wsparcie reguł dla React Fast Refresh.
- **globals** (`^16.5.0`) - definicje globali środowiskowych dla ESLint.
- **@types/react** (`^19.2.7`) - typy TypeScript dla React.
- **@types/react-dom** (`^19.2.3`) - typy TypeScript dla React DOM.
- **@types/node** (`^24.10.1`) - typy TypeScript dla Node.js (konfiguracja narzędziowa).

## Dodatkowe obserwacje

- Projekt nie używa frameworka CSS (np. Tailwind, MUI, Bootstrap) - styling oparty o własne pliki `App.css` i `index.css`.
- Lokalizacja (i18n) jest zaimplementowana własnym modułem (`src/i18n/*`), bez zewnętrznej biblioteki typu `i18next`.
