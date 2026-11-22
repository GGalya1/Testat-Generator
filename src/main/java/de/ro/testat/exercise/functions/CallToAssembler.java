package de.ro.testat.exercise.functions;

import de.ro.testat.exercise.Auswahl;
import de.ro.testat.exercise.Exercise;

import java.util.List;
import java.util.Random;

/**
 * "Funktionsaufrufe in RISC-V": Ein C-Programm aus einer Blattfunktion g (if/else oder
 * Schleife) und einer Funktion f, die g in einer Schleife, unter einer Bedingung oder zweimal
 * aufruft (siehe {@link CallChain}), ist nach der Aufrufkonvention zu uebersetzen. Geprueft wird
 * vor allem, ob Werte, die einen Aufruf ueberleben muessen, in s-Registern bzw. auf dem Stack
 * liegen.
 *
 * <p>Kontrolle wie bei {@link RecursionToAssembler}: Werte aus dem C-Interpreter, Musterloesung
 * im Simulator auch im strengen Modus (t- und a-Register nach jedem Ruecksprung zerstoert).
 */
public final class CallToAssembler implements Exercise {

    private final String auswahl;
    private final List<CallChain.Rufer> pool;

    public CallToAssembler() {
        this("zufall");
    }

    /** @param auswahl "zufall" oder Formen der aufrufenden Funktion, z. B. "summe,zwei_aufrufe" */
    public CallToAssembler(String auswahl) {
        this.auswahl = Auswahl.normal(auswahl);
        this.pool = Auswahl.formen(auswahl, CallChain.Rufer.class, false, "aufruf");
    }

    @Override
    public String id() {
        return "aufruf";
    }

    @Override
    public String describe() {
        return id() + "(" + auswahl + ")";
    }

    @Override
    public List<String> preamble() {
        return List.of("\\usepackage{amsmath}");
    }

    @Override
    public Part generate(Random rng) {
        CallChain.Programm p = CallChain.zufall(rng, pool, List.of(CallChain.Blatt.values()));
        CallChain.Funktion g = p.g();
        CallChain.Funktion f = p.f();
        String c = p.cCode();

        // Kontrolle: g allein, dann f (mit g) - jeweils gegen den C-Interpreter
        List<String> kg = ConventionCheck.kontrollwerte(c, g.asm(), g.name(), g.params(), g.kontrolle());
        List<String> kf = ConventionCheck.kontrollwerte(c, p.asm(), f.name(), f.params(), f.kontrolle());
        String aufruf = ConventionCheck.aufruf(f.name(), p.aufgabe());
        int ergebnis = ConventionCheck.geprueft(c, p.asm(), f.name(), f.params(), p.aufgabe());

        String gT = "\\texttt{" + g.name() + "}";
        String fT = "\\texttt{" + f.name() + "}";
        StringBuilder a = new StringBuilder();
        a.append("Gegeben sind folgende C-Funktionen:\n");
        a.append("\\begin{lstlisting}[language=C]\n").append(AsmText.lst(c)).append("\\end{lstlisting}\n");
        a.append("\\begin{enumerate}[label=\\alph*)]\n");
        a.append("\\item Implementieren Sie ").append(gT).append(" und ").append(fT).append(" in RISC-V-Assembler. Halten Sie sich "
                + "an die Aufrufkonventionen: Argumente stehen der Reihe nach in \\texttt{a0}, \\texttt{a1}, das Ergebnis in "
                + "\\texttt{a0}. Eine aufgerufene Funktion darf alle \\texttt{t}- und \\texttt{a}-Register ver\\\"andern, muss "
                + "aber \\texttt{sp} und alle \\texttt{s}-Register wiederherstellen. F\\\"ur die Multiplikation d\\\"urfen Sie "
                + "die M-Erweiterung verwenden.\n");
        a.append(ConventionCheck.hauptprogrammItem(aufruf));
        a.append("\\end{enumerate}\n");

        AsmText loesung = ConventionCheck.main(f.name(), f.params(), p.aufgabe()).anhaengen(p.asm());
        StringBuilder l = new StringBuilder();
        l.append(ConventionCheck.LOESUNG_EINLEITUNG).append("; die Funktionen d\\\"urfen in beliebiger Reihenfolge stehen):\n");
        l.append(loesung.listing(false, true));
        l.append("{\\sloppy Kontrollwerte: ").append(String.join(", ", kg)).append("; ").append(String.join(", ", kf))
                .append(". Mit \\texttt{").append(aufruf).append("} muss am Ende \\texttt{a0} = ").append(ergebnis)
                .append(" sein.\\par}\n");
        l.append("\\textbf{Worauf achten:}\n\\begin{itemize}\n");
        l.append("\\item ").append(gT).append(" ruft nichts auf (Blattfunktion): \\texttt{ra} bleibt erhalten, ein Stackrahmen "
                + "ist nicht n\\\"otig, solange nur \\texttt{t}- und \\texttt{a}-Register benutzt werden. Benutzt ").append(gT)
                .append(" \\texttt{s}-Register, muss es diese sichern.\n");
        l.append("\\item ").append(fT).append(" muss \\texttt{ra} sichern, weil \\texttt{jal ra, ").append(g.name())
                .append("} es \\\"uberschreibt; Stackrahmen am Ende wieder freigeben.\n");
        l.append("\\item Nach \\texttt{jal ra, ").append(g.name()).append("} sind alle \\texttt{t}- und \\texttt{a}-Register "
                + "au\\ss{}er \\texttt{a0} als zerst\\\"ort anzusehen -- auch wenn die eigene Fassung von ").append(gT)
                .append(" sie zuf\\\"allig nicht ver\\\"andert. Verl\\\"asst sich ").append(fT).append(" darauf, ist das ein Fehler.\n");
        for (String h : p.hinweise()) {
            l.append("\\item ").append(h).append('\n');
        }
        l.append("\\end{itemize}\n");

        String frage = c.replace("\n", " ").replaceAll(" +", " ").trim() + " | main: " + aufruf;
        String antwort = aufruf + " = " + ergebnis;
        return new Part("Funktionsaufrufe in RISC-V", a.toString(), l.toString(), frage, antwort);
    }
}
