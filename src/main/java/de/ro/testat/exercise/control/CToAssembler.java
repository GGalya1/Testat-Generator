package de.ro.testat.exercise.control;

import java.util.Random;
import java.util.stream.Collectors;

/**
 * "Uebersetzung in RISC-V": C-Programm gegeben, Assembler gesucht.
 *
 * <pre>
 *   new CToAssembler()               // beliebige Form
 *   new CToAssembler("if")           // nur Verzweigungen
 * </pre>
 */
public final class CToAssembler extends ControlExercise {

    public CToAssembler() {
        this("zufall");
    }

    public CToAssembler(String auswahl) {
        super(auswahl);
    }

    @Override
    public String id() {
        return "c2asm";
    }

    @Override
    public Part generate(Random rng) {
        Generated g = build(rng, true);
        String zuordnung = g.program().vars().stream()
                .map(v -> "\\texttt{" + v.name() + "} in \\texttt{" + g.regs().get(v.name()) + "}")
                .collect(Collectors.joining(", "));
        int last = zuordnung.lastIndexOf(", ");
        zuordnung = zuordnung.substring(0, last) + " und" + zuordnung.substring(last + 1);

        String aufgabe = "Schreiben Sie ein RISC-V-Assemblerprogramm, das das folgende C-Programm umsetzt. "
                + "Nutzen Sie folgende Register f\\\"ur die vorkommenden Variablen, alle "
                + g.program().vars().size() + " m\\\"ussen am Ende der Ausf\\\"uhrung den korrekten Wert enthalten:\\\\\n"
                + zuordnung + ".\\\\\n"
                + "Wenn Sie weitere Register verwenden, m\\\"ussen Sie den urspr\\\"unglichen Wert wiederherstellen.\n"
                + cListing(g.program())
                + "\\textbf{Hinweis:} Die \\textbf{M-Extension} (f\\\"ur Multiplikation) ist verf\\\"ugbar und darf verwendet werden.\n";

        StringBuilder l = new StringBuilder();
        l.append("Musterl\\\"osung (andere korrekte L\\\"osungen sind m\\\"oglich):\n");
        l.append(asmListing(g.asm()));
        l.append("\\textbf{Kontrollwerte am Ende:} ").append(kontrollwerte(g)).append(".\n");
        l.append("\\par\\medskip\\textbf{Bewertung:}\n\\begin{itemize}\n");
        l.append("\\item Initialisierung und Multiplikation (\\texttt{mul}) korrekt\n");
        if (g.shape().isLoop()) {
            l.append("\\item Schleifenbedingung korrekt umgesetzt (Abbruch bzw. bedingter R\\\"ucksprung, "
                    + "ggf. negierte Bedingung)\n");
            l.append("\\item Ablauf korrekt: ").append(switch (g.shape()) {
                case DO_WHILE -> "Rumpf wird vor der ersten Pr\\\"ufung ausgef\\\"uhrt";
                case FOR -> "Z\\\"ahler initialisiert, Inkrement am Ende des Rumpfes, Pr\\\"ufung vor jedem Durchlauf";
                default -> "Pr\\\"ufung vor jedem Durchlauf, R\\\"ucksprung zur Pr\\\"ufung";
            }).append("\n");
        } else {
            l.append("\\item Bedingung(en) korrekt als Sprung umgesetzt (negierte Bedingung, "
                    + "bei $>$/$\\leq$ vertauschte Operanden)\n");
            l.append("\\item Zweige korrekt getrennt (nach einem ausgef\\\"uhrten Zweig Sprung \\\"uber die anderen)\n");
        }
        if (g.asm().scratchSaved()) {
            l.append("\\item Zus\\\"atzlich verwendete Register (hier \\texttt{").append(g.scratch())
                    .append("} f\\\"ur die Konstante) gesichert und wiederhergestellt\n");
        }
        l.append("\\item Am Ende stehen die Kontrollwerte in den vorgegebenen Registern\n");
        l.append("\\end{itemize}\n");

        String frage = g.shape().id() + ": " + CRenderer.oneLine(g.program());
        return new Part("\\\"Ubersetzung in RISC-V", aufgabe, l.toString(), frage, kurzWerte(g));
    }
}
