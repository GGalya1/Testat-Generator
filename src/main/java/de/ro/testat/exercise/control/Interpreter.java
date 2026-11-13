package de.ro.testat.exercise.control;

import de.ro.testat.exercise.control.Ast.Assign;
import de.ro.testat.exercise.control.Ast.BinOp;
import de.ro.testat.exercise.control.Ast.Branch;
import de.ro.testat.exercise.control.Ast.Cond;
import de.ro.testat.exercise.control.Ast.Const;
import de.ro.testat.exercise.control.Ast.DoWhile;
import de.ro.testat.exercise.control.Ast.Expr;
import de.ro.testat.exercise.control.Ast.For;
import de.ro.testat.exercise.control.Ast.IfChain;
import de.ro.testat.exercise.control.Ast.Operand;
import de.ro.testat.exercise.control.Ast.Program;
import de.ro.testat.exercise.control.Ast.Stmt;
import de.ro.testat.exercise.control.Ast.Var;
import de.ro.testat.exercise.control.Ast.While;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fuehrt ein Programm aus (32-Bit-Arithmetik wie auf RISC-V).
 * Liefert Endwerte, Schleifendurchlaeufe und einen Ablauf der if-Entscheidungen.
 */
public final class Interpreter {

    /** Mehr Durchlaeufe gelten als Endlosschleife. */
    public static final int LIMIT = 200;

    /**
     * @param values         Endwerte je Variablenname
     * @param terminated     false = Endlosschleife (Abbruch nach LIMIT Durchlaeufen)
     * @param loopIterations Durchlaeufe je Schleife, in Programmreihenfolge
     * @param ifTrace        je if-Kette eine LaTeX-Zeile: welche Bedingung wie ausgewertet wurde
     * @param branchTaken    je if-Kette: Index des ausgefuehrten Zweigs, -1 = else, -2 = keiner
     */
    public record Result(Map<String, Integer> values, boolean terminated, List<Integer> loopIterations,
                         List<String> ifTrace, List<Integer> branchTaken) {
        public int value(Var v) {
            return values.get(v.name());
        }
    }

    private static final class Abort extends RuntimeException {
        Abort() {
            super(null, null, false, false);
        }
    }

    private final Map<String, Integer> env = new LinkedHashMap<>();
    private final List<Integer> loops = new ArrayList<>();
    private final List<String> trace = new ArrayList<>();
    private final List<Integer> taken = new ArrayList<>();

    private Interpreter() {
    }

    public static Result run(Program p) {
        Interpreter it = new Interpreter();
        for (Var v : p.vars()) {
            it.env.put(v.name(), 0);
        }
        boolean ok = true;
        try {
            for (Stmt s : p.stmts()) {
                it.exec(s);
            }
        } catch (Abort a) {
            ok = false;
        }
        return new Result(new LinkedHashMap<>(it.env), ok, List.copyOf(it.loops), List.copyOf(it.trace),
                List.copyOf(it.taken));
    }

    private void exec(Stmt s) {
        if (s instanceof Assign a) {
            env.put(a.target().name(), eval(a.value()));
        } else if (s instanceof IfChain c) {
            List<String> parts = new ArrayList<>();
            String chosen = c.orElse().isEmpty() ? "kein Zweig wird ausgef\\\"uhrt" : "der else-Zweig wird ausgef\\\"uhrt";
            boolean done = false;
            int idx = c.orElse().isEmpty() ? -2 : -1;
            for (int i = 0; i < c.branches().size() && !done; i++) {
                Branch b = c.branches().get(i);
                int l = value(b.cond().left());
                int r = value(b.cond().right());
                boolean res = b.cond().op().test(l, r);
                parts.add("\\texttt{" + CRenderer.cond(b.cond()) + "}: $" + l + " " + b.cond().op().math() + " " + r
                        + "$ ist " + (res ? "wahr" : "falsch"));
                if (res) {
                    chosen = (i == 0 ? "der if-Zweig" : "der else-if-Zweig") + " wird ausgef\\\"uhrt";
                    execAll(b.body());
                    done = true;
                    idx = i;
                }
            }
            if (!done) {
                execAll(c.orElse());
            }
            trace.add(String.join(", ", parts) + " $\\Rightarrow$ " + chosen);
            taken.add(idx);
        } else if (s instanceof While w) {
            int n = 0;
            while (test(w.cond())) {
                execAll(w.body());
                n = count(n);
            }
            loops.add(n);
        } else if (s instanceof DoWhile d) {
            int n = 0;
            do {
                execAll(d.body());
                n = count(n);
            } while (test(d.cond()));
            loops.add(n);
        } else if (s instanceof For f) {
            exec(f.init());
            int n = 0;
            while (test(f.cond())) {
                execAll(f.body());
                exec(f.step());
                n = count(n);
            }
            loops.add(n);
        }
    }

    private static int count(int n) {
        if (n + 1 > LIMIT) {
            throw new Abort();
        }
        return n + 1;
    }

    private void execAll(List<Assign> body) {
        for (Assign a : body) {
            exec(a);
        }
    }

    private boolean test(Cond c) {
        return c.op().test(value(c.left()), value(c.right()));
    }

    private int value(Operand o) {
        return o instanceof Var v ? env.get(v.name()) : ((Const) o).value();
    }

    private int eval(Expr e) {
        if (e instanceof Var v) {
            return env.get(v.name());
        }
        if (e instanceof Const c) {
            return c.value();
        }
        BinOp b = (BinOp) e;
        return b.op().apply(env.get(b.left().name()), value(b.right()));
    }
}
