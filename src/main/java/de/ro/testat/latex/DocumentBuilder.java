package de.ro.testat.latex;

import de.ro.testat.exercise.Exercise;
import de.ro.testat.testat.Testat;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static de.ro.testat.latex.Tex.f;

/**
 * Baut das ShareLaTeX-Projekt auf Basis der TU-Klasse {@code tudaexercise}:
 * <pre>
 *   main.tex            Aufgabenblaetter (solution=false) - zum Drucken
 *   loesungen.tex       dieselben Blaetter mit Loesungen (solution=true) - fuer Tutor:innen
 *   praeambel.tex       gemeinsame Praeambel (aus src/main/resources/latex)
 *   kopf.tex            Autor:innen, Semester, Kurztitel (aus src/main/resources/latex)
 *   riscv.tex           listings-Stil fuer RISC-V (aus src/main/resources/latex)
 *   zusatz.tex          Pakete, die die Aufgaben dieses Testats brauchen (erzeugt)
 *   blaetter/NNNNNN.tex ein Blatt pro Matrikelnummer, Aufgaben + Loesungen
 * </pre>
 * main.tex und loesungen.tex binden dieselben Blaetter ein; die Klassenoption
 * {@code solution} entscheidet, ob die {@code solution}-Umgebungen gedruckt werden.
 */
public final class DocumentBuilder {

    /** Ein Studierender mit seinen Aufgaben. */
    public record Entry(String matrikel, long seed, List<Exercise.Part> parts) {
    }

    /** Statische Dateien, die unveraendert ins Projekt kopiert werden. */
    private static final List<String> RESOURCES = List.of("praeambel.tex", "kopf.tex", "riscv.tex");

    /**
     * Klassenoptionen wie in der TU-Vorlage; LOESUNG = true/false fuer die Loesungen.
     * Ohne String.format: das "%%" darin bringt den Formatter von TeaVM (Browser) durcheinander.
     */
    private static final String DOCUMENTCLASS = """
            \\documentclass[
                german,
                points=true,% fuer die Aktivierung der Punktereferenzen, funktioniert nicht fuer TeX Versionen vor 2020
                match-task-subtask-syntax,
                accentcolor=TUDa-2b,
                textaccentcolor=TUDa-2b,
                identbarcolor=TUDa-2b,
                solution=LOESUNG
            ]{tudaexercise}
            """;

    private static final String HINWEIS =
            "\\textit{Hinweis:} Zur Hilfe k\\\"onnen Sie das ausgeteilte \\\"Ubersichtsblatt verwenden.\\par\\medskip";

    private DocumentBuilder() {
    }

    /** Liefert Pfad im Zip -> Dateiinhalt. */
    public static Map<String, String> build(Testat testat, String werkName, List<Entry> entries) {
        return build(testat, werkName, entries, DocumentBuilder::resource);
    }

    /** Namen der statischen Dateien (praeambel.tex, ...), die {@code ressourcen} liefern muss. */
    public static List<String> resourceNames() {
        return RESOURCES;
    }

    /**
     * Wie {@link #build(Testat, String, List)}, aber die statischen Dateien kommen von
     * {@code ressourcen} (Name -> Inhalt) statt aus dem Klassenpfad. Fuer den Browser: dort gibt
     * es kein getResourceAsStream, die Webseite laedt die Dateien selbst und reicht sie herein.
     */
    public static Map<String, String> build(Testat testat, String werkName, List<Entry> entries,
                                            java.util.function.Function<String, String> ressourcen) {
        Map<String, String> files = new LinkedHashMap<>();
        for (String r : RESOURCES) {
            files.put(r, ressourcen.apply(r));
        }
        files.put("zusatz.tex", zusatz(testat));

        StringBuilder body = new StringBuilder();
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            if (i > 0) {
                body.append("\\clearpage\n");
            }
            body.append("\\input{blaetter/").append(e.matrikel()).append("}\n");
            files.put("blaetter/" + e.matrikel() + ".tex", blatt(testat, e));
        }
        // Titel und Dateiname koennen vom Benutzer kommen: im Kommentar nur ohne Zeilenumbruch
        String comment = "% " + einzeilig(testat.title()) + " (" + einzeilig(werkName) + "), Vorlage: " + testat.id() + "\n";
        files.put("main.tex", comment + "% Aufgabenblaetter zum Drucken\n" + document(false, body));
        files.put("loesungen.tex", comment + "% Loesungen fuer Tutor:innen\n" + document(true, body));
        return files;
    }

    private static String document(boolean solution, CharSequence body) {
        return DOCUMENTCLASS.replace("LOESUNG", Boolean.toString(solution)) + "\\input{praeambel}\n\n\\begin{document}\n" + body + "\\end{document}\n";
    }

    /** Ein Blatt: Titelbox mit Matrikelnummer, dann die Aufgaben in tudaexercise-Umgebungen. */
    static String blatt(Testat testat, Entry e) {
        StringBuilder L = new StringBuilder();
        L.append("% Matrikelnummer ").append(e.matrikel()).append(f(", Seed %016x\n", e.seed()));
        L.append("\\title[\\TestatKurztitel]{").append(Tex.text(testat.title())).append("}\n");
        L.append("\\subtitle{Matrikelnummer: \\texttt{").append(e.matrikel()).append('}')
                .append(f("\\IfSolutionT{\\quad Seed: \\texttt{%016x}}}\n", e.seed()));
        L.append("\\author{\\TestatAutoren}\n");
        L.append("\\term{\\TestatSemester}\n");
        L.append("\\setcounter{task}{0}\n");
        L.append("\\BlattTitel\n");
        L.append("\\IfSolutionF{").append(HINWEIS).append("}\n\n");

        List<Exercise.Part> parts = e.parts();
        for (int i = 0; i < parts.size(); i++) {
            Exercise.Part p = parts.get(i);
            if (i > 0) {
                L.append("\\IfSolutionT{\\clearpage}\n"); // im Loesungsdokument jede Aufgabe auf eigener Seite
            }
            L.append("\\begin{task}{").append(p.titel()).append("}\n");
            L.append(p.aufgabe());
            L.append("\\begin{solution}\n").append(p.loesung()).append("\\end{solution}\n");
            L.append("\\end{task}\n\n");
        }
        return L.toString();
    }

    private static String einzeilig(String s) {
        return s.replace('\r', ' ').replace('\n', ' ');
    }

    private static String zusatz(Testat testat) {
        StringBuilder sb = new StringBuilder("% Vom Generator erzeugt: Pakete und Definitionen fuer die Aufgaben\n");
        sb.append("% dieses Testats (").append(testat.describe()).append(")\n");
        for (String line : testat.preamble()) {
            sb.append(line).append('\n');
        }
        return sb.toString();
    }

    private static String resource(String name) {
        try (InputStream in = DocumentBuilder.class.getResourceAsStream("/latex/" + name)) {
            if (in == null) {
                throw new IllegalStateException("Ressource fehlt: latex/" + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
