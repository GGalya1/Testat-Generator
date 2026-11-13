package de.ro.testat.exercise.control;

import java.util.Random;
import java.util.stream.Collectors;

/**
 * "RISC-V zu Hochsprache": Assembler gegeben (mit Zeilennummern), C gesucht.
 *
 * <pre>
 *   new AssemblerToC()                 // beliebige Form, 2 Zusatzfragen
 *   new AssemblerToC("schleife")       // nur Schleifen
 *   new AssemblerToC("if-else", 1)
 * </pre>
 */
public final class AssemblerToC extends ControlExercise {

    public AssemblerToC() {
        this("zufall");
    }

    public AssemblerToC(String auswahl) {
        this(auswahl, 2);
    }

    public AssemblerToC(String auswahl, int fragen) {
        super(auswahl, fragen);
    }

    @Override
    public String id() {
        return "asm2c";
    }

    @Override
    public Part generate(Random rng) {
        Generated g = build(rng, false, Questions.Mode.ASM2C);

        String aufgabe = "\\\"Ubersetzen Sie den folgenden RISC-V-Assemblercode in C. "
                + "Sie d\\\"urfen Variablennamen frei w\\\"ahlen.\n"
                + asmListing(g.asm())
                + fragenAufgabe(g.fragen());

        String zuordnung = g.program().vars().stream()
                .map(v -> "\\texttt{" + g.regs().get(v.name()) + "} $\\rightarrow$ \\texttt{" + v.name() + "}")
                .collect(Collectors.joining(", "));
        StringBuilder l = new StringBuilder();
        l.append("M\\\"ogliche L\\\"osung (Register: ").append(zuordnung).append("):\n");
        l.append(cListing(g.program()));
        if (g.asm().scratchUsed()) {
            l.append("\\texttt{").append(g.scratch())
                    .append("} ist nur ein Hilfsregister f\\\"ur die Konstante im Vergleich und keine eigene Variable.\\\\\n");
        }
        l.append("\\textbf{Kontrollwerte am Ende:} ").append(kontrollwerte(g)).append(".\\\\\n");
        l.append("Jede \\\"aquivalente \\\"Ubersetzung ist richtig");
        if (g.shape() == Shape.FOR) {
            l.append(" (z.\\,B. while statt for)");
        }
        l.append(".\n");
        l.append(fragenLoesung(g.fragen()));

        String frage = g.shape().id() + ": " + CRenderer.oneLine(g.program())
                + (g.fragen().isEmpty() ? "" : " | Fragen: " + fragenIds(g));
        return new Part("RISC-V zu Hochsprache", aufgabe, l.toString(), frage, kurzWerte(g));
    }
}
