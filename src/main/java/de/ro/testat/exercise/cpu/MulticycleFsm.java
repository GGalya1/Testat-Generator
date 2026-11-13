package de.ro.testat.exercise.cpu;

import de.ro.testat.exercise.Exercise;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * "Erweiterung des Steuerwerks fuer den Mehrtakt-Prozessor".
 *
 * Gegeben ist das Steuerwerk (FSM) mit lw und evtl. einer weiteren Befehlsklasse;
 * gesucht sind Befehlstyp/Opcode, die neuen Zustaende und Uebergaenge und die
 * Steuersignale fuer einen zufaelligen Befehl. Zustaende und Signale wie im
 * Mehrtakt-Prozessor aus Harris &amp; Harris, RISC-V Edition (S0-S10).
 */
public final class MulticycleFsm implements Exercise {

    /** Zustand mit Steuersignalen und Position im Diagramm (cm). */
    enum State {
        S0("Fetch", -4, 0, "AdrSrc = 0", "IRWrite", "ALUSrcA = 00", "ALUSrcB = 10", "ALUOp = 00", "ResultSrc = 10", "PCUpdate"),
        S1("Decode", 0, 0, "ALUSrcA = 01", "ALUSrcB = 01", "ALUOp = 00"),
        S2("MemAdr", -5.6, -3.6, "ALUSrcA = 10", "ALUSrcB = 01", "ALUOp = 00"),
        S3("MemRead", -7, -6.8, "ResultSrc = 00", "AdrSrc = 1"),
        S4("MemWB", -7, -9.6, "ResultSrc = 01", "RegWrite"),
        S5("MemWrite", -4.2, -6.8, "ResultSrc = 00", "AdrSrc = 1", "MemWrite"),
        S6("ExecuteR", -2.8, -3.6, "ALUSrcA = 10", "ALUSrcB = 00", "ALUOp = 10"),
        S7("ALUWB", -1.4, -6.8, "ResultSrc = 00", "RegWrite"),
        S8("ExecuteI", 0, -3.6, "ALUSrcA = 10", "ALUSrcB = 01", "ALUOp = 10"),
        S9("JAL", 2.8, -3.6, "ALUSrcA = 01", "ALUSrcB = 10", "ALUOp = 00", "ResultSrc = 00", "PCUpdate"),
        S10("BEQ", 5.6, -3.6, "ALUSrcA = 10", "ALUSrcB = 00", "ALUOp = 01", "ResultSrc = 00", "Branch");

        final String title;
        final double x;
        final double y;
        final List<String> signals;

        State(String title, double x, double y, String... signals) {
            this.title = title;
            this.x = x;
            this.y = y;
            this.signals = List.of(signals);
        }
    }

    /** Befehlsklasse: Opcode und Zustandsfolge ab Fetch. */
    enum Kind {
        LW("lw", "0000011", State.S0, State.S1, State.S2, State.S3, State.S4),
        SW("sw", "0100011", State.S0, State.S1, State.S2, State.S5),
        R("R-Typ", "0110011", State.S0, State.S1, State.S6, State.S7),
        I("I-Typ (ALU)", "0010011", State.S0, State.S1, State.S8, State.S7),
        JAL("jal", "1101111", State.S0, State.S1, State.S9, State.S7),
        BEQ("beq", "1100011", State.S0, State.S1, State.S10);

        final String label;
        final String opcode;
        final List<State> path;

        Kind(String label, String opcode, State... path) {
            this.label = label;
            this.opcode = opcode;
            this.path = List.of(path);
        }
    }

    /** Befehl, der erweitert werden soll, mit Beispiel fuer die Aufgabe. */
    record Target(String mnemonic, String example, Kind kind) {
    }

    static final List<Target> TARGETS = List.of(
            new Target("sw", "sw rs2, offset(rs1)", Kind.SW),
            new Target("add", "add rd, rs1, rs2", Kind.R),
            new Target("sub", "sub rd, rs1, rs2", Kind.R),
            new Target("and", "and rd, rs1, rs2", Kind.R),
            new Target("or", "or rd, rs1, rs2", Kind.R),
            new Target("mul", "mul rd, rs1, rs2", Kind.R),
            new Target("addi", "addi rd, rs1, imm", Kind.I),
            new Target("andi", "andi rd, rs1, imm", Kind.I),
            new Target("ori", "ori rd, rs1, imm", Kind.I),
            new Target("beq", "beq rs1, rs2, label", Kind.BEQ),
            new Target("jal", "jal rd, label", Kind.JAL));

