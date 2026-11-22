package de.ro.testat.exercise.functions;

import de.ro.testat.exercise.Exercise;
import de.ro.testat.probelauf.RiscvMaschine;

import java.util.Map;
import java.util.Random;

/**
 * "Multiplikation und Division durch Shift" (wie Uebung 3.6): n * c mit zwei gesetzten Bits
 * (Shift + add), n * c als Differenz zweier Zweierpotenzen (Shift + sub) und n / 2^k mit
 * Vorzeichen (Rundung Richtung 0 wie in C). Jede Musterloesung wird fuer viele n im Simulator
 * mit Java-Arithmetik verglichen.
 */
public final class ShiftArithmetic implements Exercise {

    static final int[] ZWEI_BITS = {3, 5, 6, 9, 10, 12, 17, 18, 20, 24, 33, 34, 36, 40, 48};
    static final int[] DIFFERENZ = {7, 14, 15, 28, 30, 31, 56, 60, 62, 63};
    static final int[] TESTWERTE = {0, 1, -1, 2, -2, 5, -5, 7, -7, 37, -37, 1000, -1001, 123_456_789, -123_456_789,
            Integer.MAX_VALUE, Integer.MIN_VALUE};

    @Override
    public String id() {
        return "shift";
    }

    @Override
    public Part generate(Random rng) {
        int c1 = ZWEI_BITS[rng.nextInt(ZWEI_BITS.length)];
        int c2 = DIFFERENZ[rng.nextInt(DIFFERENZ.length)];
        int k = 1 + rng.nextInt(4);
        int d = 1 << k;
        int beispiel = -(11 + rng.nextInt(80));
        if (beispiel % d == 0) {
            beispiel -= 1;
        }

        AsmText mal1 = mal(c1);
        AsmText mal2 = mal(c2);
        AsmText durch = durch(k);
        for (int n : TESTWERTE) {
            pruefe(mal1, n, n * c1);
            pruefe(mal2, n, n * c2);
            pruefe(durch, n, n / d);
        }

        StringBuilder a = new StringBuilder();
        a.append("Implementieren Sie die folgenden Berechnungen in RISC-V-Assembler \\emph{ohne} \\texttt{mul} und \\texttt{div}; "
                + "verwenden Sie Schiebeoperationen und Addition/Subtraktion. \\texttt{n} steht in \\texttt{a0}, das Ergebnis "
                + "soll in \\texttt{a1} stehen. Als Hilfsregister d\\\"urfen Sie \\texttt{t0} und \\texttt{t1} verwenden.\n");
        a.append("\\begin{enumerate}[label=\\alph*)]\n");
        a.append("\\item \\texttt{erg = n * ").append(c1).append("}\n");
        a.append("\\item \\texttt{erg = n * ").append(c2).append("}\n");
        a.append("\\item \\texttt{erg = n / ").append(d).append("} (mit Vorzeichen, Ergebnis wie in C)\n");
        a.append("\\end{enumerate}\n");

        StringBuilder l = new StringBuilder();
        l.append("\\begin{enumerate}[label=\\alph*)]\n");
        l.append("\\item $").append(c1).append(" = ").append(zerlegung(c1, true)).append("$:\n").append(mal1.listing(false, true));
        l.append("\\item $").append(c2).append(" = ").append(zerlegung(c2, false)).append("$:\n").append(mal2.listing(false, true));
        l.append("\\item Division durch $").append(d).append(" = 2^").append(k).append("$ mit Rundung Richtung 0:\n")
                .append(durch.listing(false, true));
        l.append("\\texttt{srai} allein rundet bei negativen Zahlen nach $-\\infty$, C rundet Richtung 0. Deshalb wird "
                + "bei negativem \\texttt{n} vorher $").append(d - 1).append("$ addiert (die ersten beiden Befehle liefern "
                + "$").append(d - 1).append("$ oder $0$). Andere korrekte Varianten (z.\\,B.\\ mit Verzweigung) sind ebenso richtig.\n");
        l.append("\\end{enumerate}\n");
        l.append("Kontrollwerte f\\\"ur \\texttt{n} = ").append(beispiel).append(": a) ").append(beispiel * c1)
                .append(", b) ").append(beispiel * c2).append(", c) ").append(beispiel / d)
                .append(" (nur \\texttt{srai} ergibt ").append(beispiel >> k).append(").\n");

        String frage = "n*" + c1 + ", n*" + c2 + ", n/" + d;
        String antwort = "n=" + beispiel + ": " + beispiel * c1 + ", " + beispiel * c2 + ", " + beispiel / d;
        return new Part("Multiplikation und Division durch Shift", a.toString(), l.toString(), frage, antwort);
    }

    /** n * c mit Shifts: zwei gesetzte Bits -> add, sonst 2^p - 2^q -> sub. */
    static AsmText mal(int c) {
        int p = 31 - Integer.numberOfLeadingZeros(c);
        AsmText t = new AsmText();
        if (Integer.bitCount(c) == 2) {
            int q = Integer.numberOfTrailingZeros(c);
            t.b("slli t0, a0, " + p, "n * " + (1 << p));
            if (q == 0) {
                t.b("add  a1, t0, a0", "+ n");
            } else {
                t.b("slli t1, a0, " + q, "n * " + (1 << q));
                t.b("add  a1, t0, t1");
            }
        } else {
            int hoch = p + 1;
            int q = Integer.numberOfTrailingZeros(c);
            t.b("slli t0, a0, " + hoch, "n * " + (1 << hoch));
            if (q == 0) {
                t.b("sub  a1, t0, a0", "- n");
            } else {
                t.b("slli t1, a0, " + q, "n * " + (1 << q));
                t.b("sub  a1, t0, t1");
            }
        }
        return t;
    }

    /** "8 + 2" bzw. "16 - 1". */
    static String zerlegung(int c, boolean plus) {
        int p = 31 - Integer.numberOfLeadingZeros(c);
        int q = Integer.numberOfTrailingZeros(c);
        return plus ? (1 << p) + " + " + (1 << q) : (1 << (p + 1)) + " - " + (1 << q);
    }

    /** n / 2^k mit Rundung Richtung 0. */
    static AsmText durch(int k) {
        AsmText t = new AsmText();
        t.b("srai t0, a0, 31", "0 oder -1 (Vorzeichen)");
        t.b("srli t0, t0, " + (32 - k), "0 oder " + ((1 << k) - 1));
        t.b("add  t0, a0, t0", "bei n < 0: n + " + ((1 << k) - 1));
        t.b("srai a1, t0, " + k, "/ " + (1 << k));
        return t;
    }

    private static void pruefe(AsmText t, int n, int erwartet) {
        RiscvMaschine.Ergebnis e = t.run(Map.of(AsmText.reg("a0"), n));
        if (e.ende()[AsmText.reg("a1")] != erwartet) {
            throw new IllegalStateException("Interner Fehler: Shift-Loesung falsch fuer n = " + n + "\n" + t.text());
        }
    }
}
