# Testat-Generator Rechnerorganisation

Erzeugt aus einer Liste von Matrikelnummern individuelle Testat-Aufgaben samt Lösung
als ShareLaTeX-Projekt (Zip). Ein Blatt besteht aus einer frei kombinierbaren Kette von Aufgaben; fertige Ketten
liegen als Vorlagen bereit. Aktuelle Aufgabentypen: Befehlskodierung (RISC-V RV32I,
Assembler → Maschinencode und zurück).

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

Eigene Kette ohne Programmieren (Reihenfolge = Reihenfolge auf dem Blatt):

```
java -jar testat-generator.jar gruppe_A.werk --aufgaben "asm2bin:B,J asm2bin:sw bin2asm" --titel "Mini-Testat"
```

Befehlsauswahl nach dem Doppelpunkt: `R`, `I`, `Shift`, `Load`, `jalr`, `S`, `B`, `U`, `J`,
ein Befehl (`beq`) oder eine Liste (`B,J,S`); ohne Angabe beliebig.

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
│   └── encoding/         AsmToMachineCode, MachineCodeToAsm + LaTeX dazu
├── rv/                   RISC-V: Formate, Befehle, Kodierung, Zufallsoperanden
├── latex/                gemeinsame LaTeX-Hilfen, TikZ-Schemata, Projektaufbau
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
       public Part generate(Random rng, int nr) {
           // Zufall NUR aus rng ziehen, sonst ist die Aufgabe nicht reproduzierbar
           String aufgabe = "\\subsection*{Aufgabe " + nr + ": C $\\rightarrow$ RISC-V} ...";
           String loesung = "...";
           return new Part(aufgabe, loesung, "Kurzfassung Frage", "Kurzfassung Antwort");
       }
   }
   ```
2. Optional in `exercise.Exercises` registrieren, damit sie per `--aufgaben "c2asm:..."` nutzbar ist.
3. In Vorlagen mit `.add(new CToAssembler())` verwenden.

Hinweis für LaTeX-Ausgaben: Zahlen immer über `Tex.f(...)` formatieren (Locale.ROOT).
Mit deutschem Locale würde `String.format("%.2f")` ein Komma erzeugen und TikZ-Koordinaten
zerstören.

### Nicht ins Repository

`.werk`-Dateien (Schlüssel + Matrikelnummern), erzeugte Zips und Zuordnungsdateien
(siehe `.gitignore`).
