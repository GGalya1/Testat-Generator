package de.ro.testat.exercise.memory;

import de.ro.testat.testat.JdkRandom;

import de.ro.testat.exercise.Auswahl;
import de.ro.testat.exercise.Exercise;
import de.ro.testat.latex.Tex;
import de.ro.testat.probelauf.RiscvMaschine;
import de.ro.testat.probelauf.RiscvParser;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * "Miss-Rate eines Programms" (wie Harris &amp; Harris, Beispiele zu Kap. 8.3): Ein kurzes
 * Programm (RISC-V-Schleife mit festen Ladeadressen oder C-Schleife ueber zwei Arrays) laeuft
 * auf zwei Cache-Konfigurationen; gefragt sind die Miss-Raten und die Einteilung der Misses.
 *
 * <p>Kontrolle: Die Adressfolge des Modells muss genau der Folge der Speicherzugriffe
 * entsprechen, die der RISC-V-Simulator beim Ausfuehren des Programms aufzeichnet (fuer die
 * C-Variante wird dazu eine gleichwertige RISC-V-Schleife mit .data-Bereich ausgefuehrt).
 */
public final class CacheProgram implements Exercise {

    private final String auswahl;

    public CacheProgram() {
        this("zufall");
    }

    /** @param auswahl "schleife", "array" oder "zufall" */
    public CacheProgram(String auswahl) {
        this.auswahl = Auswahl.variante(auswahl, "cache-prog", "schleife", "array");
    }

    @Override
    public String id() {
        return "cache-prog";
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
        boolean array = auswahl.equals("array") || (auswahl.equals("zufall") && rng.nextBoolean());
        return array ? array(rng) : schleife(rng);
    }

    // ------------------------------------------------------------------ Variante Schleife

