# Testat-Generator Rechnerorganisation

Erzeugt aus einer Liste von Matrikelnummern individuelle Testat-Aufgaben samt Lösung
als ShareLaTeX-Projekt (Zip). Ein Blatt besteht aus einer frei kombinierbaren Kette von Aufgaben; fertige Ketten
liegen als Vorlagen bereit. Aktuelle Aufgabentypen: Befehlskodierung (RISC-V RV32I,
Assembler → Maschinencode und zurück), Kontrollstrukturen (C → RISC-V, RISC-V → C)
und Prozessor (Eintakt-Timing, Mehrtakt-Steuerwerk, Pipeline-Konflikte).

## Für Tutor:innen

Voraussetzung: Java 17 oder neuer.

1. `.werk`-Datei vom Testat-Team erhalten (oder selbst anlegen, siehe unten).
2. Erzeugen:
   ```
   java -jar testat-generator.jar gruppe_A.werk
   ```
3. Im selben Ordner liegen danach:
   - `gruppe_A_kodierung_<Zeitstempel>.zip` – das ShareLaTeX-Projekt
   - `gruppe_A_kodierung_<Zeitstempel>_zuordnung.csv` – welche Matrikelnummer welchen Seed
     und welche Aufgabe bekommen hat (**nicht weitergeben, enthält die Lösungen**)
4. In ShareLaTeX: *New Project → Upload Project* → Zip auswählen.
   - `main.tex` = Aufgabenblätter, eine Seite pro Matrikelnummer → drucken
   - `loesungen.tex` = Lösungen mit Zuordnungsschema → über *Menu → Main document* umschalten
5. Das Projekt **nur mit Tutor:innen teilen**, nicht über Moodle oder öffentliche Links.

### Aufbau der `.werk`-Datei

```
# Kommentare und Leerzeilen werden ignoriert
mein-geheimer-schluessel-testat1-x8Fq2      <- optional, nur in der ersten Zeile
123456
234567
```

- **Mit Schlüssel:** Seed = HMAC-SHA256(Schlüssel, Thema | Matrikelnummer). Dieselbe Datei
  erzeugt immer dieselben Aufgaben; ohne den Schlüssel sind sie nicht vorhersagbar.
  Thema ist die Vorlage (`--vorlage`) bzw. bei `--aufgaben` der Titel zusammen mit der Aufgabenliste –
  zwei verschiedene eigene Testate liefern also auch bei gleichem Schlüssel verschiedene Aufgaben.
  Für einen Nachtermin einen **neuen** Schlüssel verwenden.
- **Ohne Schlüssel:** zufällige Seeds. Reproduzierbar nur über die Zuordnungsdatei.
- Der Schlüssel muss mindestens ein Zeichen enthalten, das keine Ziffer ist (so wird eine
  vertippte Matrikelnummer nie als Schlüssel gelesen). Empfohlen: ≥ 12 zufällige Zeichen.
- Matrikelnummern: 6 oder 7 Ziffern. Ungültige Zeilen und Duplikate werden mit Warnung übersprungen.
  Das Format steht in `WerkFile.MATRIKEL`.

### Vorlagen und eigene Ketten

Ein Testatblatt ist eine Kette von Aufgaben. Fertige Ketten heißen **Vorlagen**:

```
java -jar testat-generator.jar --liste                          # alle Vorlagen und Aufgabentypen
java -jar testat-generator.jar gruppe_A.werk --vorlage spruenge
```

| Vorlage | Aufgaben |
|---|---|
| `kodierung` (Standard) | asm2bin(zufall), bin2asm(zufall) |
| `kodierung-stufen` | asm2bin(R,I) – leicht, asm2bin(S,Load,Shift) – mittel, asm2bin(B,J) – schwer, bin2asm(zufall) |
| `spruenge` | asm2bin(B), asm2bin(J), bin2asm(B,J) |
| `speicher` | asm2bin(S), bin2asm(Load) |
| `kontrollstrukturen` | c2asm(zufall), asm2c(zufall) |
| `verzweigungen` | c2asm(if), asm2c(if) |
| `schleifen` | c2asm(schleife), asm2c(schleife) |
| `funktionen` | rekursion, aufruf, stack, array (alle zufall) – Testat 2; `shift` bei Bedarf über `--aufgaben` |
| `speicherhierarchie` | cache-bits, cache-sim, cache-prog, amat, vm (alle zufall) – Testat 4; `schreiben` bei Bedarf über `--aufgaben` |
| `prozessor` | eintakt, mehrtakt(zufall), hazards |
| `prozessor-neu` | eintakt, mehrtakt-neu(zufall), hazards |

