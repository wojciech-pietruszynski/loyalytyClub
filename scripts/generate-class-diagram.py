# -*- coding: utf-8 -*-
"""
Generator diagramu klas backendu loyalty-club.

Czyta zrodla z src/main/java, buduje diagram klas w PlantUML (jedna strona na
warstwe architektury), renderuje strony do SVG i sklada je w wielostronicowe
PDF-y w formatach A4 i A3 (orientacja pozioma).

Uruchomienie:
    python scripts/generate-class-diagram.py

Wymagania:
    - java (do uruchomienia plantuml.jar; pobierany przez Mavena, patrz PLANTUML_JAR)
    - Google Chrome (druk HTML -> PDF w trybie headless)

Wynik trafia do docs/diagrams/ (zrodla .puml, rysunki .svg, gotowe .pdf).
"""
import io
import json
import os
import re
import shutil
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "src", "main", "java")
OUT = os.path.join(ROOT, "docs", "diagrams")

PLANTUML_VERSION = "1.2025.4"
PLANTUML_JAR = os.path.join(
    os.path.expanduser("~"), ".m2", "repository", "net", "sourceforge",
    "plantuml", "plantuml", PLANTUML_VERSION, "plantuml-%s.jar" % PLANTUML_VERSION)

CHROME_CANDIDATES = [
    r"C:\Program Files\Google\Chrome\Application\chrome.exe",
    r"C:\Program Files (x86)\Google\Chrome\Application\chrome.exe",
    r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe",
    r"C:\Program Files\Microsoft\Edge\Application\msedge.exe",
]

# Maksymalny rozmiar rysunku strony w pikselach SVG. Dobrany tak, aby po
# wpasowaniu w arkusz A4 poziomo tekst klas mial ok. 1.2 mm wysokosci.
MAX_W, MAX_H = 2400, 1660

PKG_PREFIX = "pl.pietruszynski.loyaltyclub."

# --------------------------------------------------------------------------
# 1. Parsowanie zrodel
# --------------------------------------------------------------------------

TYPE_RE = re.compile(
    r'^\s*(?:public\s+|protected\s+)?(?:static\s+)?'
    r'(?:final\s+|abstract\s+|sealed\s+|non-sealed\s+)*'
    r'(class|interface|enum|record)\s+(\w+)\s*(\([^)]*\))?\s*([^{]*)\{', re.M)
FIELD_RE = re.compile(
    r'^\s{0,8}(private|protected|public)\s+(?:static\s+)?(?:final\s+)?'
    r'([\w.]+(?:\s*<[^;=()]*>)?(?:\[\])?)\s+(\w+)\s*(?:=[^;]*)?;', re.M)
METHOD_RE = re.compile(
    r'^\s{0,8}(?:public|protected)\s+(?:static\s+)?(?:final\s+)?(?:<[^>]+>\s+)?'
    r'([\w.]+(?:\s*<[^()]*?>)?(?:\[\])?)\s+(\w+)\s*\(', re.M)
NOT_A_METHOD = {"if", "for", "while", "switch", "return", "new", "catch"}


def strip_noise(text):
    """Usuwa komentarze i adnotacje, ktore zaklocaja proste wyrazenia regularne."""
    text = re.sub(r'/\*.*?\*/', '', text, flags=re.S)
    text = re.sub(r'//[^\n]*', '', text)
    text = re.sub(r'^\s*@\w+(\s*\([^\n]*\))?\s*$', '', text, flags=re.M)
    text = re.sub(r'@\w+(\([^()]*(\([^()]*\))?[^()]*\))?\s*', '', text)
    return text


def body_of(text, start):
    """Zwraca cialo typu od pozycji start, dopasowujac nawiasy klamrowe."""
    depth = 1
    for i, ch in enumerate(text[start:]):
        if ch == '{':
            depth += 1
        elif ch == '}':
            depth -= 1
            if depth == 0:
                return text[start:start + i]
    return text[start:]


