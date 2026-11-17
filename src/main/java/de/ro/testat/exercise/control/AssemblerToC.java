package de.ro.testat.exercise.control;

import java.util.Random;
import java.util.stream.Collectors;

/**
 * "RISC-V zu Hochsprache": Assembler gegeben (mit Zeilennummern), C gesucht.
 *
 * <pre>
 *   new AssemblerToC()                 // beliebige Form
 *   new AssemblerToC("schleife")       // nur Schleifen
 * </pre>
 */
public final class AssemblerToC extends ControlExercise {

    public AssemblerToC() {
        this("zufall");
    }

    public AssemblerToC(String auswahl) {
        super(auswahl);
    }

    @Override
    public String id() {
        return "asm2c";
    }

    @Override
    public Part generate(Random rng) {
        Generated g = build(rng, false);

        String aufgabe = "\\\"Ubersetzen Sie den folgenden RISC-V-Assemblercode in C. "
                + "Sie d\\\"urfen Variablennamen frei w\\\"ahlen.\n"
                + asmListing(g.asm());

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

        String frage = g.shape().id() + ": " + CRenderer.oneLine(g.program());
        return new Part("RISC-V zu Hochsprache", aufgabe, l.toString(), frage, kurzWerte(g));
    }
}
