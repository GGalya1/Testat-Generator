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
import de.ro.testat.exercise.control.Ast.Op;
import de.ro.testat.exercise.control.Ast.Operand;
import de.ro.testat.exercise.control.Ast.Program;
import de.ro.testat.exercise.control.Ast.Stmt;
import de.ro.testat.exercise.control.Ast.Var;
import de.ro.testat.exercise.control.Ast.While;

import java.util.ArrayList;
import java.util.List;

/** Programm als C-Code (fuer lstlisting). */
public final class CRenderer {

    private static final String IND = "    ";

    private CRenderer() {
    }

    public static String program(Program p) {
        List<String> out = new ArrayList<>();
        Stmt prev = null;
        for (Stmt s : p.stmts()) {
            if (prev != null && !(prev instanceof Assign && s instanceof Assign)) {
                out.add("");
            }
            stmt(s, "", out);
            prev = s;
        }
        return String.join("\n", out);
    }

    /** Einzeilige Fassung fuer die Zuordnungsdatei. */
    public static String oneLine(Program p) {
        return program(p).replaceAll("\\s+", " ").trim();
    }

    public static String cond(Cond c) {
        return c.left().name() + " " + c.op().c() + " " + operand(c.right());
    }

    public static String assign(Assign a) {
        return a.target().name() + " = " + expr(a.value());
    }

    static String operand(Operand o) {
        return o instanceof Var v ? v.name() : Integer.toString(((Const) o).value());
    }

    static String expr(Expr e) {
        if (e instanceof Var v) {
            return v.name();
        }
        if (e instanceof Const c) {
            return Integer.toString(c.value());
        }
        BinOp b = (BinOp) e;
        return b.left().name() + " " + b.op().c() + " " + operand(b.right());
    }

    private static String forStep(Assign s) {
        if (s.value() instanceof BinOp b && b.op() == Op.ADD && b.left().equals(s.target())
                && b.right() instanceof Const c && c.value() == 1) {
            return s.target().name() + "++";
        }
        return assign(s);
    }

    private static void body(List<Assign> body, String ind, List<String> out) {
        for (Assign a : body) {
            out.add(ind + assign(a) + ";");
        }
    }

    private static void stmt(Stmt s, String ind, List<String> out) {
        if (s instanceof Assign a) {
            out.add(ind + assign(a) + ";");
        } else if (s instanceof IfChain c) {
            for (int i = 0; i < c.branches().size(); i++) {
                Branch b = c.branches().get(i);
                out.add(ind + (i == 0 ? "" : "} else ") + "if (" + cond(b.cond()) + ") {");
                body(b.body(), ind + IND, out);
            }
            if (!c.orElse().isEmpty()) {
                out.add(ind + "} else {");
                body(c.orElse(), ind + IND, out);
            }
            out.add(ind + "}");
        } else if (s instanceof While w) {
            out.add(ind + "while (" + cond(w.cond()) + ") {");
            body(w.body(), ind + IND, out);
            out.add(ind + "}");
        } else if (s instanceof DoWhile d) {
            out.add(ind + "do {");
            body(d.body(), ind + IND, out);
            out.add(ind + "} while (" + cond(d.cond()) + ");");
        } else if (s instanceof For f) {
            out.add(ind + "for (" + assign(f.init()) + "; " + cond(f.cond()) + "; " + forStep(f.step()) + ") {");
            body(f.body(), ind + IND, out);
            out.add(ind + "}");
        }
    }
}
