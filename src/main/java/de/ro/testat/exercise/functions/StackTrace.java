package de.ro.testat.exercise.functions;

import de.ro.testat.exercise.Exercise;
import de.ro.testat.probelauf.RiscvMaschine;
import de.ro.testat.probelauf.RiscvParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * "Stack nachvollziehen": Gegeben ist ein Programm mit rekursiver Funktion (mit Adressen wie in
 * RARS). Gefragt sind sp und der Stackinhalt, wenn der Basisfall zum ersten Mal erreicht wird,
 * und das Ergebnis. Die Werte kommen aus dem Simulator (Haltepunkt) und werden mit dem
 * erwarteten Aufbau der Stackrahmen verglichen.
 */
public final class StackTrace implements Exercise {

    static final List<Integer> SP_START = List.of(0x7FFF_EFFC, 0x7FFF_F000, 0x0001_0000, 0x0000_2000);

    /** Ein Wort auf dem Stack. */
    record Eintrag(int adresse, int wert, String bedeutung) {
    }

    @Override
    public String id() {
        return "stack";
    }

    @Override
    public Part generate(Random rng) {
        RecursiveFunction f = RecursiveFunction.zufall(rng, true);
        int k = f.form == RecursiveFunction.Form.PROD ? f.c + 1 + rng.nextInt(2) : 2 + rng.nextInt(2);
        int sp0 = SP_START.get(rng.nextInt(SP_START.size()));
        int s0 = 10 + rng.nextInt(90);
        AsmText prog = f.asm(k, true);
        RiscvParser.Programm p = prog.programm();
        Map<Integer, Integer> start = Map.of(2, sp0, AsmText.reg("s0"), s0);

        int halt = p.befehlInZeile(prog.markierteZeile());
        RiscvMaschine.Ergebnis bei = RiscvMaschine.runBis(p, 100_000, start, halt, 1);
        RiscvMaschine.Ergebnis ende = RiscvMaschine.run(p, 100_000, start);
        if (!"Haltepunkt".equals(bei.endeGrund()) || ende.abgebrochen()) {
            throw new IllegalStateException("Interner Fehler: Haltepunkt nicht erreicht");
        }
        int sp = bei.ende()[2];
        List<Eintrag> stack = erwartet(f, k, sp0, s0, prog);
        // Selbstkontrolle: Simulator == erwarteter Aufbau der Stackrahmen
        if (sp != stack.get(stack.size() - 1).adresse()) {
            throw new IllegalStateException("Interner Fehler: sp passt nicht zum Rahmenmodell");
        }
        for (Eintrag e : stack) {
            Integer w = bei.geschrieben().get(e.adresse());
            if (w == null || w != e.wert()) {
                throw new IllegalStateException(String.format("Interner Fehler: Stackwort 0x%08X", e.adresse()));
            }
        }
        if (ende.ende()[AsmText.reg("a0")] != f.wert(k) || ende.ende()[2] != sp0) {
            throw new IllegalStateException("Interner Fehler: Ergebnis");
        }

        StringBuilder a = new StringBuilder();
        a.append("Das folgende Programm wird ab Adresse \\texttt{0x00400000} ausgef\\\"uhrt (links die Adresse jedes Befehls). "
                + "Zu Beginn gilt \\texttt{sp} = \\texttt{").append(hex(sp0)).append("} und \\texttt{s0} = ").append(s0)
                .append(", alle anderen Register sind 0.\n");
        a.append(prog.listing(true, false));
        a.append("\\begin{enumerate}[label=\\alph*)]\n");
        a.append("\\item Welchen Wert hat \\texttt{sp}, wenn der mit (*) markierte Befehl zum ersten Mal ausgef\\\"uhrt wird?\n");
        a.append("\\item Geben Sie zu diesem Zeitpunkt den Inhalt des Stacks an: alle Speicherw\\\"orter von \\texttt{sp} bis "
                + "\\texttt{").append(hex(sp0 - 4)).append("} mit Adresse und Wert.\n");
        a.append("\\item Welchen Wert hat \\texttt{a0} am Ende des Programms?\n");
        a.append("\\end{enumerate}\n");

        StringBuilder l = new StringBuilder();
        l.append("\\begin{enumerate}[label=\\alph*)]\n");
        int aufrufe = stack.size() / 2;
        l.append("\\item \\texttt{sp} = \\texttt{").append(hex(sp)).append("}: ").append(aufrufe).append(" Aufrufe von \\texttt{")
                .append(f.name).append("} (n = ").append(k).append(" bis ").append(k - aufrufe + 1).append(") mit je ")
                .append(f.rahmen()).append(" Byte Stackrahmen.\n");
        l.append("\\item \\begin{tabular}[t]{lrl}\n\\hline\n\\textbf{Adresse} & \\textbf{Wert} & \\textbf{Bedeutung} \\\\\n\\hline\n");
        for (int i = stack.size() - 1; i >= 0; i--) {
            Eintrag e = stack.get(i);
            l.append("\\texttt{").append(hex(e.adresse())).append("} & ")
                    .append(e.bedeutung().startsWith("ra") ? "\\texttt{" + hex(e.wert()) + "}" : Integer.toString(e.wert()))
                    .append(" & ").append(e.bedeutung()).append(" \\\\\n");
        }
        l.append("\\hline\n\\end{tabular}\\\\[2pt]\n(oben die kleinste Adresse = aktueller \\texttt{sp})\n");
        l.append("\\item \\texttt{a0} = ").append(f.wert(k)).append(", denn das Programm berechnet\\\\[2pt]\n");
        l.append("\\begin{lstlisting}[language=C]\n").append(f.cCode()).append("\\end{lstlisting}\n");
        l.append("mit n = ").append(k).append(".\n");
        l.append("\\end{enumerate}\n");

        String frage = f.name + "(" + k + "), sp=" + hex(sp0) + ", s0=" + s0;
        String antwort = "sp=" + hex(sp) + ", " + stack.size() + " Woerter, a0=" + f.wert(k);
        return new Part("Stack nachvollziehen", a.toString(), l.toString(), frage, antwort);
    }