    /** Bedeutung der Steuersignale fuer die Loesung. */
    static final Map<String, String> MEANING = Map.ofEntries(
            Map.entry("AdrSrc = 0", "Speicheradresse = PC (Befehl holen)"),
            Map.entry("AdrSrc = 1", "Speicheradresse = Result (Datenadresse aus ALUOut)"),
            Map.entry("IRWrite", "Instruktionsregister \\\"ubernimmt den gelesenen Befehl"),
            Map.entry("ALUSrcA = 00", "ALU-Operand A = PC"),
            Map.entry("ALUSrcA = 01", "ALU-Operand A = OldPC"),
            Map.entry("ALUSrcA = 10", "ALU-Operand A = Register A (rs1)"),
            Map.entry("ALUSrcB = 00", "ALU-Operand B = Register WriteData (rs2)"),
            Map.entry("ALUSrcB = 01", "ALU-Operand B = ImmExt"),
            Map.entry("ALUSrcB = 10", "ALU-Operand B = Konstante 4"),
            Map.entry("ALUOp = 00", "ALU addiert"),
            Map.entry("ALUOp = 01", "ALU subtrahiert (Vergleich, setzt Zero)"),
            Map.entry("ALUOp = 10", "Operation aus funct3/funct7 (ALU-Decoder)"),
            Map.entry("ResultSrc = 00", "Result = ALUOut"),
            Map.entry("ResultSrc = 01", "Result = Data (aus dem Speicher gelesen)"),
            Map.entry("ResultSrc = 10", "Result = ALUResult"),
            Map.entry("PCUpdate", "PC \\\"ubernimmt Result"),
            Map.entry("RegWrite", "Registerfeld schreibt Result nach rd"),
            Map.entry("MemWrite", "Speicher schreibt WriteData (rs2) an die Adresse"),
            Map.entry("Branch", "PC \\\"ubernimmt Result (Sprungziel), wenn Zero = 1"));

    private final String auswahl;
    private final List<Target> pool;

    public MulticycleFsm() {
        this("zufall");
    }

    /**
     * @param auswahl "zufall", Befehle ("sw,beq,mul") oder Klassen ("R", "I", "sw", "beq", "jal")
     */
    public MulticycleFsm(String auswahl) {
        this.auswahl = auswahl == null || auswahl.isBlank() ? "zufall" : auswahl.trim();
        this.pool = resolve(this.auswahl);
    }

    static List<Target> resolve(String sel) {
        String s = sel.toLowerCase(Locale.ROOT);
        if (s.equals("zufall")) {
            return TARGETS;
        }
        Set<Target> out = new LinkedHashSet<>();
        for (String p : s.split(",")) {
            String q = p.trim();
            List<Target> hit = TARGETS.stream()
                    .filter(t -> t.mnemonic().equals(q) || (q.equals("r") && t.kind() == Kind.R)
                            || (q.equals("i") && t.kind() == Kind.I))
                    .toList();
            if (hit.isEmpty()) {
                throw new IllegalArgumentException("Unbekannter Befehl fuer mehrtakt: " + q
                        + " (erlaubt: R, I oder " + TARGETS.stream().map(Target::mnemonic).collect(Collectors.joining(", ")) + ")");
            }
            out.addAll(hit);
        }
        return List.copyOf(out);
    }

    @Override
    public String id() {
        return "mehrtakt";
    }

    @Override
    public String describe() {
        return id() + "(" + auswahl + ")";
    }

    @Override
    public List<String> preamble() {
        return List.of("\\usepackage{tikz}", "\\usetikzlibrary{arrows.meta}",
                "\\definecolor{fsmNeu}{HTML}{C2410C}   % neue Zustaende/Uebergaenge im Mehrtakt-Steuerwerk");
    }

