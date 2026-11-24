package de.ro.testat.exercise.control;

import de.ro.testat.testat.JdkRandom;

import de.ro.testat.exercise.Exercise;
import de.ro.testat.exercise.control.Ast.Program;
import de.ro.testat.exercise.control.Ast.Var;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

/** Gemeinsame Logik fuer C -&gt; RISC-V und RISC-V -&gt; C. */
abstract class ControlExercise implements Exercise {

    /** Register fuer Variablen; Hilfsregister kommt aus dem Rest. */
    private static final List<String> VAR_REGS = List.of("t0", "t1", "t2", "a0", "a1", "a2", "a3", "a4", "a5");
    private static final List<String> SCRATCH_REGS = List.of("t3", "t4", "t5", "t6");

    private final String auswahl;
    private final List<Shape> shapes;

    /** @param auswahl Formen: "zufall", "if", "schleife" oder Liste wie "while,for" */
    ControlExercise(String auswahl) {
        this.auswahl = auswahl == null || auswahl.isBlank() ? "zufall" : auswahl.trim();
        this.shapes = Shape.resolve(this.auswahl);
    }

    @Override
    public String describe() {
        return id() + "(" + auswahl + ")";
    }

    /** Alles, was beide Aufgaben brauchen. */
    record Generated(Shape shape, Program program, Interpreter.Result result, Map<String, String> regs,
                     String scratch, RiscvRenderer.Asm asm) {
    }

    Generated build(Random rng, boolean preserve) {
        Shape shape = shapes.get(rng.nextInt(shapes.size()));
        Program p = ProgramGenerator.generate(shape, rng);
        Interpreter.Result res = Interpreter.run(p);

        List<String> pool = new ArrayList<>(VAR_REGS);
        JdkRandom.mischen(pool, rng);
        Map<String, String> regs = new LinkedHashMap<>();
        for (int i = 0; i < p.vars().size(); i++) {
            regs.put(p.vars().get(i).name(), pool.get(i));
        }
        String scratch = SCRATCH_REGS.get(rng.nextInt(SCRATCH_REGS.size()));

        RiscvRenderer.Asm asm = RiscvRenderer.render(p, regs, scratch, preserve, rng);
        verify(p, res, regs, asm);
        return new Generated(shape, p, res, regs, scratch, asm);
    }

    /** Selbstkontrolle: der Assemblercode muss dasselbe berechnen wie das C-Programm. */
    private static void verify(Program p, Interpreter.Result res, Map<String, String> regs, RiscvRenderer.Asm asm) {
        Map<String, Integer> init = new HashMap<>();
        int garbage = 0x5A5A0000;
        for (String r : List.of("t0", "t1", "t2", "t3", "t4", "t5", "t6", "a0", "a1", "a2", "a3", "a4", "a5")) {
            init.put(r, garbage++);
        }
        init.put("sp", 0x7FFFEFF0);
        Map<String, Integer> out = RvSim.run(asm.text(), init, 100_000);
        for (Var v : p.vars()) {
            if (out.get(regs.get(v.name())) != res.value(v)) {
                throw new IllegalStateException("Interner Fehler: Assembler und C-Programm liefern verschiedene Werte\n"
                        + CRenderer.program(p) + "\n---\n" + asm.text());
            }
        }
        if (asm.scratchSaved() && !out.get(asm.scratch()).equals(init.get(asm.scratch()))) {
            throw new IllegalStateException("Interner Fehler: Hilfsregister nicht wiederhergestellt");
        }
        if (!out.get("sp").equals(init.get("sp"))) {
            throw new IllegalStateException("Interner Fehler: sp nicht wiederhergestellt");
        }
    }

    // ------------------------------------------------------------------
    // LaTeX-Bausteine
    // ------------------------------------------------------------------

    static String cListing(Program p) {
        return "\\begin{lstlisting}[language=C]\n" + CRenderer.program(p) + "\n\\end{lstlisting}\n";
    }

    static String asmListing(RiscvRenderer.Asm asm) {
        return "\\begin{lstlisting}[numbers=left, numberstyle=\\tiny]\n" + asm.text() + "\n\\end{lstlisting}\n";
    }

    /** "t0 = 4 (a), t1 = 6 (b), a0 = 30 (result)" */
    static String kontrollwerte(Generated g) {
        return g.program().vars().stream()
                .map(v -> "\\texttt{" + g.regs().get(v.name()) + "} $= " + g.result().value(v) + "$ (\\texttt{"
                        + v.name() + "})")
                .collect(Collectors.joining(", "));
    }

    /** Kurzfassung fuer die Zuordnungsdatei. */
    static String kurzWerte(Generated g) {
        return g.program().vars().stream().map(v -> v.name() + "=" + g.result().value(v))
                .collect(Collectors.joining(", "));
    }

}
