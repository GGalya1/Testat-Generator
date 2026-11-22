package de.ro.testat.exercise.functions;

import static de.ro.testat.latex.Tex.tt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Zufaelliges C-Programm aus zwei nicht rekursiven Funktionen fuer {@link CallToAssembler}:
 * eine Blattfunktion g (if/else oder Schleife, ruft nichts auf) und eine Funktion f, die g in
 * einer Schleife, unter einer Bedingung oder zweimal hintereinander aufruft. Dazu die
 * Musterloesung in RISC-V, Kontrollargumente und Hinweise.
 *
 * <p>Kombinationen, deren C-Code nicht schnell endet oder sehr grosse Werte liefert, werden
 * verworfen und neu gezogen (gemessen mit dem C-Interpreter).
 */
final class CallChain {

    private CallChain() {
    }

    /** Blattfunktion g; stellen = Anzahl Argumente. */
    enum Blatt {
        BEDINGUNG(1), MAXMIN(2), SUMME_SCHLEIFE(1), ABZIEHEN(1), BEGRENZEN(1), POTENZ(2);

        final int stellen;

        Blatt(int stellen) {
            this.stellen = stellen;
        }
    }

    /** Aufrufende Funktion f. */
    enum Rufer {
        /** s = s + g(..) in einer for-Schleife */
        SUMME,
        /** zaehlt, wie oft g(..) eine Schwelle ueberschreitet */
        ZAEHLEN,
        /** x = g(x), bis x klein genug ist (nur einstellige g) */
        ITERATION,
        /** u = g(..), v = g(..), danach if/else */
        ZWEI_AUFRUFE,
        /** while-Schleife, g nur fuer gerade/ungerade n */
        BEDINGT
    }

    static final List<String> G_NAMEN = List.of("g", "hilf", "teil", "stufe", "schritt");
    static final List<String> F_NAMEN = List.of("f", "rechne", "auswerten", "verarbeite", "haupt");

    record Funktion(String name, List<String> params, String cCode, AsmText asm, List<int[]> kontrolle) {
    }

    /**
     * zaehlCode: nur bei ITERATION der C-Code von f in der Fassung "return k;" (Anzahl der
     * Schleifendurchlaeufe), sonst null.
     */
    record Programm(Blatt blatt, Rufer rufer, Funktion g, Funktion f, int[] aufgabe, List<String> hinweise, String zaehlCode) {
        /** C-Code beider Funktionen (g zuerst, damit f es aufrufen kann). */
        String cCode() {
            return g.cCode() + "\n" + f.cCode();
        }

        /** f und g in Assembler (f zuerst). */
        AsmText asm() {
            return new AsmText().anhaengen(f.asm()).raw("").anhaengen(g.asm());
        }
    }

    static Programm zufall(Random rng, List<Rufer> rufer, List<Blatt> blaetter) {
        for (int versuch = 0; versuch < 200; versuch++) {
            Rufer r = rufer.get(rng.nextInt(rufer.size()));
            List<Blatt> passend = blaetter.stream()
                    .filter(b -> r != Rufer.ITERATION || (b == Blatt.BEDINGUNG || b == Blatt.ABZIEHEN)).toList();
            if (passend.isEmpty()) {
                continue;
            }
            Blatt b = passend.get(rng.nextInt(passend.size()));
            Programm p = erzeuge(r, b, rng);
            if (brauchbar(p)) {
                return p;
            }
        }
        throw new IllegalStateException("Keine brauchbare Kombination fuer aufruf gefunden: " + rufer + " / " + blaetter);
    }

    /** Endet schnell, Werte nicht zu gross, Ergebnis der Aufgabe nicht trivial. */
    private static boolean brauchbar(Programm p) {
        List<int[]> alle = new ArrayList<>(p.f().kontrolle());
        alle.add(p.aufgabe());
        for (int[] a : alle) {
            Integer w = ConventionCheck.cWert(p.cCode(), ConventionCheck.aufruf(p.f().name(), a), 50_000);
            if (w == null || Math.abs(w) > 100_000) {
                return false;
            }
        }
        if (p.rufer() == Rufer.ITERATION) {
            // mindestens zwei Schleifendurchlaeufe in der Aufgabe
            String probe = p.g().cCode() + "\n" + p.zaehlCode();
            Integer k = ConventionCheck.cWert(probe, ConventionCheck.aufruf(p.f().name(), p.aufgabe()), 50_000);
            return k != null && k >= 2;
        }
        return true;
    }

