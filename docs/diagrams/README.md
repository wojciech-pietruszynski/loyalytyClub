# Diagram klas -- loyalty-club (backend)

Diagram jest generowany ze zrodel `src/main/java` skryptem
[`scripts/generate-class-diagram.py`](../../scripts/generate-class-diagram.py),
a nie eksportowany recznie z IntelliJ -- dzieki temu da sie go odtworzyc po
kazdej zmianie w kodzie.

## Zawartosc katalogu

| Plik | Opis |
| --- | --- |
| `class-diagram-a4.pdf` | 15 stron A4 poziomo, jedna warstwa architektury na strone |
| `class-diagram-a3.pdf` | te same 15 stron w formacie A3 poziomo (czytelniejsze w druku) |
| `page-NN.svg` | rysunek pojedynczej strony (zrodlo wektorowe, skalowalne bez straty) |
| `page-NN.puml` | zrodlo PlantUML danej strony -- mozna edytowac recznie |
| `class-diagram-full.svg` | caly diagram (142 typy) na jednym arkuszu, 13576 x 2362 px -- do ogladania na ekranie |
| `manifest.json` | spis stron: tytul, lista klas, wymiary rysunku |

## Podzial na strony

Strony odpowiadaja warstwom architektury: model domenowy, repozytoria,
modul administracyjny, moduly kanalowe (coupon / ecom / store), kontrakty API
(DTO) oraz infrastruktura. Warstwa, ktorej rysunek nie miescil sie na arkuszu,
jest dzielona na czesci (`cz. 1 z 2` itd.) -- skrypt robi to automatycznie,
mierzac wymiary wyrenderowanego SVG i dobierajac podzial tak, by tekst
pozostal czytelny po wydruku.

Relacje wychodzace poza biezaca strone sa pokazane jako szare pudelka w ramce
`powiazania spoza tej strony`, dzieki czemu widac powiazania miedzy warstwami.

## Co pokazuje diagram

- klasy, interfejsy, rekordy i typy wyliczeniowe wraz z polami i metodami publicznymi,
- dziedziczenie i implementacje interfejsow (`<|--`),
- asocjacje wynikajace z typow pol, z licznoscia `1 --> *` dla kolekcji,
- kolor tla koduje rodzaj klasy (encja, DTO, repozytorium, kontroler, usluga,
  enum, infrastruktura) -- legenda jest na dole kazdej strony PDF.

## Regeneracja

```bash
python scripts/generate-class-diagram.py
```

Wymagania: `java` (skrypt uzywa `plantuml.jar` z lokalnego repozytorium Mavena)
oraz zainstalowany Chrome lub Edge (druk HTML do PDF w trybie headless).
Jesli brakuje `plantuml.jar`:

```bash
mvn dependency:get -Dartifact=net.sourceforge.plantuml:plantuml:1.2025.4
```
