package de.ro.testat.exercise.control;

import de.ro.testat.exercise.control.Ast.Assign;
import de.ro.testat.exercise.control.Ast.BinOp;
import de.ro.testat.exercise.control.Ast.Branch;
import de.ro.testat.exercise.control.Ast.Cond;
import de.ro.testat.exercise.control.Ast.Const;
import de.ro.testat.exercise.control.Ast.DoWhile;
import de.ro.testat.exercise.control.Ast.For;
import de.ro.testat.exercise.control.Ast.IfChain;
import de.ro.testat.exercise.control.Ast.Operand;
import de.ro.testat.exercise.control.Ast.Program;
import de.ro.testat.exercise.control.Ast.RelOp;
import de.ro.testat.exercise.control.Ast.Stmt;
import de.ro.testat.exercise.control.Ast.Var;
import de.ro.testat.exercise.control.Ast.While;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Uebersetzt ein Programm nach RISC-V. Fuer if-else und Schleifen gibt es je zwei
 * Uebersetzungsschemata (zufaellig gewaehlt), damit derselbe C-Code verschieden aussieht.
 * Marken heissen L1, L2, ... in der Reihenfolge ihres Auftretens.
 */
public final class RiscvRenderer {

    public enum LoopScheme {
        /** Bedingung oben pruefen, unbedingter Ruecksprung unten. */
        TOP,
        /** Erst zur Pruefung unten springen, bedingter Ruecksprung nach oben. */
        BOTTOM,
        /** do-while: Rumpf, dann bedingter Ruecksprung. */
        DO
    }

    /**
     * Ein bedingter Sprung im Code.
     *
     * @param cond       Bedingung aus dem C-Code
     * @param jumpIfTrue true: Sprung, wenn cond gilt; false: Sprung, wenn cond NICHT gilt
     * @param mnemonic   beq, bne, blt oder bge
     * @param op1        erster Operand (Registername)
     * @param op2        zweiter Operand
     * @param swapped    true, wenn die Operanden fuer &gt; / &lt;= vertauscht wurden
     */
    public record CondSite(Cond cond, boolean jumpIfTrue, String label, int line, String text,
                           String mnemonic, String op1, String op2, boolean swapped) {
    }

    /** Eine Schleife im Code (Zeilennummern 1-basiert, -1 = nicht vorhanden). */
    public record LoopSite(Stmt loop, LoopScheme scheme, String bodyLabel, CondSite branch,
                           String entryJump, int entryJumpLine, String backJump, int backJumpLine,
                           int initLine, int stepLine) {
    }