    @Override
    public Part generate(Random rng) {
        Target t = pool.get(rng.nextInt(pool.size()));
        // Gegeben: lw, in der Haelfte der Faelle eine weitere Klasse (nicht die gesuchte)
        List<Kind> given = new ArrayList<>(List.of(Kind.LW));
        if (rng.nextBoolean()) {
            List<Kind> extra = Arrays.stream(Kind.values()).filter(k -> k != Kind.LW && k != t.kind()).toList();
            given.add(extra.get(rng.nextInt(extra.size())));
        }
        List<Kind> after = new ArrayList<>(given);
        after.add(t.kind());

        Set<State> givenStates = states(given);
        List<State> neu = t.kind().path.stream().filter(s -> !givenStates.contains(s)).toList();

        // ---------------- Aufgabe
        String bisher = given.stream().map(k -> k == Kind.LW ? "\\texttt{lw}" : kindName(k)).collect(Collectors.joining(" und "));
        StringBuilder a = new StringBuilder();
        a.append("Betrachtet wird der Mehrtakt-Prozessor aus der Vorlesung. Sein Steuerwerk unterst\\\"utzt bisher ")
                .append(bisher).append(":\n");
        a.append("\\begin{center}\n").append(diagram(given, Set.of())).append("\\end{center}\n");
        a.append("Nun soll der Prozessor um den Befehl \\texttt{").append(t.example()).append("} erweitert werden.");
        if (t.mnemonic().equals("mul")) {
            a.append(" Nehmen Sie an, dass die ALU bereits Multiplikation unterst\\\"utzt.");
        }
        a.append("\n\\begin{enumerate}[label=\\alph*)]\n");
        a.append("\\item Um welchen Instruktionstyp handelt es sich, und an welchem Opcode erkennt das Steuerwerk ihn?\n");
        a.append("\\item Erg\\\"anzen Sie den Zustandsautomaten um die n\\\"otigen Zust\\\"ande und \\\"Uberg\\\"ange.\n");
        a.append("\\item Geben Sie f\\\"ur jeden neuen Zustand die Steuersignale an und erkl\\\"aren Sie deren Bedeutung. "
                + "Wie viele Takte ben\\\"otigt \\texttt{").append(t.mnemonic()).append("}?\n");
        a.append("\\end{enumerate}\n");

        // ---------------- Loesung
        StringBuilder l = new StringBuilder();
        l.append("\\begin{enumerate}[label=\\alph*)]\n");
        l.append("\\item ").append(typeAnswer(t)).append("\n");
        l.append("\\item ");
        if (neu.isEmpty()) {
            l.append("Keine neuen Zust\\\"ande -- ");
        } else {
            l.append("Neu: ").append(neu.stream().map(s -> s.name() + " (" + s.title + ")").collect(Collectors.joining(", "))).append(". ");
        }
        l.append(transitionsAnswer(t, given)).append(" Neue Teile sind farbig markiert:\n");
        l.append("\\begin{center}\n").append(diagram(after, Set.of(t.kind()))).append("\\end{center}\n");
        l.append("\\item ").append(signalsAnswer(t, neu)).append("\n");
        l.append("\\end{enumerate}\n");

        String frage = "Steuerwerk mit " + given.stream().map(Kind::name).collect(Collectors.joining("+")) + ", erweitern um " + t.mnemonic();
        String antwort = t.kind().label + ", opcode " + t.kind().opcode + ", Zustaende "
                + t.kind().path.stream().map(State::name).collect(Collectors.joining("-")) + ", " + t.kind().path.size() + " Takte";
        return new Part("Erweiterung des Steuerwerks f\\\"ur den Mehrtakt-Prozessor", a.toString(), l.toString(), frage, antwort);
    }

    // ------------------------------------------------------------------

    static Set<State> states(List<Kind> kinds) {
        Set<State> s = new LinkedHashSet<>();
        kinds.forEach(k -> s.addAll(k.path));
        return s;
    }

    private static String kindName(Kind k) {
        return switch (k) {
            case R -> "R-Typ-Befehle";
            case I -> "I-Typ-ALU-Befehle";
            default -> "\\texttt{" + k.label + "}";
        };
    }

    private static String typeAnswer(Target t) {
        String base = switch (t.kind()) {
            case R -> "R-Typ, opcode \\texttt{0110011}.";
            case I -> "I-Typ (ALU mit Immediate), opcode \\texttt{0010011}.";
            case SW -> "S-Typ (Store), opcode \\texttt{0100011}.";
            case BEQ -> "B-Typ (Verzweigung), opcode \\texttt{1100011}.";
            case JAL -> "J-Typ, opcode \\texttt{1101111}.";
            case LW -> "I-Typ (Load), opcode \\texttt{0000011}.";
        };
        if (t.mnemonic().equals("mul")) {
            base += " \\texttt{mul} hat denselben Opcode wie \\texttt{add}; er unterscheidet sich nur durch "
                    + "funct7 = \\texttt{0000001}. Das wertet der ALU-Decoder aus, nicht die FSM.";
        } else if (t.kind() == Kind.R || t.kind() == Kind.I) {
            base += " Die genaue Operation (\\texttt{" + t.mnemonic() + "}) bestimmt der ALU-Decoder aus funct3"
                    + (t.kind() == Kind.R ? "/funct7" : "") + ", nicht die FSM.";
        }
        return base;
    }

