package de.ro.testat;

import de.ro.testat.exercise.Exercise;
import de.ro.testat.exercise.Exercises;
import de.ro.testat.io.OutputWriter;
import de.ro.testat.io.OutputWriter.MappingRow;
import de.ro.testat.io.SeedDeriver;
import de.ro.testat.io.WerkFile;
import de.ro.testat.latex.DocumentBuilder;
import de.ro.testat.testat.Templates;
import de.ro.testat.testat.Testat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Kommandozeile.
 *
 * <pre>
 *   java -jar testat-generator.jar gruppe_A.werk                              (Vorlage "kodierung")
 *   java -jar testat-generator.jar gruppe_A.werk --vorlage spruenge
 *   java -jar testat-generator.jar gruppe_A.werk --aufgaben "asm2bin:B,J bin2asm:R"
 *   java -jar testat-generator.jar --seed 1a2b... --matrikel 123456 --vorlage spruenge
 *   java -jar testat-generator.jar --liste
 *   java -jar testat-generator.jar ausfuehren loesung.s                       (Probelauf fuer eigenen Code)
 * </pre>
 */
public final class Main {

    private static final String USAGE = """
            Aufruf:
              java -jar testat-generator.jar <datei.werk> [Optionen]
              java -jar testat-generator.jar --seed <hex> --matrikel <nr> [Optionen]
              java -jar testat-generator.jar --liste
              java -jar testat-generator.jar ausfuehren <datei.s|datei.c|datei.txt>   (Probelauf, siehe ausfuehren -h)

            Optionen:
              --vorlage <id>       fertige Vorlage (Standard: kodierung), siehe --liste
              --aufgaben "<kette>" eigene Aufgabenkette statt Vorlage, z. B. "asm2bin:B,J asm2bin:S bin2asm"
              --titel "<text>"     Ueberschrift fuer --aufgaben (Standard: Testat)
              --out <ordner>       Ausgabeordner (Standard: Ordner der .werk-Datei)
              --seed / --matrikel  ein einzelnes Blatt mit bekanntem Seed (aus der Zuordnungsdatei)
                                   neu erzeugen; Vorlage bzw. Kette muss dieselbe sein wie damals.

            .werk-Datei:
              erste Zeile optional ein geheimer Schluessel (muss ein Nicht-Ziffer-Zeichen enthalten),
              danach eine Matrikelnummer (6 oder 7 Ziffern) pro Zeile. # leitet Kommentare ein.
            """;

    public static void main(String[] args) {
        de.ro.testat.probelauf.GrosserStack.aktivieren(); // tiefe Rekursion im C-Interpreter (nur JVM)
        try {
            System.exit(run(args));
        } catch (IllegalArgumentException | IllegalStateException e) {
            System.err.println("Fehler: " + e.getMessage());
            System.exit(2);
        } catch (Exception e) {
            System.err.println("Fehler: " + e);
            System.exit(1);
        }
    }

