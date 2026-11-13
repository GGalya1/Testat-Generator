package de.ro.testat.exercise.control;

import de.ro.testat.exercise.control.Ast.Assign;
import de.ro.testat.exercise.control.Ast.BinOp;
import de.ro.testat.exercise.control.Ast.Branch;
import de.ro.testat.exercise.control.Ast.Cond;
import de.ro.testat.exercise.control.Ast.Const;
import de.ro.testat.exercise.control.Ast.DoWhile;
import de.ro.testat.exercise.control.Ast.For;
import de.ro.testat.exercise.control.Ast.IfChain;
import de.ro.testat.exercise.control.Ast.Op;
import de.ro.testat.exercise.control.Ast.Operand;
import de.ro.testat.exercise.control.Ast.Program;
import de.ro.testat.exercise.control.Ast.RelOp;
import de.ro.testat.exercise.control.Ast.Stmt;
import de.ro.testat.exercise.control.Ast.Var;
import de.ro.testat.exercise.control.Ast.While;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Erzeugt zufaellige Programme einer Form. Jedes Programm beginnt wie im
 * Beispieltestat mit drei Zuweisungen (zwei Konstanten und einer Multiplikation)
 * und wird so lange neu gezogen, bis es sinnvoll ist: endet, Schleifen laufen
 * 2-6 Mal, Werte bleiben ueberschaubar.
 */
public final class ProgramGenerator {

    /** Variablennamen: Eingabe 1, Eingabe 2, Ergebnis. */
    private static final String[][] NAMES = {
            {"a", "b", "result"}, {"x", "y", "z"}, {"n", "m", "sum"}, {"p", "q", "r"}, {"i", "k", "acc"}};

    private static final int MIN_ITER = 2;
    private static final int MAX_ITER = 6;
    private static final int MAX_ABS = 5000;
    private static final int ATTEMPTS = 20000;

    private ProgramGenerator() {
    }

    public static Program generate(Shape shape, Random rng) {
        for (int i = 0; i < ATTEMPTS; i++) {
            Program p = attempt(shape, rng);
            if (valid(shape, Interpreter.run(p))) {
                return p;
            }
        }
        throw new IllegalStateException("Kein gueltiges Programm fuer " + shape + " gefunden");
    }

    static boolean valid(Shape shape, Interpreter.Result r) {
        if (!r.terminated()) {
            return false;
        }
        // zwei if hintereinander: mindestens eines muss greifen, sonst ist die Aufgabe witzlos
        if (shape == Shape.IF_IF && r.branchTaken().stream().allMatch(t -> t == -2)) {
            return false;
        }
        for (int n : r.loopIterations()) {
            if (n < MIN_ITER || n > MAX_ITER) {
                return false;
            }
        }
        return r.values().values().stream().allMatch(v -> Math.abs(v) <= MAX_ABS);
    }

