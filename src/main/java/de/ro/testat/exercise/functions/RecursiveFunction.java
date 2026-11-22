package de.ro.testat.exercise.functions;

import java.util.List;
import java.util.Random;

/**
 * Zufaellige rekursive Funktion int f(int n) nach festen Formen, mit C-Code, Wert und
 * Musterloesung in RISC-V (Aufrufkonvention: n in a0, Ergebnis in a0, ra und s-Register auf
 * dem Stack). Grundlage fuer {@link StackTrace} und die klassischen Formen in {@link RecursionForms}.
 */
final class RecursiveFunction {

    /** Form der Rekursion. eineRekursion = ein Aufruf pro Ebene (fuer die Stack-Aufgabe). */
    enum Form {
        /** n &lt; c ? b : (n + a) * f(n - 1) */
        PROD(true),
        /** n &lt;= 0 ? b : a * n + f(n - 1) */
        SUMME(true),
        /** n == 0 ? b : a * f(n - 1) + n */
        LINEAR(true),
        /** n &lt; 2 ? n : f(n - 1) + f(n - 2) (+ a) */
        FIB(false),
        /** n &lt; 2 ? b : f(n - 1) + a * f(n - 2) */
        FIB_MUL(false);

        final boolean eineRekursion;

        Form(boolean eineRekursion) {
            this.eineRekursion = eineRekursion;
        }
    }

    static final List<String> NAMEN = List.of("compute", "berechne", "rek", "folge", "wert", "calc");

    final Form form;
    final String name;
    final int a;
    final int b;
    final int c;

    RecursiveFunction(Form form, String name, int a, int b, int c) {
        this.form = form;
        this.name = name;
        this.a = a;
        this.b = b;
        this.c = c;
    }

    static RecursiveFunction zufall(Random rng, boolean nurEineRekursion) {
        List<Form> formen = nurEineRekursion ? List.of(Form.PROD, Form.SUMME, Form.LINEAR) : List.of(Form.values());
        return mit(formen.get(rng.nextInt(formen.size())), rng);
    }

    /** Zufaellige Konstanten und Name fuer eine feste Form. */
    static RecursiveFunction mit(Form f, Random rng) {
        String name = NAMEN.get(rng.nextInt(NAMEN.size()));
        return switch (f) {
            case PROD -> new RecursiveFunction(f, name, 1 + rng.nextInt(3), 1 + rng.nextInt(3), 1 + rng.nextInt(2));
            case SUMME -> new RecursiveFunction(f, name, 2 + rng.nextInt(4), rng.nextInt(4), 0);
            case LINEAR -> new RecursiveFunction(f, name, 2 + rng.nextInt(2), 1 + rng.nextInt(2), 0);
            case FIB -> new RecursiveFunction(f, name, rng.nextInt(3), 0, 2);
            case FIB_MUL -> new RecursiveFunction(f, name, 2 + rng.nextInt(2), 1 + rng.nextInt(2), 2);
        };
    }

    /** Wert in 32-Bit-Arithmetik wie in C. */
    int wert(int n) {
        return switch (form) {
            case PROD -> n < c ? b : (n + a) * wert(n - 1);
            case SUMME -> n <= 0 ? b : a * n + wert(n - 1);
            case LINEAR -> n == 0 ? b : a * wert(n - 1) + n;
            case FIB -> n < 2 ? n : wert(n - 1) + wert(n - 2) + a;
            case FIB_MUL -> n < 2 ? b : wert(n - 1) + a * wert(n - 2);
        };
    }

    /** Basisfall erreicht? */
    boolean basis(int n) {
        return switch (form) {
            case PROD -> n < c;
            case SUMME -> n <= 0;
            case LINEAR -> n == 0;
            case FIB, FIB_MUL -> n < 2;
        };
    }

    String cCode() {
        String bed = switch (form) {
            case PROD -> "n < " + c;
            case SUMME -> "n <= 0";
            case LINEAR -> "n == 0";
            case FIB, FIB_MUL -> "n < 2";
        };
        String basis = form == Form.FIB ? "n" : Integer.toString(b);
        String rek = switch (form) {
            case PROD -> "(n + " + a + ") * " + name + "(n - 1)";
            case SUMME -> a + " * n + " + name + "(n - 1)";
            case LINEAR -> a + " * " + name + "(n - 1) + n";
            case FIB -> name + "(n - 1) + " + name + "(n - 2)" + (a == 0 ? "" : " + " + a);
            case FIB_MUL -> name + "(n - 1) + " + a + " * " + name + "(n - 2)";
        };
        return "int " + name + "(int n) {\n"
                + "    if (" + bed + ")\n"
                + "        return " + basis + ";\n"
                + "    return " + rek + ";\n"
                + "}\n";
    }