    private static String wahl(Random rng, String... s) {
        return s[rng.nextInt(s.length)];
    }

    // --------------------------------------------------------------------------------------- Blatt

    private static Funktion blatt(Blatt b, String n, Random rng) {
        AsmText t = new AsmText();
        t.marke(n);
        String c;
        List<String> params = b.stellen == 1 ? List.of("x") : List.of("x", "y");
        List<int[]> kontrolle;
        switch (b) {
            case BEDINGUNG -> {
                String cmp = wahl(rng, ">", "<", ">=", "<=");
                int k = rng.nextInt(10);
                String op1 = wahl(rng, "+", "-", "*");
                String op2 = op1.equals("+") ? wahl(rng, "-", "*") : op1.equals("-") ? wahl(rng, "+", "*") : wahl(rng, "+", "-");
                int d1 = 2 + rng.nextInt(4);
                int d2 = 1 + rng.nextInt(5);
                c = "int " + n + "(int x) {\n"
                        + "    if (x " + cmp + " " + k + ")\n"
                        + "        return x " + op1 + " " + d1 + ";\n"
                        + "    return x " + op2 + " " + d2 + ";\n"
                        + "}\n";
                t.b("addi t0, zero, " + k);
                t.b(switch (cmp) {
                    case ">" -> "bge  t0, a0, G1";
                    case "<" -> "bge  a0, t0, G1";
                    case ">=" -> "blt  a0, t0, G1";
                    default -> "blt  t0, a0, G1";
                }, "Bedingung falsch: else-Zweig");
                rechne(t, op1, d1);
                t.b("jalr zero, 0(ra)", "ret");
                t.marke("G1");
                rechne(t, op2, d2);
                t.b("jalr zero, 0(ra)", "ret");
                kontrolle = eindeutig(new int[]{k - 2}, new int[]{k}, new int[]{k + 3}, new int[]{0});
            }
            case MAXMIN -> {
                int art = rng.nextInt(3);
                String bed = art == 1 ? "x < y" : "x > y";
                String dann = art == 2 ? "x - y" : "x";
                String sonst = art == 2 ? "y - x" : "y";
                c = "int " + n + "(int x, int y) {\n"
                        + "    if (" + bed + ")\n"
                        + "        return " + dann + ";\n"
                        + "    return " + sonst + ";\n"
                        + "}\n";
                t.b(art == 1 ? "bge  a0, a1, G1" : "bge  a1, a0, G1", "Bedingung falsch: else-Zweig");
                if (art == 2) {
                    t.b("sub  a0, a0, a1", "x - y");
                    t.b("jalr zero, 0(ra)", "ret");
                } else {
                    t.b("jalr zero, 0(ra)", "Ergebnis x steht schon in a0");
                }
                t.marke("G1");
                t.b(art == 2 ? "sub  a0, a1, a0" : "addi a0, a1, 0", sonst);
                t.b("jalr zero, 0(ra)", "ret");
                kontrolle = List.of(new int[]{3, 5}, new int[]{5, 3}, new int[]{4, 4}, new int[]{-2, 1});
            }
            case SUMME_SCHLEIFE -> {
                int art = rng.nextInt(3);
                int k = 2 + rng.nextInt(3);
                String term = switch (art) {
                    case 0 -> "i";
                    case 1 -> "i * i";
                    default -> k + " * i";
                };
                c = "int " + n + "(int x) {\n"
                        + "    int s = 0;\n"
                        + "    for (int i = 1; i <= x; i++)\n"
                        + "        s = s + " + term + ";\n"
                        + "    return s;\n"
                        + "}\n";
                t.b("addi t0, zero, 0", "s = 0");
                t.b("addi t1, zero, 1", "i = 1");
                t.marke("G1");
                t.b("blt  a0, t1, G2", "i > x: fertig");
                switch (art) {
                    case 0 -> t.b("add  t0, t0, t1", "s = s + i");
                    case 1 -> {
                        t.b("mul  t2, t1, t1", "i * i");
                        t.b("add  t0, t0, t2");
                    }
                    default -> {
                        t.b("addi t2, zero, " + k);
                        t.b("mul  t2, t2, t1", k + " * i");
                        t.b("add  t0, t0, t2");
                    }
                }
                t.b("addi t1, t1, 1", "i++");
                t.b("jal  zero, G1");
                t.marke("G2");
                t.b("addi a0, t0, 0", "Ergebnis s");
                t.b("jalr zero, 0(ra)", "ret");
                kontrolle = List.of(new int[]{0}, new int[]{1}, new int[]{3}, new int[]{5});
            }
            case ABZIEHEN -> {
                int d = 2 + rng.nextInt(4);
                String ret = wahl(rng, "k", "x", "k + x");
                c = "int " + n + "(int x) {\n"
                        + "    int k = 0;\n"
                        + "    while (x >= " + d + ") {\n"
                        + "        x = x - " + d + ";\n"
                        + "        k = k + 1;\n"
                        + "    }\n"
                        + "    return " + ret + ";\n"
                        + "}\n";
                t.b("addi t0, zero, 0", "k = 0");
                t.b("addi t1, zero, " + d);
                t.marke("G1");
                t.b("blt  a0, t1, G2", "x < " + d + ": fertig");
                t.b("sub  a0, a0, t1", "x = x - " + d);
                t.b("addi t0, t0, 1", "k = k + 1");
                t.b("jal  zero, G1");
                t.marke("G2");
                switch (ret) {
                    case "k" -> {
                        t.b("addi a0, t0, 0", "Ergebnis k");
                        t.b("jalr zero, 0(ra)", "ret");
                    }
                    case "x" -> t.b("jalr zero, 0(ra)", "Ergebnis x steht schon in a0");
                    default -> {
                        t.b("add  a0, t0, a0", "k + x");
                        t.b("jalr zero, 0(ra)", "ret");
                    }
                }
                kontrolle = List.of(new int[]{0}, new int[]{3}, new int[]{7}, new int[]{12}, new int[]{20});
            }
            case BEGRENZEN -> {
                int lo = rng.nextInt(4);
                int hi = lo + 3 + rng.nextInt(6);
                boolean verschieben = lo > 0 && rng.nextBoolean();
                c = "int " + n + "(int x) {\n"
                        + "    if (x < " + lo + ")\n"
                        + "        return " + lo + ";\n"
                        + "    if (x > " + hi + ")\n"
                        + "        return " + hi + ";\n"
                        + "    return " + (verschieben ? "x - " + lo : "x") + ";\n"
                        + "}\n";
                t.b("addi t0, zero, " + lo);
                t.b("bge  a0, t0, G1", "x >= " + lo);
                t.b("addi a0, zero, " + lo);
                t.b("jalr zero, 0(ra)", "ret");
                t.marke("G1");
                t.b("addi t0, zero, " + hi);
                t.b("bge  t0, a0, G2", "x <= " + hi);
                t.b("addi a0, zero, " + hi);
                t.b("jalr zero, 0(ra)", "ret");
                t.marke("G2");
                if (verschieben) {
                    t.b("addi a0, a0, -" + lo, "x - " + lo);
                    t.b("jalr zero, 0(ra)", "ret");
                } else {
                    t.b("jalr zero, 0(ra)", "Ergebnis x steht schon in a0");
                }
                kontrolle = eindeutig(new int[]{lo - 2}, new int[]{lo}, new int[]{(lo + hi) / 2}, new int[]{hi + 3});
            }
            default -> { // POTENZ
                c = "int " + n + "(int x, int y) {\n"
                        + "    int p = 1;\n"
                        + "    for (int i = 0; i < y; i++)\n"
                        + "        p = p * x;\n"
                        + "    return p;\n"
                        + "}\n";
                t.b("addi t0, zero, 1", "p = 1");
                t.b("addi t1, zero, 0", "i = 0");
                t.marke("G1");
                t.b("bge  t1, a1, G2", "i >= y: fertig");
                t.b("mul  t0, t0, a0", "p = p * x");
                t.b("addi t1, t1, 1", "i++");
                t.b("jal  zero, G1");
                t.marke("G2");
                t.b("addi a0, t0, 0", "Ergebnis p");
                t.b("jalr zero, 0(ra)", "ret");
                kontrolle = List.of(new int[]{2, 3}, new int[]{3, 2}, new int[]{5, 0}, new int[]{-2, 3});
            }
        }
        return new Funktion(n, params, c, t, kontrolle);
    }

