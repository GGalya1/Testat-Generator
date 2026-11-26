package de.ro.testat.probelauf;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Probelauf fuer Studierende: fuehrt eigenen RISC-V-Assembler- oder C-Code aus und zeigt
 * Syntaxfehler, Laufzeitfehler und die Werte am Ende. Es wird nichts mit einer Aufgabe
 * verglichen und nichts als richtig oder falsch bewertet.
 *
 * <pre>
 *   java -jar testat-generator-probelauf.jar loesung.s
 *   java -jar testat-generator.jar ausfuehren loesung.txt --c --schritte
 * </pre>
 */
public final class Probelauf {

    public enum Sprache { ASM, C }

    /** Standard-Schrittlimits und Laenge der Befehlsliste bei --schritte (auch fuer die Webseite). */
    public static final int MAX_SCHRITTE_ASM = 100_000;
    public static final int MAX_SCHRITTE_C = 1_000_000;
    public static final int SCHRITTE_ANZEIGEN = 300;

    public static final String USAGE = """
            Probelauf (eigenen Code ausfuehren, ohne Bewertung):
              java -jar testat-generator-probelauf.jar <datei> [--asm | --c] [--schritte] [--streng] [--max <n>]
              java -jar testat-generator.jar ausfuehren <datei> [...]      (dasselbe im Generator-jar)

              <datei>      .s/.asm = RISC-V-Assembler, .c = C; sonst (z. B. .txt) wird die Sprache erkannt
              --asm, --c   Sprache erzwingen
              --schritte   (nur Assembler) jeden ausgefuehrten Befehl mit geaenderten Registern zeigen
              --streng     (nur Assembler) strenger Modus: zusaetzlich die Aufrufkonvention pruefen
                           (sp, ra und s0-s11 erhalten; t0-t6 und a1-a7 nach einem Aufruf nicht weiterverwenden)
              --max <n>    Schrittlimit (Standard 100000 fuer Assembler, 1000000 fuer C)
            """;

    private Probelauf() {
    }

    /** Einstieg fuer das eigene Studierenden-jar: java -jar testat-generator-probelauf.jar datei.s */
    public static void main(String[] args) {
        GrosserStack.aktivieren(); // tiefe Rekursion im C-Interpreter
        try {
            System.exit(run(args));
        } catch (IllegalArgumentException e) {
            System.err.println("Fehler: " + e.getMessage());
            System.exit(2);
        } catch (IOException e) {
            System.err.println("Datei kann nicht gelesen werden: " + e.getMessage());
            System.exit(2);
        }
    }