def parse_sources():
    types = {}
    for dirpath, _, files in os.walk(SRC):
        for name in files:
            if not name.endswith(".java"):
                continue
            raw = io.open(os.path.join(dirpath, name), encoding="utf-8").read()
            pkg_match = re.search(r'^package\s+([\w.]+);', raw, re.M)
            pkg = pkg_match.group(1) if pkg_match else ""
            text = strip_noise(raw)
            decl = TYPE_RE.search(text)
            if not decl:
                continue
            kind, type_name, record_comps, tail = decl.groups()
            body = body_of(text, decl.end())

            extends = re.search(r'\bextends\s+([\w.]+(?:<[^{]*?>)?)', tail)
            implements = re.search(r'\bimplements\s+([^{]+)', tail)
            supers = ([extends.group(1)] if extends else []) + \
                     ([s.strip() for s in implements.group(1).split(',')] if implements else [])

            fields, constants, methods = [], [], []
            if kind == "enum":
                for const in body.split(';')[0].split(','):
                    const = const.strip().split('(')[0].strip()
                    if re.match(r'^\w+$', const or ""):
                        constants.append(const)
            if kind == "record" and record_comps:
                for comp in re.split(r',(?![^<>]*>)', record_comps[1:-1]):
                    comp = comp.strip()
                    if ' ' in comp:
                        ctype, cname = comp.rsplit(' ', 1)
                        fields.append((cname, re.sub(r'\s+', '', ctype)))
            for f in FIELD_RE.finditer(body):
                fields.append((f.group(3), re.sub(r'\s+', '', f.group(2))))
            for m in METHOD_RE.finditer(body):
                if m.group(2) not in NOT_A_METHOD:
                    methods.append((m.group(2), re.sub(r'\s+', '', m.group(1))))

            short_pkg = pkg[len(PKG_PREFIX):] if pkg.startswith(PKG_PREFIX) else ""
            types[type_name] = dict(pkg=short_pkg, kind=kind, supers=supers,
                                    fields=fields, consts=constants, methods=methods)
    return types


# --------------------------------------------------------------------------
# 2. Podzial na strony i relacje
# --------------------------------------------------------------------------

LAYERS = [
    ("Model domenowy - encje JPA i typy wyliczeniowe",
     lambda p: p.endswith("model") or p == "model"),
    ("Warstwa dostepu do danych - repozytoria i idempotencja",
     lambda p: p.endswith("repository") or p.startswith("idempotency")),
    ("Modul administracyjny - kontrolery, uslugi, audyt, bezpieczenstwo",
     lambda p: p.startswith("api.admin") and not p.endswith(("model", "repository", "dto"))),
    ("Moduly kanalowe - coupon, ecom, store",
     lambda p: p.startswith(("api.coupon", "api.ecom", "api.store"))
     and not p.endswith(("model", "repository", "dto"))),
    ("Kontrakty API - DTO modulu administracyjnego i wspolne",
     lambda p: p in ("api.admin.dto", "api.common.dto")),
    ("Kontrakty API - DTO modulow coupon, ecom, store",
     lambda p: p in ("api.coupon.dto", "api.ecom.dto", "api.store.dto")),
    ("Infrastruktura - konfiguracja, bezpieczenstwo, wyjatki, powiadomienia, narzedzia",
     lambda p: True),
]

COLOR = {"entity": "#FFF3C4", "dto": "#E3F2FD", "repo": "#E8F5E9", "ctrl": "#FFE0E6",
         "svc": "#EDE7F6", "cfg": "#ECEFF1", "enum": "#FFECB3", "record": "#E0F7FA",
         "other": "#FFFFFF", "ghost": "#F0F0F0"}


def stereotype(t, name):
    pkg, kind = t["pkg"], t["kind"]
    if kind == "enum":
        return "enum"
    if pkg.endswith("model") or pkg == "model":
        return "entity"
    if pkg.endswith("dto") or name.endswith(("Dto", "Request", "Response")):
        return "dto"
    if pkg.endswith("repository") or name.endswith("Repository"):
        return "repo"
    if pkg.endswith("controller") or name.endswith("Controller"):
        return "ctrl"
    if pkg.endswith("service") or name.endswith(("Service", "Aspect")):
        return "svc"
    if name.endswith(("Config", "Filter", "Seeder", "Handler", "Exception", "Properties")):
        return "cfg"
    if kind == "record":
        return "record"
    return "other"


def build_edges(types):
    """Dziedziczenie/implementacja oraz asocjacje wynikajace z typow pol."""
    known = set(types)
    edges = set()
    for name, t in types.items():
        for sup in t["supers"]:
            base = re.split(r'[<.]', sup)[0]
            if base in known:
                edges.add((base, name, "inherit"))
        for _, ftype in t["fields"]:
            multi = any(token in ftype for token in ("List<", "Set<", "Collection<", "[]"))
            for target in re.findall(r'\w+', ftype):
                if target in known and target != name:
                    edges.add((name, target, "many" if multi else "one"))
    return sorted(edges)