    private static Program attempt(Shape shape, Random rng) {
        String[] n = NAMES[rng.nextInt(NAMES.length)];
        Var v0 = new Var(n[0]), v1 = new Var(n[1]), v2 = new Var(n[2]);
        List<Var> vars = List.of(v0, v1, v2);
        int c0 = rnd(rng, 2, 9), c1 = rnd(rng, 2, 9);

        List<Stmt> s = new ArrayList<>();
        s.add(new Assign(v0, new Const(c0)));
        s.add(new Assign(v1, new Const(c1)));
        s.add(new Assign(v2, new BinOp(Op.MUL, v0, v1)));  // M-Extension kommt immer vor
        Map<Var, Integer> val = Map.of(v0, c0, v1, c1, v2, c0 * c1);

        switch (shape) {
            case IF_ELSE -> s.add(new IfChain(
                    List.of(new Branch(cond(vars, val, rng), body(vars, rng, 1 + rng.nextInt(2)))),
                    body(vars, rng, 1)));
            case IF_ELSEIF_ELSE -> {
                // typische Kette: dieselbe Variable gegen verschiedene Schwellen
                Cond first = cond(vars, val, rng);
                Cond second;
                do {
                    second = rng.nextInt(10) < 6 ? sameLeft(first, val, rng) : cond(vars, val, rng);
                } while (second.equals(first) || second.equals(first.negate()));
                List<Assign> b1 = body(vars, rng, 1), b2, b3;
                do {
                    b2 = body(vars, rng, 1);
                    b3 = body(vars, rng, 1);
                } while (b2.equals(b1) || b3.equals(b1) || b3.equals(b2));
                s.add(new IfChain(List.of(new Branch(first, b1), new Branch(second, b2)), b3));
            }
            case IF_IF -> {
                Cond first = cond(vars, val, rng);
                Cond second;
                do {
                    second = cond(vars, val, rng);
                } while (second.equals(first));
                s.add(new IfChain(List.of(new Branch(first, body(vars, rng, 1))), List.of()));
                s.add(new IfChain(List.of(new Branch(second, body(vars, rng, 1))), List.of()));
            }
            case WHILE, DO_WHILE -> s.add(loop(shape, vars, val, rng));
            case FOR -> s.add(forLoop(vars, rng));
        }
        return new Program(vars, List.copyOf(s));
    }

    /** Bedingung fuer if: Variable gegen andere Variable oder gegen eine Konstante nahe ihrem Wert. */
    private static Cond cond(List<Var> vars, Map<Var, Integer> val, Random rng) {
        Var left = vars.get(rng.nextInt(vars.size()));
        RelOp op = RelOp.values()[rng.nextInt(RelOp.values().length)];
        Operand right;
        if (rng.nextBoolean()) {
            right = other(vars, left, rng);
        } else {
            right = new Const(Math.max(0, val.get(left) + rnd(rng, -3, 3)));
        }
        return new Cond(left, op, right);
    }

    /** Gleiche Variable, andere Schwelle bzw. anderer Operator. */
    private static Cond sameLeft(Cond first, Map<Var, Integer> val, Random rng) {
        RelOp op = RelOp.values()[rng.nextInt(RelOp.values().length)];
        Operand right = first.right() instanceof Const k
                ? new Const(Math.max(0, k.value() + (rng.nextBoolean() ? 1 : -1) * rnd(rng, 2, 6)))
                : new Const(Math.max(0, val.get(first.left()) + rnd(rng, -3, 3)));
        return new Cond(first.left(), op, right);
    }