    private Part schleife(Random rng) {
        for (int versuch = 0; versuch < 1000; versuch++) {
            int n = 3 + rng.nextInt(4);
            int kapWorte = rng.nextBoolean() ? 8 : 16;
            int blockWorte = rng.nextInt(3) == 0 ? 2 : 1;
            CacheModel a = new CacheModel(4 * kapWorte, 4 * blockWorte, 1);
            CacheModel b = rng.nextInt(4) == 0 ? a.vollAssoziativ() : new CacheModel(4 * kapWorte, 4 * blockWorte, 2);
            int k = 2 + rng.nextInt(3);
            // zwei Adressen im selben Satz von A, der Rest zufaellig
            Set<Integer> adr = new LinkedHashSet<>();
            int satz = rng.nextInt(a.saetze);
            while (adr.size() < 2) {
                adr.add(a.blockAdresse(rng.nextInt(4), satz) + 4 * rng.nextInt(blockWorte));
            }
            while (adr.size() < k) {
                adr.add(4 * rng.nextInt(kapWorte * 4));
            }
            List<Integer> lade = new ArrayList<>(adr);
            JdkRandom.mischen(lade, rng);
            List<Integer> folge = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                folge.addAll(lade);
            }
            int ma = a.misses(folge);
            int mb = b.misses(folge);
            List<CacheModel.MissArt> arten = CacheModel.klassifiziere(a, folge);
            long konflikt = arten.stream().filter(x -> x == CacheModel.MissArt.CONFLICT).count();
            if (konflikt == 0 || mb >= ma) {
                continue;
            }
            // Programm und Kontrolle per Simulator
            StringBuilder p = new StringBuilder();
            p.append("      addi s0, zero, ").append(n).append('\n');
            p.append("loop: beq  s0, zero, done\n");
            for (int i = 0; i < lade.size(); i++) {
                p.append("      lw   s").append(i + 1).append(", ").append(Tex.hex(lade.get(i))).append("(zero)\n");
            }
            p.append("      addi s0, s0, -1\n");
            p.append("      j    loop\n");
            p.append("done:\n");
            vergleiche(p + "      addi a7, zero, 10\n      ecall\n", folge);
            return schleifeText(p.toString(), n, lade, folge, a, b, ma, mb, arten);
        }
        throw new IllegalStateException("cache-prog: keine geeignete Schleife gefunden");
    }

    private Part schleifeText(String prog, int n, List<Integer> lade, List<Integer> folge, CacheModel a, CacheModel b,
                              int ma, int mb, List<CacheModel.MissArt> arten) {
        StringBuilder t = new StringBuilder();
        t.append("Das folgende Programm l\\\"auft auf einem Prozessor mit Daten-Cache. Betrachten Sie nur die Datenzugriffe "
                + "(\\texttt{lw}); der Cache ist anfangs leer, Ersetzung LRU.\n");
        t.append("\\begin{lstlisting}[language={[RISC-V]Assembler}]\n").append(prog).append("\\end{lstlisting}\n");
        t.append("\\begin{enumerate}[label=\\alph*)]\n");
        t.append("\\item Wie hoch ist die Miss-Rate mit Cache A: ").append(a.beschreibung()).append("?\n");
        t.append("\\item Wie hoch ist die Miss-Rate mit Cache B: ").append(b.beschreibung()).append("?\n");
        t.append("\\item Wie viele der Misses in Cache A sind Compulsory-, Capacity- und Conflict-Misses?\n");
        t.append("\\end{enumerate}\n");

        StringBuilder l = new StringBuilder();
        l.append("Pro Durchlauf ").append(lade.size()).append(" Ladebefehle, insgesamt $").append(n).append(" \\cdot ")
                .append(lade.size()).append(" = ").append(folge.size()).append("$ Zugriffe.\n");
        l.append("\\begin{enumerate}[label=\\alph*)]\n");
        l.append("\\item Cache A (").append(a.saetze).append(" S\\\"atze):\n").append(matrix(a, lade, n));
        l.append("Miss-Rate ").append(MemTex.rate(ma, folge.size())).append(".\n");
        l.append("\\item Cache B (").append(b.saetze).append(b.saetze == 1 ? " Satz" : " S\\\"atze").append(", ")
                .append(b.wege).append(" Wege):\n").append(matrix(b, lade, n));
        l.append("Miss-Rate ").append(MemTex.rate(mb, folge.size())).append(".\n");
        l.append("\\item ").append(einteilung(arten)).append("\n");
        l.append("\\end{enumerate}\n");
        String frage = "Schleife n=" + n + " lw " + lade.stream().map(Tex::hex).toList() + ", A: " + a.organisation() + " "
                + a.kapazitaet / 4 + " W/" + a.worteProBlock() + " W, B: " + b.organisation();
        String antwort = "A " + ma + "/" + folge.size() + ", B " + mb + "/" + folge.size() + ", " + einteilungKurz(arten);
        return new Part("Miss-Rate einer Schleife", t.toString(), l.toString(), frage, antwort);
    }

    /** Tabelle Ladeadresse x Durchlauf mit H/M, dazu Satz und Tag. */
    private static String matrix(CacheModel leer, List<Integer> lade, int n) {
        CacheModel c = leer.leer();
        String[][] hm = new String[lade.size()][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < lade.size(); j++) {
                hm[j][i] = c.lesen(lade.get(j)).hit() ? "H" : "\\textbf{M}";
            }
        }
        List<String> kopf = new ArrayList<>(List.of("Adresse", "Satz", "Tag"));
        for (int i = 1; i <= n; i++) {
            kopf.add(i + ".");
        }
        List<List<String>> zeilen = new ArrayList<>();
        for (int j = 0; j < lade.size(); j++) {
            List<String> z = new ArrayList<>(List.of("\\texttt{" + Tex.hex(lade.get(j)) + "}",
                    Integer.toString(c.satz(lade.get(j))), "\\texttt{" + Tex.hex(c.tag(lade.get(j))) + "}"));
            z.addAll(List.of(hm[j]));
            zeilen.add(z);
        }
        return MemTex.tabelle("|c|c|c|" + "c|".repeat(n), kopf, zeilen, false);
    }

    // ------------------------------------------------------------------ Variante Array

    private Part array(Random rng) {
        int n = rng.nextBoolean() ? 8 : 16;
        int kapWorte = List.of(8, 16, 32).get(rng.nextInt(3));
        // Bloecke ab 2 Woertern: bei 1 Wort waere jeder Zugriff ein Compulsory-Miss (100 %, nichts zu erkennen)
        int blockWorte = rng.nextBoolean() ? 2 : 4;
        boolean konflikt = rng.nextInt(4) > 0;
        int basisA = RiscvParser.DATA_BASE;
        int abstand; // Bytes von a[0] bis b[0]
        int kapBytes = 4 * kapWorte;
        if (konflikt) {
            abstand = ((4 * n + kapBytes - 1) / kapBytes) * kapBytes;
        } else {
            abstand = ((4 * n + kapBytes - 1) / kapBytes) * kapBytes + kapBytes / 2;
        }
        int basisB = basisA + abstand;
        CacheModel a = new CacheModel(kapBytes, 4 * blockWorte, 1);
        CacheModel b = new CacheModel(kapBytes, 4 * blockWorte, 2);
        List<Integer> folge = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            folge.add(basisA + 4 * i);
            folge.add(basisB + 4 * i);
        }
        // Kontrolle: gleichwertige RISC-V-Schleife ausfuehren
        StringBuilder p = new StringBuilder(".data\n");
        p.append("a: .space ").append(4 * n).append('\n');
        if (abstand > 4 * n) {
            p.append("   .space ").append(abstand - 4 * n).append('\n');
        }
        p.append("b: .space ").append(4 * n).append('\n');
        p.append(".text\n    la   t0, a\n    la   t1, b\n    addi t2, zero, 0\n    addi t3, zero, ").append(n).append('\n')
                .append("    addi s0, zero, 0\nL1: bge  t2, t3, L2\n    slli t4, t2, 2\n    add  t5, t0, t4\n    lw   t6, 0(t5)\n")
                .append("    add  s0, s0, t6\n    add  t5, t1, t4\n    lw   t6, 0(t5)\n    add  s0, s0, t6\n    addi t2, t2, 1\n")
                .append("    jal  zero, L1\nL2:\n");
        RiscvParser.Programm prog = vergleiche(p.toString(), folge);
        if (prog.marken().get("b") != basisB || prog.marken().get("a") != basisA) {
            throw new IllegalStateException("Interner Fehler: Array-Adressen");
        }
        int ma = a.misses(folge);
        int mb = b.misses(folge);
        List<CacheModel.MissArt> arten = CacheModel.klassifiziere(a, folge);

        StringBuilder t = new StringBuilder();
        t.append("Gegeben ist folgender C-Code:\n");
        t.append("\\begin{lstlisting}[language=C]\n")
                .append("int a[").append(n).append("];   // a[0] liegt bei ").append(Tex.hex8(basisA)).append('\n')
                .append("int b[").append(n).append("];   // b[0] liegt bei ").append(Tex.hex8(basisB)).append('\n')
                .append("int s = 0;\n")
                .append("for (int i = 0; i < ").append(n).append("; i++)\n")
                .append("    s = s + a[i] + b[i];\n")
                .append("\\end{lstlisting}\n");
        t.append("Nur die Zugriffe auf die Arrays gehen \\\"uber den Daten-Cache, und zwar in der Reihenfolge \\texttt{a[i]}, "
                + "\\texttt{b[i]}; \\texttt{s} und \\texttt{i} liegen in Registern. Der Cache ist anfangs leer, Ersetzung LRU.\n");
        t.append("\\begin{enumerate}[label=\\alph*)]\n");
        t.append("\\item Wie hoch ist die Miss-Rate mit Cache A: ").append(a.beschreibung()).append("?\n");
        t.append("\\item Wie hoch ist die Miss-Rate mit Cache B: ").append(b.beschreibung()).append("?\n");
        t.append("\\item Wie viele der Misses in Cache A sind Compulsory-, Capacity- und Conflict-Misses?\n");
        t.append("\\end{enumerate}\n");

        StringBuilder l = new StringBuilder();
        l.append("Zugriffe: $2 \\cdot ").append(n).append(" = ").append(folge.size()).append("$. Abstand von \\texttt{a[i]} zu "
                + "\\texttt{b[i]}: ").append(abstand).append("~Byte; die Cache-Gr\\\"o\\ss{}e ist ").append(kapBytes).append("~Byte, also ")
                .append(abstand % kapBytes == 0 ? "landen \\texttt{a[i]} und \\texttt{b[i]} in Cache A immer im selben Satz."
                        : "liegen \\texttt{a[i]} und \\texttt{b[i]} in Cache A in verschiedenen S\\\"atzen.").append('\n');
        l.append("\\begin{enumerate}[label=\\alph*)]\n");
        l.append("\\item ").append(erklaerungArray(a, n, ma)).append(" Miss-Rate ").append(MemTex.rate(ma, folge.size())).append(".\n");
        l.append("\\item ").append(erklaerungArray(b, n, mb)).append(" Miss-Rate ").append(MemTex.rate(mb, folge.size())).append(".\n");
        l.append("\\item ").append(einteilung(arten)).append("\n");
        l.append("\\end{enumerate}\n");
        String frage = "Array n=" + n + ", a@" + Tex.hex8(basisA) + " b@" + Tex.hex8(basisB) + ", Cache " + kapWorte
                + " W, Block " + blockWorte + " W";
        String antwort = "A " + ma + "/" + folge.size() + ", B " + mb + "/" + folge.size() + ", " + einteilungKurz(arten);
        return new Part("Miss-Rate einer Array-Schleife", t.toString(), l.toString(), frage, antwort);
    }

    private static String erklaerungArray(CacheModel c, int n, int misses) {
        int ohneKonflikt = 2 * n / c.worteProBlock();
        if (misses == ohneKonflikt) {
            return "Jeder Block wird einmal geladen und danach " + (c.worteProBlock() == 1 ? "nicht mehr gebraucht"
                    : c.worteProBlock() == 2 ? "f\\\"ur das zweite Wort wiederverwendet"
                    : "f\\\"ur die restlichen " + (c.worteProBlock() - 1) + " W\\\"orter wiederverwendet")
                    + ": $2 \\cdot " + n + " / " + c.worteProBlock() + " = " + misses + "$ Misses.";
        }
        if (misses == 2 * n) {
            return "\\texttt{a[i]} und \\texttt{b[i]} verdr\\\"angen sich gegenseitig: jeder Zugriff ist ein Miss ("
                    + misses + " Misses).";
        }
        return misses + " Misses (siehe Simulation).";
    }

    // ------------------------------------------------------------------ gemeinsam

    /** Programm uebersetzen, ausfuehren und Speicherzugriffe mit der Modellfolge vergleichen. */
    private static RiscvParser.Programm vergleiche(String quelle, List<Integer> folge) {
        RiscvParser.Programm p = RiscvParser.parse(quelle);
        if (!p.fehlerfrei()) {
            throw new IllegalStateException("Interner Fehler: Programm nicht uebersetzbar: " + p.meldungen() + "\n" + quelle);
        }
        List<int[]> z = RiscvMaschine.speicherzugriffe(p, 100_000);
        if (z.size() != folge.size()) {
            throw new IllegalStateException("Interner Fehler: " + z.size() + " Zugriffe statt " + folge.size());
        }
        for (int i = 0; i < z.size(); i++) {
            if (z.get(i)[0] != folge.get(i) || z.get(i)[1] != 0) {
                throw new IllegalStateException("Interner Fehler: Zugriff " + (i + 1) + " weicht ab");
            }
        }
        return p;
    }

    private static String einteilung(List<CacheModel.MissArt> arten) {
        long c = arten.stream().filter(x -> x == CacheModel.MissArt.COMPULSORY).count();
        long k = arten.stream().filter(x -> x == CacheModel.MissArt.CAPACITY).count();
        long f = arten.stream().filter(x -> x == CacheModel.MissArt.CONFLICT).count();
        return c + " Compulsory (erster Zugriff auf einen Block), " + k + " Capacity, " + f + " Conflict. "
                + (f > 0 ? "Die Conflict-Misses verschwinden in einem gleich gro\\ss{}en voll assoziativen Cache." : "");
    }

    private static String einteilungKurz(List<CacheModel.MissArt> arten) {
        return "comp/cap/conf=" + arten.stream().filter(x -> x == CacheModel.MissArt.COMPULSORY).count() + "/"
                + arten.stream().filter(x -> x == CacheModel.MissArt.CAPACITY).count() + "/"
                + arten.stream().filter(x -> x == CacheModel.MissArt.CONFLICT).count();
    }
}
