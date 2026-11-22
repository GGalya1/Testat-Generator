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

        // ---------------- Eingabe bestimmen
        List<String> matrikel;
        Optional<String> key;
        Map<String, Long> fixedSeeds = new HashMap<>();
        String baseName;
        Path outDir;

        if (opt.containsKey("seed")) {
            String m = opt.get("matrikel");
            if (m == null || !WerkFile.MATRIKEL.matcher(m).matches()) {
                throw new IllegalArgumentException("--seed braucht --matrikel mit einer gueltigen Matrikelnummer.");
            }
            matrikel = List.of(m);
            key = Optional.empty();
            fixedSeeds.put(m, Long.parseUnsignedLong(opt.get("seed"), 16));
            baseName = "einzeln_" + m;
            outDir = Path.of(opt.getOrDefault("out", "."));
        } else {
            if (werkArg == null) {
                System.out.print(USAGE);
                return 2;
            }
            Path werk = Path.of(werkArg);
            if (!Files.isRegularFile(werk)) {
                throw new IllegalArgumentException("Datei nicht gefunden: " + werk);
            }
            WerkFile wf = WerkFile.read(werk);
            wf.warnings().forEach(w -> System.out.println("Warnung: " + w));
            matrikel = wf.matrikel();
            key = wf.key();
            String fn = werk.getFileName().toString();
            baseName = fn.contains(".") ? fn.substring(0, fn.lastIndexOf('.')) : fn;
            outDir = opt.containsKey("out") ? Path.of(opt.get("out")) : werk.toAbsolutePath().getParent();
        }

        if (matrikel.isEmpty()) {
            throw new IllegalArgumentException("Keine gueltige Matrikelnummer gefunden.");
        }
        System.out.println("Testat: " + testat.id() + "  [" + testat.describe() + "]");
        if (key.isPresent()) {
            System.out.println("Schluessel gefunden: Blaetter sind ueber Schluessel + Matrikelnummer reproduzierbar.");
        } else if (fixedSeeds.isEmpty()) {
            System.out.println("Kein Schluessel: zufaellige Seeds. Reproduzierbar nur ueber die Zuordnungsdatei.");
        }

        // ---------------- Erzeugen
        String kontext = seedKontext(opt, testat);
        List<DocumentBuilder.Entry> entries = new ArrayList<>();
        List<MappingRow> rows = new ArrayList<>();
        for (String m : matrikel) {
            long seed = fixedSeeds.containsKey(m) ? fixedSeeds.get(m)
                    : key.map(k -> SeedDeriver.derive(k, kontext, m)).orElseGet(SeedDeriver::random);
            List<Exercise.Part> parts = testat.generate(seed);
            entries.add(new DocumentBuilder.Entry(m, seed, parts));
            List<String> fragen = new ArrayList<>();
            for (int i = 0; i < parts.size(); i++) {
                Exercise.Part p = parts.get(i);
                rows.add(new MappingRow(m, testat.id(), testat.describe(), seed, key.isPresent(),
                        (i + 1) + " " + testat.exercises().get(i).describe(), p.frage(), p.antwort()));
                fragen.add(p.frage());
            }
            System.out.printf("  %s  seed %016x  %s%n", m, seed, String.join("  |  ", fragen));
        }

        // ---------------- Schreiben
        Files.createDirectories(outDir);
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        String name = baseName + "_" + testat.id() + "_" + stamp;
        Path zip = outDir.resolve(name + ".zip");
        Path csv = outDir.resolve(name + "_zuordnung.csv");
        OutputWriter.writeZip(zip, DocumentBuilder.build(testat, baseName, entries));
        OutputWriter.writeMapping(csv, rows);

        System.out.println();
        System.out.println(matrikel.size() + " Blaetter erzeugt.");
        System.out.println("  ShareLaTeX-Projekt: " + zip);
        System.out.println("    main.tex      -> Aufgabenblaetter zum Drucken");
        System.out.println("    loesungen.tex -> Loesungen (in ShareLaTeX: Menue -> Main document)");
        System.out.println("  Zuordnung:          " + csv + "   (nicht weitergeben, enthaelt Loesungen)");
        return 0;
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
