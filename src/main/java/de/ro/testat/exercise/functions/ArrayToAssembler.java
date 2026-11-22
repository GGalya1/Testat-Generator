package de.ro.testat.exercise.functions;

import de.ro.testat.exercise.Exercise;
import de.ro.testat.probelauf.CInterpreter;
import de.ro.testat.probelauf.CParser;
import de.ro.testat.probelauf.RiscvMaschine;
import de.ro.testat.probelauf.RiscvParser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * "Arrays im Speicher" (wie Uebung 3.10): Arrays stehen im Datensegment, eine Schleife in C
 * (Skalarprodukt, Summe, Maximum, Zaehlen, Umrechnen, Umdrehen, Praefixsumme) ist in RISC-V zu
 * uebersetzen. Kontrolle: Musterloesung im RISC-V-Simulator == derselbe C-Code im C-Interpreter.
 */
public final class ArrayToAssembler implements Exercise {

    enum Form {
        SKALARPRODUKT(true, true), SUMME_GROESSER(true, false), MAXIMUM(true, false), ZAEHLEN(true, false),
        UMRECHNEN(false, false), UMDREHEN(false, true), PRAEFIXSUMME(false, false);

        /** Ergebnis in result/a0 (sonst im Speicher). */
        final boolean result;
        /** braucht ein zweites Array b. */
        final boolean zweiArrays;

        Form(boolean result, boolean zweiArrays) {
            this.result = result;
            this.zweiArrays = zweiArrays;
        }
    }

    private final String auswahl;
    private final List<Form> pool;

    public ArrayToAssembler() {
        this("zufall");
    }

