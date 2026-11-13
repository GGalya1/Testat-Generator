package de.ro.testat.exercise.cpu;

import de.ro.testat.exercise.Exercise;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * "Rechenleistung und kritischer Pfad" (Eintakt-Prozessor).
 *
 * Zufaellig: das Programm (3-5 Befehle, mit oder ohne Speicherzugriff) und die
 * Verzoegerungszeiten. Gefragt: Takte, Pfad eines Befehls, Taktperiode Tc des
 * Prozessors, Gesamtzeit. Tc wird ueber ALLE Befehlsklassen bestimmt, die der
 * Prozessor kann - nicht nur ueber die im Programm.
 *
 * Die Pfade (Kind) folgen dem vereinfachten Modell des bisherigen Testats
 * (ohne Ergebnis-Multiplexer und Extend-Einheit). Bei Bedarf hier anpassen.
 */
public final class SingleCycleTiming implements Exercise {

    /** Bauteil mit Name, Symbol und Bereich der zufaelligen Verzoegerung (ps, Vielfache von 5). */
    enum Comp {
        PCQ("Register clk-to-Q", "t_\\mathrm{pcq}", 30, 50),
        SETUP("Register Setup", "t_\\mathrm{setup}", 40, 60),
        MUX("Multiplexer", "t_\\mathrm{mux}", 20, 40),
        ANDOR("AND-OR-Gatter", "t_\\mathrm{AND\\mbox{-}OR}", 15, 30),
        ALU("ALU", "t_\\mathrm{ALU}", 100, 160),
        MEM("Instruktions-/Datenspeicher lesen", "t_\\mathrm{mem}", 150, 250),
        RF("Registerfeld lesen", "t_\\mathrm{RFread}", 80, 120);

        final String name;
        final String sym;
        final int lo;
        final int hi;

        Comp(String name, String sym, int lo, int hi) {
            this.name = name;
            this.sym = sym;
            this.lo = lo;
            this.hi = hi;
        }
    }

    /** Befehlsklasse mit ihrem Pfad durch den Eintakt-Prozessor. */
    enum Kind {
        ALU_OP("R-/I-Typ (\\texttt{add}, \\texttt{addi}, \\dots)",
                List.of(Comp.PCQ, Comp.MEM, Comp.RF, Comp.MUX, Comp.ALU, Comp.SETUP)),
        LW("\\texttt{lw}", List.of(Comp.PCQ, Comp.MEM, Comp.RF, Comp.MUX, Comp.ALU, Comp.MEM, Comp.SETUP)),
        SW("\\texttt{sw}", List.of(Comp.PCQ, Comp.MEM, Comp.RF, Comp.MUX, Comp.ALU, Comp.SETUP)),
        BEQ("\\texttt{beq}", List.of(Comp.PCQ, Comp.MEM, Comp.RF, Comp.MUX, Comp.ALU, Comp.ANDOR, Comp.MUX, Comp.SETUP));

        final String label;
        final List<Comp> path;

        Kind(String label, List<Comp> path) {
            this.label = label;
            this.path = path;
        }

        int delay(Map<Comp, Integer> d) {
            return path.stream().mapToInt(d::get).sum();
        }
    }

    private static final String[] REGS = {"t0", "t1", "t2", "t3", "t4", "t5", "s0", "s1", "a0", "a1"};
    private static final String[] ALU_OPS = {"add", "sub", "and", "or", "xor", "slt"};
    private static final String[] IMM_OPS = {"addi", "andi", "ori", "slli"};

    record Line(String text, Kind kind) {
    }

    @Override
    public String id() {
        return "eintakt";
    }

    @Override
    public List<String> preamble() {
        return List.of("\\usepackage{amsmath}");
    }

