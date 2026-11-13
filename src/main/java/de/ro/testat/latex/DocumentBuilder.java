package de.ro.testat.latex;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static de.ro.testat.latex.Tex.f;

/**
 * Baut das ShareLaTeX-Projekt:
 * <pre>
 *   main.tex           Aufgabenblaetter (eine Seite pro Matrikelnummer) - zum Drucken
 *   loesungen.tex      Loesungen mit Zuordnungsschema - fuer Tutor:innen
 *   praeambel.tex      gemeinsame Pakete und Farben
 *   aufgaben/NNNNNN.tex
 *   loesungen/NNNNNN.tex
 * </pre>
 */
public final class DocumentBuilder {

    /** Ein Studierender mit fertig erzeugtem Inhalt. */
    public record Entry(String matrikel, long seed, String aufgabeTex, String loesungTex) {
    }

    public static final String PREAMBLE = """
            % Gemeinsame Praeambel fuer main.tex und loesungen.tex
            \\usepackage[T1]{fontenc}
            \\usepackage[utf8]{inputenc}
            \\usepackage[margin=1.5cm]{geometry}
            \\usepackage{amsmath}
            \\usepackage{xcolor}
            \\usepackage{tikz}
            \\usetikzlibrary{decorations.pathreplacing}
            \\setlength{\\parindent}{0pt}
            \\sloppy
            \\pagestyle{empty}

            % Farben: gleiche Farbe = gleiche Herkunft
            \\definecolor{cMn}{HTML}{4B5563}    % aus dem Mnemonic: opcode, funct3, funct7
            \\definecolor{cRd}{HTML}{D97706}    % rd
            \\definecolor{cRsA}{HTML}{2563EB}   % rs1
            \\definecolor{cRsB}{HTML}{16A34A}   % rs2
            \\definecolor{cSh}{HTML}{0891B2}    % shamt
            \\definecolor{cImmA}{HTML}{DC2626}  % Immediate-Gruppen
            \\definecolor{cImmB}{HTML}{9333EA}
            \\definecolor{cImmC}{HTML}{0D9488}
            \\definecolor{cImmD}{HTML}{B45309}
            \\definecolor{cImmGen}{HTML}{7C3AED}
            """;

    private static final String HINWEIS =
            "\\par\\vspace{1cm}\\textit{Hinweis:} Zur Hilfe k\\\"onnen Sie das ausgeteilte \\\"Ubersichtsblatt verwenden.\n";

    private DocumentBuilder() {
    }

    /** Liefert Pfad im Zip -> Dateiinhalt. */
    public static Map<String, String> build(String testatTitle, String werkName, List<Entry> entries) {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("praeambel.tex", PREAMBLE);

        StringBuilder main = new StringBuilder(head("Aufgabenbl\\\"atter: " + testatTitle + " (" + werkName + ")"));
        StringBuilder sol = new StringBuilder(head("L\\\"osungen: " + testatTitle + " (" + werkName + ")"));
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            if (i > 0) {
                main.append("\\clearpage\n");
                sol.append("\\clearpage\n");
            }
            main.append("\\input{aufgaben/").append(e.matrikel()).append("}\n");
            sol.append("\\input{loesungen/").append(e.matrikel()).append("}\n");

            files.put("aufgaben/" + e.matrikel() + ".tex",
                    header(testatTitle, e.matrikel(), null) + e.aufgabeTex() + HINWEIS);
            files.put("loesungen/" + e.matrikel() + ".tex",
                    header("L\\\"osung -- " + testatTitle, e.matrikel(), e.seed()) + e.loesungTex());
        }
        main.append("\\end{document}\n");
        sol.append("\\end{document}\n");
        files.put("main.tex", main.toString());
        files.put("loesungen.tex", sol.toString());
        return files;
    }

    private static String head(String comment) {
        return "% " + comment + "\n"
                + "\\documentclass[a4paper,11pt]{article}\n"
                + "\\input{praeambel}\n"
                + "\\begin{document}\n";
    }

    private static String header(String title, String matrikel, Long seed) {
        String right = "Matrikelnummer: \\textbf{\\texttt{" + matrikel + "}}";
        if (seed != null) {
            right += f(" \\quad {\\scriptsize Seed: \\texttt{%016x}}", seed);
        }
        return "\\noindent\\textbf{\\large Testat: " + title + "}\\hfill " + right + "\\par\\smallskip\\hrule\\medskip\n";
    }
}
