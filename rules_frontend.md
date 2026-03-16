# Frontend Rules

`App.tsx` ma być kontenerem orkiestracji, a nie miejscem na pełne widoki. Trzyma stan aplikacji, ładuje dane, spina routing zakładek i przekazuje jawnie typowane propsy do modułów domenowych.

## Struktura

- Każdą większą sekcję widoku wydzielaj do osobnego komponentu w `frontend/src/components`.
- Modale, formularze i panele z własnym renderingiem wydzielaj osobno, gdy tylko blok zaczyna zaciemniać plik nadrzędny.
- Typy współdzielone między komponentami trzymaj w `frontend/src/types/ui.ts` lub w typach domenowych, nie duplikuj ich lokalnie.
- Funkcje pomocnicze niezależne od renderu trzymaj poza komponentem lub w osobnym module util.

## React

- Używaj ścisłego TypeScriptu. Nie wprowadzaj `any`; dla błędów z API przyjmuj `unknown` i parsuj je jawnie.
- Dla callbacków używanych w efektach preferuj `useEffectEvent`, jeśli celem jest uniknięcie niestabilnych zależności.
- Nie dodawaj `useMemo` i `useCallback` bez konkretnego powodu mierzalnego w czytelności albo wydajności.
- Props komponentów opisuj dedykowanym typem lokalnym `...Props`.
- Komponent prezentacyjny nie powinien znać szczegółów pobierania danych, jeśli może dostać gotowy model i akcje z góry.

## UI i i18n

- Każdy tekst użytkownika musi przechodzić przez `t(...)`; nie zostawiaj twardo wpisanych etykiet w JSX.
- Przy wydzielaniu komponentów zachowuj istniejące klucze tłumaczeń albo aktualizuj wszystkie słowniki w tym samym change secie.
- Daty i liczby formatuj przez wspólne helpery zależne od aktywnego języka.
- Zachowuj istniejący język wizualny projektu; refaktor nie może zmieniać zachowania ani układu bez potrzeby biznesowej.

## Jakość zmian

- Refaktor wykonuj małymi krokami: najpierw wydzielenie renderu, potem porządkowanie typów, na końcu dalszy podział logiki.
- Po każdym większym wydzieleniu usuń martwe importy i nieużywany kod.
- Nowy komponent powinien mieć czytelny, płaski interfejs. Jeśli lista propsów staje się nieczytelna, rozważ wydzielenie kolejnego poziomu modułu.
- Nie mieszaj odpowiedzialności administracyjnych, kuponowych, klientowskich i narzędziowych w jednym komponencie.

## Weryfikacja

- Minimalny pakiet weryfikacji po zmianach frontowych: `npm run lint`, `npm test`, `npm run build`.
- Jeśli refaktor dotyka tłumaczeń, dat, sortowania albo formularzy, sprawdź też ręcznie przepływ użytkownika w odpowiadającej sekcji.
- W odpowiedzi końcowej podawaj realny wynik weryfikacji i otwarte ryzyka, jeśli coś nie zostało sprawdzone.
