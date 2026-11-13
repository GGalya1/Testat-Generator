# Testat-Generator Rechnerorganisation

Erzeugt aus einer Liste von Matrikelnummern individuelle Testat-Aufgaben samt Lösung
als ShareLaTeX-Projekt (Zip). Ein Blatt besteht aus einer frei kombinierbaren Kette von Aufgaben; fertige Ketten
liegen als Vorlagen bereit. Aktuelle Aufgabentypen: Befehlskodierung (RISC-V RV32I,
Assembler → Maschinencode und zurück), Kontrollstrukturen (C → RISC-V, RISC-V → C)
mit zufälligen Zusatzfragen und Prozessor (Eintakt-Timing, Mehrtakt-Steuerwerk, Pipeline-Konflikte).

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
  Für einen Nachtermin einen **neuen** Schlüssel verwenden.
- **Ohne Schlüssel:** zufällige Seeds. Reproduzierbar nur über die Zuordnungsdatei.
- Der Schlüssel muss mindestens ein Zeichen enthalten, das keine Ziffer ist (so wird eine
  vertippte Matrikelnummer nie als Schlüssel gelesen). Empfohlen: ≥ 12 zufällige Zeichen.
- Matrikelnummern: 6 Ziffern. Ungültige Zeilen und Duplikate werden mit Warnung übersprungen.
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
| `prozessor` | eintakt, mehrtakt(zufall), hazards |

Eigene Kette ohne Programmieren (Reihenfolge = Reihenfolge auf dem Blatt):

```
java -jar testat-generator.jar gruppe_A.werk --aufgaben "asm2bin:B,J asm2bin:sw bin2asm" --titel "Mini-Testat"
```

Befehlsauswahl nach dem Doppelpunkt: `R`, `I`, `Shift`, `Load`, `jalr`, `S`, `B`, `U`, `J`,
ein Befehl (`beq`) oder eine Liste (`B,J,S`); ohne Angabe beliebig.

Für `c2asm`/`asm2c`: Form nach dem Doppelpunkt (`if`, `schleife`, `if-if`, `if-elseif`,
`if-else`, `while`, `for`, `do-while` oder Liste), optional `/n` für die Anzahl der
Zusatzfragen (Standard 2), z. B. `c2asm:while,for/1`.

Für `mehrtakt`: zu ergänzender Befehl (`sw`, `add`, `sub`, `and`, `or`, `mul`, `addi`, `andi`,
`ori`, `beq`, `jal`) oder Klasse (`R`, `I`), z. B. `mehrtakt:sw,beq`. `eintakt` und `hazards`
haben keine Parameter.

### Optionen

| Option | Bedeutung | Standard |
|---|---|---|
| `--vorlage <id>` | fertige Vorlage | `kodierung` |
| `--aufgaben "<kette>"` | eigene Kette statt Vorlage | – |
| `--titel "<text>"` | Überschrift für `--aufgaben` | `Testat` |
| `--out` | Ausgabeordner | Ordner der `.werk`-Datei |
| `--seed <hex> --matrikel <nr>` | ein einzelnes Blatt aus der Zuordnungsdatei neu erzeugen (mit derselben Vorlage/Kette wie damals) | – |
| `--liste` | Vorlagen und Aufgabentypen anzeigen | – |

## Für Entwickler:innen

```
mvn package          # baut target/testat-generator.jar und führt die Tests aus
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
│   ├── control/          CToAssembler, AssemblerToC, Generator, Interpreter, Simulator, Fragen
│   └── cpu/              SingleCycleTiming, MulticycleFsm, PipelineHazards
├── rv/                   RISC-V: Formate, Befehle, Kodierung, Zufallsoperanden
├── latex/                LaTeX-Hilfen, TikZ-Schemata, Projektaufbau (DocumentBuilder)
└── io/                   .werk-Datei, Seeds, Zip + Zuordnung
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
- Zusatzfragen (`Questions.Q`): Bedingung tauschen, Schleifenart, Umbau while ↔ do-while,
  `mul` durch Shifts ersetzen, Endwerte, Anzahl Durchläufe, anderer Startwert (inkl.
  Endlosschleife), Pseudobefehle, `bgt`/`ble` ohne eigenen Befehl, Register sichern,
  ausgeführter Zweig. Pro Aufgabe werden passende Fragen zufällig gezogen; die Antworten
  berechnet der Generator. Neue Frage: Konstante in `Questions.Q` ergänzen.

### Prozessor (`exercise.cpu`)

| Aufgabe | Zufällig | Gefragt |
|---|---|---|
| `eintakt` – Rechenleistung und kritischer Pfad | Programm (3–5 Befehle, in ~40 % ohne `lw`), Verzögerungszeiten | Takte, Pfad eines Befehls, Tc und warum, Gesamtzeit |
| `mehrtakt` – Erweiterung des Steuerwerks | gegebene FSM (`lw`, evtl. + eine Klasse), zu ergänzender Befehl | Typ/Opcode, neue Zustände + Übergänge, Steuersignale, Takte |
| `hazards` – Datenkonflikte in der Pipeline | Programm aus 5–6 Befehlen ohne Sprünge | RAW-Abhängigkeiten, minimale `nop`s ohne Forwarding, Stalls + Pipeline-Diagramm mit Forwarding, Umordnung ohne Stall |

Modell (bitte mit der Vorlesung abgleichen, Änderungen jeweils an einer Stelle):

- **Eintakt** (`SingleCycleTiming.Kind`): Pfade wie im bisherigen Testat 2 (ohne Ergebnis-Mux
  und Extend-Einheit). Logikbefehle (`and`, `or`, …) laufen über die ALU; das AND-OR-Gatter
  liegt im `beq`-Pfad (Sprunglogik). Tc = langsamste Befehlsklasse des **Prozessors**
  (immer `lw`), nicht des Programms. Die Lösung weist darauf hin, wenn `lw` im Programm fehlt.
- **Mehrtakt** (`MulticycleFsm.State`): Zustände S0–S10 und Steuersignale wie im Harris &
  Harris (RISC-V Edition). Die FSM wird als TikZ gezeichnet, neue Teile in der Lösung farbig.
  Das Schaltbild des Mehrtakt-Prozessors ist **nicht** enthalten (bei Bedarf als Bild ergänzen).
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