    static int run(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("ausfuehren")) {
            return de.ro.testat.probelauf.Probelauf.run(java.util.Arrays.copyOfRange(args, 1, args.length));
        }
        Map<String, String> opt = new HashMap<>();
        String werkArg = null;
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (a.equals("-h") || a.equals("--help")) {
                System.out.print(USAGE);
                return 0;
            }
            if (a.equals("--liste")) {
                printList();
                return 0;
            }
            if (a.startsWith("--")) {
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException("Option " + a + " braucht einen Wert.");
                }
                opt.put(a.substring(2), args[++i]);
            } else if (werkArg == null) {
                werkArg = a;
            } else {
                throw new IllegalArgumentException("Unerwartetes Argument: " + a);
            }
        }

        Testat testat = buildTestat(opt);
        Eingabe ein = opt.containsKey("seed") ? einzelnesBlatt(opt) : werkDatei(opt, werkArg);
        if (ein == null) {
            System.out.print(USAGE);
            return 2;
        }
        if (ein.matrikel().isEmpty()) {
            throw new IllegalArgumentException("Keine gueltige Matrikelnummer gefunden.");
        }
        System.out.println("Testat: " + testat.id() + "  [" + testat.describe() + "]");
        if (ein.key().isPresent()) {
            System.out.println("Schluessel gefunden: Blaetter sind ueber Schluessel + Matrikelnummer reproduzierbar.");
        } else if (ein.festerSeed() == null) {
            System.out.println("Kein Schluessel: zufaellige Seeds. Reproduzierbar nur ueber die Zuordnungsdatei.");
        }
        Blaetter blaetter = erzeugen(testat, seedKontext(opt, testat), ein);
        schreiben(testat, ein, blaetter);
        return 0;
    }

    /** Wer ein Blatt bekommt und wohin die Dateien kommen. festerSeed nur bei --seed (dann genau ein Blatt). */
    private record Eingabe(List<String> matrikel, Optional<String> key, Long festerSeed, String baseName, Path outDir) {
    }

    /** Erzeugte Blaetter und die Zeilen fuer die Zuordnungsdatei. */
    private record Blaetter(List<DocumentBuilder.Entry> entries, List<MappingRow> rows) {
    }

    /** --seed / --matrikel: ein Blatt mit bekanntem Seed (aus der Zuordnungsdatei) neu erzeugen. */
    private static Eingabe einzelnesBlatt(Map<String, String> opt) {
        String m = opt.get("matrikel");
        if (m == null || !WerkFile.MATRIKEL.matcher(m).matches()) {
            throw new IllegalArgumentException("--seed braucht --matrikel mit einer gueltigen Matrikelnummer.");
        }
        return new Eingabe(List.of(m), Optional.empty(), hexSeed(opt.get("seed")), "einzeln_" + m,
                Path.of(opt.getOrDefault("out", ".")));
    }

    /** Seed wie in der Zuordnungsdatei (Hexziffern), auch mit 0x davor. */
    static long hexSeed(String s) {
        String t = s.trim();
        if (t.startsWith("0x") || t.startsWith("0X")) {
            t = t.substring(2);
        }
        try {
            return Long.parseUnsignedLong(t, 16);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("--seed erwartet eine Hexzahl wie in der Zuordnungsdatei "
                    + "(z. B. 1a2b3c4d5e6f7081), gefunden: " + s);
        }
    }

    /** .werk-Datei (optional Schluessel, dann Matrikelnummern); null, wenn keine angegeben ist. */
    private static Eingabe werkDatei(Map<String, String> opt, String werkArg) throws IOException {
        if (werkArg == null) {
            return null;
        }
        Path werk = Path.of(werkArg);
        if (!Files.isRegularFile(werk)) {
            throw new IllegalArgumentException("Datei nicht gefunden: " + werk);
        }
        WerkFile wf = WerkFile.read(werk);
        wf.warnings().forEach(w -> System.out.println("Warnung: " + w));
        String fn = werk.getFileName().toString();
        String baseName = fn.contains(".") ? fn.substring(0, fn.lastIndexOf('.')) : fn;
        Path outDir = opt.containsKey("out") ? Path.of(opt.get("out")) : werk.toAbsolutePath().getParent();
        return new Eingabe(wf.matrikel(), wf.key(), null, baseName, outDir);
    }

    /** Ein Blatt je Matrikelnummer; Seed fest (--seed), aus Schluessel abgeleitet oder zufaellig. */
    private static Blaetter erzeugen(Testat testat, String kontext, Eingabe ein) {
        List<DocumentBuilder.Entry> entries = new ArrayList<>();
        List<MappingRow> rows = new ArrayList<>();
        for (String m : ein.matrikel()) {
            long seed = ein.festerSeed() != null ? ein.festerSeed()
                    : ein.key().map(k -> SeedDeriver.derive(k, kontext, m)).orElseGet(SeedDeriver::random);
            List<Exercise.Part> parts = testat.generate(seed);
            entries.add(new DocumentBuilder.Entry(m, seed, parts));
            List<String> fragen = new ArrayList<>();
            for (int i = 0; i < parts.size(); i++) {
                Exercise.Part p = parts.get(i);
                rows.add(new MappingRow(m, testat.id(), testat.describe(), seed, ein.key().isPresent(),
                        (i + 1) + " " + testat.exercises().get(i).describe(), p.frage(), p.antwort()));
                fragen.add(p.frage());
            }
            System.out.printf("  %s  seed %016x  %s%n", m, seed, String.join("  |  ", fragen));
        }
        return new Blaetter(entries, rows);
    }

    /** ShareLaTeX-Zip und Zuordnungsdatei, beide mit Zeitstempel im Namen. */
    private static void schreiben(Testat testat, Eingabe ein, Blaetter blaetter) throws IOException {
        Files.createDirectories(ein.outDir());
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        String name = ein.baseName() + "_" + testat.id() + "_" + stamp;
        Path zip = ein.outDir().resolve(name + ".zip");
        Path csv = ein.outDir().resolve(name + "_zuordnung.csv");
        OutputWriter.writeZip(zip, DocumentBuilder.build(testat, ein.baseName(), blaetter.entries()));
        OutputWriter.writeMapping(csv, blaetter.rows());

        System.out.println();
        System.out.println(ein.matrikel().size() + " Blaetter erzeugt.");
        System.out.println("  ShareLaTeX-Projekt: " + zip);
        System.out.println("    main.tex      -> Aufgabenblaetter zum Drucken");
        System.out.println("    loesungen.tex -> Loesungen (in ShareLaTeX: Menue -> Main document)");
        System.out.println("  Zuordnung:          " + csv + "   (nicht weitergeben, enthaelt Loesungen)");
    }

    /**
     * Thema fuer die Seed-Ableitung: bei einer Vorlage ihre id. Bei --aufgaben gehoeren Titel und
     * Aufgabenliste dazu - sonst haetten alle eigenen Testate dasselbe Thema "eigen", und ein
     * Studierender bekaeme in zwei verschiedenen eigenen Testaten (z. B. Probe- und echtes Testat)
     * an gleicher Stelle dieselbe Aufgabe.
     */
    static String seedKontext(Map<String, String> opt, Testat testat) {
        if (!opt.containsKey("aufgaben")) {
            return testat.id();
        }
        return "eigen|" + opt.getOrDefault("titel", "Testat").trim() + "|" + opt.get("aufgaben").trim().replaceAll("\\s+", " ");
    }

    private static Testat buildTestat(Map<String, String> opt) {
        if (opt.containsKey("aufgaben")) {
            if (opt.containsKey("vorlage")) {
                throw new IllegalArgumentException("--vorlage und --aufgaben bitte nicht gleichzeitig angeben.");
            }
            return Testat.builder("eigen", opt.getOrDefault("titel", "Testat"))
                    .addAll(Exercises.parseChain(opt.get("aufgaben")))
                    .build();
        }
        String id = opt.getOrDefault("vorlage", "kodierung");
        return Templates.get(id).orElseThrow(() -> new IllegalArgumentException(
                "Unbekannte Vorlage: " + id + " (verfuegbar: " + Templates.all().keySet() + ")"));
    }

    private static void printList() {
        System.out.println("Vorlagen (--vorlage):");
        Templates.all().forEach((id, s) -> {
            Testat t = s.get();
            System.out.printf("  %-18s %s%n  %-18s   %s%n", id, t.title(), "", t.describe());
        });
        System.out.println();
        System.out.println("Aufgabentypen (--aufgaben \"typ:parameter typ:parameter ...\"):");
        Exercises.help().forEach((id, h) -> System.out.printf("  %-18s %s%n", id, h));
        System.out.println();
        System.out.println("Befehlsauswahl: R, I, Shift, Load, jalr, S, B, U, J, ein Befehl (beq) oder Liste (B,J,S)");
    }
}