    private static List<Assign> body(List<Var> vars, Random rng, int count) {
        List<Assign> out = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            out.add(randomAssign(vars.get(rng.nextInt(vars.size())), vars, rng));
        }
        return List.copyOf(out);
    }

    /** target = target (+|-|*|<<) etwas. Multiplikation nur mit Variablen (mul). */
    private static Assign randomAssign(Var t, List<Var> vars, Random rng) {
        return switch (rng.nextInt(6)) {
            case 0 -> new Assign(t, new BinOp(Op.ADD, t, new Const(rnd(rng, 1, 9))));
            case 1 -> new Assign(t, new BinOp(Op.SUB, t, new Const(rnd(rng, 1, 9))));
            case 2 -> new Assign(t, new BinOp(Op.ADD, t, other(vars, t, rng)));
            case 3 -> new Assign(t, new BinOp(Op.SUB, t, other(vars, t, rng)));
            case 4 -> new Assign(t, new BinOp(Op.MUL, t, other(vars, t, rng)));
            default -> new Assign(t, new BinOp(Op.SHL, t, new Const(rnd(rng, 1, 2))));
        };
    }

    /** while / do-while: Laufvariable wird schrittweise veraendert, bis die Bedingung kippt. */
    private static Stmt loop(Shape shape, List<Var> vars, Map<Var, Integer> val, Random rng) {
        Var l = rng.nextInt(10) < 7 ? vars.get(2) : vars.get(0);
        List<Var> others = vars.stream().filter(v -> !v.equals(l)).toList();
        boolean up = rng.nextBoolean();
        int s = rnd(rng, 1, 5);
        boolean shift = up && val.get(l) > 0 && rng.nextInt(5) == 0;
        Assign step = shift ? new Assign(l, new BinOp(Op.SHL, l, new Const(1)))
                : new Assign(l, new BinOp(up ? Op.ADD : Op.SUB, l, new Const(s)));

        RelOp op = up ? pick(rng, RelOp.LT, RelOp.LE, RelOp.NE) : pick(rng, RelOp.GT, RelOp.GE, RelOp.NE);
        Operand bound;
        Var boundVar = null;
        if (rng.nextInt(10) < 3) {
            boundVar = others.get(rng.nextInt(others.size()));
            bound = boundVar;
        } else {
            int k = rnd(rng, MIN_ITER, MAX_ITER);
            int target = val.get(l);
            for (int i = 0; i < k; i++) {
                target = shift ? target << 1 : (up ? target + s : target - s);
            }
            int slack = op == RelOp.NE || shift ? 0 : rng.nextInt(s);
            bound = new Const(up ? target - slack : target + slack);
        }

        List<Assign> body = new ArrayList<>();
        body.add(step);
        if (rng.nextBoolean()) {
            Var bv = boundVar;
            List<Var> cand = others.stream().filter(v -> !v.equals(bv)).toList();
            Var x = cand.get(rng.nextInt(cand.size()));
            Assign extra = switch (rng.nextInt(3)) {
                case 0 -> new Assign(x, new BinOp(Op.ADD, x, new Const(rnd(rng, 1, 9))));
                case 1 -> new Assign(x, new BinOp(Op.ADD, x, l));
                default -> new Assign(x, new BinOp(Op.SUB, x, new Const(rnd(rng, 1, 9))));
            };
            body.add(rng.nextBoolean() ? 0 : 1, extra);
        }
        Cond c = new Cond(l, op, bound);
        return shape == Shape.WHILE ? new While(c, List.copyOf(body)) : new DoWhile(List.copyOf(body), c);
    }

    /** for (v0 = start; v0 op bound; v0 += step) { v2 = ... } */
    private static Stmt forLoop(List<Var> vars, Random rng) {
        Var i = vars.get(0), b = vars.get(1), acc = vars.get(2);
        int start = rnd(rng, 0, 1);
        int step = rng.nextInt(5) == 0 ? 2 : 1;
        Operand bound = rng.nextInt(10) < 6 ? b : new Const(start + step * rnd(rng, MIN_ITER, MAX_ITER));
        RelOp op = pick(rng, RelOp.LT, RelOp.LE, RelOp.NE);
        Assign body = switch (rng.nextInt(5)) {
            case 0 -> new Assign(acc, new BinOp(Op.ADD, acc, new Const(rnd(rng, 1, 9))));
            case 1 -> new Assign(acc, new BinOp(Op.ADD, acc, i));
            case 2 -> new Assign(acc, new BinOp(Op.SUB, acc, i));
            case 3 -> new Assign(acc, new BinOp(Op.SHL, acc, new Const(1)));
            default -> new Assign(acc, new BinOp(Op.ADD, acc, b));
        };
        return new For(new Assign(i, new Const(start)), new Cond(i, op, bound),
                new Assign(i, new BinOp(Op.ADD, i, new Const(step))), List.of(body));
    }

    private static Var other(List<Var> vars, Var not, Random rng) {
        List<Var> c = vars.stream().filter(v -> !v.equals(not)).toList();
        return c.get(rng.nextInt(c.size()));
    }

    @SafeVarargs
    private static <T> T pick(Random rng, T... options) {
        return options[rng.nextInt(options.length)];
    }

    private static int rnd(Random rng, int lo, int hi) {
        return lo + rng.nextInt(hi - lo + 1);
    }
}