    private static String transitionsAnswer(Target t, List<Kind> given) {
        boolean hasR = given.contains(Kind.R), hasI = given.contains(Kind.I), hasJ = given.contains(Kind.JAL);
        return switch (t.kind()) {
            case SW -> "Der \\\"Ubergang S1 $\\rightarrow$ S2 gilt jetzt f\\\"ur lw \\emph{oder} sw "
                    + "(Adresse wird gleich berechnet); von S2 geht es bei sw nach S5, danach zur\\\"uck zu S0.";
            case R -> "S1 $\\rightarrow$ S6 bei op = \\texttt{0110011}, S6 $\\rightarrow$ S7"
                    + (hasI || hasJ ? " (S7 existiert schon und wird mitbenutzt)" : "") + ", S7 $\\rightarrow$ S0.";
            case I -> "S1 $\\rightarrow$ S8 bei op = \\texttt{0010011}, S8 $\\rightarrow$ S7"
                    + (hasR || hasJ ? " (S7 existiert schon und wird mitbenutzt)" : "") + ", S7 $\\rightarrow$ S0.";
            case JAL -> "S1 $\\rightarrow$ S9 bei op = \\texttt{1101111}, S9 $\\rightarrow$ S7"
                    + (hasR || hasI ? " (S7 existiert schon und wird mitbenutzt)" : "")
                    + ", S7 $\\rightarrow$ S0. Das Sprungziel PC + imm hat die ALU schon in S1 (Decode) berechnet.";
            case BEQ -> "S1 $\\rightarrow$ S10 bei op = \\texttt{1100011}, S10 $\\rightarrow$ S0. "
                    + "Das Sprungziel PC + imm hat die ALU schon in S1 (Decode) berechnet.";
            case LW -> "";
        };
    }