    /** Kontrollargumente ohne Wiederholungen. */
    private static List<int[]> eindeutig(int[]... args) {
        Map<String, int[]> m = new LinkedHashMap<>();
        for (int[] a : args) {
            m.putIfAbsent(java.util.Arrays.toString(a), a);
        }
        return List.copyOf(m.values());
    }

    /** "a", "a und b", "a, b und c" */
    private static String aufzaehlung(List<String> teile) {
        if (teile.size() == 1) {
            return teile.get(0);
        }
        return String.join(", ", teile.subList(0, teile.size() - 1)) + " und " + teile.get(teile.size() - 1);
    }

    /** a0 = a0 op d */
    private static void rechne(AsmText t, String op, int d) {
        switch (op) {
            case "+" -> t.b("addi a0, a0, " + d, "x + " + d);
            case "-" -> t.b("addi a0, a0, -" + d, "x - " + d);
            default -> {
                t.b("addi t1, zero, " + d);
                t.b("mul  a0, a0, t1", "x * " + d);
            }
        }
    }

    // --------------------------------------------------------------------------------------- Rufer

    private static Programm erzeuge(Rufer r, Blatt b, Random rng) {
        String gn = G_NAMEN.get(rng.nextInt(G_NAMEN.size()));
        String fn = F_NAMEN.get(rng.nextInt(F_NAMEN.size()));
        Funktion g = blatt(b, gn, rng);
        return switch (r) {
            case SUMME, ZAEHLEN -> schleife(r, b, g, fn, rng);
            case ITERATION -> iteration(b, g, fn, rng);
            case ZWEI_AUFRUFE -> zweiAufrufe(b, g, fn, rng);
            case BEDINGT -> bedingt(b, g, fn, rng);
        };
    }