HEAD = """@startuml
!pragma layout smetana
skinparam dpi 96
skinparam shadowing false
skinparam defaultFontName Segoe UI
skinparam defaultFontSize 11
skinparam classFontSize 12
skinparam classAttributeFontSize 10
skinparam ArrowColor #546E7A
skinparam classBorderColor #37474F
skinparam classBackgroundColor #FFFFFF
skinparam packageBorderColor #90A4AE
skinparam packageBackgroundColor #FAFAFA
skinparam packageFontSize 13
skinparam packageFontStyle bold
skinparam nodesep 22
skinparam ranksep 40
hide empty members
"""

MAX_GHOSTS = 24


def render_puml(types, edges, title, members):
    """Buduje tresc pliku .puml dla jednej strony diagramu."""
    member_set = set(members)
    cross = [e for e in edges if (e[0] in member_set) != (e[1] in member_set)]
    ghosts = {(b if a in member_set else a) for a, b, _ in cross} - member_set
    if len(ghosts) > MAX_GHOSTS:
        ghosts, cross = set(), []

    # tytul strony rysuje naglowek arkusza w HTML, wiec sam rysunek go nie powtarza
    lines = [HEAD]
    by_pkg = {}
    for name in sorted(members):
        by_pkg.setdefault(types[name]["pkg"] or "(root)", []).append(name)
    for pkg in sorted(by_pkg):
        lines.append('package "%s" {' % pkg)
        for name in by_pkg[pkg]:
            t = types[name]
            keyword = {"interface": "interface", "enum": "enum"}.get(t["kind"], "class")
            lines.append('  %s %s %s {' % (keyword, name, COLOR[stereotype(t, name)]))
            for const in t["consts"][:16]:
                lines.append("    %s" % const)
            for fname, ftype in t["fields"][:18]:
                lines.append("    - %s : %s" % (fname, ftype))
            for mname, mtype in t["methods"][:14]:
                lines.append("    + %s() : %s" % (mname, mtype))
            lines.append("  }")
        lines.append("}")
    if ghosts:
        lines.append('package "powiazania spoza tej strony" %s {' % COLOR["ghost"])
        for ghost in sorted(ghosts):
            lines.append('  class %s %s' % (ghost, COLOR["ghost"]))
        lines.append("}")

    local = [e for e in edges if e[0] in member_set and e[1] in member_set]
    for a, b, kind in sorted(set(local + cross)):
        if kind == "inherit":
            lines.append("%s <|-- %s" % (a, b))
        elif kind == "many":
            lines.append('%s "1" --> "*" %s' % (a, b))
        else:
            lines.append("%s --> %s" % (a, b))
    lines.append("@enduml")
    return "\n".join(lines)


# --------------------------------------------------------------------------
# 3. Renderowanie SVG (z podzialem zbyt duzych stron)
# --------------------------------------------------------------------------

def plantuml(paths):
    subprocess.check_call(
        ["java", "-Djava.awt.headless=true", "-Xmx2g", "-jar", PLANTUML_JAR,
         "-tsvg", "-charset", "UTF-8"] + paths,
        stdout=subprocess.DEVNULL)


def svg_size(path):
    head = io.open(path, encoding="utf-8").read(400)
    box = re.search(r'viewBox="0 0 (\d+) (\d+)"', head)
    return (int(box.group(1)), int(box.group(2))) if box else (0, 0)