Eigene Kette ohne Programmieren (Reihenfolge = Reihenfolge auf dem Blatt):

```
java -jar testat-generator.jar gruppe_A.werk --aufgaben "asm2bin:B,J asm2bin:sw bin2asm" --titel "Mini-Testat"
```

Befehlsauswahl nach dem Doppelpunkt: `R`, `I`, `Shift`, `Load`, `jalr`, `S`, `B`, `U`, `J`,
ein Befehl (`beq`) oder eine Liste (`B,J,S`); ohne Angabe beliebig.

Für `c2asm`/`asm2c`: Form nach dem Doppelpunkt (`if`, `schleife`, `if-if`, `if-elseif`,
`if-else`, `while`, `for`, `do-while` oder Liste), z. B. `c2asm:while,for`.

Für `mehrtakt`: zu ergänzender Befehl (`sw`, `add`, `sub`, `and`, `or`, `mul`, `addi`, `andi`,
`ori`, `beq`, `jal`) oder Klasse (`R`, `I`), z. B. `mehrtakt:sw,beq`. Für `mehrtakt-neu`: Befehle
aus dem Katalog (`jalr`, `auipc`, `lwr`, `lwpc`, `swpc`, `lwi`, `swi`, `jrr`, `jalrr`, `jm`, `jalm`,
`jmpc`), z. B. `mehrtakt-neu:jalm,lwi`. `eintakt` und `hazards` haben keine Parameter.

### Optionen

| Option | Bedeutung | Standard |
|---|---|---|
| `--vorlage <id>` | fertige Vorlage | `kodierung` |
| `--aufgaben "<kette>"` | eigene Kette statt Vorlage | – |
| `--titel "<text>"` | Überschrift für `--aufgaben` | `Testat` |
| `--out` | Ausgabeordner | Ordner der `.werk`-Datei |
| `--seed <hex> --matrikel <nr>` | ein einzelnes Blatt aus der Zuordnungsdatei neu erzeugen (mit derselben Vorlage/Kette wie damals) | – |
| `--liste` | Vorlagen und Aufgabentypen anzeigen | – |

## Probelauf für Studierende

Studierende können eigenen RISC-V-Assembler- oder C-Code ausführen und sehen Syntaxfehler,
Laufzeitfehler und die Werte am Ende. Es wird **nichts** mit einer Aufgabe verglichen und nichts
als richtig/falsch bewertet – das entscheiden die Tutor:innen.

```
java -jar testat-generator-probelauf.jar loesung.s          # eigenes kleines jar, ohne Generator
java -jar testat-generator-probelauf.jar loesung.txt        # Sprache wird erkannt (C: ';' oder {})
java -jar testat-generator-probelauf.jar loesung.txt --c    # Sprache erzwingen (--asm / --c)
java -jar testat-generator-probelauf.jar loesung.s --schritte   # jeden Befehl mit Registeränderung zeigen
java -jar testat-generator.jar ausfuehren loesung.s         # dasselbe im Generator-jar
```

- **Assembler:** RV32I + M-Extension, Pseudobefehle (`li`, `la`, `mv`, `j`, `call`, `ret`, `beqz`,
  `bgt`, `ble`, …), `.text`/`.data` (`.word`, `.byte`, `.asciz`, …), `ecall` (1, 4, 11, 34, 10).
  Start wie in RARS: `sp = 0x7FFFEFFC`, `gp = 0x10008000`, sonst 0. Ausgabe: geänderte Register,
  geschriebener Speicher, ecall-Ausgabe. Fehler: unbekannte Befehle/Register, falsche Operanden,
  Immediate außerhalb des Bereichs, fehlende/doppelte Marken (alle auf einmal, mit Zeile);
  nicht ausgerichteter Zugriff, ungültiger Sprung, Schrittlimit (Endlosschleife).
- **C:** `int`-Variablen und -Arrays, Funktionen (auch rekursiv, Arrays als Parameter),
  if/else if/else, while, do-while, for, break/continue, return, alle int-Operatoren, `printf`
  (`%d %u %x %c`), `putchar`. Anweisungen ohne `main` und ohne Deklaration sind erlaubt (wie in den
  Testat-Aufgaben; es gibt einen Hinweis). Fehler: erster Syntaxfehler mit Zeile/Spalte; zur
  Laufzeit Lesen ohne Wert, nicht deklarierte Variable, Division durch 0, Index außerhalb,
  Endlosschleife, zu tiefe Rekursion.