    /** Argumentausdruecke fuer g mit Schleifenvariable v und evtl. Parameter a. */
    private static List<String> argumente(Blatt b, String v, Random rng) {
        if (b.stellen == 1) {
            return List.of(wahl(rng, v, "a + " + v, "a * " + v));
        }
        return rng.nextBoolean() ? List.of(v, "a") : List.of("a", v);
    }

    private static boolean nutztA(List<String> args) {
        return args.stream().anyMatch(s -> s.equals("a") || s.startsWith("a "));
    }

    private static void argumenteLaden(AsmText t, List<String> args, Map<String, String> reg) {
        for (int i = 0; i < args.size(); i++) {
            ConventionCheck.argument(t, args.get(i), "a" + i, reg);
        }
    }

    private static int wert(String ausdruck, Map<String, Integer> env) {
        String[] t = ausdruck.split(" ");
        if (t.length == 1) {
            return env.get(t[0]);
        }
        int x = env.get(t[0]);
        int y = env.get(t[2]);
        return switch (t[1]) {
            case "+" -> x + y;
            case "-" -> x - y;
            default -> x * y;
        };
    }

    private static List<int[]> schleifenKontrolle(boolean mitA) {
        int[][] an = {{2, 4}, {1, 5}, {-1, 3}, {3, 0}, {0, 6}};
        List<int[]> l = new ArrayList<>();
        for (int[] x : an) {
            l.add(mitA ? x : new int[]{x[1]});
        }
        return l;
    }

    private static String aufrufC(String g, List<String> args) {
        return g + "(" + String.join(", ", args) + ")";
    }

