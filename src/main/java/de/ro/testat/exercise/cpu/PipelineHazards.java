package de.ro.testat.exercise.cpu;

import de.ro.testat.exercise.Exercise;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * "Datenkonflikte in der Pipeline".
 *
 * 5-stufige Pipeline wie in der Vorlesung (Harris &amp; Harris): Registerfeld schreibt in der
 * ersten, liest in der zweiten Takthaelfte; Forwarding von EX/MEM und MEM/WB in die EX-Stufe;
 * Hazard-Einheit haelt bei lw mit direkt folgendem Verbraucher einen Takt an.
 *
 * Zufaellig: ein Programm aus 5-6 Befehlen ohne Spruenge. Gefragt: RAW-Abhaengigkeiten,
 * nops ohne Forwarding, Stalls und Pipeline-Diagramm mit Forwarding, Umordnung ohne Stall.
 * Der Generator nimmt nur Programme, bei denen es eine solche Umordnung gibt.
 */
public final class PipelineHazards implements Exercise {

    enum Op { ALU, LOAD, STORE }

    /** Befehl: Text, Art, Zielregister (oder null) und gelesene Register. */
    record Instr(String text, Op op, String rd, List<String> srcs) {
        boolean writes(String r) {
            return rd != null && rd.equals(r);
        }

        boolean reads(String r) {
            return srcs.contains(r);
        }

        boolean memory() {
            return op != Op.ALU;
        }
    }

    /** RAW-Abhaengigkeit: Befehl to liest reg, das zuletzt Befehl from geschrieben hat (Indizes 0-basiert). */
    record Dep(int from, int to, String reg) {
        int distance() {
            return to - from;
        }
    }

    private static final String[] REGS = {"t0", "t1", "t2", "t3", "s0", "s1", "a0", "a1"};
    private static final String[] R_OPS = {"add", "sub", "and", "or", "xor"};
    private static final String[] I_OPS = {"addi", "andi", "ori", "slli"};

    @Override
    public String id() {
        return "hazards";
    }

    @Override
    public Part generate(Random rng) {
        for (int attempt = 0; attempt < 100_000; attempt++) {
            List<Instr> p = program(rng);
            List<Dep> deps = deps(p);
            long noFwd = deps.stream().filter(PipelineHazards::hazardWithoutForwarding).count();
            int nops = sum(nopsWithoutForwarding(p));
            int stalls = stalls(p);
            if (noFwd < 2 || stalls < 1 || nops > 7) {
                continue;
            }
            List<Integer> order = bestOrder(p);
            if (stalls(permute(p, order)) != 0) {
                continue;
            }
            return render(p, deps, order);
        }
        throw new IllegalStateException("kein passendes Programm gefunden");
    }

    // ------------------------------------------------------------------
    // Programm
    // ------------------------------------------------------------------