    @Override
    public Part generate(Random rng) {
        Map<Comp, Integer> d = new EnumMap<>(Comp.class);
        for (Comp c : Comp.values()) {
            d.put(c, c.lo + 5 * rng.nextInt((c.hi - c.lo) / 5 + 1));
        }
        List<Line> prog = program(rng);
        Line asked = prog.get(rng.nextInt(prog.size()));

        Kind crit = Kind.LW;
        for (Kind k : Kind.values()) {
            if (k.delay(d) > crit.delay(d)) {
                crit = k;
            }
        }
        int tc = crit.delay(d);
        int n = prog.size();
        boolean critInProgram = prog.stream().anyMatch(l -> l.kind() == Kind.LW);

        // ---------------- Aufgabe
        StringBuilder a = new StringBuilder();
        a.append("Gegeben sei folgendes RISC-V-Programm, das auf dem Eintakt-Prozessor aus der Vorlesung ausgef\\\"uhrt wird:\n");
        a.append("\\begin{lstlisting}\n")
                .append(prog.stream().map(Line::text).collect(Collectors.joining("\n")))
                .append("\n\\end{lstlisting}\n");
        a.append("Verwenden Sie die folgenden Verz\\\"ogerungszeiten:\n");
        a.append(delayTable(d));
        a.append("\\begin{enumerate}[label=\\alph*)]\n");
        a.append("\\item Wie viele Taktzyklen ben\\\"otigt die Ausf\\\"uhrung auf dem Eintakt-Prozessor?\n");
        a.append("\\item Welche Komponenten durchl\\\"auft die Instruktion \\texttt{").append(asked.text())
                .append("}? Wie lange dauert sie?\n");
        a.append("\\item Bestimmen Sie die Taktperiode $T_c$ des Eintakt-Prozessors. Welche Instruktion legt sie fest, und warum?\n");
        a.append("\\item Berechnen Sie die Gesamtausf\\\"uhrungszeit des Programms in Pikosekunden (ps).\n");
        a.append("\\end{enumerate}\n");

        // ---------------- Loesung
        StringBuilder l = new StringBuilder();
        l.append("\\begin{enumerate}[label=\\alph*)]\n");
        l.append("\\item ").append(n).append(" Takte: Der Eintakt-Prozessor f\\\"uhrt genau eine Instruktion pro Takt aus.\n");
        l.append("\\item \\texttt{").append(asked.text()).append("} (").append(asked.kind().label).append("): ")
                .append(pathText(asked.kind(), d)).append("\n");
        l.append("\\item Alle Befehlsklassen des Prozessors:\\\\[4pt]\n").append(kindTable(d, crit));
        l.append("\\\\[4pt]$T_c = ").append(tc).append("$\\,ps, festgelegt durch ").append(crit.label)
                .append(": Der Takt muss f\\\"ur \\emph{jede} Instruktion reichen, die der Prozessor ausf\\\"uhren kann, ")
                .append("und ").append(crit.label).append(" greift zweimal auf den Speicher zu (Befehl holen und Daten lesen).");
        if (!critInProgram) {
            l.append(" \\textbf{Achtung:} ").append(crit.label)
                    .append(" kommt im Programm nicht vor -- $T_c$ ist trotzdem dieser Wert, nicht die l\\\"angste Instruktion des Programms.");
        }
        l.append("\n");
        l.append("\\item $").append(n).append(" \\cdot ").append(tc).append("\\,\\mathrm{ps} = ").append(n * tc).append("\\,\\mathrm{ps}$\n");
        l.append("\\end{enumerate}\n");

        String frage = prog.stream().map(Line::text).collect(Collectors.joining("; ")) + " | Pfad: " + asked.text();
        String antwort = n + " Takte, Tc=" + tc + "ps (" + crit.name().toLowerCase() + "), Gesamt=" + (n * tc) + "ps";
        return new Part("Rechenleistung und kritischer Pfad", a.toString(), l.toString(), frage, antwort);
    }

    /** 3-5 Befehle; in etwa 40 % der Faelle ohne lw, damit klar wird, dass Tc nicht vom Programm abhaengt. */
    static List<Line> program(Random rng) {
        int n = 3 + rng.nextInt(3);
        boolean withLoad = rng.nextInt(10) < 6;
        List<Line> out = new ArrayList<>();
        int loadAt = withLoad ? rng.nextInt(n) : -1;
        for (int i = 0; i < n; i++) {
            String rd = REGS[rng.nextInt(REGS.length)];
            String r1 = REGS[rng.nextInt(REGS.length)];
            String r2 = REGS[rng.nextInt(REGS.length)];
            if (i == loadAt) {
                out.add(new Line("lw " + rd + ", " + 4 * rng.nextInt(8) + "(" + r1 + ")", Kind.LW));
                continue;
            }
            int k = rng.nextInt(10);
            if (k < 4) {
                out.add(new Line(ALU_OPS[rng.nextInt(ALU_OPS.length)] + " " + rd + ", " + r1 + ", " + r2, Kind.ALU_OP));
            } else if (k < 8) {
                String op = IMM_OPS[rng.nextInt(IMM_OPS.length)];
                int imm = op.equals("slli") ? 1 + rng.nextInt(4) : 1 + rng.nextInt(60);
                out.add(new Line(op + " " + rd + ", " + r1 + ", " + imm, Kind.ALU_OP));
            } else {
                out.add(new Line("sw " + rd + ", " + 4 * rng.nextInt(8) + "(" + r1 + ")", Kind.SW));
            }
        }
        return out;
    }

    private static String delayTable(Map<Comp, Integer> d) {
        StringBuilder sb = new StringBuilder("\\begin{center}\\begin{tabular}{llr}\n\\hline\n");
        sb.append("\\textbf{Element} & \\textbf{Symbol} & \\textbf{Verz\\\"ogerung (ps)} \\\\\n\\hline\n");
        for (Comp c : Comp.values()) {
            sb.append(c.name).append(" & $").append(c.sym).append("$ & ").append(d.get(c)).append(" \\\\\n");
        }
        return sb.append("\\hline\n\\end{tabular}\\end{center}\n").toString();
    }

    static String pathText(Kind k, Map<Comp, Integer> d) {
        String names = k.path.stream().map(c -> c.name).collect(Collectors.joining(" $\\rightarrow$ "));
        String syms = k.path.stream().map(c -> c.sym).collect(Collectors.joining(" + "));
        String nums = k.path.stream().map(c -> Integer.toString(d.get(c))).collect(Collectors.joining(" + "));
        return names + ":\\\\ $" + syms + " = " + nums + " = " + k.delay(d) + "\\,\\mathrm{ps}$";
    }

    private static String kindTable(Map<Comp, Integer> d, Kind crit) {
        StringBuilder sb = new StringBuilder("\\begin{tabular}{lp{10.5cm}r}\n\\hline\n");
        sb.append("\\textbf{Klasse} & \\textbf{Pfad} & \\textbf{ps} \\\\\n\\hline\n");
        for (Kind k : Kind.values()) {
            String path = k.path.stream().map(c -> "$" + c.sym + "$").collect(Collectors.joining(" + "));
            String sum = Integer.toString(k.delay(d));
            sb.append(k.label).append(" & ").append(path).append(" & ")
                    .append(k == crit ? "\\textbf{" + sum + "}" : sum).append(" \\\\\n");
        }
        return sb.append("\\hline\n\\end{tabular}").toString();
    }
}
