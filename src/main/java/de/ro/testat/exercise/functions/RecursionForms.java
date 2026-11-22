package de.ro.testat.exercise.functions;

import static de.ro.testat.latex.Tex.tt;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Katalog typischer rekursiver Funktionen fuer {@link RecursionToAssembler}: C-Code mit
 * zufaelligen Konstanten, Musterloesung in RISC-V, Kontrollargumente und Hinweise fuer die
 * Korrektur. Die Werte selbst berechnet nicht diese Klasse, sondern der C-Interpreter
 * (siehe {@link ConventionCheck#cWert}).
 */
final class RecursionForms {

    private RecursionForms() {
    }

    enum Form {
        /** n &lt; c ? b : (n + a) * f(n - 1) */
        PROD,
        /** n &lt;= 0 ? b : a * n + f(n - 1) */
        SUMME,
        /** n == 0 ? b : a * f(n - 1) + n */
        LINEAR,
        /** Fibonacci-artig: f(n - 1) + f(n - 2) (+ a) */
        FIB,
        /** f(n - 1) + a * f(n - 2) */
        FIB_MUL,
        /** x * f(x, n - 1) (+ a), Basis b */
        POTENZ,
        /** Euklid: f(b, a % b), wahlweise Anzahl der Schritte */
        GGT,
        /** Ziffernsumme oder Anzahl Ziffern zur Basis B (n % B, n / B) */
        ZIFFERN,
        /** Summe der Bitgruppen oder Bitlaenge (n &amp; m, n &gt;&gt; s) */
        BITS,
        /** Collatz-Folge mit gewichteten Schritten, zwei Aufrufstellen */
        COLLATZ,
        /** Binomialkoeffizient (Pascal), zwei Argumente, zwei Aufrufe */
        BINOM,
        /** Summe ueber einen Bereich a..b, zwei Argumente */
        BEREICH
    }

    /**
     * Eine konkrete Funktion. kontrolle: Argumente fuer Kontrollwerte und Pruefung;
     * aufgabe: Argumente fuer das Hauptprogramm; hinweise: LaTeX-Punkte fuer "Worauf achten".
     */
    record Instanz(Form form, String name, List<String> params, String cCode, AsmText funktion,
                   List<int[]> kontrolle, int[] aufgabe, List<String> hinweise) {
    }

    static Instanz erzeuge(Form form, Random rng) {
        return switch (form) {
            case PROD, SUMME, LINEAR, FIB, FIB_MUL -> klassisch(form, rng);
            case POTENZ -> potenz(rng);
            case GGT -> ggt(rng);
            case ZIFFERN -> ziffern(rng);
            case BITS -> bits(rng);
            case COLLATZ -> collatz(rng);
            case BINOM -> binom(rng);
            case BEREICH -> bereich(rng);
        };
    }

    private static String name(Random rng) {
        return RecursiveFunction.NAMEN.get(rng.nextInt(RecursiveFunction.NAMEN.size()));
    }

    private static List<int[]> args(int[]... a) {
        return List.of(a);
    }

    // ---------------------------------------------------------------------------------------

    private static Instanz klassisch(Form form, Random rng) {
        RecursiveFunction r = RecursiveFunction.mit(RecursiveFunction.Form.valueOf(form.name()), rng);
        int k = r.form.eineRekursion ? 3 + rng.nextInt(3) : 4 + rng.nextInt(3);
        List<int[]> kontrolle = new ArrayList<>();
        for (int n = 0; n <= 6; n++) {
            kontrolle.add(new int[]{n});
        }
        List<String> h = new ArrayList<>();
        h.add(tt("n") + " wird nach dem Aufruf noch gebraucht: in " + tt("s0") + " (selbst gesichert) oder auf dem Stack aufbewahren.");
        if (!r.form.eineRekursion) {
            h.add("Das Ergebnis des ersten Aufrufs muss den zweiten Aufruf \\\"uberleben (hier " + tt("s1") + ").");
        }
        return new Instanz(form, r.name, List.of("n"), r.cCode(), r.funktion(false), kontrolle, new int[]{k}, h);
    }

    private static Instanz potenz(Random rng) {
        String n = name(rng);
        int b = 1 + rng.nextInt(2);
        int a = rng.nextInt(4);
        String c = "int " + n + "(int x, int n) {\n"
                + "    if (n == 0)\n"
                + "        return " + b + ";\n"
                + "    return x * " + n + "(x, n - 1)" + (a > 0 ? " + " + a : "") + ";\n"
                + "}\n";
        AsmText t = new AsmText();
        t.marke(n);
        ConventionCheck.prolog(t, 1);
        t.b("addi s0, a0, 0", "x sichern (nach dem Aufruf gebraucht)");
        t.b("bne  a1, zero, L1", "n != 0: Rekursion");
        t.b("addi a0, zero, " + b, "Basisfall");
        t.b("jal  zero, L2");
        t.marke("L1");
        t.b("addi a1, a1, -1", "n - 1 (x steht noch in a0)");
        t.b("jal  ra, " + n);
        t.b("mul  a0, s0, a0", "x * " + n + "(x, n - 1)");
        if (a > 0) {
            t.b("addi a0, a0, " + a);
        }
        t.marke("L2");
        ConventionCheck.epilog(t, 1);
        List<String> h = List.of(
                "Zwei Argumente: " + tt("x") + " in " + tt("a0") + ", " + tt("n") + " in " + tt("a1") + ".",
                "Nach dem Aufruf wird nur noch " + tt("x") + " gebraucht (" + tt("s0") + "), " + tt("n") + " nicht.");
        return new Instanz(Form.POTENZ, n, List.of("x", "n"), c, t,
                args(new int[]{2, 0}, new int[]{2, 3}, new int[]{3, 2}, new int[]{-2, 3}, new int[]{3, 4}, new int[]{2, 5}),
                new int[]{2 + rng.nextInt(2), 3 + rng.nextInt(3)}, h);
    }

    private static Instanz ggt(Random rng) {
        String n = name(rng);
        boolean schritte = rng.nextBoolean();
        String c = "int " + n + "(int a, int b) {\n"
                + "    if (b == 0)\n"
                + "        return " + (schritte ? "0" : "a") + ";\n"
                + "    return " + (schritte ? "1 + " : "") + n + "(b, a % b);\n"
                + "}\n";
        AsmText t = new AsmText();
        t.marke(n);
        ConventionCheck.prolog(t, 0);
        if (schritte) {
            t.b("bne  a1, zero, L1", "b != 0: Rekursion");
            t.b("addi a0, zero, 0", "Basisfall");
            t.b("jal  zero, L2");
        } else {
            t.b("beq  a1, zero, L2", "b == 0: Ergebnis a steht schon in a0");
        }
        if (schritte) {
            t.marke("L1");
        }
        t.b("rem  t0, a0, a1", "a % b");
        t.b("addi a0, a1, 0", "neues a = b");
        t.b("addi a1, t0, 0", "neues b = a % b");
        t.b("jal  ra, " + n);
        if (schritte) {
            t.b("addi a0, a0, 1", "1 + ...");
        }
        t.marke("L2");
        ConventionCheck.epilog(t, 0);
        List<String> h = new ArrayList<>();
        h.add("Nach dem Aufruf wird kein alter Wert mehr gebraucht: nur " + tt("ra") + " sichern, keine "
                + tt("s") + "-Register n\\\"otig. " + tt("rem") + " f\\\"ur den Rest.");
        if (!schritte) {
            h.add("Auch richtig: Endaufruf ohne Stackrahmen (" + tt("jal zero, " + n) + " bzw.\\ " + tt("j " + n) + ").");
        }
        int a = 20 + rng.nextInt(80);
        int b = 10 + rng.nextInt(60);
        return new Instanz(Form.GGT, n, List.of("a", "b"), c, t,
                args(new int[]{48, 18}, new int[]{35, 14}, new int[]{17, 5}, new int[]{100, 75}, new int[]{9, 0}, new int[]{21, 34}),
                new int[]{a, b}, h);
    }

    private static Instanz ziffern(Random rng) {
        String n = name(rng);
        int basis = List.of(10, 10, 8, 5).get(rng.nextInt(4));
        boolean anzahl = rng.nextBoolean();
        String c = "int " + n + "(int n) {\n"
                + "    if (n < " + basis + ")\n"
                + "        return " + (anzahl ? "1" : "n") + ";\n"
                + "    return " + (anzahl ? "1 + " : "n % " + basis + " + ") + n + "(n / " + basis + ");\n"
                + "}\n";
        AsmText t = new AsmText();
        t.marke(n);
        List<String> h = new ArrayList<>();
        if (anzahl) {
            ConventionCheck.prolog(t, 0);
            t.b("addi t0, zero, " + basis);
            t.b("bge  a0, t0, L1", "n >= " + basis + ": Rekursion");
            t.b("addi a0, zero, 1", "Basisfall");
            t.b("jal  zero, L2");
            t.marke("L1");
            t.b("div  a0, a0, t0", "n / " + basis);
            t.b("jal  ra, " + n);
            t.b("addi a0, a0, 1", "1 + ...");
            t.marke("L2");
            ConventionCheck.epilog(t, 0);
            h.add("Nach dem Aufruf wird nur 1 addiert: au\\ss{}er " + tt("ra") + " muss nichts gesichert werden.");
        } else {
            ConventionCheck.prolog(t, 1);
            t.b("addi t0, zero, " + basis);
            t.b("blt  a0, t0, L1", "n < " + basis + ": Ergebnis n steht schon in a0");
            t.b("rem  s0, a0, t0", "n % " + basis + " (muss den Aufruf ueberleben)");
            t.b("div  a0, a0, t0", "n / " + basis);
            t.b("jal  ra, " + n);
            t.b("add  a0, s0, a0");
            t.marke("L1");
            ConventionCheck.epilog(t, 1);
            h.add(tt("n % " + basis) + " muss den Aufruf \\\"uberleben (" + tt("s0") + "), " + tt("t0") + " nicht.");
        }
        h.add(tt("div") + " und " + tt("rem") + " aus der M-Erweiterung; die Konstante " + basis + " zuerst in ein Register laden.");
        return new Instanz(Form.ZIFFERN, n, List.of("n"), c, t,
                args(new int[]{0}, new int[]{7}, new int[]{12}, new int[]{99}, new int[]{345}, new int[]{2026}),
                new int[]{100 + rng.nextInt(1900)}, h);
    }

    private static Instanz bits(Random rng) {
        String n = name(rng);
        int s = 1 + rng.nextInt(2);
        int maske = (1 << s) - 1;
        boolean laenge = rng.nextBoolean();
        String c = "int " + n + "(int n) {\n"
                + "    if (n == 0)\n"
                + "        return 0;\n"
                + "    return " + (laenge ? "1" : "(n & " + maske + ")") + " + " + n + "(n >> " + s + ");\n"
                + "}\n";
        AsmText t = new AsmText();
        t.marke(n);
        List<String> h = new ArrayList<>();
        if (laenge) {
            ConventionCheck.prolog(t, 0);
            t.b("beq  a0, zero, L1", "n == 0: Ergebnis 0 steht schon in a0");
            t.b("srai a0, a0, " + s, "n >> " + s);
            t.b("jal  ra, " + n);
            t.b("addi a0, a0, 1", "1 + ...");
            t.marke("L1");
            ConventionCheck.epilog(t, 0);
            h.add("Nach dem Aufruf wird nur 1 addiert: au\\ss{}er " + tt("ra") + " muss nichts gesichert werden.");
        } else {
            ConventionCheck.prolog(t, 1);
            t.b("beq  a0, zero, L1", "n == 0: Ergebnis 0 steht schon in a0");
            t.b("andi s0, a0, " + maske, "n & " + maske + " (muss den Aufruf ueberleben)");
            t.b("srai a0, a0, " + s, "n >> " + s);
            t.b("jal  ra, " + n);
            t.b("add  a0, s0, a0");
            t.marke("L1");
            ConventionCheck.epilog(t, 1);
            h.add(tt("n \\& " + maske) + " muss den Aufruf \\\"uberleben (" + tt("s0") + ").");
        }
        h.add(tt("\\&") + " wird zu " + tt("andi") + ", " + tt(">>") + " zu " + tt("srai") + " (" + tt("srli") + " ist f\\\"ur positive "
                + tt("n") + " ebenso richtig).");
        return new Instanz(Form.BITS, n, List.of("n"), c, t,
                args(new int[]{0}, new int[]{1}, new int[]{5}, new int[]{12}, new int[]{37}, new int[]{100}, new int[]{255}),
                new int[]{20 + rng.nextInt(200)}, h);
    }

    private static Instanz collatz(Random rng) {
        String n = name(rng);
        int p = 1 + rng.nextInt(3);
        int q = 1 + rng.nextInt(3);
        String c = "int " + n + "(int n) {\n"
                + "    if (n == 1)\n"
                + "        return 0;\n"
                + "    if (n % 2 == 0)\n"
                + "        return " + p + " + " + n + "(n / 2);\n"
                + "    return " + q + " + " + n + "(3 * n + 1);\n"
                + "}\n";
        AsmText t = new AsmText();
        t.marke(n);
        ConventionCheck.prolog(t, 0);
        t.b("addi t0, zero, 1");
        t.b("bne  a0, t0, L1", "n != 1: Rekursion");
        t.b("addi a0, zero, 0", "Basisfall");
        t.b("jal  zero, L3");
        t.marke("L1");
        t.b("andi t0, a0, 1", "n % 2");
        t.b("bne  t0, zero, L2", "ungerade");
        t.b("srai a0, a0, 1", "n / 2 (n > 0)");
        t.b("jal  ra, " + n);
        t.b("addi a0, a0, " + p);
        t.b("jal  zero, L3");
        t.marke("L2");
        t.b("addi t0, zero, 3");
        t.b("mul  a0, a0, t0", "3 * n");
        t.b("addi a0, a0, 1", "+ 1");
        t.b("jal  ra, " + n);
        t.b("addi a0, a0, " + q);
        t.marke("L3");
        ConventionCheck.epilog(t, 0);
        List<String> h = List.of(
                "Zwei Aufrufstellen, aber nach jedem Aufruf wird nur eine Konstante addiert: nur " + tt("ra") + " sichern.",
                tt("n % 2") + " als " + tt("andi") + " (oder " + tt("rem") + "), " + tt("n / 2") + " als " + tt("srai")
                        + " (oder " + tt("div") + ") -- f\\\"ur positive " + tt("n") + " gleichwertig.");
        List<Integer> start = List.of(3, 5, 6, 7, 9, 10, 12);
        return new Instanz(Form.COLLATZ, n, List.of("n"), c, t,
                args(new int[]{1}, new int[]{2}, new int[]{3}, new int[]{4}, new int[]{5}, new int[]{6}, new int[]{7}),
                new int[]{start.get(rng.nextInt(start.size()))}, h);
    }

    private static Instanz binom(Random rng) {
        String n = name(rng);
        int b = 1 + rng.nextInt(2);
        int a = 1 + rng.nextInt(2);
        String c = "int " + n + "(int n, int k) {\n"
                + "    if (k == 0 || k == n)\n"
                + "        return " + b + ";\n"
                + "    return " + n + "(n - 1, k - 1) + " + (a > 1 ? a + " * " : "") + n + "(n - 1, k);\n"
                + "}\n";
        AsmText t = new AsmText();
        t.marke(n);
        ConventionCheck.prolog(t, 3);
        t.b("addi s0, a0, 0", "n sichern");
        t.b("addi s1, a1, 0", "k sichern");
        t.b("beq  a1, zero, L1", "k == 0: Basisfall");
        t.b("beq  a1, a0, L1", "k == n: Basisfall");
        t.b("addi a0, s0, -1");
        t.b("addi a1, s1, -1");
        t.b("jal  ra, " + n, n + "(n - 1, k - 1)");
        t.b("addi s2, a0, 0", "Ergebnis sichern");
        t.b("addi a0, s0, -1");
        t.b("addi a1, s1, 0");
        t.b("jal  ra, " + n, n + "(n - 1, k)");
        if (a > 1) {
            t.b("addi t0, zero, " + a);
            t.b("mul  a0, a0, t0");
        }
        t.b("add  a0, s2, a0");
        t.b("jal  zero, L2");
        t.marke("L1");
        t.b("addi a0, zero, " + b, "Basisfall");
        t.marke("L2");
        ConventionCheck.epilog(t, 3);
        List<String> h = List.of(
                "Nach dem ersten Aufruf werden " + tt("n") + ", " + tt("k") + " und das Zwischenergebnis gebraucht: drei "
                        + tt("s") + "-Register plus " + tt("ra") + " (16 Byte Rahmen).",
                tt("||") + ": zwei Vergleiche, die beide zum Basisfall springen.");
        int nn = 4 + rng.nextInt(3);
        return new Instanz(Form.BINOM, n, List.of("n", "k"), c, t,
                args(new int[]{0, 0}, new int[]{3, 1}, new int[]{4, 2}, new int[]{5, 2}, new int[]{5, 3}, new int[]{6, 3}),
                new int[]{nn, 1 + rng.nextInt(nn - 1)}, h);
    }

    private static Instanz bereich(Random rng) {
        String n = name(rng);
        int d = 1 + rng.nextInt(2);
        int art = rng.nextInt(3);
        int k = 2 + rng.nextInt(4);
        String term = switch (art) {
            case 0 -> "a";
            case 1 -> "a * a";
            default -> k + " * a";
        };
        String c = "int " + n + "(int a, int b) {\n"
                + "    if (a > b)\n"
                + "        return 0;\n"
                + "    return " + term + " + " + n + "(a + " + d + ", b);\n"
                + "}\n";
        AsmText t = new AsmText();
        t.marke(n);
        ConventionCheck.prolog(t, 1);
        t.b("bge  a1, a0, L1", "a <= b: Rekursion");
        t.b("addi a0, zero, 0", "Basisfall");
        t.b("jal  zero, L2");
        t.marke("L1");
        t.b("addi s0, a0, 0", "a sichern");
        t.b("addi a0, a0, " + d, "a + " + d + " (b steht noch in a1)");
        t.b("jal  ra, " + n);
        switch (art) {
            case 0 -> t.b("add  a0, s0, a0");
            case 1 -> {
                t.b("mul  t0, s0, s0", "a * a");
                t.b("add  a0, t0, a0");
            }
            default -> {
                t.b("addi t0, zero, " + k);
                t.b("mul  t0, t0, s0", k + " * a");
                t.b("add  a0, t0, a0");
            }
        }
        t.marke("L2");
        ConventionCheck.epilog(t, 1);
        List<String> h = List.of(
                tt("a > b") + " ist " + tt("b < a") + ": " + tt("blt a1, a0, ...") + " bzw.\\ umgekehrt " + tt("bge a1, a0, ...") + ".",
                "Nur " + tt("a") + " wird nach dem Aufruf gebraucht (" + tt("s0") + "); " + tt("b") + " kann in " + tt("a1")
                        + " bleiben, weil es vor dem Aufruf nicht ver\\\"andert wird.");
        int a = 1 + rng.nextInt(3);
        return new Instanz(Form.BEREICH, n, List.of("a", "b"), c, t,
                args(new int[]{1, 4}, new int[]{2, 5}, new int[]{3, 3}, new int[]{5, 2}, new int[]{0, 6}, new int[]{-2, 3}),
                new int[]{a, a + 3 + rng.nextInt(4)}, h);
    }
}
