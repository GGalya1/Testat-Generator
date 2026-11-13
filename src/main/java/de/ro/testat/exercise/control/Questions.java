package de.ro.testat.exercise.control;

import de.ro.testat.exercise.control.Ast.Assign;
import de.ro.testat.exercise.control.Ast.Branch;
import de.ro.testat.exercise.control.Ast.Cond;
import de.ro.testat.exercise.control.Ast.Const;
import de.ro.testat.exercise.control.Ast.DoWhile;
import de.ro.testat.exercise.control.Ast.For;
import de.ro.testat.exercise.control.Ast.IfChain;
import de.ro.testat.exercise.control.Ast.Program;
import de.ro.testat.exercise.control.Ast.RelOp;
import de.ro.testat.exercise.control.Ast.Stmt;
import de.ro.testat.exercise.control.Ast.Var;
import de.ro.testat.exercise.control.Ast.While;
import de.ro.testat.exercise.control.RiscvRenderer.Asm;
import de.ro.testat.exercise.control.RiscvRenderer.CondSite;
import de.ro.testat.exercise.control.RiscvRenderer.LoopScheme;
import de.ro.testat.exercise.control.RiscvRenderer.LoopSite;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * Zusatzfragen zu den Kontrollstruktur-Aufgaben. Pro Aufgabe werden zufaellig
 * 1-2 passende Fragen gezogen; die Antworten werden aus dem Programm berechnet.
 *
 * Neue Frage: Konstante in {@link Q} mit applies() und make() ergaenzen.
 */
public final class Questions {

    public enum Mode {
        /** C gegeben, Assembler gesucht. Bezug: Code der Studierenden bzw. Musterloesung. */
        C2ASM,
        /** Assembler gegeben (mit Zeilennummern), C gesucht. */
        ASM2C
    }

    /** Frage und Antwort als LaTeX. */
    public record QA(String id, String frage, String antwort) {
    }

    /** Alles, was eine Frage ueber die Aufgabe wissen muss. */
    public record Ctx(Mode mode, Shape shape, Program program, Interpreter.Result result, Asm asm,
                     Map<String, String> regs, String scratch) {
        Var v(int i) {
            return program.vars().get(i);
        }

        String reg(Var v) {
            return regs.get(v.name());
        }

        int init(Var v) {
            for (Stmt s : program.stmts()) {
                if (s instanceof Assign a && a.target().equals(v) && a.value() instanceof Const k) {
                    return k.value();
                }
            }
            throw new IllegalStateException();
        }

        boolean asm2c() {
            return mode == Mode.ASM2C;
        }

        /** " (Zeile n)" nur, wenn der Code mit Zeilennummern auf dem Blatt steht. */
        String zeile(int line) {
            return asm2c() && line > 0 ? " (Zeile " + line + ")" : "";
        }
    }

    private Questions() {
    }