    /** Einstieg aus Main (args ohne das Wort "ausfuehren") und aus {@link #main}. Liefert den Exit-Code. */
    public static int run(String[] args) throws IOException {
        String datei = null;
        Sprache sprache = null;
        boolean schritte = false;
        boolean streng = false;
        Integer max = null;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--asm" -> sprache = Sprache.ASM;
                case "--c" -> sprache = Sprache.C;
                case "--schritte" -> schritte = true;
                case "--streng" -> streng = true;
                case "--max" -> {
                    if (i + 1 >= args.length) {
                        throw new IllegalArgumentException("--max braucht eine Zahl");
                    }
                    max = Integer.parseInt(args[++i]);
                }
                case "-h", "--help" -> {
                    System.out.print(USAGE);
                    return 0;
                }
                default -> {
                    if (datei != null) {
                        throw new IllegalArgumentException("Unerwartetes Argument: " + args[i]);
                    }
                    datei = args[i];
                }
            }
        }
        if (datei == null) {
            System.out.print(USAGE);
            return 2;
        }
        Path p = Path.of(datei);
        if (!Files.isRegularFile(p)) {
            throw new IllegalArgumentException("Datei nicht gefunden: " + p.toAbsolutePath());
        }
        String quelle;
        try {
            quelle = Files.readString(p, StandardCharsets.UTF_8);
        } catch (java.nio.charset.MalformedInputException e) {
            quelle = Files.readString(p, StandardCharsets.ISO_8859_1); // z. B. mit altem Windows-Editor gespeichert
        }
        if (!quelle.isEmpty() && quelle.charAt(0) == '\uFEFF') {
            quelle = quelle.substring(1);
        }
        boolean erkannt = sprache == null;
        if (sprache == null) {
            sprache = erkenne(p.getFileName().toString(), quelle);
        }
        Bericht b = sprache == Sprache.ASM
                ? assembler(quelle, max == null ? MAX_SCHRITTE_ASM : max, schritte ? SCHRITTE_ANZEIGEN : 0, streng)
                : c(quelle, max == null ? MAX_SCHRITTE_C : max);
        System.out.println("=== Probelauf: " + p.getFileName() + " (" + (sprache == Sprache.ASM ? "RISC-V-Assembler" : "C")
                + (erkannt ? ", erkannt" : "") + ") ===");
        System.out.println();
        System.out.print(b.text());
        return b.fehler() ? 1 : 0;
    }

    /** Ergebnis als Text; fehler = Syntax- oder Laufzeitfehler. */
    public record Bericht(String text, boolean fehler) {
    }

    /** Sprache nur nach dem Inhalt (Webseite, dort gibt es keinen Dateinamen). */
    public static Sprache erkenneAmInhalt(String quelle) {
        return erkenne("", quelle);
    }

    /** Sprache nach Dateiendung, sonst nach Inhalt (C: Zeilen mit ';' am Ende oder geschweifte Klammern). */
    static Sprache erkenne(String name, String quelle) {
        String n = name.toLowerCase(Locale.ROOT);
        if (n.endsWith(".s") || n.endsWith(".asm")) {
            return Sprache.ASM;
        }
        if (n.endsWith(".c") || n.endsWith(".h")) {
            return Sprache.C;
        }
        for (String z : quelle.split("\n")) {
            String t = z.replaceAll("//.*", "").replaceAll("#.*", "").trim();
            if (t.endsWith(";") || t.contains("{") || t.contains("}")) {
                return Sprache.C;
            }
        }
        return Sprache.ASM;
    }

    // ------------------------------------------------------------------ Assembler

    public static Bericht assembler(String quelle, int maxSchritte, int traceMax) {
        return assembler(quelle, maxSchritte, traceMax, false);
    }

    /** Wie {@link #assembler(String, int, int)}; streng = zusaetzlich die Aufrufkonvention pruefen (--streng). */
    public static Bericht assembler(String quelle, int maxSchritte, int traceMax, boolean streng) {
        String[] zeilen = quelle.replace("\r", "").split("\n", -1);
        StringBuilder sb = new StringBuilder();
        RiscvParser.Programm prog = RiscvParser.parse(quelle);
        List<Meldung> syntax = prog.meldungen();
        if (!prog.fehlerfrei()) {
            long n = syntax.stream().filter(m -> m.art() == Meldung.Art.FEHLER).count();
            sb.append("Syntax: ").append(n).append(" Fehler").append(" - das Programm wurde nicht ausgefuehrt.\n\n");
            syntax.forEach(m -> sb.append(m.format(zeilen)).append("\n\n"));
            return new Bericht(sb.toString(), true);
        }
        sb.append("Syntax: keine Fehler (").append(prog.befehle().size()).append(" Befehle).\n");
        RiscvMaschine.Ergebnis e = streng
                ? RiscvMaschine.runMitKonvention(prog, maxSchritte, traceMax)
                : RiscvMaschine.run(prog, maxSchritte, traceMax);
        sb.append("Ablauf: ").append(e.endeGrund()).append(", ").append(e.schritte()).append(" Befehle ausgefuehrt.\n");
        if (streng) {
            sb.append("Aufrufkonvention (strenger Modus): ");
            if (e.aufrufe() == 0) {
                sb.append("kein Funktionsaufruf (jal/jalr/call mit ra) - nichts zu pruefen.\n");
            } else {
                sb.append(e.aufrufe()).append(e.aufrufe() == 1 ? " Aufruf" : " Aufrufe").append(" geprueft, ")
                        .append(e.konventionsfehler() == 0 ? "keine Verstoesse"
                                : e.konventionsfehler() + (e.konventionsfehler() == 1 ? " Verstoss (siehe unten)" : " Verstoesse (siehe unten)"))
                        .append(".\n");
            }
        }
        sb.append("\n");

        sb.append("Register am Ende (nur geaenderte):\n");
        boolean irgendwas = false;
        for (int r = 1; r < 32; r++) {
            if (e.ende()[r] != e.start()[r]) {
                irgendwas = true;
                sb.append("  ").append(links(RiscvParser.ABI[r], 4)).append(" x").append(links(Integer.toString(r), 2))
                        .append(" = ").append(rechts(Integer.toString(e.ende()[r]), 11))
                        .append("   (0x").append(hex8(e.ende()[r])).append(')')
                        .append(r == 2 ? "   Start: 0x" + hex8(e.start()[r]) : "").append('\n');
            }
        }
        if (!irgendwas) {
            sb.append("  (kein Register geaendert)\n");
        }
        if (!e.geschrieben().isEmpty()) {
            sb.append("\nSpeicher (geschriebene Woerter):\n");
            e.geschrieben().forEach((a, v) -> sb.append("  0x").append(hex8(a)).append(" = ")
                    .append(rechts(Integer.toString(v), 11)).append("   (0x").append(hex8(v)).append(")\n"));
        }
        if (!e.ausgabe().isEmpty()) {
            sb.append("\nAusgabe (ecall):\n").append(einruecken(e.ausgabe())).append("\n");
        }
        meldungen(sb, syntax, e.meldungen(), zeilen);
        if (!e.trace().isEmpty()) {
            sb.append("\nAusgefuehrte Befehle").append(e.trace().size() < e.schritte() ? " (die ersten " + e.trace().size() + ")" : "")
                    .append(":\n");
            e.trace().forEach(t -> sb.append("  ").append(t).append("\n"));
        }
        return new Bericht(sb.toString(), e.abgebrochen() || e.konventionsfehler() > 0);
    }

    // ------------------------------------------------------------------ C

    public static Bericht c(String quelle, int maxSchritte) {
        return c(quelle, maxSchritte, CInterpreter.MAX_TIEFE);
    }

    /** Wie {@link #c(String, int)}, mit eigener Grenze fuer die Rekursionstiefe (Webseite). */
    public static Bericht c(String quelle, int maxSchritte, int maxTiefe) {
        String[] zeilen = quelle.replace("\r", "").split("\n", -1);
        StringBuilder sb = new StringBuilder();
        CParser.Programm prog;
        try {
            prog = CParser.parse(quelle);
        } catch (CParser.SyntaxFehler f) {
            sb.append("Syntax: Fehler - das Programm wurde nicht ausgefuehrt.\n\n");
            sb.append(f.meldung().format(zeilen)).append("\n\n");
            sb.append("(Es wird nur der erste Syntaxfehler gezeigt.)\n");
            return new Bericht(sb.toString(), true);
        }
        sb.append("Syntax: keine Fehler.\n");
        CInterpreter.Ergebnis e = CInterpreter.run(prog, maxSchritte, maxTiefe);
        sb.append("Ablauf: ").append(e.endeGrund()).append(", ").append(e.schritte()).append(" Schritte.\n");
        if (e.rueckgabe() != null) {
            sb.append("Rueckgabewert: ").append(e.rueckgabe()).append("\n");
        }
        sb.append("\nVariablen am Ende:\n");
        if (e.variablen().isEmpty()) {
            sb.append("  (keine)\n");
        }
        e.variablen().forEach((k, v) -> sb.append("  ").append(links(k, 10)).append(" = ").append(v).append('\n'));
        if (!e.ausgabe().isEmpty()) {
            sb.append("\nAusgabe (printf):\n").append(einruecken(e.ausgabe())).append("\n");
        }
        meldungen(sb, List.of(), e.meldungen(), zeilen);
        return new Bericht(sb.toString(), e.abgebrochen());
    }

    private static void meldungen(StringBuilder sb, List<Meldung> a, List<Meldung> b, String[] zeilen) {
        if (a.isEmpty() && b.isEmpty()) {
            return;
        }
        sb.append("\n");
        for (List<Meldung> l : List.of(a, b)) {
            for (Meldung m : l) {
                sb.append(m.format(zeilen)).append("\n");
            }
        }
    }

    // Spalten ohne String.format-Breiten und ohne %n: dieselbe Ausgabe auch im Browser (TeaVM)

    /** Rechts mit Leerzeichen auf n Zeichen auffuellen (wie %-ns). */
    static String links(String s, int n) {
        return s.length() >= n ? s : s + " ".repeat(n - s.length());
    }

    /** Links mit Leerzeichen auf n Zeichen auffuellen (wie %ns / %nd). */
    static String rechts(String s, int n) {
        return s.length() >= n ? s : " ".repeat(n - s.length()) + s;
    }

    /** 8 Hex-Ziffern, Grossbuchstaben (wie %08X). */
    static String hex8(int v) {
        String h = Integer.toHexString(v).toUpperCase(Locale.ROOT);
        return "0".repeat(8 - h.length()) + h;
    }

    private static String einruecken(String s) {
        String t = s.endsWith("\n") ? s.substring(0, s.length() - 1) : s;
        return "  " + t.replace("\n", "\n  ");
    }
}
