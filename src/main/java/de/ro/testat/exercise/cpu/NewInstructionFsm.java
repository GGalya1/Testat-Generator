package de.ro.testat.exercise.cpu;

import de.ro.testat.exercise.Auswahl;
import de.ro.testat.exercise.Exercise;
import de.ro.testat.exercise.cpu.Datapath.Goal;
import de.ro.testat.exercise.cpu.Datapath.Step;
import de.ro.testat.exercise.cpu.MulticycleFsm.State;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * "Neuer Befehl im Mehrtakt-Steuerwerk": Ein (meist ausgedachter) Befehl aus bekannten Bausteinen
 * soll ins Steuerwerk eingebaut werden. Gegeben sind nur Fetch/Decode oder zusaetzlich die
 * lw-Zustaende als Stuetze.
 *
 * <p>Die Loesung wird nicht aus einer Tabelle genommen, sondern berechnet: {@link Datapath#shortest}
 * sucht die kuerzeste Steuerfolge auf dem unveraenderten Datenpfad, vorhandene Zustaende werden
 * wiederverwendet, wo es passt, und {@link Datapath#simulate} prueft das Ergebnis mit konkreten Zahlen.
 * Neue Befehle: Eintrag in {@link #CATALOG}; nicht umsetzbare Befehle werden automatisch verworfen.
 */
public final class NewInstructionFsm implements Exercise {

    /** Befehl: Schreibweise, Format, Opcode (null = frei waehlbarer custom-Opcode) und Wirkung. */
    record Instr(String mnemonic, String syntax, String format, String opcode, Goal goal) {
    }

    static final List<Instr> CATALOG = List.of(
            new Instr("jalr", "jalr rd, imm(rs1)", "I-Typ", "1100111", new Goal("pc+4", "rs1+imm", null, null)),
            new Instr("auipc", "auipc rd, imm", "U-Typ", "0010111", new Goal("pc+imm", null, null, null)),
            new Instr("lwr", "lwr rd, rs1, rs2", "R-Typ", null, new Goal("M[rs1+rs2]", null, null, null)),
            new Instr("lwpc", "lwpc rd, imm", "I-Typ (rs1 wird nicht verwendet)", null, new Goal("M[pc+imm]", null, null, null)),
            new Instr("swpc", "swpc rs2, imm", "S-Typ (rs1 wird nicht verwendet)", null, new Goal(null, null, "pc+imm", "rs2")),
            new Instr("lwi", "lwi rd, imm(rs1)", "I-Typ", null, new Goal("M[M[rs1+imm]]", null, null, null)),
            new Instr("swi", "swi rs2, imm(rs1)", "S-Typ", null, new Goal(null, null, "M[rs1+imm]", "rs2")),
            new Instr("jrr", "jrr rs1, rs2", "R-Typ (rd wird nicht verwendet)", null, new Goal(null, "rs1+rs2", null, null)),
            new Instr("jalrr", "jalrr rd, rs1, rs2", "R-Typ", null, new Goal("pc+4", "rs1+rs2", null, null)),
            new Instr("jm", "jm imm(rs1)", "I-Typ (rd wird nicht verwendet)", null, new Goal(null, "M[rs1+imm]", null, null)),
            new Instr("jalm", "jalm rd, imm(rs1)", "I-Typ", null, new Goal("pc+4", "M[rs1+imm]", null, null)),
            new Instr("jmpc", "jmpc imm", "I-Typ (rd und rs1 werden nicht verwendet)", null, new Goal(null, "M[pc+imm]", null, null)));

    /** Im RISC-V-Standard fuer eigene Erweiterungen reservierte Opcodes (custom-0 bis custom-3). */
    static final List<String> CUSTOM_OPCODES = List.of("0001011", "0101011", "1011011", "1111011");

    static final String LW_OPCODE = "0000011";

    /** Was im gegebenen Steuerwerk schon steht. */
    enum Given {
        FETCH_DECODE("nur Fetch und Decode", State.S0, State.S1),
        WITH_LW("Fetch, Decode und die Zust\\\"ande f\\\"ur \\texttt{lw}", State.S0, State.S1, State.S2, State.S3, State.S4);

        final String text;
        final List<State> states;

        Given(String text, State... states) {
            this.text = text;
            this.states = List.of(states);
        }

        /** Nachfolger im gegebenen Steuerwerk ("S0" = zurueck zu Fetch). */
        String successor(State s) {
            return switch (s) {
                case S2 -> "S3";
                case S3 -> "S4";
                case S4 -> "S0";
                default -> throw new IllegalArgumentException(s.name());
            };
        }
    }

    /** Zustand im Pfad des neuen Befehls. existing = Zustand des gegebenen Steuerwerks, der wiederverwendet wird. */
    record Node(String id, String title, List<String> signals, List<String> effects, State existing) {
        boolean isNew() {
            return existing == null;
        }
    }

    /** Berechnete Loesung fuer einen Befehl und ein gegebenes Steuerwerk. */
    record Plan(Instr ins, Given given, List<Step> steps, List<Node> nodes) {
        int cycles() {
            return steps.size() + 2;
        }

        long newStates() {
            return nodes.stream().filter(Node::isNew).count();
        }
    }

    /** H&amp;H-Zustaende, deren Namen fuer gleiche Signale uebernommen werden (allgemeine Bausteine). */
    private static final Set<State> GENERIC = Set.of(State.S2, State.S3, State.S4, State.S5, State.S7);

    private static final Map<String, Optional<Plan>> CACHE = new HashMap<>();

    private final String auswahl;
    private final List<Instr> pool;

    public NewInstructionFsm() {
        this("zufall");
    }

    /** @param auswahl "zufall" oder Befehle, z. B. "jalm,lwi,swpc" */
    public NewInstructionFsm(String auswahl) {
        this.auswahl = Auswahl.normal(auswahl);
        this.pool = Auswahl.liste(auswahl, CATALOG, Instr::mnemonic, false, "mehrtakt-neu");
    }

    @Override
    public String id() {
        return "mehrtakt-neu";
    }

    @Override
    public String describe() {
        return id() + "(" + auswahl + ")";
    }

    @Override
    public List<String> preamble() {
        return new MulticycleFsm().preamble();
    }

    // ------------------------------------------------------------------
    // Planung
    // ------------------------------------------------------------------

    static synchronized Optional<Plan> plan(Instr ins, Given given) {
        return CACHE.computeIfAbsent(ins.mnemonic() + "|" + given, k -> compute(ins, given));
    }

    private static Optional<Plan> compute(Instr ins, Given given) {
        List<List<Step>> sols = Datapath.shortest(ins.goal(), 6);
        Plan best = null;
        for (List<Step> sol : sols) {
            Plan p = map(ins, given, sol);
            if (best == null || better(p, best)) {
                best = p;
            }
        }
        if (best == null || best.newStates() == 0) {
            return Optional.empty();
        }
        for (long seed = 0; seed < 50; seed++) {
            if (!Datapath.simulate(ins.goal(), best.steps(), seed)) {
                throw new IllegalStateException("Interner Fehler: Steuerfolge fuer " + ins.mnemonic() + " falsch: " + best.steps());
            }
        }
        return Optional.of(best);
    }

    /** Mehr Wiederverwendung, dann weniger Signale, dann fest (deterministisch). */
    private static boolean better(Plan p, Plan q) {
        long rp = p.nodes().size() - p.newStates(), rq = q.nodes().size() - q.newStates();
        if (rp != rq) {
            return rp > rq;
        }
        // Zustaende, die es so in der Vorlesung gibt, sind fuer Studierende vertrauter
        long kp = p.steps().stream().filter(s -> match(s).isPresent()).count();
        long kq = q.steps().stream().filter(s -> match(s).isPresent()).count();
        if (kp != kq) {
            return kp > kq;
        }
        int sp = p.steps().stream().mapToInt(s -> s.signals().size()).sum();
        int sq = q.steps().stream().mapToInt(s -> s.signals().size()).sum();
        if (sp != sq) {
            return sp < sq;
        }
        return p.steps().toString().compareTo(q.steps().toString()) < 0;
    }

    static Optional<State> match(Step st) {
        Set<String> sig = new HashSet<>(st.signals());
        for (State s : State.values()) {
            if (new HashSet<>(s.signals).equals(sig)) {
                return Optional.of(s);
            }
        }
        return Optional.empty();
    }

    /** Ordnet den Takten Zustaende zu: gegebene wiederverwenden, wenn Signale und Nachfolger passen. */
    static Plan map(Instr ins, Given given, List<Step> steps) {
        int n = steps.size();
        State[] m = new State[n];
        boolean[] reuse = new boolean[n];
        Set<State> used = new HashSet<>();
        for (int i = 0; i < n; i++) {
            m[i] = match(steps.get(i)).orElse(null);
            reuse[i] = m[i] != null && given.states.contains(m[i]) && used.add(m[i]);
        }
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int i = 0; i < n; i++) {
                if (!reuse[i]) {
                    continue;
                }
                String oldSucc = given.successor(m[i]);
                String newSucc = i + 1 < n ? (reuse[i + 1] ? m[i + 1].name() : "neu") : "S0";
                boolean ok = oldSucc.equals(newSucc) || (!oldSucc.equals("S0") && !newSucc.equals("S0"));
                if (!ok) {
                    reuse[i] = false;
                    changed = true;
                }
            }
        }
        // Namen fuer neue Zustaende
        Set<String> ids = new HashSet<>();
        given.states.forEach(s -> ids.add(s.name()));
        for (int i = 0; i < n; i++) {
            if (reuse[i]) {
                ids.add(m[i].name());
            }
        }
        long customCount = 0;
        String[] id = new String[n];
        String[] title = new String[n];
        for (int i = 0; i < n; i++) {
            if (reuse[i]) {
                id[i] = m[i].name();
                title[i] = m[i].title;
            } else if (m[i] != null && GENERIC.contains(m[i]) && ids.add(m[i].name())) {
                id[i] = m[i].name();
                title[i] = m[i].title;
            }
        }
        long customs = java.util.stream.IntStream.range(0, n).filter(i -> id[i] == null).count();
        int next = 11;
        for (int i = 0; i < n; i++) {
            if (id[i] == null) {
                while (ids.contains("S" + next)) {
                    next++;
                }
                id[i] = "S" + next;
                ids.add(id[i]);
                customCount++;
                title[i] = ins.mnemonic().toUpperCase(Locale.ROOT) + (customs > 1 ? Long.toString(customCount) : "");
            }
        }
        List<Node> nodes = new ArrayList<>();
        Datapath.Sym s = Datapath.afterDecode();
        for (int i = 0; i < n; i++) {
            Step st = steps.get(i);
            nodes.add(new Node(id[i], title[i], st.signals(), Datapath.effects(s, st), reuse[i] ? m[i] : null));
            s = Datapath.apply(s, st);
        }
        return new Plan(ins, given, steps, nodes);
    }

    // ------------------------------------------------------------------
    // Aufgabe
    // ------------------------------------------------------------------

    @Override
    public Part generate(Random rng) {
        Given given = rng.nextBoolean() ? Given.WITH_LW : Given.FETCH_DECODE;
        List<Plan> ok = pool.stream().map(i -> plan(i, given)).flatMap(Optional::stream).toList();
        if (ok.isEmpty()) {
            Given other = given == Given.WITH_LW ? Given.FETCH_DECODE : Given.WITH_LW;
            ok = pool.stream().map(i -> plan(i, other)).flatMap(Optional::stream).toList();
        }
        if (ok.isEmpty()) {
            throw new IllegalStateException("Keiner der Befehle " + auswahl + " braucht einen neuen Zustand");
        }
        Plan p = ok.get(rng.nextInt(ok.size()));
        Instr ins = p.ins();
        String opcode = ins.opcode() != null ? ins.opcode() : CUSTOM_OPCODES.get(rng.nextInt(CUSTOM_OPCODES.size()));

        StringBuilder a = new StringBuilder();
        a.append("Der Mehrtakt-Prozessor aus der Vorlesung soll um folgenden Befehl erweitert werden:\n");
        a.append("\\begin{center}\\begin{tabular}{ll}\n\\hline\n");
        a.append("Befehl & \\texttt{").append(ins.syntax()).append("}")
                .append(ins.opcode() != null ? " (aus RV32I)" : " (ausgedacht)").append(" \\\\\n");
        a.append("Format & ").append(ins.format()).append(" \\\\\n");
        a.append("Opcode & \\texttt{").append(opcode).append("} \\\\\n");
        a.append("Wirkung & ").append(rtl(ins.goal())).append(" \\\\\n\\hline\n\\end{tabular}\\end{center}\n");
        a.append("{\\small PC ist die Adresse des Befehls, imm der vom Decoder erzeugte Direktwert (ImmExt), "
                + "Mem[$x$] das Speicherwort an Adresse $x$.}\\par\\medskip\n");
        a.append("Der Datenpfad bleibt unver\\\"andert. Pro Takt sind h\\\"ochstens eine ALU-Operation, ein Speicherzugriff "
                + "und ein Schreibzugriff auf das Registerfeld m\\\"oglich. Ergebnisse von ALU und Speicher stehen erst im "
                + "n\\\"achsten Takt (in ALUOut bzw.\\ Data) f\\\"ur das Registerfeld oder als Speicheradresse zur Verf\\\"ugung. "
                + "Vorhandene Zust\\\"ande d\\\"urfen wiederverwendet werden.\n");
        a.append("\\begin{enumerate}[label=\\alph*)]\n");
        a.append("\\item Erg\\\"anzen Sie den Zustandsautomaten so, dass \\texttt{").append(ins.mnemonic())
                .append("} m\\\"oglichst wenige Takte ben\\\"otigt (Zust\\\"ande und \\\"Uberg\\\"ange mit Bedingung).\n");
        a.append("\\item Geben Sie f\\\"ur jeden neuen Zustand die Steuersignale an.\n");
        a.append("\\item Wie viele Takte ben\\\"otigt \\texttt{").append(ins.mnemonic()).append("}?\n");
        a.append("\\end{enumerate}\n");
        a.append("Gegeben ist das Steuerwerk mit ").append(given.text)
                .append(". Tragen Sie Ihre Erg\\\"anzungen direkt ein:\n");
        a.append("\\begin{center}\n").append(diagram(p, opcode, false)).append("\\end{center}\n");

        StringBuilder l = new StringBuilder();
        l.append("\\begin{enumerate}[label=\\alph*)]\n");
        l.append("\\item M\\\"ogliche L\\\"osung mit minimaler Taktzahl, neue Teile farbig. Andere L\\\"osungen mit "
                + "gleicher Taktzahl sind ebenfalls richtig (z.\\,B. eigene Zust\\\"ande statt wiederverwendeter).\n");
        l.append("\\begin{center}\n").append(diagram(p, opcode, true)).append("\\end{center}\n");
        l.append("\\item \\begin{tabular}[t]{p{3.2cm}p{3.6cm}p{6.6cm}}\n\\hline\n"
                + "\\textbf{Zustand} & \\textbf{Steuersignale} & \\textbf{Wirkung} \\\\\n\\hline\n");
        for (Node nd : p.nodes()) {
            l.append(nd.id()).append(" (").append(nd.title()).append(")").append(nd.isNew() ? "" : ",\\newline vorhanden")
                    .append(" & ").append(nd.signals().stream().map(x -> "\\texttt{" + x + "}").collect(Collectors.joining("\\newline ")))
                    .append(" & ").append(String.join("\\newline ", nd.effects())).append(" \\\\\n\\hline\n");
        }
        l.append("\\end{tabular}\\\\[3pt]\n");
        l.append("Nicht genannte Signale sind inaktiv (Write-/Update-Signale = 0), Mux-Auswahlen beliebig.\n");
        l.append("\\item ").append(p.cycles()).append(" Takte (S0, S1, ")
                .append(p.nodes().stream().map(Node::id).collect(Collectors.joining(", "))).append(").\n");
        l.append("\\end{enumerate}\n");

        String frage = "neuer Befehl " + ins.mnemonic() + " (opcode " + opcode + "), gegeben: " + given.name();
        String antwort = p.nodes().stream().map(Node::id).collect(Collectors.joining("-")) + ", " + p.cycles() + " Takte";
        return new Part("Neuer Befehl im Mehrtakt-Steuerwerk", a.toString(), l.toString(), frage, antwort);
    }

    /** Wirkung als Registertransfer: rd, Speicher, PC. */
    static String rtl(Goal g) {
        List<String> parts = new ArrayList<>();
        if (g.rd() != null) {
            parts.add("rd $\\leftarrow$ " + Datapath.tex(g.rd()));
        }
        if (g.memAddr() != null) {
            parts.add(Datapath.tex("M[" + g.memAddr() + "]") + " $\\leftarrow$ " + Datapath.tex(g.memVal()));
        }
        if (g.pc() != null) {
            parts.add("PC $\\leftarrow$ " + Datapath.tex(g.pc()));
        } else {
            parts.add("PC $\\leftarrow$ PC + 4");
        }
        return "\\texttt{" + String.join(";\\enspace ", parts) + "}";
    }

    // ------------------------------------------------------------------
    // Zeichnung
    // ------------------------------------------------------------------

    private static final double NEW_X = 4.0;

    private static double rowY(int k) {
        return -4.8 - 3.5 * k;
    }

    /** Knoten im Bild. */
    private record DNode(String id, String title, double x, double y, List<String> signals, boolean isNew) {
    }

    static String diagram(Plan p, String opcode, boolean solution) {
        Map<String, DNode> nodes = new LinkedHashMap<>();
        for (State s : p.given().states) {
            nodes.put(s.name(), new DNode(s.name(), s.title, s.x, s.y, s.signals, false));
        }
        // Kanten: (von, nach) -> Befehle, die sie benutzen; Endzustaende -> zurueck zu S0
        Map<String, LinkedHashSet<String>> base = new LinkedHashMap<>();
        Set<String> baseTerminals = new LinkedHashSet<>();
        base.computeIfAbsent("S0>S1", k -> new LinkedHashSet<>());
        if (p.given() == Given.WITH_LW) {
            base.computeIfAbsent("S1>S2", k -> new LinkedHashSet<>()).add("lw");
            base.computeIfAbsent("S2>S3", k -> new LinkedHashSet<>()).add("lw");
            base.computeIfAbsent("S3>S4", k -> new LinkedHashSet<>()).add("lw");
            baseTerminals.add("S4");
        }
        Map<String, LinkedHashSet<String>> all = new LinkedHashMap<>();
        base.forEach((k, v) -> all.put(k, new LinkedHashSet<>(v)));
        Set<String> terminals = new LinkedHashSet<>(baseTerminals);
        if (solution) {
            String prev = "S1";
            for (int k = 0; k < p.nodes().size(); k++) {
                Node nd = p.nodes().get(k);
                if (nd.isNew()) {
                    nodes.put(nd.id(), new DNode(nd.id(), nd.title(), NEW_X, rowY(k), nd.signals(), true));
                }
                all.computeIfAbsent(prev + ">" + nd.id(), x -> new LinkedHashSet<>()).add(p.ins().mnemonic());
                prev = nd.id();
            }
            terminals.add(prev);
        }
        Map<String, String> opc = Map.of("lw", LW_OPCODE, p.ins().mnemonic(), opcode);
        Map<String, Long> outDegree = all.keySet().stream()
                .collect(Collectors.groupingBy(k -> k.substring(0, k.indexOf('>')), Collectors.counting()));
        Map<String, String> baseLabels = labels(base, Map.of("lw", LW_OPCODE), countOut(base));
        Map<String, String> allLabels = labels(all, opc, outDegree);

        StringBuilder sb = new StringBuilder(MulticycleFsm.TIKZ_OPEN);
        for (DNode d : nodes.values()) {
            sb.append(MulticycleFsm.stateNode(d.id(), d.title(), d.x(), d.y(), d.signals(), d.isNew()));
        }
        sb.append("\\node[font=\\small\\sffamily] (reset) at (-2.2,1.9) {Reset};\n");
        sb.append("\\draw[tr] (reset) -- (S0.north west);\n");
        for (String key : all.keySet()) {
            String from = key.substring(0, key.indexOf('>'));
            String to = key.substring(key.indexOf('>') + 1);
            String label = allLabels.get(key);
            boolean isNew = !base.containsKey(key) || !java.util.Objects.equals(label, baseLabels.get(key));
            sb.append("\\draw[").append(isNew ? "trNeu" : "tr").append("] (").append(from).append(") -- (").append(to).append(")");
            if (label != null) {
                sb.append("\n  node[lbl, ").append(place(key, nodes, base.containsKey(key))).append(isNew ? ", text=fsmNeu" : "").append("] {")
                        .append(label).append("}");
            }
            sb.append(";\n");
        }
        if (!terminals.isEmpty()) {
            double loopY = nodes.values().stream().mapToDouble(DNode::y).min().orElse(0) - 1.9;
            double right = nodes.values().stream().mapToDouble(DNode::x).max().orElse(0) + 2.2;
            double left = terminals.stream().mapToDouble(t -> nodes.get(t).x()).min().orElse(0);
            boolean loopNew = baseTerminals.isEmpty();
            for (String t : terminals) {
                boolean isNew = !baseTerminals.contains(t);
                sb.append(String.format(Locale.ROOT, "\\draw[%s] (%s.south) -- (%.1f,%.1f);%n",
                        isNew ? "fsmNeu, very thick" : "semithick", t, nodes.get(t).x(), loopY));
            }
            sb.append(String.format(Locale.ROOT, "\\draw[%s] (%.1f,%.1f) -- (%.1f,%.1f) -- (%.1f,2.2) -- (0,2.2) -- (S0.north);%n",
                    loopNew ? "trNeu" : "tr", left, loopY, right, loopY, right));
        }
        sb.append("\\end{tikzpicture}\n");
        return sb.toString();
    }

    private static Map<String, Long> countOut(Map<String, LinkedHashSet<String>> edges) {
        return edges.keySet().stream().collect(Collectors.groupingBy(k -> k.substring(0, k.indexOf('>')), Collectors.counting()));
    }

    /** Beschriftung: aus S1 immer, sonst bei Verzweigung; S2 -> S3 wie in der Vorlesung immer. */
    private static Map<String, String> labels(Map<String, LinkedHashSet<String>> edges, Map<String, String> opc,
                                              Map<String, Long> outDegree) {
        Map<String, String> out = new HashMap<>();
        for (var e : edges.entrySet()) {
            String from = e.getKey().substring(0, e.getKey().indexOf('>'));
            if (e.getValue().isEmpty()) {
                continue;
            }
            boolean show = from.equals("S1") || outDegree.getOrDefault(from, 0L) > 1
                    || (e.getKey().equals("S2>S3") && e.getValue().contains("lw"));
            if (!show) {
                continue;
            }
            boolean oneLine = from.equals("S1");
            out.put(e.getKey(), e.getValue().stream()
                    .map(c -> oneLine ? "op = " + opc.get(c) + " (\\texttt{" + c + "})"
                            : "op =\\\\" + opc.get(c) + "\\\\(\\texttt{" + c + "})")
                    .collect(Collectors.joining("\\\\OR\\\\")));
        }
        return out;
    }

    private static String place(String key, Map<String, DNode> nodes, boolean given) {
        if (given && key.equals("S1>S2")) {
            return "pos=0.6, above left=-1pt";
        }
        if (given && key.equals("S2>S3")) {
            return "midway, left";
        }
        DNode f = nodes.get(key.substring(0, key.indexOf('>')));
        DNode t = nodes.get(key.substring(key.indexOf('>') + 1));
        if (Math.abs(f.x() - t.x()) < 0.1) {
            return "midway, right=3pt";
        }
        return "midway, fill=white, inner sep=1pt";
    }

    /** Fuer Tests: alle Befehle, die mit diesem Steuerwerk eine Aufgabe ergeben. */
    static List<Plan> plans(Given g) {
        return CATALOG.stream().map(i -> plan(i, g)).flatMap(Optional::stream)
                .sorted(Comparator.comparing(pl -> pl.ins().mnemonic())).toList();
    }
}