    static List<Instr> program(Random rng) {
        int n = 5 + rng.nextInt(2);
        Set<Integer> loads = new LinkedHashSet<>();
        loads.add(rng.nextInt(n - 1));
        if (rng.nextBoolean()) {
            loads.add(rng.nextInt(n - 1));
        }
        List<Instr> out = new ArrayList<>();
        List<String> written = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            String rd = REGS[rng.nextInt(REGS.length)];
            String a = src(rng, written);
            String b = src(rng, written);
            Instr ins;
            int off = 4 * rng.nextInt(8);
            if (loads.contains(i)) {
                ins = new Instr("lw " + rd + ", " + off + "(" + a + ")", Op.LOAD, rd, List.of(a));
            } else {
                int k = rng.nextInt(10);
                if (k < 4) {
                    String op = R_OPS[rng.nextInt(R_OPS.length)];
                    ins = new Instr(op + " " + rd + ", " + a + ", " + b, Op.ALU, rd, a.equals(b) ? List.of(a) : List.of(a, b));
                } else if (k < 8) {
                    String op = I_OPS[rng.nextInt(I_OPS.length)];
                    int imm = op.equals("slli") ? 1 + rng.nextInt(4) : 1 + rng.nextInt(60);
                    ins = new Instr(op + " " + rd + ", " + a + ", " + imm, Op.ALU, rd, List.of(a));
                } else {
                    // sw rs2, off(rs1): rs2 = a (Wert), rs1 = b (Basis)
                    ins = new Instr("sw " + a + ", " + off + "(" + b + ")", Op.STORE, null, a.equals(b) ? List.of(a) : List.of(b, a));
                }
            }
            out.add(ins);
            if (ins.rd() != null) {
                written.add(ins.rd());
            }
        }
        return out;
    }

    /** Quellregister: oft eines der zuletzt geschriebenen, damit Abhaengigkeiten entstehen. */
    private static String src(Random rng, List<String> written) {
        if (!written.isEmpty() && rng.nextInt(10) < 6) {
            int k = Math.min(2, written.size());
            return written.get(written.size() - 1 - rng.nextInt(k));
        }
        return REGS[rng.nextInt(REGS.length)];
    }

    // ------------------------------------------------------------------
    // Analyse
    // ------------------------------------------------------------------

    static List<Dep> deps(List<Instr> p) {
        List<Dep> out = new ArrayList<>();
        for (int j = 0; j < p.size(); j++) {
            for (String r : p.get(j).srcs()) {
                for (int i = j - 1; i >= 0; i--) {
                    if (p.get(i).writes(r)) {
                        out.add(new Dep(i, j, r));
                        break;
                    }
                }
            }
        }
        return out;
    }

    /** Ohne Forwarding: Verbraucher liest in ID, Erzeuger schreibt in WB (erste Takthaelfte) -> Abstand >= 3. */
    static boolean hazardWithoutForwarding(Dep d) {
        return d.distance() < 3;
    }

    /** Mit Forwarding bleibt nur lw mit direkt folgendem Verbraucher. */
    static boolean hazardWithForwarding(List<Instr> p, Dep d) {
        return p.get(d.from()).op() == Op.LOAD && d.distance() == 1;
    }

    /** nops vor jedem Befehl (ohne Forwarding, minimal). ID(j) &gt;= WB(i) &lt;=&gt; Abstand in Takten &gt;= 3. */
    static int[] nopsWithoutForwarding(List<Instr> p) {
        int[] c = new int[p.size()];   // Takt, in dem der Befehl geholt wird
        int[] nops = new int[p.size()];
        for (int j = 0; j < p.size(); j++) {
            int t = j == 0 ? 1 : c[j - 1] + 1;
            for (Dep d : deps(p)) {
                if (d.to() == j) {
                    t = Math.max(t, c[d.from()] + 3);
                }
            }
            nops[j] = j == 0 ? 0 : t - c[j - 1] - 1;
            c[j] = t;
        }
        return nops;
    }

    /** EX-Takt jedes Befehls mit Forwarding und Hazard-Einheit (erster Befehl: IF in Takt 1, EX in Takt 3). */
    static int[] exCycles(List<Instr> p) {
        int[] e = new int[p.size()];
        for (int j = 0; j < p.size(); j++) {
            int t = j == 0 ? 3 : e[j - 1] + 1;
            for (Dep d : deps(p)) {
                if (d.to() == j) {
                    t = Math.max(t, e[d.from()] + (p.get(d.from()).op() == Op.LOAD ? 2 : 1));
                }
            }
            e[j] = t;
        }
        return e;
    }

    static int stalls(List<Instr> p) {
        int[] e = exCycles(p);
        return e[e.length - 1] - 3 - (p.size() - 1);
    }

    /** Takte vom ersten IF bis zum letzten WB. */
    static int cycles(int instructions, int bubbles) {
        return instructions + bubbles + 4;
    }

    // ------------------------------------------------------------------
    // Umordnung
    // ------------------------------------------------------------------

    /** Muss Befehl i (i &lt; j) vor j bleiben? RAW, WAR, WAW und Speicherreihenfolge (sw nie ueber lw/sw). */
    static boolean mustPrecede(Instr i, Instr j) {
        if (i.rd() != null && (j.reads(i.rd()) || j.writes(i.rd()))) {
            return true;
        }
        if (j.rd() != null && i.reads(j.rd())) {
            return true;
        }
        return i.memory() && j.memory() && (i.op() == Op.STORE || j.op() == Op.STORE);
    }

    /** Alle zulaessigen Reihenfolgen; beste = wenigste Stalls, dann geringste Verschiebung. */
    static List<Integer> bestOrder(List<Instr> p) {
        List<List<Integer>> all = new ArrayList<>();
        enumerate(p, new ArrayList<>(), new boolean[p.size()], all);
        return all.stream()
                .min(Comparator.<List<Integer>>comparingInt(o -> stalls(permute(p, o)))
                        .thenComparingInt(PipelineHazards::displacement)
                        .thenComparing(Object::toString))
                .orElseThrow();
    }

    static List<List<Integer>> validOrders(List<Instr> p) {
        List<List<Integer>> all = new ArrayList<>();
        enumerate(p, new ArrayList<>(), new boolean[p.size()], all);
        return all;
    }

    private static void enumerate(List<Instr> p, List<Integer> cur, boolean[] used, List<List<Integer>> out) {
        if (cur.size() == p.size()) {
            out.add(List.copyOf(cur));
            return;
        }
        for (int j = 0; j < p.size(); j++) {
            if (used[j]) {
                continue;
            }
            boolean ready = true;
            for (int i = 0; i < j && ready; i++) {
                if (!used[i] && mustPrecede(p.get(i), p.get(j))) {
                    ready = false;
                }
            }
            if (ready) {
                used[j] = true;
                cur.add(j);
                enumerate(p, cur, used, out);
                cur.remove(cur.size() - 1);
                used[j] = false;
            }
        }
    }

    private static int displacement(List<Integer> o) {
        int s = 0;
        for (int k = 0; k < o.size(); k++) {
            s += Math.abs(o.get(k) - k);
        }
        return s;
    }

    static List<Instr> permute(List<Instr> p, List<Integer> order) {
        return order.stream().map(p::get).toList();
    }

    // ------------------------------------------------------------------
    // LaTeX
    // ------------------------------------------------------------------

    private Part render(List<Instr> p, List<Dep> deps, List<Integer> order) {
        int n = p.size();
        int[] nops = nopsWithoutForwarding(p);
        int nopSum = sum(nops);
        int stalls = stalls(p);
        int gridCols = cycles(n, 0) + 3;

        StringBuilder a = new StringBuilder();
        a.append("Das folgende Programm wird auf der 5-stufigen Pipeline aus der Vorlesung (IF, ID, EX, MEM, WB) ausgef\\\"uhrt. "
                + "Das Registerfeld wird in der ersten Takth\\\"alfte geschrieben und in der zweiten Takth\\\"alfte gelesen.\n");
        a.append("\\begin{center}\n").append(listing(p, null)).append("\\end{center}\n");
        a.append("\\begin{enumerate}[label=\\alph*)]\n");
        a.append("\\item Geben Sie alle Datenabh\\\"angigkeiten (RAW) an. Welche davon f\\\"uhren \\emph{ohne} Forwarding zu einem Datenkonflikt?\n");
        a.append("\\item Der Prozessor hat weder Forwarding noch eine Hazard-Einheit. F\\\"ugen Sie m\\\"oglichst wenige "
                + "\\texttt{nop}-Befehle ein, sodass das Programm korrekt ausgef\\\"uhrt wird. Wie viele Takte ben\\\"otigt es dann "
                + "(vom ersten IF bis zum letzten WB)?\n");
        a.append("\\item Jetzt hat der Prozessor Forwarding und eine Hazard-Einheit. Welche Konflikte bleiben? Tragen Sie den "
                + "Ablauf in das Pipeline-Diagramm ein und geben Sie die Anzahl der Takte an.\n");
        a.append("\\begin{center}\n").append(diagram(p, null, gridCols)).append("\\end{center}\n");
        a.append("\\item Ordnen Sie die Befehle so um, dass mit Forwarding kein Stall mehr n\\\"otig ist. "
                + "Das Ergebnis des Programms darf sich nicht \\\"andern.\n");
        a.append("\\end{enumerate}\n");

        StringBuilder l = new StringBuilder();
        l.append("\\begin{enumerate}[label=\\alph*)]\n");
        // a)
        l.append("\\item \\begin{tabular}[t]{lllc}\n\\hline\n\\textbf{Abh\\\"angigkeit} & \\textbf{Register} & \\textbf{Abstand} & "
                + "\\textbf{Konflikt ohne Forwarding} \\\\\n\\hline\n");
        for (Dep d : deps) {
            l.append("(").append(d.from() + 1).append(") $\\rightarrow$ (").append(d.to() + 1).append(") & \\texttt{")
                    .append(d.reg()).append("} & ").append(d.distance()).append(" & ")
                    .append(hazardWithoutForwarding(d) ? "\\textbf{ja}" : "nein").append(" \\\\\n");
        }
        l.append("\\hline\n\\end{tabular}\\\\[3pt]\n");
        l.append("Ohne Forwarding liest der Verbraucher in ID, der Erzeuger schreibt in WB: Konflikt bei Abstand $< 3$.\n");
        // b)
        l.append("\\item ").append(nopSum).append(" \\texttt{nop}s:\\\\[2pt]\n").append(listing(p, nops));
        l.append("\\\\[2pt]").append(n).append(" Befehle + ").append(nopSum).append(" \\texttt{nop}s + 4 = \\textbf{")
                .append(cycles(n, nopSum)).append(" Takte}.\n");
        // c)
        List<Dep> remaining = deps.stream().filter(d -> hazardWithForwarding(p, d)).toList();
        l.append("\\item Es bleiben nur Load-Use-Konflikte (\\texttt{lw} mit direkt folgendem Verbraucher, der Wert ist erst nach MEM da): ")
                .append(remaining.stream().map(d -> "(" + (d.from() + 1) + ") $\\rightarrow$ (" + (d.to() + 1) + ") \\texttt{" + d.reg() + "}")
                        .collect(Collectors.joining(", ")))
                .append(". Alle anderen l\\\"ost Forwarding. ").append(stalls).append(" Stall").append(stalls == 1 ? "" : "s")
                .append(", also ").append(n).append(" + ").append(stalls).append(" + 4 = \\textbf{").append(cycles(n, stalls))
                .append(" Takte}. Grau: angehaltene Stufe.\\\\[3pt]\n");
        l.append(diagram(p, exCycles(p), gridCols));
        l.append("\n");
        // d)
        l.append("\\item Eine m\\\"ogliche Reihenfolge (urspr\\\"ungliche Nummer in Klammern):\\\\[2pt]\n")
                .append(reordered(p, order));
        l.append("\\\\[2pt]Keine Abh\\\"angigkeit wird umgedreht (auch kein WAR/WAW, \\texttt{sw} bleibt in der Reihenfolge zu "
                + "\\texttt{lw}/\\texttt{sw}), und kein Verbraucher steht direkt hinter seinem \\texttt{lw}: 0 Stalls, "
                + n + " + 4 = \\textbf{" + cycles(n, 0) + " Takte}. Andere Reihenfolgen mit diesen Eigenschaften sind ebenfalls richtig.\n");
        l.append("\\end{enumerate}\n");

        String frage = p.stream().map(Instr::text).collect(Collectors.joining("; "));
        String antwort = "RAW ohne FW: " + deps.stream().filter(PipelineHazards::hazardWithoutForwarding).count()
                + ", nops=" + nopSum + " (" + cycles(n, nopSum) + " Takte), Stalls mit FW=" + stalls + " (" + cycles(n, stalls)
                + " Takte), Umordnung " + order.stream().map(i -> Integer.toString(i + 1)).collect(Collectors.joining(","));
        return new Part("Datenkonflikte in der Pipeline", a.toString(), l.toString(), frage, antwort);
    }

    /** Nummerierte Befehlsliste; mit nops (Anzahl vor jedem Befehl), falls angegeben. */
    private static String listing(List<Instr> p, int[] nops) {
        StringBuilder sb = new StringBuilder("\\begin{tabular}[t]{rl}\n");
        for (int j = 0; j < p.size(); j++) {
            if (nops != null) {
                for (int k = 0; k < nops[j]; k++) {
                    sb.append(" & \\textcolor{TUDa-9b}{\\texttt{nop}} \\\\\n");
                }
            }
            sb.append("(").append(j + 1).append(") & \\texttt{").append(p.get(j).text()).append("} \\\\\n");
        }
        return sb.append("\\end{tabular}").toString();
    }

    private static String reordered(List<Instr> p, List<Integer> order) {
        StringBuilder sb = new StringBuilder("\\begin{tabular}[t]{rl}\n");
        for (int i : order) {
            sb.append("(").append(i + 1).append(") & \\texttt{").append(p.get(i).text()).append("} \\\\\n");
        }
        return sb.append("\\end{tabular}").toString();
    }

    /**
     * Pipeline-Diagramm. ex == null: leeres Raster fuer die Aufgabe.
     * Befehl j: IF ab ID-Beginn von j-1, ID ab EX von j-1 bis EX(j)-1, dann EX, MEM, WB.
     */
    static String diagram(List<Instr> p, int[] ex, int cols) {
        StringBuilder sb = new StringBuilder("{\\scriptsize\\setlength{\\tabcolsep}{3pt}\\renewcommand{\\arraystretch}{1.25}%\n");
        sb.append("\\begin{tabular}{l|*{").append(cols).append("}{c|}}\n\\hline\n\\textbf{Takt}");
        for (int c = 1; c <= cols; c++) {
            sb.append(" & ").append(c);
        }
        sb.append(" \\\\\n\\hline\n");
        int prevIdStart = 1;
        int prevEx = 2;
        for (int j = 0; j < p.size(); j++) {
            String[] cell = new String[cols + 1];
            if (ex != null) {
                int f = prevIdStart;
                int idStart = prevEx;
                for (int c = f; c < idStart; c++) {
                    cell[c] = c == f ? "IF" : "\\textcolor{gray}{IF}";
                }
                for (int c = idStart; c < ex[j]; c++) {
                    cell[c] = c == idStart ? "ID" : "\\textcolor{gray}{ID}";
                }
                cell[ex[j]] = "EX";
                cell[ex[j] + 1] = "MEM";
                cell[ex[j] + 2] = "WB";
                prevIdStart = idStart;
                prevEx = ex[j];
            }
            sb.append("(").append(j + 1).append(") \\texttt{").append(p.get(j).text()).append("}");
            for (int c = 1; c <= cols; c++) {
                sb.append(" & \\makebox[5.5mm]{").append(cell[c] == null ? "" : cell[c]).append("}");
            }
            sb.append(" \\\\\n\\hline\n");
        }
        return sb.append("\\end{tabular}}\n").toString();
    }

    private static int sum(int[] a) {
        int s = 0;
        for (int x : a) {
            s += x;
        }
        return s;
    }
}
