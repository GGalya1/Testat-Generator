package de.ro.testat.exercise.control;

import java.util.List;

/**
 * Kleines Programmmodell fuer die Kontrollstruktur-Aufgaben.
 * Aus einem {@link Program} werden C-Code, RISC-V-Code und die erwarteten Werte erzeugt.
 * Bewusst ohne Verschachtelung: Rumpf von if/Schleifen besteht nur aus Zuweisungen.
 */
public final class Ast {

    private Ast() {
    }

    /** Vergleichsoperator. GT und LE gibt es in RV32I nicht direkt (Operanden tauschen). */
    public enum RelOp {
        EQ("==", "="), NE("!=", "\\neq"), LT("<", "<"), GE(">=", "\\geq"), GT(">", ">"), LE("<=", "\\leq");

        private final String c;
        private final String math;

        RelOp(String c, String math) {
            this.c = c;
            this.math = math;
        }

        public String c() {
            return c;
        }

        public String math() {
            return math;
        }

        public RelOp negate() {
            return switch (this) {
                case EQ -> NE;
                case NE -> EQ;
                case LT -> GE;
                case GE -> LT;
                case GT -> LE;
                case LE -> GT;
            };
        }

        /** Gleiche Bedeutung bei vertauschten Operanden: a < b  <=>  b > a. */
        public RelOp mirror() {
            return switch (this) {
                case EQ -> EQ;
                case NE -> NE;
                case LT -> GT;
                case GE -> LE;
                case GT -> LT;
                case LE -> GE;
            };
        }

        public boolean test(int a, int b) {
            return switch (this) {
                case EQ -> a == b;
                case NE -> a != b;
                case LT -> a < b;
                case GE -> a >= b;
                case GT -> a > b;
                case LE -> a <= b;
            };
        }
    }

    public enum Op {
        ADD("+"), SUB("-"), MUL("*"), SHL("<<");

        private final String c;

        Op(String c) {
            this.c = c;
        }

        public String c() {
            return c;
        }

        public int apply(int a, int b) {
            return switch (this) {
                case ADD -> a + b;
                case SUB -> a - b;
                case MUL -> a * b;
                case SHL -> a << b;
            };
        }
    }

    public sealed interface Operand permits Var, Const {
    }

    public sealed interface Expr permits Var, Const, BinOp {
    }

    public record Var(String name) implements Operand, Expr {
    }

    public record Const(int value) implements Operand, Expr {
    }

    /** Immer "Variable op Operand", z. B. result = result + 3. */
    public record BinOp(Op op, Var left, Operand right) implements Expr {
    }

    public record Cond(Var left, RelOp op, Operand right) {
        public Cond negate() {
            return new Cond(left, op.negate(), right);
        }
    }

    public sealed interface Stmt permits Assign, IfChain, While, DoWhile, For {
    }

    public record Assign(Var target, Expr value) implements Stmt {
    }

    public record Branch(Cond cond, List<Assign> body) {
    }

    /** if / else if ... / else. orElse leer = kein else-Zweig. */
    public record IfChain(List<Branch> branches, List<Assign> orElse) implements Stmt {
    }

    public record While(Cond cond, List<Assign> body) implements Stmt {
    }

    public record DoWhile(List<Assign> body, Cond cond) implements Stmt {
    }

    public record For(Assign init, Cond cond, Assign step, List<Assign> body) implements Stmt {
    }

    public record Program(List<Var> vars, List<Stmt> stmts) {
    }
}