- Beispiele: `beispiel/probelauf_beispiel.s`, `beispiel/probelauf_beispiel.c`.
- Kontrolle: `ProbelaufTest` führt alle vom Generator erzeugten Assembler- und C-Programme
  (6 Formen × 400 Seeds) im Probelauf aus und vergleicht mit dem Interpreter des Generators.

## Webseite für Übungsblätter

Statische Seite, auf der Studierende Übungsblätter (fertige Vorlage oder eigene Auswahl, 1–10 Blätter)
als ZIP für Overleaf erzeugen. Der Generator wird mit [TeaVM](https://teavm.org) nach JavaScript
übersetzt und läuft **komplett im Browser** – kein Server rechnet, nichts wird gesendet.
Nur Übungsblätter mit zufälligen Seeds, Lösungen inklusive. Schlüssel, `.werk`-Dateien und
Zuordnungsdateien gibt es auf der Seite nicht; echte Testate entstehen weiter offline.

```
mvn -Pweb package                       # Tests + Seite nach target/site (generator.js, index.html, ...)
node web/test/smoke.mjs                 # generator.js gegen die JVM pruefen (target/site, target/web-referenz.jsonl)
jwebserver -d <absoluter Pfad>/target/site   # lokal ansehen: http://127.0.0.1:8000
```

- `de.ro.testat.web.WebApi` – Schnittstelle für JavaScript (`katalog`, `setzeRessource`, `erzeugen`),
  nur Strings/JSON über die Grenze. Läuft auch auf der JVM (`WebApiTest`).
- `web/` – die Seite: `index.html`, `app.js` (Bedienung), `generator-worker.js` + `generator-kern.js`
  (Generator im Web Worker), `zip.js` (ZIP ohne Bibliothek). Die LaTeX-Dateien aus
  `src/main/resources/latex` werden nach `target/site/latex` kopiert und von der Seite geladen.
- Kontrolle: `WebReferenzTest` schreibt mit `-Pweb` die JVM-Ausgabe für feste Seeds (alle Vorlagen und
  Aufgabentypen), `web/test/smoke.mjs` lässt dieselben Fälle durch `generator.js` laufen und
  vergleicht Zeichen für Zeichen.
- Veröffentlichung: `.github/workflows/webseite.yml` baut bei jedem Push auf `main`, vergleicht und
  stellt `target/site` auf GitHub Pages (einmalig: Settings → Pages → Source: „GitHub Actions“).
  Weicht das JavaScript von der JVM ab, wird nichts veröffentlicht.
- Im Browser gibt es keine Threads: Der C-Interpreter läuft dort direkt (`CInterpreter.setAusfuehrung`).
  Die Kommandozeile schaltet mit `GrosserStack.aktivieren()` auf den Thread mit 512 MB Stack um
  (nur für tiefe Rekursion im Probelauf nötig, der Generator kommt mit 256 KB aus).

## Für Entwickler:innen

```
mvn package          # baut target/testat-generator.jar (+ target/testat-generator-probelauf.jar) und führt die Tests aus
mvn test             # nur Tests
```

`EncoderLlvmTest` vergleicht die Kodierung von ~2200 zufälligen Befehlen mit `llvm-mc`
(LLVM-Assembler). Ist `llvm-mc` nicht installiert, wird der Test übersprungen.

### Struktur

```
de.ro.testat
├── Main                  Kommandozeile
├── testat/               Testat (Builder fuer die Aufgabenkette), Templates (Vorlagen)
├── exercise/             Exercise-Interface, Exercises (Verzeichnis fuer --aufgaben)
│   ├── encoding/         AsmToMachineCode, MachineCodeToAsm + LaTeX dazu
│   ├── control/          CToAssembler, AssemblerToC, Generator, Interpreter, Simulator
│   ├── functions/        RecursionToAssembler, CallToAssembler, StackTrace, ArrayToAssembler, ShiftArithmetic (Testat 2)
│   ├── memory/           CacheParameters, CacheSimulation, CacheProgram, AccessTime, WritePolicy, VirtualMemory (Testat 4)
│   └── cpu/              SingleCycleTiming, MulticycleFsm, NewInstructionFsm, Datapath, PipelineHazards
├── rv/                   RISC-V: Formate, Befehle, Kodierung, Zufallsoperanden
├── latex/                LaTeX-Hilfen, TikZ-Schemata, Projektaufbau (DocumentBuilder)
├── io/                   .werk-Datei, Seeds, Zip + Zuordnung
├── probelauf/            Probelauf für Studierende: RiscvParser/RiscvMaschine, CParser/CInterpreter
│                         (unabhängig vom Rest, eigenes jar)
└── web/                  WebApi: Schnittstelle fuer die Webseite (TeaVM, siehe "Webseite")
```

### Neue Vorlage

In `testat.Templates` eine Kette eintragen:

```java
register("mein-testat", () -> Testat.builder("mein-testat", "Mein Testat")
        .add(new AsmToMachineCode("B,J"))
        .add(new MachineCodeToAsm("R"))
        .build());
```

Jede Aufgabe bekommt einen eigenen, aus Seed und Position abgeleiteten Zufallsgenerator:
Wird z. B. Aufgabe 3 ausgetauscht, bleiben Aufgabe 1 und 2 für dieselbe Matrikelnummer gleich.

### Neuer Aufgabentyp

1. Klasse anlegen, die `exercise.Exercise` implementiert:
   ```java
   public final class CToAssembler implements Exercise {
       public String id() { return "c2asm"; }

       // optional: Pakete/Definitionen, die nur diese Aufgabe braucht
       public List<String> preamble() { return List.of("\\usepackage{tabularx}"); }

       public Part generate(Random rng) {
           // Zufall NUR aus rng ziehen, sonst ist die Aufgabe nicht reproduzierbar
           String titel   = "C $\\rightarrow$ RISC-V";          // "Aufgabe N:" setzt tudaexercise
           String aufgabe = "\\begin{lstlisting}[language=C] ... \\end{lstlisting}";
           String loesung = "\\begin{lstlisting} ... \\end{lstlisting}"; // ohne \begin{solution}
           return new Part(titel, aufgabe, loesung, "Kurzfassung Frage", "Kurzfassung Antwort");
       }
   }
   ```
   `\begin{task}{titel}` und `\begin{solution}` erzeugt der Generator selbst. Teilaufgaben
   gehen wie gewohnt im Aufgabentext: `\begin{subtask}[title={...}] ... \end{subtask}`.
2. Optional in `exercise.Exercises` registrieren, damit sie per `--aufgaben "c2asm:..."` nutzbar ist.
3. In Vorlagen mit `.add(new CToAssembler())` verwenden.

Hinweis für LaTeX-Ausgaben: Zahlen immer über `Tex.f(...)` formatieren (Locale.ROOT).
Mit deutschem Locale würde `String.format("%.2f")` ein Komma erzeugen und TikZ-Koordinaten
zerstören.

### Kontrollstrukturen (`exercise.control`)

```
ProgramGenerator ──► Program (AST) ──► Interpreter ──► Endwerte, Durchläufe, if-Ablauf
                          │
                          ├──► CRenderer      ──► C-Code
                          └──► RiscvRenderer  ──► RISC-V (zufälliges Übersetzungsschema)
                                                     │
                     Selbstkontrolle bei jeder Aufgabe: RvSim(Assembler) == Interpreter(C)
```

- Formen (`Shape`): `if-if`, `if-elseif` (if / else if / else), `if-else`, `while`, `for`,
  `do-while`. Keine Verschachtelung. Jedes Programm beginnt mit zwei Konstanten und einer
  Multiplikation (M-Extension).
- Der Generator verwirft Programme, die nicht enden, deren Schleifen nicht 2–6 Mal laufen oder
  bei denen (bei `if-if`) kein Zweig greift.
- Variablennamen, Register, Vergleichsoperatoren, Konstanten und Übersetzungsschema
  (Prüfung oben/unten, Sprung bei wahr/falsch) sind zufällig. Marken heißen `L1`, `L2`, …
- C → RISC-V: Die Musterlösung sichert ein benötigtes Hilfsregister auf dem Stack.
- Die Aufgaben bestehen nur aus der Aufgabenstellung; es gibt keine Zusatz- oder Theoriefragen.

### Funktionen und Speicher (`exercise.functions`, Testat 2)

| Aufgabe | Zufällig | Gefragt | Selbstkontrolle |
|---|---|---|---|
| `rekursion` – Rekursive Funktion in RISC-V | Form (12 Stück, Parameter `rekursion:<form>`): prod, summe, linear, fib, fib_mul (klassisch, ein Argument), potenz (x, n), ggt (Euklid oder Anzahl Schritte), ziffern (Ziffernsumme/-anzahl zur Basis 10, 8, 5), bits (`n & m`, `n >> s`), collatz (zwei Aufrufstellen), binom (zwei Argumente, zwei Aufrufe, 3 s-Register), bereich (a..b); Konstanten, Name, Argumente für main | Funktion nach Aufrufkonvention + Hauptprogramm | Werte aus dem C-Interpreter; Musterlösung im Simulator für alle Kontrollargumente, auch im strengen Modus; `sp` und s0–s11 unverändert |
| `aufruf` – Funktionsaufrufe in RISC-V | Blattfunktion g (if/else, max/min/Abstand, for-Summe, wiederholtes Abziehen, Begrenzen, Potenz-Schleife) × Aufrufer f (`summe`: for mit s += g(..), `zaehlen`: if (g(..) > K), `iteration`: x = g(x) bis x ≤ K, `zwei_aufrufe`: u = g(..), v = g(..), if/else, `bedingt`: while mit g nur bei geraden/ungeraden n); Konstanten, Argumentausdrücke, Namen | g und f nach Aufrufkonvention + Hauptprogramm | wie `rekursion`; Kombinationen, die nicht schnell enden oder zu große Werte liefern, werden verworfen |
| `stack` – Stack nachvollziehen | Funktion (eine Rekursion), n, Start-`sp`, Start-`s0` | `sp` und Stackinhalt beim ersten Basisfall (Haltepunkt), Ergebnis | Simulator-Haltepunkt == erwarteter Aufbau der Stackrahmen |
| `array` – Arrays im Speicher | Form (Skalarprodukt, Summe > k, Maximum, Zählen, Umrechnen, Umdrehen, Präfixsumme), Werte | C-Schleife → RISC-V mit `la`/`lw`/`sw` | Simulator == C-Interpreter (Probelauf) |
| `shift` – Multiplikation/Division durch Shift | n·c (zwei Bits), n·c (2^p − 2^q), n / 2^k | ohne `mul`/`div` | Simulator == Java-Arithmetik für viele n (inkl. negative, MIN/MAX) |

**Strenger Modus.** `RiscvMaschine.runStreng` überschreibt bei jedem Rücksprung (`jalr zero, 0(ra)`) alle Register,
die eine Funktion nicht erhalten muss (t0–t6, a1–a7), mit Zufallswerten. Eine Lösung, die sich nach einem Aufruf noch auf
ein t- oder a-Register verlässt, fällt dabei auf, auch wenn die aufgerufene Funktion das Register zufällig nicht verändert.
`ConventionCheck.pruefe` lässt jede Musterlösung normal und zweimal streng laufen.

Die Musterlösungen verwenden nur echte Befehle (`addi` statt `li`, `jal ra` statt `call`, `jalr zero, 0(ra)` statt `ret`),
damit die Adressen im Listing der Stack-Aufgabe genau wie in RARS stimmen. `RecursiveFunction`, `RecursionForms`, `CallChain`, `ConventionCheck` und `AsmText` sind
Hilfsklassen; geprüft wird mit dem Simulator aus `probelauf`.

### Speicherhierarchie (`exercise.memory`, Testat 4)

Grundlage ist Harris & Harris, Kap. 8: 32-Bit-Byteadressen, Wörter zu 4 Byte, Adresse = Tag | Satz | Block-Offset | Byte-Offset,
Ersetzung LRU (neuer Block in den freien Weg mit der kleinsten Nummer), Misses eingeteilt in Compulsory (erster Zugriff auf den
Block), Capacity (auch ein gleich großer voll assoziativer LRU-Cache verfehlt) und Conflict (Rest).

| Aufgabe | Zufällig | Gefragt | Selbstkontrolle |
|---|---|---|---|
| `cache-bits` – Cache-Parameter | Kapazität 1–256 KiB, Block 8–64 Byte, N (direkt, 2/4/8-fach, voll bis 4 KiB), Write-Back/-Through, Adresse; Parameter `vorwaerts`/`rueckwaerts` | B, S, Bitbreiten, Speicherbedarf in Bit, Zerlegung einer Adresse; rückwärts: aus der Aufteilung Block, S, B, Kapazität und Aufteilung bei doppeltem N | Zerlegung per Division == per Bitmaske, ergibt zusammengesetzt die Adresse |
| `cache-sim` – Zugriffsfolge | kleiner Cache (8/16 Wörter, Block 1–4 Wörter), Parameter `direkt`/`assoziativ`/`voll`; 10–12 Adressen mit Konflikten und Lokalität | Satz, Tag, Hit/Miss, Miss-Art je Zugriff, Endinhalt, Miss-Rate | zweite, anders geschriebene LRU-Simulation; Folge muss Hits, Verdrängung und Conflict- (bzw. Capacity-) Misses enthalten |
| `cache-prog` – Miss-Rate eines Programms | `schleife`: RISC-V-Schleife mit festen `lw`-Adressen (wie H&H 8.3), `array`: C-Schleife über zwei Arrays mit/ohne Satzkonflikt | Miss-Rate für Cache A (direkt) und B (2-fach bzw. voll), Anzahl je Miss-Art | Adressfolge des Modells == Speicherzugriffe, die der RISC-V-Simulator beim Ausführen aufzeichnet |
| `amat` – mittlere Zugriffszeit | t, Miss-Raten, L2, Hauptspeicher, Ziel-AMAT, Anteil Lade-/Speicherbefehle, Takt | AMAT mit L1, mit L2, nötige Miss-Rate, CPI und Laufzeit | exakt mit BigDecimal, zweite Rechnung in ganzen Zahlen |
| `schreiben` – Schreibstrategien | 4 Blöcke à 1–2 Wörter, 8–10 `lw`/`sw` | Hit/Miss bei Write-Through/no-allocate und Write-Back/allocate, Rückschreiben, Zahl der Speicherzugriffe, Dirty-Blöcke | eigene Zählung über Tabelle Satz → (Tag, dirty) und Invarianten |
| `vm` – virtueller Speicher | physische Adressbreite 24–30 Bit, Seiten 1–16 KiB, Seitentabellen-Ausschnitt, TLB | Offset/VPN/PPN, Seitenzahlen, Größe der Seitentabelle, Übersetzung (TLB-Treffer, Seitentabelle, Seitenfehler) | Übersetzung per Bits == per Division, TLB passt zur Seitentabelle |

Die Lehrbuchwerte aus H&H (Schleife 5 × `lw 0x4, 0xC, 0x8` → 3/15; `lw 0x4, 0x24` → 10/10 direkt, 2/10 2-fach) sind als Tests hinterlegt.

### Prozessor (`exercise.cpu`)

| Aufgabe | Zufällig | Gefragt |
|---|---|---|
| `eintakt` – Rechenleistung und kritischer Pfad | Programm (3–5 Befehle, in ~40 % ohne `lw`), Verzögerungszeiten | Takte, Pfad eines Befehls, Tc, Gesamtzeit |
| `mehrtakt` – Erweiterung des Steuerwerks | gegebene FSM (`lw`, evtl. + eine Klasse), zu ergänzender Befehl | neue Zustände + Übergänge, Steuersignale, Takte |
| `mehrtakt-neu` – Neuer Befehl im Mehrtakt-Steuerwerk | Befehl aus dem Katalog (meist ausgedacht, custom-Opcode), gegeben nur Fetch/Decode oder zusätzlich `lw` | neue Zustände + Übergänge, Steuersignale, Takte (minimal) |
| `hazards` – Datenkonflikte in der Pipeline | Programm aus 5–6 Befehlen ohne Sprünge | RAW-Abhängigkeiten, minimale `nop`s ohne Forwarding, Stalls + Pipeline-Diagramm mit Forwarding, Umordnung ohne Stall |

Modell (bitte mit der Vorlesung abgleichen, Änderungen jeweils an einer Stelle):

- **Eintakt** (`SingleCycleTiming.Kind`): Pfade wie im bisherigen Testat 2 (ohne Ergebnis-Mux
  und Extend-Einheit). Logikbefehle (`and`, `or`, …) laufen über die ALU; das AND-OR-Gatter
  liegt im `beq`-Pfad (Sprunglogik). Tc = langsamste Befehlsklasse des **Prozessors**
  (immer `lw`), nicht des Programms. Die Lösung weist darauf hin, wenn `lw` im Programm fehlt.
- **Mehrtakt** (`MulticycleFsm.State`): Zustände S0–S10 und Steuersignale wie im Harris &
  Harris (RISC-V Edition). Die FSM wird als TikZ gezeichnet, neue Teile in der Lösung farbig.
  Das Schaltbild des Mehrtakt-Prozessors ist **nicht** enthalten (bei Bedarf als Bild ergänzen).
- **Neuer Befehl** (`NewInstructionFsm`, `Datapath`): Die Lösung wird berechnet, nicht
  nachgeschlagen. `Datapath` modelliert den Mehrtakt-Datenpfad symbolisch (pro Takt eine
  ALU-Operation, ein Speicherzugriff, ein Registerschreibzugriff; ALU-Ergebnis im selben Takt nur in
  den PC, sonst über ALUOut) und sucht die kürzeste Steuerfolge für die Wirkung des Befehls.
  Vorhandene Zustände werden wiederverwendet, wenn Signale und Nachfolger passen; Zustände mit
  den Signalen eines Vorlesungszustands (S2, S3, S4, S5, S7) behalten dessen Namen, sonst S11, S12, …
  Kontrolle: Das Modell findet für lw/sw/jal/add/addi genau die Taktzahlen der Vorlesung, und jede
  Lösung wird mit konkreten Zahlen simuliert (unbenutzte Signale = Zufall). Neuer Befehl: Eintrag in
  `NewInstructionFsm.CATALOG` mit seiner Wirkung, z. B. `new Goal("pc+4", "M[rs1+imm]", null, null)`
  für `rd ← PC+4; PC ← Mem[rs1+imm]`. Nicht umsetzbare Befehle (z. B. zwei Registerschreibzugriffe)
  findet die Suche nicht und sie werden verworfen.
- **Pipeline** (`PipelineHazards`): Registerfeld schreibt in der ersten, liest in der zweiten
  Takthälfte (ohne Forwarding: Abstand ≥ 3). Forwarding in die EX-Stufe; Stall nur bei `lw`
  mit direkt folgendem Verbraucher (auch `sw`). Der Generator nimmt nur Programme mit
  ≥ 2 Konflikten ohne Forwarding, ≥ 1 Load-Use-Stall und einer Umordnung ohne Stall.
  Zulässige Umordnung: keine RAW/WAR/WAW-Abhängigkeit umgedreht, `sw` nicht über `lw`/`sw`.

Selbstkontrolle (`CpuTest`): Pipeline-Takte gegen eine taktgenaue Simulation mit
Hazard-Einheit, `nop`s ausreichend und minimal, jede zulässige Umordnung liefert dieselben
Register- und Speicherwerte, Takte der Mehrtakt-Befehle (lw 5, sw/R/I/jal 4, beq 3).

### LaTeX-Vorlage (TU-Klasse `tudaexercise`)

Das erzeugte ShareLaTeX-Projekt:

| Datei | Inhalt | Quelle |
|---|---|---|
| `main.tex` | Aufgabenblätter, `solution=false` | erzeugt |
| `loesungen.tex` | dieselben Blätter mit Lösungen, `solution=true` | erzeugt |
| `praeambel.tex` | gemeinsame Präambel nach TU-Vorlage | `src/main/resources/latex/` |
| `kopf.tex` | Kurztitel, Autor:innen, Semester für die Titelbox | `src/main/resources/latex/` |
| `riscv.tex` | `listings`-Stil für RISC-V | `src/main/resources/latex/` |
| `zusatz.tex` | Pakete, die die Aufgaben dieses Testats brauchen (`Exercise.preamble()`) | erzeugt |
| `blaetter/NNNNNN.tex` | ein Blatt pro Matrikelnummer: Titelbox + `task`/`solution` | erzeugt |

Beide Hauptdateien binden dieselben Blätter ein; nur die Klassenoption `solution` entscheidet,
ob die `solution`-Umgebungen gedruckt werden. Semester oder Autor:innen ändern: `kopf.tex`
(in `src/main/resources/latex/` dauerhaft oder direkt im ShareLaTeX-Projekt).

Die Klasse, die TU-Schriften und `tuda_logo.pdf` müssen in der TeX-Installation vorhanden sein
(auf dem ShareLaTeX der TU der Fall). Fehlt das Logo, `tuda_logo.pdf` ins Projekt hochladen.

### Nicht ins Repository

`.werk`-Dateien (Schlüssel + Matrikelnummern), erzeugte Zips und Zuordnungsdateien
(siehe `.gitignore`).