    private static Programm schleife(Rufer r, Blatt b, Funktion g, String fn, Random rng) {
        List<String> args = argumente(b, "i", rng);
        boolean mitA = nutztA(args);
        int start = rng.nextInt(2);
        boolean kleinerGleich = rng.nextBoolean();
        int a = 1 + rng.nextInt(4);
        int n = 3 + rng.nextInt(4);
        int[] aufgabe = mitA ? new int[]{a, n} : new int[]{n};
        List<String> params = mitA ? List.of("a", "n") : List.of("n");
        String gAufruf = aufrufC(g.name(), args);
        String cmp = wahl(rng, ">", "<");
        int schwelle = 0;
        if (r == Rufer.ZAEHLEN) {
            // Schwelle = Wert von g in der Mitte der Schleife der Aufgabe -> Ergebnis nicht trivial
            Map<String, Integer> env = new HashMap<>();
            env.put("a", a);
            env.put("i", (start + n) / 2);
            int[] ga = args.stream().mapToInt(s -> wert(s, env)).toArray();
            Integer w = ConventionCheck.cWert(g.cCode(), ConventionCheck.aufruf(g.name(), ga), 50_000);
            schwelle = w == null ? 0 : w;
        }
        String var = r == Rufer.SUMME ? "s" : "z";
        String c = "int " + fn + "(" + (mitA ? "int a, " : "") + "int n) {\n"
                + "    int " + var + " = 0;\n"
                + "    for (int i = " + start + "; i " + (kleinerGleich ? "<=" : "<") + " n; i++)\n"
                + (r == Rufer.SUMME
                ? "        s = s + " + gAufruf + ";\n"
                : "        if (" + gAufruf + " " + cmp + " " + schwelle + ")\n            z = z + 1;\n")
                + "    return " + var + ";\n"
                + "}\n";

        int anzahlS = mitA ? 4 : 3;
        AsmText t = new AsmText();
        t.marke(fn);
        ConventionCheck.prolog(t, anzahlS);
        if (mitA) {
            t.b("addi s3, a0, 0", "a sichern");
            t.b("addi s2, a1, 0", "n sichern");
        } else {
            t.b("addi s2, a0, 0", "n sichern");
        }
        t.b("addi s0, zero, 0", var + " = 0");
        t.b("addi s1, zero, " + start, "i = " + start);
        t.marke("F1");
        t.b(kleinerGleich ? "blt  s2, s1, F2" : "bge  s1, s2, F2", kleinerGleich ? "i > n: fertig" : "i >= n: fertig");
        Map<String, String> reg = Map.of("i", "s1", "a", "s3", "n", "s2");
        argumenteLaden(t, args, reg);
        t.b("jal  ra, " + g.name(), gAufruf);
        if (r == Rufer.SUMME) {
            t.b("add  s0, s0, a0", "s = s + " + gAufruf);
            t.b("addi s1, s1, 1", "i++");
            t.b("jal  zero, F1");
        } else {
            t.b("addi t0, zero, " + schwelle);
            t.b(cmp.equals(">") ? "bge  t0, a0, F3" : "bge  a0, t0, F3", "nicht zaehlen");
            t.b("addi s0, s0, 1", "z = z + 1");
            t.marke("F3");
            t.b("addi s1, s1, 1", "i++");
            t.b("jal  zero, F1");
        }
        t.marke("F2");
        t.b("addi a0, s0, 0", "Ergebnis " + var);
        ConventionCheck.epilog(t, anzahlS);

        List<String> h = new ArrayList<>();
        h.add(tt(var) + ", " + tt("i") + ", " + tt("n") + (mitA ? " und " + tt("a") : "") + " werden nach dem Aufruf von "
                + tt(g.name()) + " noch gebraucht: in " + tt("s") + "-Registern (hier " + (mitA ? "s0--s3" : "s0--s2")
                + ", selbst gesichert) oder auf dem Stack aufbewahren, nicht in " + tt("t") + "- oder " + tt("a") + "-Registern.");
        if (r == Rufer.ZAEHLEN) {
            h.add("Der Vergleich mit " + schwelle + " kommt erst nach dem Aufruf: Ergebnis von " + tt(g.name()) + " in "
                    + tt("a0") + "; die Konstante in ein " + tt("t") + "-Register zu laden ist jetzt erlaubt.");
        }
        Funktion f = new Funktion(fn, params, c, t, schleifenKontrolle(mitA));
        return new Programm(b, r, g, f, aufgabe, h, null);
    }