    /** @param auswahl "zufall" oder Formen, z. B. "skalarprodukt,maximum" */
    public ArrayToAssembler(String auswahl) {
        this.auswahl = auswahl == null || auswahl.isBlank() ? "zufall" : auswahl.trim();
        if (this.auswahl.equalsIgnoreCase("zufall")) {
            pool = List.of(Form.values());
        } else {
            List<Form> p = new ArrayList<>();
            for (String s : this.auswahl.split(",")) {
                String n = s.trim().toUpperCase(Locale.ROOT).replace('-', '_');
                Form f = Arrays.stream(Form.values()).filter(x -> x.name().equals(n) || x.name().startsWith(n)).findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("Unbekannte Form fuer array: " + s + " (erlaubt: "
                                + Arrays.stream(Form.values()).map(x -> x.name().toLowerCase(Locale.ROOT)).collect(Collectors.joining(", ")) + ")"));
                p.add(f);
            }
            pool = List.copyOf(p);
        }
    }

    @Override
    public String id() {
        return "array";
    }

    @Override
    public String describe() {
        return id() + "(" + auswahl + ")";
    }

    @Override
    public Part generate(Random rng) {
        Form f = pool.get(rng.nextInt(pool.size()));
        int n = 4 + rng.nextInt(3);
        int[] a = werte(rng, n, f == Form.UMRECHNEN || f == Form.PRAEFIXSUMME ? 9 : 20);
        int[] b = f == Form.SKALARPRODUKT ? werte(rng, n, 9) : new int[n];
        int k = f == Form.ZAEHLEN || f == Form.SUMME_GROESSER ? a[rng.nextInt(n)] : 0;
        int faktor = 2 + rng.nextInt(4);
        int plus = -3 + rng.nextInt(9);
        String c = cCode(f, n, k, faktor, plus);
        AsmText asm = asm(f, a, b, k, faktor, plus);

        // Kontrolle: Simulator vs. C-Interpreter
        String cVoll = "int a[" + n + "] = " + liste(a) + ";\n" + (f.zweiArrays ? "int b[" + n + "] = " + liste(b) + ";\n" : "") + c;
        CInterpreter.Ergebnis ce = CInterpreter.run(CParser.parse(cVoll), 1_000_000);
        RiscvParser.Programm prog = asm.programm();
        RiscvMaschine.Ergebnis re = asm.run(Map.of());
        String erwartet;
        String kurz;
        if (f.result) {
            String r = ce.variablen().get("result");
            if (!r.equals(Integer.toString(re.ende()[AsmText.reg("a0")]))) {
                throw new IllegalStateException("Interner Fehler: result " + r + " != a0");
            }
            erwartet = "\\texttt{a0} = " + r;
            kurz = "a0=" + r;
        } else {
            String ziel = f == Form.UMDREHEN ? "b" : "a";
            int basis = prog.marken().get(ziel);
            int[] init = ziel.equals("a") ? a : b;
            int[] mem = new int[n];
            for (int i = 0; i < n; i++) {
                mem[i] = re.geschrieben().getOrDefault(basis + 4 * i, init[i]);
            }
            if (!liste(mem).replace(" ", "").equals(ce.variablen().get(ziel).replace(" ", ""))) {
                throw new IllegalStateException("Interner Fehler: Array " + ziel);
            }
            erwartet = "danach \\texttt{" + ziel + "} = " + liste(mem).replace("{", "\\{").replace("}", "\\}");
            kurz = ziel + "=" + liste(mem);
        }

        StringBuilder t = new StringBuilder();
        t.append("Im Datensegment stehen folgende Arrays:\n");
        t.append("\\begin{lstlisting}\n.data\na: .word ").append(wortliste(a)).append("\n");
        if (f.zweiArrays) {
            t.append("b: .word ").append(wortliste(b)).append("\n");
        }
        t.append("\\end{lstlisting}\n");
        t.append("\\\"Ubersetzen Sie den folgenden C-Code in RISC-V-Assembler. Laden Sie die Startadressen mit \\texttt{la}");
        t.append(f.result ? "; am Ende soll \\texttt{result} in \\texttt{a0} stehen" : "; das Ergebnis steht danach im Speicher");
        t.append(". Das Programm endet mit \\texttt{ecall} (\\texttt{a7} = 10).\n");
        t.append("\\begin{lstlisting}[language=C]\n").append(c).append("\\end{lstlisting}\n");

        StringBuilder l = new StringBuilder();
        l.append("M\\\"ogliche L\\\"osung:\n").append(asm.listing(false, true));
        l.append("Kontrollwert: ").append(erwartet).append(".\\par\n");
        l.append("\\textbf{Worauf achten:} Adresse von Element $i$ ist Startadresse $+ 4i$ (\\texttt{slli} um 2); "
                + "\\texttt{lw}/\\texttt{sw} mit Offset; Schleifenbedingung und Grenzen wie im C-Code.\n");

        String frage = f.name().toLowerCase(Locale.ROOT) + ": a=" + liste(a) + (f.zweiArrays ? " b=" + liste(b) : "")
                + " | " + c.replace("\n", " ").replaceAll(" +", " ");
        return new Part("Arrays im Speicher", t.toString(), l.toString(), frage, kurz);
    }

    private static int[] werte(Random rng, int n, int max) {
        int[] v = new int[n];
        for (int i = 0; i < n; i++) {
            v[i] = -max / 2 + rng.nextInt(max + 1);
        }
        return v;
    }

    static String liste(int[] v) {
        return "{" + Arrays.stream(v).mapToObj(Integer::toString).collect(Collectors.joining(", ")) + "}";
    }

    private static String wortliste(int[] v) {
        return Arrays.stream(v).mapToObj(Integer::toString).collect(Collectors.joining(", "));
    }

    static String cCode(Form f, int n, int k, int faktor, int plus) {
        String kopf = "for (int i = 0; i < " + n + "; i++) {\n";
        return switch (f) {
            case SKALARPRODUKT -> "int result = 0;\n" + kopf + "    result = result + a[i] * b[i];\n}\n";
            case SUMME_GROESSER -> "int result = 0;\n" + kopf + "    if (a[i] > " + k + ") {\n        result = result + a[i];\n    }\n}\n";
            case MAXIMUM -> "int result = a[0];\nfor (int i = 1; i < " + n + "; i++) {\n    if (a[i] > result) {\n"
                    + "        result = a[i];\n    }\n}\n";
            case ZAEHLEN -> "int result = 0;\n" + kopf + "    if (a[i] < " + k + ") {\n        result = result + 1;\n    }\n}\n";
            case UMRECHNEN -> kopf + "    a[i] = a[i] * " + faktor + (plus < 0 ? " - " + (-plus) : " + " + plus) + ";\n}\n";
            case UMDREHEN -> kopf + "    b[i] = a[" + (n - 1) + " - i];\n}\n";
            case PRAEFIXSUMME -> "for (int i = 1; i < " + n + "; i++) {\n    a[i] = a[i] + a[i - 1];\n}\n";
        };
    }

    static AsmText asm(Form f, int[] a, int[] b, int k, int faktor, int plus) {
        int n = a.length;
        AsmText t = new AsmText();
        t.raw(".data");
        t.raw("a: .word " + wortliste(a));
        if (f.zweiArrays) {
            t.raw("b: .word " + wortliste(b));
        }
        t.raw(".text");
        t.marke("main");
        t.b("la   t0, a", "Startadresse a");
        if (f.zweiArrays) {
            t.b("la   t1, b", "Startadresse b");
        }
        switch (f) {
            case SKALARPRODUKT, SUMME_GROESSER, ZAEHLEN -> t.b("li   a0, 0", "result = 0");
            case MAXIMUM -> t.b("lw   a0, 0(t0)", "result = a[0]");
            case UMRECHNEN -> t.b("li   t1, " + faktor, "Faktor");
            default -> {
            }
        }
        t.b("li   t2, " + (f == Form.MAXIMUM || f == Form.PRAEFIXSUMME ? 1 : 0), "i");
        t.b("li   t3, " + n, "Grenze");
        if (f == Form.SUMME_GROESSER || f == Form.ZAEHLEN) {
            t.b("li   t6, " + k, "Vergleichswert");
        }
        t.marke("L1");
        String ende = f == Form.SUMME_GROESSER || f == Form.ZAEHLEN || f == Form.MAXIMUM ? "L3" : "L2";
        t.b("bge  t2, t3, " + ende, "Schleife beenden, wenn i >= " + n);
        switch (f) {
            case SKALARPRODUKT -> {
                t.b("slli t4, t2, 2", "Offset i * 4");
                t.b("add  t5, t0, t4");
                t.b("lw   t5, 0(t5)", "a[i]");
                t.b("add  t6, t1, t4");
                t.b("lw   t6, 0(t6)", "b[i]");
                t.b("mul  t5, t5, t6");
                t.b("add  a0, a0, t5");
            }
            case SUMME_GROESSER, ZAEHLEN, MAXIMUM -> {
                t.b("slli t4, t2, 2", "Offset i * 4");
                t.b("add  t5, t0, t4");
                t.b("lw   t5, 0(t5)", "a[i]");
                switch (f) {
                    case SUMME_GROESSER -> {
                        t.b("ble  t5, t6, L2", "a[i] <= " + k + ": ueberspringen");
                        t.b("add  a0, a0, t5");
                    }
                    case ZAEHLEN -> {
                        t.b("bge  t5, t6, L2", "a[i] >= " + k + ": ueberspringen");
                        t.b("addi a0, a0, 1");
                    }
                    default -> {
                        t.b("ble  t5, a0, L2", "a[i] <= result: ueberspringen");
                        t.b("mv   a0, t5");
                    }
                }
                t.marke("L2");
            }
            case UMRECHNEN -> {
                t.b("slli t4, t2, 2", "Offset i * 4");
                t.b("add  t5, t0, t4", "Adresse von a[i]");
                t.b("lw   t6, 0(t5)");
                t.b("mul  t6, t6, t1");
                t.b("addi t6, t6, " + plus);
                t.b("sw   t6, 0(t5)", "a[i] zurueckschreiben");
            }
            case UMDREHEN -> {
                t.b("sub  t4, t3, t2");
                t.b("addi t4, t4, -1", (n - 1) + " - i");
                t.b("slli t4, t4, 2");
                t.b("add  t4, t0, t4");
                t.b("lw   t5, 0(t4)", "a[" + (n - 1) + " - i]");
                t.b("slli t6, t2, 2");
                t.b("add  t6, t1, t6");
                t.b("sw   t5, 0(t6)", "b[i]");
            }
            case PRAEFIXSUMME -> {
                t.b("slli t4, t2, 2");
                t.b("add  t4, t0, t4", "Adresse von a[i]");
                t.b("lw   t5, 0(t4)", "a[i]");
                t.b("lw   t6, -4(t4)", "a[i - 1]");
                t.b("add  t5, t5, t6");
                t.b("sw   t5, 0(t4)");
            }
        }
        t.b("addi t2, t2, 1", "i++");
        t.b("j    L1");
        t.marke(ende);
        t.b("li   a7, 10");
        t.b("ecall");
        return t;
    }
}