    /** Zieht bis zu n passende Fragen, ohne Wiederholung. */
    public static List<QA> pick(Ctx c, Random rng, int n) {
        List<Q> ok = new ArrayList<>(Arrays.stream(Q.values()).filter(q -> q.applies(c)).toList());
        Collections.shuffle(ok, rng);
        List<QA> out = new ArrayList<>();
        for (Q q : ok.subList(0, Math.min(n, ok.size()))) {
            out.add(q.make(c, rng));
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Fragen
    // ------------------------------------------------------------------

    enum Q {
        /** Bedingung tauschen: welcher Sprungbefehl / welche Bedingung ergibt sich? */
        BEDINGUNG {
            boolean applies(Ctx c) {
                return !c.asm().conds().isEmpty();
            }

            QA make(Ctx c, Random rng) {
                CondSite site = c.asm().conds().get(0);
                if (!c.asm2c()) {
                    Cond orig = site.cond();
                    RelOp alt = otherOp(orig.op(), rng);
                    Cond neu = orig.withOp(alt);
                    String used = RiscvRenderer.branchText(site.jumpIfTrue() ? neu : neu.negate(), c.regs(), c.scratch(), site.label());
                    String other = RiscvRenderer.branchText(site.jumpIfTrue() ? neu.negate() : neu, c.regs(), c.scratch(), site.label());
                    String frage = "Angenommen, im C-Code steht statt " + tt(CRenderer.cond(orig)) + " die Bedingung "
                            + tt(CRenderer.cond(neu)) + ". Welchen Sprungbefehl (mit Operanden) verwenden Sie dann?";
                    String antwort = "Die Musterl\\\"osung springt, wenn die Bedingung "
                            + (site.jumpIfTrue() ? "erf\\\"ullt" : "\\emph{nicht} erf\\\"ullt") + " ist: aus "
                            + tt(site.text()) + " wird " + tt(used) + ". Wer bei "
                            + (site.jumpIfTrue() ? "nicht erf\\\"ullter" : "erf\\\"ullter") + " Bedingung springt, braucht "
                            + tt(other) + "." + swapHint(alt);
                    return new QA(name(), frage, antwort);
                }
                // ASM2C: anderer Sprungbefehl -> welche C-Bedingung?
                List<String> ms = new ArrayList<>(List.of("beq", "bne", "blt", "bge"));
                ms.remove(site.mnemonic());
                String m = ms.get(rng.nextInt(ms.size()));
                RelOp instrOp = switch (m) {
                    case "beq" -> RelOp.EQ;
                    case "bne" -> RelOp.NE;
                    case "blt" -> RelOp.LT;
                    default -> RelOp.GE;
                };
                // Befehl testet "op1 instrOp op2"; als Bedingung mit der Variablen links ausdruecken
                Cond emitted = new Cond(site.cond().left(), site.swapped() ? instrOp.mirror() : instrOp, site.cond().right());
                Cond inC = site.jumpIfTrue() ? emitted : emitted.negate();
                String neuText = m + " " + site.op1() + ", " + site.op2() + ", " + site.label();
                String frage = "In Zeile " + site.line() + " steht " + tt(site.text()) + ". Wie lautet die Bedingung im C-Code, "
                        + "wenn dort stattdessen " + tt(neuText) + " steht?";
                String antwort = tt(CRenderer.cond(inC)) + ". "
                        + (site.jumpIfTrue()
                        ? "Der Sprung wird genommen, wenn die Bedingung gilt."
                        : "Der Sprung f\\\"uhrt aus dem Block heraus, wird also genommen, wenn die C-Bedingung "
                        + "\\emph{nicht} gilt -- deshalb ist sie das Gegenteil des Sprungbefehls.")
                        + (site.swapped() ? " Achtung: Die Operanden stehen vertauscht im Befehl." : "");
                return new QA(name(), frage, antwort);
            }
        },

        /** In welche Schleifenart uebersetzt man das am direktesten? */
        SCHLEIFENART {
            boolean applies(Ctx c) {
                return c.asm2c() && c.shape().isLoop();
            }

            QA make(Ctx c, Random rng) {
                LoopSite l = c.asm().loops().get(0);
                String frage = "In welche Schleifenkonstruktion von C l\\\"asst sich der Code am direktesten "
                        + "\\\"ubersetzen? Begr\\\"unden Sie kurz.";
                String antwort;
                if (l.loop() instanceof DoWhile) {
                    antwort = tt("do-while") + ": Der Rumpf wird ohne vorherige Pr\\\"ufung betreten und mindestens "
                            + "einmal ausgef\\\"uhrt; die Bedingung steht am Ende als bedingter R\\\"ucksprung "
                            + tt(l.branch().text()) + " (Zeile " + l.branch().line() + ").";
                } else if (l.loop() instanceof For f) {
                    antwort = tt("for") + ": Vor der Schleife wird der Z\\\"ahler gesetzt (Zeile " + l.initLine()
                            + "), am Ende des Rumpfes erh\\\"oht (Zeile " + l.stepLine() + "), dazu die Bedingung "
                            + "-- das sind genau die drei Teile des for-Kopfs: "
                            + tt("for (" + CRenderer.assign(f.init()) + "; " + CRenderer.cond(f.cond()) + "; ...)")
                            + ". Eine while-Schleife ist ebenfalls korrekt.";
                } else if (l.scheme() == LoopScheme.TOP) {
                    antwort = tt("while") + ": Die Bedingung wird vor jedem Durchlauf gepr\\\"uft (Sprung aus der "
                            + "Schleife in Zeile " + l.branch().line() + "), am Ende springt " + tt(l.backJump())
                            + " zur\\\"uck zur Pr\\\"ufung.";
                } else {
                    antwort = tt("while") + ": Die Pr\\\"ufung steht zwar am Ende, aber " + tt(l.entryJump())
                            + " (Zeile " + l.entryJumpLine() + ") springt vor dem ersten Durchlauf direkt zur "
                            + "Pr\\\"ufung -- der Rumpf wird also evtl. gar nicht ausgef\\\"uhrt. (Kein do-while!)";
                }
                return new QA(name(), frage, antwort);
            }
        },

        /** Schleife umbauen (while <-> do-while). */
        UMSCHREIBEN {
            boolean applies(Ctx c) {
                return c.shape().isLoop();
            }

            QA make(Ctx c, Random rng) {
                LoopSite l = c.asm().loops().get(0);
                Stmt loop = l.loop();
                boolean toDo = !(loop instanceof DoWhile);
                String wer = c.asm2c() ? "der Code" : "Ihr Assemblercode";
                String frage = "Wie m\\\"usste " + wer + " ge\\\"andert werden, damit "
                        + (toDo ? "der Schleifenrumpf mindestens einmal ausgef\\\"uhrt wird, bevor die Bedingung gepr\\\"uft wird"
                        : "die Bedingung schon vor dem ersten Durchlauf gepr\\\"uft wird")
                        + "? \\\"Andert sich das Ergebnis f\\\"ur die gegebenen Startwerte?";
                String bezug = c.asm2c() ? "" : "In der Musterl\\\"osung: ";
                String wie;
                if (!toDo) {
                    String test = RiscvRenderer.branchText(l.branch().cond().negate(), c.regs(), c.scratch(), "Lende");
                    wie = bezug + "Vor dem Rumpf pr\\\"ufen, z.\\,B. als erste Zeile " + tt(test)
                            + " mit einer neuen Marke " + tt("Lende") + " hinter der Schleife (oder mit "
                            + tt("j") + " direkt zur Pr\\\"ufung springen).";
                } else if (l.scheme() == LoopScheme.TOP) {
                    String back = RiscvRenderer.branchText(l.branch().cond(), c.regs(), c.scratch(), l.bodyLabel());
                    wie = bezug + "Den bedingten Sprung am Schleifenanfang " + tt(l.branch().text()) + c.zeile(l.branch().line())
                            + " entfernen und den R\\\"ucksprung " + tt(l.backJump()) + c.zeile(l.backJumpLine())
                            + " durch " + tt(back) + " ersetzen.";
                } else {
                    wie = bezug + "Nur den Sprung " + tt(l.entryJump()) + c.zeile(l.entryJumpLine())
                            + " vor der Schleife entfernen -- dann wird der Rumpf vor der ersten Pr\\\"ufung ausgef\\\"uhrt.";
                }
                Program alt = c.program().replace(loop, toDo ? asDoWhile(loop) : asWhile((DoWhile) loop));
                Interpreter.Result r = Interpreter.run(alt);
                String folge;
                if (!r.terminated()) {
                    folge = "Ja: Es entsteht eine Endlosschleife.";
                } else if (r.values().equals(c.result().values())) {
                    folge = "Nein, f\\\"ur diese Startwerte nicht: Die Bedingung ist vor dem ersten Durchlauf "
                            + (toDo ? "ohnehin erf\\\"ullt." : "erf\\\"ullt, der Rumpf l\\\"auft also in beiden F\\\"allen gleich oft.");
                } else {
                    folge = "Ja: Danach gilt " + endwerte(c, r) + " (statt " + endwerte(c, c.result()) + ").";
                }
                return new QA(name(), frage, wie + " " + folge);
            }
        },

        /** mul durch Shifts ersetzen? */
        SHIFT {
            boolean applies(Ctx c) {
                return true;
            }

            QA make(Ctx c, Random rng) {
                Var v0 = c.v(0), v1 = c.v(1), v2 = c.v(2);
                int c0 = c.init(v0), c1 = c.init(v1);
                int mulLine = c.asm().lineStartingWith("mul ");
                String frage = c.asm2c()
                        ? "Kann " + tt(c.asm().line(mulLine)) + " (Zeile " + mulLine + ") hier durch andere Befehle "
                        + "ersetzt werden? Wenn ja, wie?"
                        : "L\\\"asst sich " + tt(v2.name() + " = " + v0.name() + " * " + v1.name())
                        + " in diesem Programm auch ohne " + tt("mul") + " umsetzen? Wenn ja, wie?";
                String rd = c.reg(v2);
                String antwort;
                if (Integer.bitCount(c1) == 1) {
                    int k = Integer.numberOfTrailingZeros(c1);
                    antwort = "Ja. " + tt(v1.name()) + " hat an dieser Stelle immer den Wert $" + c1 + " = 2^{" + k
                            + "}$, und Linksschieben um $k$ Stellen multipliziert mit $2^k$: " + tt("slli " + rd + ", " + c.reg(v0) + ", " + k) + ".";
                } else if (Integer.bitCount(c0) == 1) {
                    int k = Integer.numberOfTrailingZeros(c0);
                    antwort = "Ja. " + tt(v0.name()) + " hat an dieser Stelle immer den Wert $" + c0 + " = 2^{" + k
                            + "}$, also " + tt("slli " + rd + ", " + c.reg(v1) + ", " + k)
                            + " (die Multiplikation ist kommutativ).";
                } else {
                    antwort = "Ja, weil " + tt(v1.name()) + " hier immer " + c1 + " ist: $" + c1 + " = "
                            + powers(c1) + "$, also mit Shifts und Additionen: " + shiftAdd(c, c1)
                            + ". Daf\\\"ur braucht man ein Hilfsregister, das gesichert werden muss. "
                            + "W\\\"are " + tt(v1.name()) + " nicht konstant, br\\\"auchte man " + tt("mul")
                            + " (oder eine Schleife).";
                }
                return new QA(name(), frage, antwort);
            }
        },

        /** Endwerte angeben. */
        WERTE {
            boolean applies(Ctx c) {
                return true;
            }

            QA make(Ctx c, Random rng) {
                String frage = c.asm2c()
                        ? "Welche Werte stehen nach der Ausf\\\"uhrung in " + regList(c) + "?"
                        : "Welche Werte haben " + varList(c) + " am Ende des Programms?";
                return new QA(name(), frage, endwerte(c, c.result()) + ".");
            }
        },

        /** Anzahl Schleifendurchlaeufe. */
        DURCHLAEUFE {
            boolean applies(Ctx c) {
                return c.shape().isLoop();
            }

            QA make(Ctx c, Random rng) {
                int n = c.result().loopIterations().get(0);
                return new QA(name(), "Wie oft wird der Schleifenrumpf ausgef\\\"uhrt?", n + "-mal.");
            }
        },

        /** Anderer Startwert: was passiert? */
        WAS_WAERE {
            boolean applies(Ctx c) {
                return true;
            }

            QA make(Ctx c, Random rng) {
                Var v = c.v(rng.nextInt(2));
                int orig = c.init(v);
                int alt;
                do {
                    alt = 2 + rng.nextInt(8);
                } while (alt == orig);
                Interpreter.Result r = Interpreter.run(c.program().withInit(v, alt));
                String frage;
                if (c.asm2c()) {
                    int line = c.asm().lineStartingWith("li " + c.reg(v) + ", " + orig);
                    frage = "Was \\\"andert sich, wenn in Zeile " + line + " " + tt("li " + c.reg(v) + ", " + alt)
                            + " statt " + tt("li " + c.reg(v) + ", " + orig) + " steht?";
                } else {
                    frage = "Was \\\"andert sich, wenn " + tt(v.name()) + " zu Beginn den Wert " + alt + " statt "
                            + orig + " hat?";
                }
                String antwort;
                if (!r.terminated()) {
                    antwort = "Das Programm terminiert nicht: Die Schleifenbedingung "
                            + tt(CRenderer.cond(loopCond(c.program()))) + " wird nie falsch (Endlosschleife).";
                } else {
                    antwort = "Am Ende gilt " + endwerte(c, r) + ".";
                    if (c.shape().isLoop()) {
                        antwort += " Der Rumpf l\\\"auft " + r.loopIterations().get(0) + "-mal.";
                    } else {
                        antwort += " Ablauf: " + String.join("; ", r.ifTrace()) + ".";
                    }
                }
                return new QA(name(), frage, antwort);
            }
        },

        /** Pseudobefehle aufloesen. */
        PSEUDO {
            boolean applies(Ctx c) {
                return c.asm().lineStartingWith("li ") > 0;
            }

            QA make(Ctx c, Random rng) {
                String li = c.asm().line(c.asm().lineStartingWith("li "));
                int jl = c.asm().lineStartingWith("j ");
                String j = jl > 0 ? c.asm().line(jl) : null;
                String frage = c.asm2c()
                        ? "Der Code verwendet Pseudobefehle, z.\\,B. " + tt(li) + (j != null ? " und " + tt(j) : "")
                        + ". Durch welche echten RV32I-Befehle werden sie ersetzt?"
                        : "Welche echten RV32I-Befehle stehen hinter den Pseudobefehlen " + tt("li")
                        + (j != null ? " und " + tt("j") : "") + "? Geben Sie sie am Beispiel " + tt(li)
                        + (j != null ? " bzw. " + tt(j) : "") + " an.";
                String[] p = li.split("\\s+|,\\s*");
                String antwort = tt(li) + " $\\rightarrow$ " + tt("addi " + p[1] + ", zero, " + p[p.length - 1])
                        + " (f\\\"ur Werte von $-2048$ bis $2047$, sonst " + tt("lui") + " + " + tt("addi") + ")";
                if (j != null) {
                    antwort += "; " + tt(j) + " $\\rightarrow$ " + tt("jal zero, " + j.substring(2).trim());
                }
                return new QA(name(), frage, antwort + ".");
            }
        },

        /** &gt; und &lt;= ohne bgt/ble. */
        OPERANDEN {
            boolean applies(Ctx c) {
                return !c.asm2c() && allConds(c.program()).stream().anyMatch(x -> x.op() == RelOp.GT || x.op() == RelOp.LE);
            }

            QA make(Ctx c, Random rng) {
                Cond k = allConds(c.program()).stream().filter(x -> x.op() == RelOp.GT || x.op() == RelOp.LE)
                        .findFirst().orElseThrow();
                String frage = "RV32I hat keine Befehle " + tt("bgt") + " und " + tt("ble") + ". Wie setzen Sie die "
                        + "Bedingung " + tt(CRenderer.cond(k)) + " trotzdem um?";
                String antwort = "Operanden vertauschen ($a > b \\Leftrightarrow b < a$, $a \\leq b \\Leftrightarrow b \\geq a$). "
                        + "Springen, wenn die Bedingung gilt: " + tt(RiscvRenderer.branchText(k, c.regs(), c.scratch(), "L"))
                        + "; springen, wenn sie nicht gilt: " + tt(RiscvRenderer.branchText(k.negate(), c.regs(), c.scratch(), "L"))
                        + ". (Der Assembler kennt " + tt("bgt") + "/" + tt("ble") + " nur als Pseudobefehle, die genau so \\\"ubersetzt werden.)";
                return new QA(name(), frage, antwort);
            }
        },

        /** Hilfsregister sichern. */
        SICHERN {
            boolean applies(Ctx c) {
                return !c.asm2c() && c.asm().scratchSaved();
            }

            QA make(Ctx c, Random rng) {
                String s = c.scratch();
                String frage = "F\\\"ur den Vergleich mit einer Konstanten wird ein zus\\\"atzliches Register gebraucht. "
                        + "Wie stellen Sie sicher, dass dessen urspr\\\"unglicher Wert erhalten bleibt?";
                String antwort = "Auf dem Stack sichern: am Anfang " + tt("addi sp, sp, -4") + " und " + tt("sw " + s + ", 0(sp)")
                        + ", am Ende " + tt("lw " + s + ", 0(sp)") + " und " + tt("addi sp, sp, 4")
                        + ". Wichtig: Der Stackpointer muss am Ende wieder denselben Wert haben.";
                return new QA(name(), frage, antwort);
            }
        },

        /** Welcher Zweig wird ausgefuehrt? */
        ZWEIG {
            boolean applies(Ctx c) {
                return !c.shape().isLoop();
            }

            QA make(Ctx c, Random rng) {
                String frage = c.shape() == Shape.IF_IF
                        ? "Welche der beiden if-Bl\\\"ocke werden bei den gegebenen Startwerten ausgef\\\"uhrt? Begr\\\"unden Sie."
                        : "Welcher Zweig wird bei den gegebenen Startwerten ausgef\\\"uhrt? Begr\\\"unden Sie.";
                List<String> t = c.result().ifTrace();
                String antwort = t.size() == 1 ? t.get(0) + "."
                        : "1.~if: " + t.get(0) + "; 2.~if: " + t.get(1) + " (mit den Werten nach dem ersten if).";
                return new QA(name(), frage, antwort);
            }
        };

        abstract boolean applies(Ctx c);

        abstract QA make(Ctx c, Random rng);
    }

    // ------------------------------------------------------------------
    // Hilfen
    // ------------------------------------------------------------------

    static String tt(String s) {
        return "\\texttt{" + s + "}";
    }

    private static RelOp otherOp(RelOp op, Random rng) {
        List<RelOp> ops = new ArrayList<>(List.of(RelOp.values()));
        ops.remove(op);
        return ops.get(rng.nextInt(ops.size()));
    }

    private static String swapHint(RelOp alt) {
        return alt == RelOp.GT || alt == RelOp.LE || alt.negate() == RelOp.GT || alt.negate() == RelOp.LE
                ? " (F\\\"ur $>$ bzw. $\\leq$ gibt es keinen eigenen Befehl, daher sind die Operanden vertauscht.)" : "";
    }

    private static String regList(Ctx c) {
        return c.program().vars().stream().map(v -> tt(c.reg(v))).collect(Collectors.joining(", "));
    }

    private static String varList(Ctx c) {
        return c.program().vars().stream().map(v -> tt(v.name())).collect(Collectors.joining(", "));
    }

    /** "t0 = 4 (a), t1 = 6 (b), ..." bzw. "a = 4, ..." */
    static String endwerte(Ctx c, Interpreter.Result r) {
        return c.program().vars().stream()
                .map(v -> c.asm2c()
                        ? tt(c.reg(v)) + " $= " + r.value(v) + "$"
                        : tt(v.name()) + " $= " + r.value(v) + "$")
                .collect(Collectors.joining(", "));
    }

    /** 6 -> "4 + 2" */
    private static String powers(int n) {
        List<String> p = new ArrayList<>();
        for (int b = 31; b >= 0; b--) {
            if ((n >> b & 1) == 1) {
                p.add(Integer.toString(1 << b));
            }
        }
        return String.join(" + ", p);
    }

    /** Befehlsfolge fuer x * n mit Shifts und Additionen. */
    private static String shiftAdd(Ctx c, int n) {
        String rd = c.reg(c.v(2)), rs = c.reg(c.v(0)), tmp = c.scratch();
        List<String> ins = new ArrayList<>();
        boolean first = true;
        for (int b = 31; b >= 0; b--) {
            if ((n >> b & 1) == 0) {
                continue;
            }
            if (first) {
                ins.add(b == 0 ? "mv " + rd + ", " + rs : "slli " + rd + ", " + rs + ", " + b);
                first = false;
            } else if (b == 0) {
                ins.add("add " + rd + ", " + rd + ", " + rs);
            } else {
                ins.add("slli " + tmp + ", " + rs + ", " + b);
                ins.add("add " + rd + ", " + rd + ", " + tmp);
            }
        }
        return ins.stream().map(Questions::tt).collect(Collectors.joining(", "));
    }

    private static List<Cond> allConds(Program p) {
        List<Cond> out = new ArrayList<>();
        for (Stmt s : p.stmts()) {
            if (s instanceof IfChain c) {
                for (Branch b : c.branches()) {
                    out.add(b.cond());
                }
            } else if (s instanceof While w) {
                out.add(w.cond());
            } else if (s instanceof DoWhile d) {
                out.add(d.cond());
            } else if (s instanceof For f) {
                out.add(f.cond());
            }
        }
        return out;
    }

    private static Cond loopCond(Program p) {
        Stmt s = p.firstControl();
        if (s instanceof While w) {
            return w.cond();
        }
        if (s instanceof DoWhile d) {
            return d.cond();
        }
        if (s instanceof For f) {
            return f.cond();
        }
        throw new IllegalStateException("keine Schleife");
    }

    /** while/for als do-while (gleicher Rumpf, Pruefung am Ende). */
    private static List<Stmt> asDoWhile(Stmt loop) {
        if (loop instanceof While w) {
            return List.of(new DoWhile(w.body(), w.cond()));
        }
        For f = (For) loop;
        List<Assign> body = new ArrayList<>(f.body());
        body.add(f.step());
        return List.of(f.init(), new DoWhile(List.copyOf(body), f.cond()));
    }

    private static List<Stmt> asWhile(DoWhile d) {
        return List.of(new While(d.cond(), d.body()));
    }
}
