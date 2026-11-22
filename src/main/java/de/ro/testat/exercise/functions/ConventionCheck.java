package de.ro.testat.exercise.functions;

import de.ro.testat.probelauf.CInterpreter;
import de.ro.testat.probelauf.CParser;
import de.ro.testat.probelauf.RiscvMaschine;
import de.ro.testat.probelauf.RiscvParser;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Gemeinsame Bausteine fuer Funktionsaufgaben: Stackrahmen (Prolog/Epilog), Hauptprogramm,
 * Werte aus dem C-Interpreter und die Pruefung einer Musterloesung gegen die Aufrufkonvention.
 *
 * <p>Die Pruefung laeuft zweimal: normal und im strengen Modus des Simulators, der bei jedem
 * Ruecksprung alle t- und a-Register (ausser a0) mit Zufallswerten ueberschreibt. Zusaetzlich
 * muessen sp und alle s-Register nach dem Aufruf wieder ihre alten Werte haben. Eine
 * Musterloesung, die das nicht einhaelt, wird nie ausgegeben.
 */
final class ConventionCheck {

    private ConventionCheck() {
    }

    /** s0..s11 */
    static final int[] S_REG = {8, 9, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27};

    /** Stackrahmen fuer ra und s0..s(anzahlS-1); ra liegt oben, s0 darunter usw. */
    static int rahmen(int anzahlS) {
        return 4 * (anzahlS + 1);
    }

    static void prolog(AsmText t, int anzahlS) {
        int r = rahmen(anzahlS);
        StringJoiner was = new StringJoiner(", ");
        was.add("ra");
        for (int i = 0; i < anzahlS; i++) {
            was.add("s" + i);
        }
        t.b("addi sp, sp, -" + r, "Platz fuer " + was);
        t.b("sw   ra, " + (r - 4) + "(sp)", "Ruecksprungadresse sichern");
        for (int i = 0; i < anzahlS; i++) {
            t.b("sw   s" + i + ", " + (r - 8 - 4 * i) + "(sp)", i == 0 ? "s-Register des Aufrufers sichern" : null);
        }
    }

    static void epilog(AsmText t, int anzahlS) {
        int r = rahmen(anzahlS);
        for (int i = anzahlS - 1; i >= 0; i--) {
            t.b("lw   s" + i + ", " + (r - 8 - 4 * i) + "(sp)", i == anzahlS - 1 ? "gesicherte Register zurueck" : null);
        }
        t.b("lw   ra, " + (r - 4) + "(sp)", anzahlS == 0 ? "Ruecksprungadresse zurueck" : null);
        t.b("addi sp, sp, " + r, "Stack freigeben");
        t.b("jalr zero, 0(ra)", "ret");
    }

    /** Konstante in ein Register laden (addi, bei grossen Werten lui + addi). */
    static void lade(AsmText t, String reg, int wert, String kommentar) {
        if (wert >= -2048 && wert <= 2047) {
            t.b("addi " + reg + ", zero, " + wert, kommentar);
            return;
        }
        int lo = (wert << 20) >> 20;
        int hi = (wert - lo) >>> 12;
        t.b("lui  " + reg + ", " + hi, kommentar);
        t.b("addi " + reg + ", " + reg + ", " + lo);
    }

    /** Hauptprogramm: Argumente nach a0, a1, ... laden, name aufrufen, mit ecall enden. */
    static AsmText main(String name, List<String> params, int[] args) {
        AsmText t = new AsmText();
        t.marke("main");
        for (int i = 0; i < args.length; i++) {
            lade(t, "a" + i, args[i], params.get(i) + " = " + args[i]);
        }
        t.b("jal  ra, " + name, "Aufruf, Ergebnis danach in a0");
        t.b("addi a7, zero, 10", "Programmende");
        t.b("ecall");
        t.raw("");
        return t;
    }

    static String aufruf(String name, int[] args) {
        StringJoiner j = new StringJoiner(", ", name + "(", ")");
        for (int a : args) {
            j.add(Integer.toString(a));
        }
        return j.toString();
    }

    /**
     * Wert eines Aufrufs, berechnet vom C-Interpreter. null, wenn das Programm nicht in
     * maxSchritte endet oder einen Laufzeitfehler hat (dann wird die Variante verworfen).
     */
    static Integer cWert(String cFunktionen, String aufruf, int maxSchritte) {
        String prog = cFunktionen + "\nint main() {\n    return " + aufruf + ";\n}\n";
        CInterpreter.Ergebnis e = CInterpreter.run(CParser.parse(prog), maxSchritte);
        if (e.abgebrochen() || e.rueckgabe() == null) {
            return null;
        }
        return e.rueckgabe();
    }

    static int cWert(String cFunktionen, String aufruf) {
        Integer w = cWert(cFunktionen, aufruf, 2_000_000);
        if (w == null) {
            throw new IllegalStateException("Interner Fehler: C-Code endet nicht: " + aufruf + "\n" + cFunktionen);
        }
        return w;
    }

    /**
     * Musterloesung pruefen: Hauptprogramm ruft name(args) auf; nach dem Lauf muss a0 = erwartet
     * sein, sp und s0..s11 unveraendert - im normalen und im strengen Modus.
     */
    static void pruefe(AsmText funktionen, String name, List<String> params, int[] args, int erwartet) {
        AsmText prog = main(name, params, args).anhaengen(funktionen);
        RiscvParser.Programm p = prog.programm();
        Map<Integer, Integer> start = new HashMap<>();
        for (int i = 0; i < S_REG.length; i++) {
            start.put(S_REG[i], 0x5000 + 17 * i);
        }
        for (int modus = 0; modus < 3; modus++) {
            RiscvMaschine.Ergebnis e = modus == 0 ? RiscvMaschine.run(p, 2_000_000, start)
                    : RiscvMaschine.runStreng(p, 2_000_000, start, 31L * modus + erwartet);
            String wo = aufruf(name, args) + (modus == 0 ? "" : " (strenger Modus)");
            if (e.abgebrochen()) {
                throw new IllegalStateException("Interner Fehler: Musterloesung bricht ab bei " + wo + ": " + e.meldungen()
                        + "\n" + prog.text());
            }
            int[] x = e.ende();
            if (x[AsmText.reg("a0")] != erwartet) {
                throw new IllegalStateException("Interner Fehler: " + wo + " liefert " + x[AsmText.reg("a0")] + " statt "
                        + erwartet + "\n" + prog.text());
            }
            if (x[2] != e.start()[2]) {
                throw new IllegalStateException("Interner Fehler: sp nach " + wo + " veraendert");
            }
            for (int r : S_REG) {
                if (x[r] != start.get(r)) {
                    throw new IllegalStateException("Interner Fehler: s-Register x" + r + " nach " + wo + " veraendert");
                }
            }
        }
    }

    /** Argument-Ausdruck ("v", "v + w", "v - w", "v * w") nach ziel berechnen; Variablen per reg. */
    static void argument(AsmText t, String ausdruck, String ziel, Map<String, String> reg) {
        String[] teile = ausdruck.split(" ");
        if (teile.length == 1) {
            t.b("addi " + ziel + ", " + reg.get(teile[0]) + ", 0", ausdruck);
            return;
        }
        String op = switch (teile[1]) {
            case "+" -> "add ";
            case "-" -> "sub ";
            case "*" -> "mul ";
            default -> throw new IllegalArgumentException(ausdruck);
        };
        t.b(op + " " + ziel + ", " + reg.get(teile[0]) + ", " + reg.get(teile[2]), ausdruck);
    }
}
