package de.ro.testat.exercise.functions;

import de.ro.testat.exercise.Exercise;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * "Rekursive Funktion in RISC-V" (wie Uebung 3.4/3.7): Eine zufaellige rekursive C-Funktion
 * aus {@link RecursionForms} ist nach der Aufrufkonvention in Assembler zu uebersetzen, dazu
 * ein Hauptprogramm, das sie aufruft.
 *
 * <p>Kontrolle: Die Kontrollwerte berechnet der C-Interpreter aus genau dem C-Code der Aufgabe;
 * die Musterloesung muss im Simulator fuer alle Kontrollargumente dieselben Werte liefern,
 * auch im strengen Modus (t- und a-Register nach jedem Ruecksprung zerstoert), und sp sowie
 * alle s-Register wiederherstellen.
 */
public final class RecursionToAssembler implements Exercise {

    private final String auswahl;
    private final List<RecursionForms.Form> pool;

    public RecursionToAssembler() {
        this("zufall");
    }

    /** @param auswahl "zufall" oder Formen, z. B. "potenz,binom" */
    public RecursionToAssembler(String auswahl) {
        this.auswahl = auswahl == null || auswahl.isBlank() ? "zufall" : auswahl.trim();
        if (this.auswahl.equalsIgnoreCase("zufall")) {
            pool = List.of(RecursionForms.Form.values());
        } else {
            List<RecursionForms.Form> p = new ArrayList<>();
            for (String s : this.auswahl.split(",")) {
                String n = s.trim().toUpperCase(Locale.ROOT).replace('-', '_');
                RecursionForms.Form f = Arrays.stream(RecursionForms.Form.values()).filter(x -> x.name().equals(n)).findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("Unbekannte Form fuer rekursion: " + s + " (erlaubt: "
                                + formen() + " oder zufall)"));
                p.add(f);
            }
            pool = List.copyOf(p);
        }
    }

    static String formen() {
        return Arrays.stream(RecursionForms.Form.values()).map(x -> x.name().toLowerCase(Locale.ROOT)).collect(Collectors.joining(", "));
    }

    @Override
    public String id() {
        return "rekursion";
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
        RecursionForms.Form form = pool.get(rng.nextInt(pool.size()));
        RecursionForms.Instanz f = RecursionForms.erzeuge(form, rng);

        // Werte aus dem C-Interpreter, Musterloesung dagegen pruefen
        List<String> kontrolle = new ArrayList<>();
        for (int[] args : f.kontrolle()) {
            int w = ConventionCheck.cWert(f.cCode(), ConventionCheck.aufruf(f.name(), args));
            ConventionCheck.pruefe(f.funktion(), f.name(), f.params(), args, w);
            kontrolle.add("\\texttt{" + ConventionCheck.aufruf(f.name(), args) + "} = " + w);
        }
        String aufruf = ConventionCheck.aufruf(f.name(), f.aufgabe());
        int ergebnis = ConventionCheck.cWert(f.cCode(), aufruf);
        ConventionCheck.pruefe(f.funktion(), f.name(), f.params(), f.aufgabe(), ergebnis);

        StringBuilder a = new StringBuilder();
        a.append("Gegeben ist folgende rekursive C-Funktion:\n");
        a.append("\\begin{lstlisting}[language=C]\n").append(AsmText.lst(f.cCode())).append("\\end{lstlisting}\n");
        a.append("\\begin{enumerate}[label=\\alph*)]\n");
        a.append("\\item Implementieren Sie \\texttt{").append(f.name()).append("} in RISC-V-Assembler. Halten Sie sich an die "
                + "Aufrufkonventionen: ").append(argumentText(f.params())).append(" und das Ergebnis in \\texttt{a0}; "
                + "\\texttt{ra} und alle Werte, die \\\"uber einen Aufruf hinweg ben\\\"otigt werden, sichern Sie auf dem Stack. "
                + "F\\\"ur Multiplikation, Division und Rest d\\\"urfen Sie die M-Erweiterung verwenden.\n");
        a.append("\\item Schreiben Sie ein Hauptprogramm, das \\texttt{").append(aufruf)
                .append("} aufruft und danach mit \\texttt{ecall} (\\texttt{a7} = 10) endet.\n");
        a.append("\\end{enumerate}\n");

        AsmText loesung = ConventionCheck.main(f.name(), f.params(), f.aufgabe()).anhaengen(f.funktion());
        StringBuilder l = new StringBuilder();
        l.append("M\\\"ogliche L\\\"osung (Pseudobefehle wie \\texttt{li}, \\texttt{mv}, \\texttt{call}, \\texttt{ret} "
                + "sind ebenso richtig):\n");
        l.append(loesung.listing(false, true));
        l.append("{\\sloppy Kontrollwerte (Ergebnis in \\texttt{a0}): ").append(String.join(", ", kontrolle))
                .append(". Mit \\texttt{").append(aufruf).append("} muss am Ende \\texttt{a0} = ").append(ergebnis)
                .append(" sein.\\par}\n");
        l.append("\\textbf{Worauf achten:}\n\\begin{itemize}\n"
                + "\\item Stackrahmen am Anfang anlegen und vor \\texttt{ret} wieder freigeben (\\texttt{sp} am Ende wie am Anfang).\n"
                + "\\item \\texttt{ra} vor dem rekursiven Aufruf sichern und danach wiederherstellen.\n"
                + "\\item Nach einem Aufruf sind alle \\texttt{t}- und \\texttt{a}-Register (au\\ss{}er dem Ergebnis in \\texttt{a0}) "
                + "unbrauchbar; benutzte \\texttt{s}-Register selbst sichern.\n");
        for (String h : f.hinweise()) {
            l.append("\\item ").append(h).append('\n');
        }
        l.append("\\end{itemize}\n");

        String frage = f.cCode().replace("\n", " ").replaceAll(" +", " ").trim() + " | main: " + aufruf;
        String antwort = aufruf + " = " + ergebnis;
        return new Part("Rekursive Funktion in RISC-V", a.toString(), l.toString(), frage, antwort);
    }

    /** "Das Argument n steht in a0" bzw. "Die Argumente x, n stehen in a0, a1". */
    static String argumentText(List<String> params) {
        if (params.size() == 1) {
            return "Das Argument \\texttt{" + params.get(0) + "} steht in \\texttt{a0}";
        }
        StringBuilder p = new StringBuilder();
        StringBuilder r = new StringBuilder();
        for (int i = 0; i < params.size(); i++) {
            p.append(i == 0 ? "" : i == params.size() - 1 ? " und " : ", ").append("\\texttt{").append(params.get(i)).append("}");
            r.append(i == 0 ? "" : i == params.size() - 1 ? " und " : ", ").append("\\texttt{a").append(i).append("}");
        }
        return "Die Argumente " + p + " stehen in " + r;
    }
}