    private static Programm iteration(Blatt b, Funktion g, String fn, Random rng) {
        int schwelle = rng.nextInt(6);
        boolean mitX = rng.nextBoolean();
        String c = iterationC(fn, g.name(), schwelle, mitX ? "k + x" : "k");
        AsmText t = new AsmText();
        t.marke(fn);
        ConventionCheck.prolog(t, 1);
        t.b("addi s0, zero, 0", "k = 0");
        t.marke("F1");
        t.b("addi t0, zero, " + schwelle, "nach jedem Aufruf neu laden");
        t.b("bge  t0, a0, F2", "x <= " + schwelle + ": fertig");
        t.b("jal  ra, " + g.name(), "x = " + g.name() + "(x), x steht schon in a0");
        t.b("addi s0, s0, 1", "k = k + 1");
        t.b("jal  zero, F1");
        t.marke("F2");
        t.b(mitX ? "add  a0, s0, a0" : "addi a0, s0, 0", mitX ? "k + x" : "Ergebnis k");
        ConventionCheck.epilog(t, 1);
        List<String> h = List.of(
                "Nur " + tt("k") + " muss den Aufruf \\\"uberleben (" + tt("s0") + "). " + tt("x") + " wird als Argument \\\"ubergeben "
                        + "und kommt als Ergebnis in " + tt("a0") + " zur\\\"uck -- es darf, muss aber nicht gesichert werden.",
                "Die Schwelle " + schwelle + " in " + tt("t0") + " ist nach dem Aufruf zerst\\\"ort: in der Schleife neu laden "
                        + "(oder in einem " + tt("s") + "-Register halten).");
        int x = 20 + rng.nextInt(41);
        Funktion f = new Funktion(fn, List.of("x"), c, t,
                List.of(new int[]{0}, new int[]{7}, new int[]{15}, new int[]{30}, new int[]{50}));
        return new Programm(b, Rufer.ITERATION, g, f, new int[]{x}, h, iterationC(fn, g.name(), schwelle, "k"));
    }

    private static String iterationC(String fn, String g, int schwelle, String ergebnis) {
        return "int " + fn + "(int x) {\n"
                + "    int k = 0;\n"
                + "    while (x > " + schwelle + ") {\n"
                + "        x = " + g + "(x);\n"
                + "        k = k + 1;\n"
                + "    }\n"
                + "    return " + ergebnis + ";\n"
                + "}\n";
    }

    private static Programm zweiAufrufe(Blatt b, Funktion g, String fn, Random rng) {
        List<String> args1;
        List<String> args2;
        if (b.stellen == 1) {
            args1 = List.of(wahl(rng, "x", "x + y"));
            args2 = List.of(wahl(rng, "y", "u + y", "u - y"));
        } else {
            args1 = List.of("x", "y");
            int w = rng.nextInt(3);
            args2 = w == 0 ? List.of("y", "x") : w == 1 ? List.of("u", "y") : List.of("y", "u");
        }
        String cmp = wahl(rng, ">", "<");
        String c = "int " + fn + "(int x, int y) {\n"
                + "    int u = " + aufrufC(g.name(), args1) + ";\n"
                + "    int v = " + aufrufC(g.name(), args2) + ";\n"
                + "    if (u " + cmp + " v)\n"
                + "        return u - v;\n"
                + "    return u + v;\n"
                + "}\n";
        // nach dem ersten Aufruf gebraucht: x/y, falls im zweiten Aufruf benutzt, und u
        List<String> gesichert = new ArrayList<>();
        for (String v : List.of("x", "y")) {
            if (args2.stream().anyMatch(s -> List.of(s.split(" ")).contains(v))) {
                gesichert.add(v);
            }
        }
        gesichert.add("u");
        Map<String, String> reg2 = new LinkedHashMap<>();
        for (int i = 0; i < gesichert.size(); i++) {
            reg2.put(gesichert.get(i), "s" + i);
        }
        int anzahlS = gesichert.size();
        String su = reg2.get("u");

        AsmText t = new AsmText();
        t.marke(fn);
        ConventionCheck.prolog(t, anzahlS);
        for (String v : gesichert) {
            if (!v.equals("u")) {
                t.b("addi " + reg2.get(v) + ", " + (v.equals("x") ? "a0" : "a1") + ", 0", v + " sichern");
            }
        }
        if (args1.size() == 1 && !args1.get(0).equals("x")) {
            ConventionCheck.argument(t, args1.get(0), "a0", Map.of("x", "a0", "y", "a1"));
        }
        t.b("jal  ra, " + g.name(), "u = " + aufrufC(g.name(), args1)
                + (args1.equals(List.of("x", "y")) ? ", x/y schon in a0/a1" : args1.equals(List.of("x")) ? ", x schon in a0" : ""));
        t.b("addi " + su + ", a0, 0", "u sichern");
        argumenteLaden(t, args2, reg2);
        t.b("jal  ra, " + g.name(), "v = " + aufrufC(g.name(), args2));
        t.b(cmp.equals(">") ? "bge  a0, " + su + ", F1" : "bge  " + su + ", a0, F1", "Bedingung falsch: else-Zweig");
        t.b("sub  a0, " + su + ", a0", "u - v");
        t.b("jal  zero, F2");
        t.marke("F1");
        t.b("add  a0, " + su + ", a0", "u + v");
        t.marke("F2");
        ConventionCheck.epilog(t, anzahlS);

        List<String> namen = new ArrayList<>();
        for (String v : gesichert) {
            namen.add(tt(v));
        }
        List<String> h = List.of(
                "Nach dem ersten Aufruf werden " + aufzaehlung(namen) + " noch gebraucht: in " + tt("s")
                        + "-Registern (hier " + (anzahlS == 1 ? "s0" : "s0--s" + (anzahlS - 1)) + ") aufbewahren. "
                        + tt("v") + " wird nach dem zweiten Aufruf direkt aus " + tt("a0") + " verwendet.",
                "Die Argumente des zweiten Aufrufs werden aus den gesicherten Registern neu in " + tt("a0")
                        + (args2.size() > 1 ? "/" + tt("a1") : "") + " geladen.");
        int x = 1 + rng.nextInt(6);
        int y = 1 + rng.nextInt(6);
        Funktion f = new Funktion(fn, List.of("x", "y"), c, t,
                List.of(new int[]{3, 2}, new int[]{1, 4}, new int[]{5, 5}, new int[]{-2, 3}, new int[]{0, 2}));
        return new Programm(b, Rufer.ZWEI_AUFRUFE, g, f, new int[]{x, y}, h, null);
    }