    public record Asm(List<String> lines, List<CondSite> conds, List<LoopSite> loops,
                      String scratch, boolean scratchUsed, boolean scratchSaved) {
        public String text() {
            return String.join("\n", lines);
        }

        /** Erste Zeile (1-basiert), deren Befehl mit prefix beginnt; -1 wenn keine. */
        public int lineStartingWith(String prefix) {
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).trim().startsWith(prefix)) {
                    return i + 1;
                }
            }
            return -1;
        }

        public String line(int nr) {
            return lines.get(nr - 1).trim();
        }
    }

    private final Map<String, String> regs;
    private final String scratch;
    private final Random rng;
    private final List<String> out = new ArrayList<>();
    private final List<CondSite> conds = new ArrayList<>();
    private final List<LoopSite> loops = new ArrayList<>();
    private int labelCounter;
    private boolean scratchUsed;

    private RiscvRenderer(Map<String, String> regs, String scratch, Random rng) {
        this.regs = regs;
        this.scratch = scratch;
        this.rng = rng;
    }

    /**
     * @param regs     Variablenname -&gt; Register
     * @param scratch  Hilfsregister fuer Konstanten in Vergleichen
     * @param preserve true: Hilfsregister auf dem Stack sichern und wiederherstellen
     * @param rng      waehlt die Uebersetzungsschemata
     */
    public static Asm render(Program p, Map<String, String> regs, String scratch, boolean preserve, Random rng) {
        RiscvRenderer r = new RiscvRenderer(regs, scratch, rng);
        boolean save = preserve && needsScratch(p);
        if (save) {
            r.emit("addi sp, sp, -4");
            r.emit("sw " + scratch + ", 0(sp)");
        }
        for (Stmt s : p.stmts()) {
            r.stmt(s);
        }
        if (save) {
            r.emit("lw " + scratch + ", 0(sp)");
            r.emit("addi sp, sp, 4");
        }
        return r.finish(save);
    }

    /** Sprungbefehl "springe nach label, wenn e gilt" - ohne etwas auszugeben (fuer Fragen). */
    public static String branchText(Cond e, Map<String, String> regs, String scratch, String label) {
        String l = regs.get(e.left().name());
        String r = operandName(e.right(), regs, scratch);
        return switch (e.op()) {
            case EQ -> "beq " + l + ", " + r + ", " + label;
            case NE -> "bne " + l + ", " + r + ", " + label;
            case LT -> "blt " + l + ", " + r + ", " + label;
            case GE -> "bge " + l + ", " + r + ", " + label;
            case GT -> "blt " + r + ", " + l + ", " + label;
            case LE -> "bge " + r + ", " + l + ", " + label;
        };
    }

    static String mnemonic(RelOp op) {
        return switch (op) {
            case EQ -> "beq";
            case NE -> "bne";
            case LT, GT -> "blt";
            case GE, LE -> "bge";
        };
    }

    private static String operandName(Operand o, Map<String, String> regs, String scratch) {
        if (o instanceof Var v) {
            return regs.get(v.name());
        }
        return ((Const) o).value() == 0 ? "zero" : scratch;
    }

    private static boolean needsScratch(Program p) {
        List<Cond> cs = new ArrayList<>();
        for (Stmt s : p.stmts()) {
            if (s instanceof IfChain c) {
                c.branches().forEach(b -> cs.add(b.cond()));
            } else if (s instanceof While w) {
                cs.add(w.cond());
            } else if (s instanceof DoWhile d) {
                cs.add(d.cond());
            } else if (s instanceof For f) {
                cs.add(f.cond());
            }
        }
        return cs.stream().anyMatch(c -> c.right() instanceof Const k && k.value() != 0);
    }

    // ------------------------------------------------------------------

    private String newLabel() {
        return "@" + (++labelCounter);
    }

    private void label(String l) {
        out.add(l + ":");
    }

    private int emit(String ins) {
        out.add("    " + ins);
        return out.size();
    }

    private String reg(Var v) {
        return regs.get(v.name());
    }

    /** Gibt ggf. "li scratch, k" aus und liefert den Registernamen. */
    private String operandReg(Operand o) {
        if (o instanceof Const k && k.value() != 0) {
            emit("li " + scratch + ", " + k.value());
            scratchUsed = true;
        }
        return operandName(o, regs, scratch);
    }

    private CondSite branch(Cond c, boolean jumpIfTrue, String label) {
        Cond e = jumpIfTrue ? c : c.negate();
        String l = reg(e.left());
        String r = operandReg(e.right());
        boolean swapped = e.op() == RelOp.GT || e.op() == RelOp.LE;
        String a = swapped ? r : l, b = swapped ? l : r;
        String m = mnemonic(e.op());
        String text = m + " " + a + ", " + b + ", " + label;
        int line = emit(text);
        CondSite s = new CondSite(c, jumpIfTrue, label, line, text, m, a, b, swapped);
        conds.add(s);
        return s;
    }

    private int assign(Assign a) {
        String rd = reg(a.target());
        if (a.value() instanceof Const k) {
            return emit("li " + rd + ", " + k.value());
        }
        if (a.value() instanceof Var v) {
            return emit("mv " + rd + ", " + reg(v));
        }
        BinOp b = (BinOp) a.value();
        String rl = reg(b.left());
        if (b.right() instanceof Var v) {
            String rr = reg(v);
            return switch (b.op()) {
                case ADD -> emit("add " + rd + ", " + rl + ", " + rr);
                case SUB -> emit("sub " + rd + ", " + rl + ", " + rr);
                case MUL -> emit("mul " + rd + ", " + rl + ", " + rr);
                case SHL -> throw new IllegalStateException("Shift um Variable nicht vorgesehen");
            };
        }
        int k = ((Const) b.right()).value();
        return switch (b.op()) {
            case ADD -> emit("addi " + rd + ", " + rl + ", " + k);
            case SUB -> emit("addi " + rd + ", " + rl + ", " + (-k));
            case SHL -> emit("slli " + rd + ", " + rl + ", " + k);
            case MUL -> throw new IllegalStateException("Multiplikation mit Konstante nicht vorgesehen");
        };
    }

    private void body(List<Assign> body) {
        body.forEach(this::assign);
    }

    private void stmt(Stmt s) {
        if (s instanceof Assign a) {
            assign(a);
        } else if (s instanceof IfChain c) {
            ifChain(c);
        } else if (s instanceof While w) {
            loop(s, w.cond(), w.body(), null, -1);
        } else if (s instanceof For f) {
            int init = assign(f.init());
            loop(s, f.cond(), f.body(), f.step(), init);
        } else if (s instanceof DoWhile d) {
            String top = newLabel();
            label(top);
            body(d.body());
            CondSite b = branch(d.cond(), true, top);
            loops.add(new LoopSite(s, LoopScheme.DO, top, b, null, -1, null, -1, -1, -1));
        }
    }

    private void ifChain(IfChain c) {
        boolean hasElse = !c.orElse().isEmpty();
        String end = newLabel();
        if (c.branches().size() == 1 && hasElse && rng.nextBoolean()) {
            // Schema B: bei erfuellter Bedingung in den then-Teil springen, else-Teil steht oben
            String then = newLabel();
            branch(c.branches().get(0).cond(), true, then);
            body(c.orElse());
            emit("j " + end);
            label(then);
            body(c.branches().get(0).body());
            label(end);
            return;
        }
        // Schema A: bei nicht erfuellter Bedingung ueber den Zweig springen
        for (int i = 0; i < c.branches().size(); i++) {
            Branch b = c.branches().get(i);
            boolean last = i == c.branches().size() - 1;
            String next = last && !hasElse ? end : newLabel();
            branch(b.cond(), false, next);
            body(b.body());
            if (!(last && !hasElse)) {
                emit("j " + end);
                label(next);
            }
        }
        body(c.orElse());
        label(end);
    }

    private void loop(Stmt s, Cond cond, List<Assign> body, Assign step, int initLine) {
        if (rng.nextBoolean()) {
            String top = newLabel(), exit = newLabel();
            label(top);
            CondSite b = branch(cond, false, exit);
            body(body);
            int stepLine = step == null ? -1 : assign(step);
            int back = emit("j " + top);
            label(exit);
            loops.add(new LoopSite(s, LoopScheme.TOP, top, b, null, -1, "j " + top, back, initLine, stepLine));
        } else {
            String bodyL = newLabel(), test = newLabel();
            int entry = emit("j " + test);
            label(bodyL);
            body(body);
            int stepLine = step == null ? -1 : assign(step);
            label(test);
            CondSite b = branch(cond, true, bodyL);
            loops.add(new LoopSite(s, LoopScheme.BOTTOM, bodyL, b, "j " + test, entry, null, -1, initLine, stepLine));
        }
    }

    /** Ersetzt @n durch L1, L2, ... in der Reihenfolge der Definitionen. */
    private Asm finish(boolean saved) {
        Map<String, String> names = new HashMap<>();
        for (String l : out) {
            if (l.matches("@\\d+:")) {
                names.put(l.substring(0, l.length() - 1), "L" + (names.size() + 1));
            }
        }
        List<String> lines = out.stream().map(l -> rename(l, names)).toList();
        List<CondSite> cs = conds.stream().map(c -> rename(c, names)).toList();
        List<LoopSite> ls = loops.stream().map(l -> new LoopSite(l.loop(), l.scheme(), rename(l.bodyLabel(), names),
                rename(l.branch(), names), rename(l.entryJump(), names), l.entryJumpLine(),
                rename(l.backJump(), names), l.backJumpLine(), l.initLine(), l.stepLine())).toList();
        return new Asm(lines, cs, ls, scratch, scratchUsed, saved);
    }

    private static final Pattern LABEL = Pattern.compile("@\\d+");

    private static String rename(String s, Map<String, String> names) {
        if (s == null) {
            return null;
        }
        Matcher m = LABEL.matcher(s);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(sb, names.getOrDefault(m.group(), m.group()));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static CondSite rename(CondSite c, Map<String, String> names) {
        return new CondSite(c.cond(), c.jumpIfTrue(), rename(c.label(), names), c.line(), rename(c.text(), names),
                c.mnemonic(), c.op1(), c.op2(), c.swapped());
    }
}