    /** Erwarteter Stack: je Aufruf ein Rahmen mit ra (oben) und s0 (darunter), aeusserer Aufruf zuerst. */
    static List<Eintrag> erwartet(RecursiveFunction f, int k, int sp0, int s0, AsmText prog) {
        int raMain = RiscvParser.TEXT_BASE + 8;           // nach "jal ra, f" in main (dritter Befehl)
        int raRek = RiscvParser.TEXT_BASE + 4 * jalIndex(prog);
        List<Eintrag> out = new ArrayList<>();
        int n = k;
        int adr = sp0;
        int j = 0;
        while (true) {
            adr -= f.rahmen();
            out.add(new Eintrag(adr + 4, j == 0 ? raMain : raRek, j == 0 ? "ra: zur\\\"uck nach \\texttt{main}"
                    : "ra: zur\\\"uck in \\texttt{" + f.name + "} (nach dem Aufruf)"));
            out.add(new Eintrag(adr, j == 0 ? s0 : n + 1, j == 0 ? "s0 von \\texttt{main} (Startwert)"
                    : "s0 des Aufrufers (dessen n = " + (n + 1) + ")"));
            if (f.basis(n)) {
                return out;
            }
            n--;
            j++;
        }
    }

    /** Befehlsindex direkt nach dem rekursiven "jal ra, f" in der Funktion. */
    private static int jalIndex(AsmText prog) {
        int idx = 0;
        int treffer = 0;
        for (AsmText.Zeile z : prog.zeilen()) {
            if (z.befehl() == null) {
                continue;
            }
            if (z.befehl().startsWith("jal  ra,")) {
                treffer++;
                if (treffer == 2) {
                    return idx + 1;
                }
            }
            idx++;
        }
        throw new IllegalStateException("kein rekursiver Aufruf");
    }

    static String hex(int v) {
        return String.format("0x%08X", v);
    }
}