    private static Programm bedingt(Blatt b, Funktion g, String fn, Random rng) {
        List<String> args = argumente(b, "n", rng);
        boolean mitA = nutztA(args);
        int rest = rng.nextInt(2);
        String op = wahl(rng, "-", "+");
        String gAufruf = aufrufC(g.name(), args);
        String c = "int " + fn + "(" + (mitA ? "int a, " : "") + "int n) {\n"
                + "    int s = 0;\n"
                + "    while (n > 0) {\n"
                + "        if (n % 2 == " + rest + ")\n"
                + "            s = s + " + gAufruf + ";\n"
                + "        else\n"
                + "            s = s " + op + " n;\n"
                + "        n = n - 1;\n"
                + "    }\n"
                + "    return s;\n"
                + "}\n";
        int anzahlS = mitA ? 3 : 2;
        AsmText t = new AsmText();
        t.marke(fn);
        ConventionCheck.prolog(t, anzahlS);
        if (mitA) {
            t.b("addi s2, a0, 0", "a sichern");
            t.b("addi s1, a1, 0", "n sichern");
        } else {
            t.b("addi s1, a0, 0", "n sichern");
        }
        t.b("addi s0, zero, 0", "s = 0");
        t.marke("F1");
        t.b("bge  zero, s1, F4", "n <= 0: fertig");
        t.b("andi t0, s1, 1", "n % 2 (n > 0)");
        t.b(rest == 0 ? "bne  t0, zero, F2" : "beq  t0, zero, F2", "else-Zweig");
        argumenteLaden(t, args, Map.of("n", "s1", "a", "s2"));
        t.b("jal  ra, " + g.name(), gAufruf);
        t.b("add  s0, s0, a0");
        t.b("jal  zero, F3");
        t.marke("F2");
        t.b((op.equals("-") ? "sub " : "add ") + " s0, s0, s1", "s = s " + op + " n");
        t.marke("F3");
        t.b("addi s1, s1, -1", "n = n - 1");
        t.b("jal  zero, F1");
        t.marke("F4");
        t.b("addi a0, s0, 0", "Ergebnis s");
        ConventionCheck.epilog(t, anzahlS);
        List<String> h = List.of(
                tt("s") + ", " + tt("n") + (mitA ? " und " + tt("a") : "") + " werden nach dem Aufruf noch gebraucht: in "
                        + tt("s") + "-Registern (hier " + (mitA ? "s0--s2" : "s0--s1") + ") aufbewahren.",
                tt("n % 2") + " als " + tt("andi") + " mit 1 (oder " + tt("rem") + "); im else-Zweig wird " + tt(g.name())
                        + " nicht aufgerufen.");
        int a = 1 + rng.nextInt(4);
        int n = 3 + rng.nextInt(4);
        Funktion f = new Funktion(fn, mitA ? List.of("a", "n") : List.of("n"), c, t, schleifenKontrolle(mitA));
        return new Programm(b, Rufer.BEDINGT, g, f, mitA ? new int[]{a, n} : new int[]{n}, h, null);
    }
}