    private static String signalsAnswer(Target t, List<State> neu) {
        StringBuilder sb = new StringBuilder();
        if (neu.isEmpty()) {
            sb.append("Keine neuen Zust\\\"ande. ");
        } else {
            sb.append("\\begin{tabular}[t]{lll}\n\\hline\n\\textbf{Zustand} & \\textbf{Signal} & \\textbf{Bedeutung} \\\\\n\\hline\n");
            for (State s : neu) {
                for (int i = 0; i < s.signals.size(); i++) {
                    String sig = s.signals.get(i);
                    sb.append(i == 0 ? s.name() + " (" + s.title + ")" : "").append(" & \\texttt{").append(sig)
                            .append("} & ").append(MEANING.get(sig)).append(" \\\\\n");
                }
                sb.append("\\hline\n");
            }
            sb.append("\\end{tabular}\\\\[4pt]\n");
            sb.append("Nicht genannte Signale sind inaktiv (Write-/Update-Signale = 0), Mux-Auswahlen beliebig.\\\\\n");
        }
        sb.append("Takte f\\\"ur \\texttt{").append(t.mnemonic()).append("}: ").append(t.kind().path.size()).append(" (")
                .append(t.kind().path.stream().map(State::name).collect(Collectors.joining(", "))).append(").");
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // TikZ
    // ------------------------------------------------------------------

    /** Kante mit Bedingung. */
    record Edge(State from, State to, String label, Kind owner) {
    }

    static List<Edge> edges(List<Kind> kinds) {
        List<Edge> e = new ArrayList<>();
        e.add(new Edge(State.S0, State.S1, null, null));
        boolean lw = kinds.contains(Kind.LW), sw = kinds.contains(Kind.SW);
        if (lw || sw) {
            String lab = lw && sw ? "op = \\texttt{0000011} (lw)\\\\oder \\texttt{0100011} (sw)"
                    : lw ? "op = \\texttt{0000011} (lw)" : "op = \\texttt{0100011} (sw)";
            e.add(new Edge(State.S1, State.S2, lab, sw ? Kind.SW : Kind.LW));
        }
        if (lw) {
            e.add(new Edge(State.S2, State.S3, sw ? "lw" : null, Kind.LW));
            e.add(new Edge(State.S3, State.S4, null, Kind.LW));
        }
        if (sw) {
            e.add(new Edge(State.S2, State.S5, "sw", Kind.SW));
        }
        if (kinds.contains(Kind.R)) {
            e.add(new Edge(State.S1, State.S6, "R-Typ\\\\\\texttt{0110011}", Kind.R));
            e.add(new Edge(State.S6, State.S7, null, Kind.R));
        }
        if (kinds.contains(Kind.I)) {
            e.add(new Edge(State.S1, State.S8, "I-Typ\\\\\\texttt{0010011}", Kind.I));
            e.add(new Edge(State.S8, State.S7, null, Kind.I));
        }
        if (kinds.contains(Kind.JAL)) {
            e.add(new Edge(State.S1, State.S9, "jal\\\\\\texttt{1101111}", Kind.JAL));
            e.add(new Edge(State.S9, State.S7, null, Kind.JAL));
        }
        if (kinds.contains(Kind.BEQ)) {
            e.add(new Edge(State.S1, State.S10, "beq\\\\\\texttt{1100011}", Kind.BEQ));
        }
        return e;
    }

    /** Zustandsdiagramm; Zustaende/Kanten der Klassen in highlight werden farbig, sofern sie neu sind. */
    static String diagram(List<Kind> kinds, Set<Kind> highlight) {
        List<Kind> base = kinds.stream().filter(k -> !highlight.contains(k)).toList();
        Set<State> baseStates = states(base);
        Set<State> all = states(kinds);
        Set<String> baseEdges = edges(base).stream().map(Edge::toString).collect(Collectors.toSet());

        StringBuilder sb = new StringBuilder();
        sb.append("\\begin{tikzpicture}[>={Stealth[length=2.2mm]}, font=\\scriptsize,\n"
                + "  st/.style={draw, rounded corners=3pt, align=center, inner sep=3pt, fill=white, thick},\n"
                + "  neu/.style={draw=fsmNeu, fill=fsmNeu!8, very thick},\n"
                + "  lab/.style={fill=white, inner sep=1pt, align=center, font=\\tiny}]\n");
        for (State s : all) {
            boolean isNew = !baseStates.contains(s);
            sb.append(String.format(Locale.ROOT, "\\node[st%s] (%s) at (%.2f,%.2f) {\\textbf{%s: %s}\\\\%s};%n",
                    isNew ? ",neu" : "", s.name(), s.x, s.y, s.name(), s.title, String.join("\\\\", s.signals)));
        }
        // feste Bounding Box: Aufgabe und Loesung gleich gross, unabhaengig von den vorhandenen Zustaenden
        sb.append("\\useasboundingbox (-8.6,1.6) rectangle (7.0,-11.2);\n");
        sb.append("\\draw[<-, thick] (S0.west) -- ++(-0.9,0) node[left] {Reset};\n");
        for (Edge e : edges(kinds)) {
            boolean isNew = !baseEdges.contains(e.toString());
            String col = isNew ? "fsmNeu, very thick" : "thick";
            String lc = isNew ? ", text=fsmNeu" : "";
            if (e.label() == null) {
                sb.append(String.format("\\draw[->, %s] (%s) -- (%s);%n", col, e.from().name(), e.to().name()));
            } else {
                sb.append(String.format("\\draw[->, %s] (%s) -- node[lab%s, pos=0.62] {%s} (%s);%n",
                        col, e.from().name(), lc, e.label(), e.to().name()));
            }
        }
        // Rueckkehr zu S0 als kurze Markierung unter den Endzustaenden
        for (State s : all) {
            boolean terminal = kinds.stream().anyMatch(k -> k.path.get(k.path.size() - 1) == s);
            if (terminal) {
                boolean isNew = !baseStates.contains(s);
                sb.append(String.format("\\draw[->, thick%s] (%s.south) -- ++(0,-0.45) node[below, font=\\tiny%s] {zu S0};%n",
                        isNew ? ", fsmNeu" : "", s.name(), isNew ? ", text=fsmNeu" : ""));
            }
        }
        sb.append("\\end{tikzpicture}\n");
        return sb.toString();
    }
}