def split_members(types, members):
    """Dzieli liste klas na dwie polowy, nie rozbijajac pakietow bez potrzeby."""
    ordered = sorted(members, key=lambda n: (types[n]["pkg"], n))
    half = len(ordered) // 2
    # przesun ciecie na granice pakietu, jesli jest blisko
    for shift in range(0, max(1, len(ordered) // 4)):
        for cut in (half - shift, half + shift):
            if 0 < cut < len(ordered) and types[ordered[cut]]["pkg"] != types[ordered[cut - 1]]["pkg"]:
                return ordered[:cut], ordered[cut:]
    return ordered[:half], ordered[half:]


class Renderer(object):
    """Renderuje strony do plikow tymczasowych i pamieta ich rozmiary."""

    def __init__(self, types, edges):
        self.types, self.edges, self.counter = types, edges, 0

    def render(self, base, members):
        self.counter += 1
        stem = os.path.join(OUT, "tmp-%04d" % self.counter)
        io.open(stem + ".puml", "w", encoding="utf-8").write(
            render_puml(self.types, self.edges, base, sorted(members)))
        plantuml([stem + ".puml"])
        width, height = svg_size(stem + ".svg")
        return dict(stem=stem, base=base, members=sorted(members),
                    width=width, height=height)

    @staticmethod
    def fits(page):
        return page["width"] <= MAX_W and page["height"] <= MAX_H

    @staticmethod
    def discard(page):
        for ext in (".puml", ".svg"):
            if os.path.exists(page["stem"] + ext):
                os.remove(page["stem"] + ext)


def render_pages(types, edges):
    """Dzieli warstwy na strony miesczace sie w arkuszu, potem scala nadmiarowe ciecia."""
    renderer = Renderer(types, edges)
    taken, layers = set(), []
    for title, predicate in LAYERS:
        members = sorted(n for n in types if n not in taken and predicate(types[n]["pkg"]))
        if members:
            taken.update(members)
            layers.append((title, members))

    pages = []
    for title, members in layers:
        work, done = [members], []
        while work:
            chunk = work.pop(0)
            page = renderer.render(title, chunk)
            if Renderer.fits(page) or len(chunk) <= 4:
                done.append(page)
                continue
            renderer.discard(page)
            first, second = split_members(types, chunk)
            work.insert(0, second)
            work.insert(0, first)
        pages.extend(merge_pass(renderer, title, done))
    return finalize(pages)


def merge_pass(renderer, title, pages):
    """Skleja sasiednie fragmenty jednej warstwy, dopoki mieszcza sie na arkuszu."""
    merged = True
    while merged and len(pages) > 1:
        merged = False
        for i in range(len(pages) - 1):
            candidate = renderer.render(title, pages[i]["members"] + pages[i + 1]["members"])
            if Renderer.fits(candidate):
                renderer.discard(pages[i])
                renderer.discard(pages[i + 1])
                pages[i:i + 2] = [candidate]
                merged = True
                break
            renderer.discard(candidate)
    return pages


def finalize(pages):
    """Nadaje tytulom numery czesci, a plikom ostateczne nazwy."""
    counts = {}
    for page in pages:
        counts[page["base"]] = counts.get(page["base"], 0) + 1
    seen = {}
    for number, page in enumerate(pages, start=1):
        total = counts[page["base"]]
        seen[page["base"]] = seen.get(page["base"], 0) + 1
        page["title"] = page["base"] if total == 1 else \
            "%s (cz. %d z %d)" % (page["base"], seen[page["base"]], total)
        page["number"] = number
        stem = os.path.join(OUT, "page-%02d" % number)
        for ext in (".puml", ".svg"):
            if os.path.exists(stem + ext):
                os.remove(stem + ext)
            shutil.move(page["stem"] + ext, stem + ext)
        page["stem"] = stem
    return pages


# --------------------------------------------------------------------------
# 4. Skladanie PDF (Chrome headless)
# --------------------------------------------------------------------------

FORMATS = {"a4": ("A4", 287.0, 200.0), "a3": ("A3", 410.0, 287.0)}


def inline_svg(path):
    svg = io.open(path, encoding="utf-8").read()
    svg = re.sub(r'\sstyle="width:[^"]*"', '', svg, count=1)
    svg = re.sub(r'\swidth="\d+px"', ' width="100%"', svg, count=1)
    svg = re.sub(r'\sheight="\d+px"', ' height="100%"', svg, count=1)
    svg = svg.replace('preserveAspectRatio="none"',
                      'preserveAspectRatio="xMidYMid meet"', 1)
    return svg


def build_html(pages, fmt):
    css_page, width_mm, height_mm = FORMATS[fmt]
    parts = ["""<meta charset="utf-8"><title>Diagram klas - loyalty-club</title><style>
@page { size: %s landscape; margin: 5mm; }
html, body { margin: 0; padding: 0; background: #fff; }
body { font-family: "Segoe UI", Arial, sans-serif; color: #263238; }
.sheet { width: %.1fmm; height: %.1fmm; page-break-after: always;
         display: flex; flex-direction: column; overflow: hidden; }
.sheet:last-child { page-break-after: auto; }
.hd { display: flex; justify-content: space-between; align-items: baseline;
      border-bottom: 0.4mm solid #37474F; padding-bottom: 1mm; margin-bottom: 2mm;
      font-size: 3.2mm; }
.hd b { font-size: 3.8mm; }
.hd span { color: #607D8B; }
.art { flex: 1 1 auto; min-height: 0; display: flex; align-items: center;
       justify-content: center; }
.art svg { max-width: 100%%; max-height: 100%%; }
.legend { margin-top: 1.5mm; font-size: 2.6mm; color: #455A64;
          display: flex; gap: 4mm; flex-wrap: wrap; }
.legend i { display: inline-block; width: 3mm; height: 2.2mm; margin-right: 1mm;
            border: 0.2mm solid #37474F; vertical-align: middle; }
</style>""" % (css_page, width_mm, height_mm)]

    legend = ("<span><i style='background:%s'></i>encja</span>" % COLOR["entity"] +
              "<span><i style='background:%s'></i>DTO</span>" % COLOR["dto"] +
              "<span><i style='background:%s'></i>repozytorium</span>" % COLOR["repo"] +
              "<span><i style='background:%s'></i>kontroler</span>" % COLOR["ctrl"] +
              "<span><i style='background:%s'></i>usluga</span>" % COLOR["svc"] +
              "<span><i style='background:%s'></i>enum</span>" % COLOR["enum"] +
              "<span><i style='background:%s'></i>konfiguracja / infrastruktura</span>" % COLOR["cfg"] +
              "<span><i style='background:%s'></i>klasa z innej strony</span>" % COLOR["ghost"])

    for page in pages:
        parts.append(
            '<div class="sheet"><div class="hd"><b>%s</b>'
            '<span>loyalty-club &middot; diagram klas &middot; %d klas &middot; '
            'strona %d/%d</span></div><div class="art">%s</div>'
            '<div class="legend">%s</div></div>'
            % (page["title"], len(page["members"]), page["number"], len(pages),
               inline_svg(page["stem"] + ".svg"), legend))
    return "".join(parts)


def find_chrome():
    for path in CHROME_CANDIDATES:
        if os.path.exists(path):
            return path
    raise SystemExit("Nie znaleziono Chrome ani Edge - nie moge wydrukowac PDF.")


def print_pdf(html_path, pdf_path):
    chrome = find_chrome()
    subprocess.check_call([
        chrome, "--headless", "--disable-gpu", "--no-sandbox",
        "--run-all-compositor-stages-before-draw", "--virtual-time-budget=20000",
        "--no-pdf-header-footer", "--print-to-pdf=" + pdf_path,
        "file:///" + html_path.replace("\\", "/")],
        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


# --------------------------------------------------------------------------

def main():
    if not os.path.exists(PLANTUML_JAR):
        raise SystemExit(
            "Brak plantuml.jar. Pobierz go poleceniem:\n"
            "  mvn dependency:get -Dartifact=net.sourceforge.plantuml:plantuml:%s"
            % PLANTUML_VERSION)
    if not os.path.isdir(OUT):
        os.makedirs(OUT)
    for stale in os.listdir(OUT):
        if re.match(r'^(page-\d+|tmp-\d+|class-diagram-full)\.(puml|svg)$', stale):
            os.remove(os.path.join(OUT, stale))

    types = parse_sources()
    edges = build_edges(types)
    print("Sparsowano %d typow, %d relacji." % (len(types), len(edges)))

    # pelny diagram w jednym arkuszu - material zrodlowy do przegladania na ekranie
    full = os.path.join(OUT, "class-diagram-full")
    io.open(full + ".puml", "w", encoding="utf-8").write(
        render_puml(types, edges, "calosc", list(types)))
    plantuml([full + ".puml"])
    print("class-diagram-full.svg -> %dx%d px" % svg_size(full + ".svg"))

    pages = render_pages(types, edges)
    print("Strony diagramu: %d" % len(pages))
    for page in pages:
        print("  %2d. %-70s %3d klas  %dx%d px"
              % (page["number"], page["title"][:70], len(page["members"]),
                 page["width"], page["height"]))

    for fmt in ("a4", "a3"):
        html_path = os.path.join(OUT, "class-diagram-%s.html" % fmt)
        pdf_path = os.path.join(OUT, "class-diagram-%s.pdf" % fmt)
        io.open(html_path, "w", encoding="utf-8").write(build_html(pages, fmt))
        print_pdf(html_path, pdf_path)
        os.remove(html_path)
        size = os.path.getsize(pdf_path) if os.path.exists(pdf_path) else 0
        print("%s -> %.1f kB" % (os.path.basename(pdf_path), size / 1024.0))

    io.open(os.path.join(OUT, "manifest.json"), "w", encoding="utf-8").write(
        json.dumps([{k: p[k] for k in ("number", "title", "members", "width", "height")}
                    for p in pages], indent=1, ensure_ascii=False))


if __name__ == "__main__":
    sys.exit(main())