    /**
     * Musterloesung mit Hauptprogramm, das f(k) aufruft. markiereBasis: Befehl des Basisfalls
     * mit (*) markieren (Haltepunkt fuer die Stack-Aufgabe).
     */
    AsmText asm(int k, boolean markiereBasis) {
        AsmText t = new AsmText();
        t.marke("main");
        t.b("addi a0, zero, " + k, "Argument n = " + k);
        t.b("jal  ra, " + name, "Aufruf");
        t.b("addi a7, zero, 10", "Programmende");
        t.b("ecall");
        t.raw("");
        return t.anhaengen(funktion(markiereBasis));
    }

    /** Nur die Funktion selbst (ohne Hauptprogramm). */
    AsmText funktion(boolean markiereBasis) {
        boolean zwei = !form.eineRekursion;
        int rahmen = zwei ? 12 : 8;
        AsmText t = new AsmText();
        t.marke(name);
        t.b("addi sp, sp, -" + rahmen, "Platz fuer " + (zwei ? "ra, s0, s1" : "ra, s0"));
        t.b("sw   ra, " + (rahmen - 4) + "(sp)", "Ruecksprungadresse sichern");
        t.b("sw   s0, " + (rahmen - 8) + "(sp)", "s0 des Aufrufers sichern");
        if (zwei) {
            t.b("sw   s1, 0(sp)", "s1 des Aufrufers sichern");
        }
        t.b("addi s0, a0, 0", "n in s0 (bleibt ueber Aufrufe erhalten)");
        switch (form) {
            case PROD, FIB, FIB_MUL -> {
                int grenze = form == Form.PROD ? c : 2;
                t.b("addi t0, zero, " + grenze);
                t.b("bge  a0, t0, L1", "n >= " + grenze + ": Rekursion");
            }
            case SUMME -> t.b("blt  zero, a0, L1", "n > 0: Rekursion");
            case LINEAR -> t.b("bne  a0, zero, L1", "n != 0: Rekursion");
        }
        if (form == Form.FIB) {
            t.b("jal  zero, L2", "Basisfall: Ergebnis n steht schon in a0");
        } else if (markiereBasis) {
            t.markiert("addi a0, zero, " + b, "Basisfall");
            t.b("jal  zero, L2");
        } else {
            t.b("addi a0, zero, " + b, "Basisfall");
            t.b("jal  zero, L2");
        }
        t.marke("L1");
        t.b("addi a0, s0, -1", "n - 1");
        t.b("jal  ra, " + name);
        switch (form) {
            case PROD -> {
                t.b("addi t0, s0, " + a, "n + " + a);
                t.b("mul  a0, t0, a0");
            }
            case SUMME -> {
                t.b("addi t0, zero, " + a);
                t.b("mul  t0, t0, s0", a + " * n");
                t.b("add  a0, t0, a0");
            }
            case LINEAR -> {
                t.b("addi t0, zero, " + a);
                t.b("mul  a0, a0, t0", a + " * " + name + "(n - 1)");
                t.b("add  a0, a0, s0", "+ n");
            }
            case FIB, FIB_MUL -> {
                t.b("addi s1, a0, 0", name + "(n - 1) sichern");
                t.b("addi a0, s0, -2", "n - 2");
                t.b("jal  ra, " + name);
                if (form == Form.FIB_MUL) {
                    t.b("addi t0, zero, " + a);
                    t.b("mul  a0, a0, t0", a + " * " + name + "(n - 2)");
                }
                t.b("add  a0, s1, a0");
                if (form == Form.FIB && a != 0) {
                    t.b("addi a0, a0, " + a);
                }
            }
        }
        t.marke("L2");
        if (zwei) {
            t.b("lw   s1, 0(sp)", "gesicherte Register zurueck");
        }
        t.b("lw   s0, " + (rahmen - 8) + "(sp)", zwei ? null : "gesicherte Register zurueck");
        t.b("lw   ra, " + (rahmen - 4) + "(sp)");
        t.b("addi sp, sp, " + rahmen, "Stack freigeben");
        t.b("jalr zero, 0(ra)", "ret");
        return t;
    }

    /** Groesse eines Stackrahmens in Byte. */
    int rahmen() {
        return form.eineRekursion ? 8 : 12;
    }
}
