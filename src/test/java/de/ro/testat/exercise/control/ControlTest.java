package de.ro.testat.exercise.control;

import de.ro.testat.exercise.Exercise;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ControlTest {

    private static final int SEEDS = 1500;

    /** Kern der Selbstkontrolle: Assembler (simuliert) == C-Programm (interpretiert), fuer alle Formen. */
    @Test
    void assemblerMatchesInterpreterForAllShapes() {
        List<String> pool = List.of("t0", "a1", "a3");
        for (Shape s : Shape.values()) {
            for (long seed = 0; seed < SEEDS; seed++) {
                Random rng = new Random(seed * 31 + s.ordinal());
                Ast.Program p = ProgramGenerator.generate(s, rng);
                Interpreter.Result r = Interpreter.run(p);
                assertTrue(r.terminated(), "Programm muss enden");
                Map<String, String> regs = new LinkedHashMap<>();
                for (int i = 0; i < 3; i++) {
                    regs.put(p.vars().get(i).name(), pool.get(i));
                }
                for (boolean preserve : new boolean[]{true, false}) {
                    RiscvRenderer.Asm asm = RiscvRenderer.render(p, regs, "t5", preserve, new Random(seed));
                    Map<String, Integer> out = RvSim.run(asm.text(), new HashMap<>(Map.of("t5", 777, "sp", 4096)), 100_000);
                    for (int i = 0; i < 3; i++) {
                        assertEquals(r.value(p.vars().get(i)), out.get(pool.get(i)), s + " seed " + seed + "\n" + asm.text());
                    }
                    assertEquals(4096, out.get("sp"));
                    if (asm.scratchSaved()) {
                        assertEquals(777, out.get("t5"), "Hilfsregister muss wiederhergestellt sein");
                    }
                }
            }
        }
    }

    @Test
    void loopsRunTwoToSixTimesAndMulAlwaysPresent() {
        for (Shape s : Shape.values()) {
            for (long seed = 0; seed < 300; seed++) {
                Ast.Program p = ProgramGenerator.generate(s, new Random(seed));
                Interpreter.Result r = Interpreter.run(p);
                r.loopIterations().forEach(n -> assertTrue(n >= 2 && n <= 6, "Durchlaeufe: " + n));
                assertTrue(CRenderer.program(p).contains(" * "), "M-Extension (mul) muss vorkommen");
                if (s == Shape.IF_IF) {
                    assertTrue(r.branchTaken().stream().anyMatch(t -> t != -2), "mindestens ein if greift");
                }
            }
        }
    }

    @Test
    void labelsAreNumberedInOrder() {
        for (long seed = 0; seed < 200; seed++) {
            Ast.Program p = ProgramGenerator.generate(Shape.IF_ELSEIF_ELSE, new Random(seed));
            Map<String, String> regs = Map.of(p.vars().get(0).name(), "t0", p.vars().get(1).name(), "t1",
                    p.vars().get(2).name(), "a0");
            String text = RiscvRenderer.render(p, regs, "t3", false, new Random(seed)).text();
            int expected = 1;
            for (String line : text.split("\n")) {
                if (line.endsWith(":")) {
                    assertEquals("L" + expected + ":", line);
                    expected++;
                }
            }
            assertTrue(!text.contains("@"));
        }
    }

    @Test
    void exercisesGenerateWithQuestionsAndAreDeterministic() {
        Map<String, Integer> questionUse = new HashMap<>();
        for (Exercise e : List.of(new CToAssembler(), new AssemblerToC())) {
            for (long seed = 0; seed < 400; seed++) {
                Exercise.Part a = e.generate(new Random(seed));
                assertEquals(a, e.generate(new Random(seed)), "gleicher Seed, gleiche Aufgabe");
                assertTrue(a.aufgabe().contains("Zusatzfragen:"));
                assertTrue(a.loesung().contains("Kontrollwerte"));
                for (String id : a.frage().substring(a.frage().indexOf("Fragen: ") + 8).split(",")) {
                    questionUse.merge(e.id() + ":" + id, 1, Integer::sum);
                }
            }
        }
        // jede Frage, die zu einer Richtung passt, kommt auch vor
        Set<String> expected = new HashSet<>();
        for (Questions.Q q : Questions.Q.values()) {
            if (q != Questions.Q.SCHLEIFENART) {
                expected.add("c2asm:" + q.name());
            }
            if (q != Questions.Q.OPERANDEN && q != Questions.Q.SICHERN) {
                expected.add("asm2c:" + q.name());
            }
        }
        expected.removeAll(questionUse.keySet());
        assertTrue(expected.isEmpty(), "nie gezogen: " + expected);
    }

    @Test
    void shapeSelection() {
        assertEquals(3, Shape.resolve("if").size());
        assertEquals(List.of(Shape.WHILE, Shape.FOR), Shape.resolve("while,for"));
        assertEquals(Shape.values().length, Shape.resolve("zufall").size());
        Map<Shape, Integer> seen = new EnumMap<>(Shape.class);
        for (long seed = 0; seed < 200; seed++) {
            String f = new AssemblerToC("schleife").generate(new Random(seed)).frage();
            for (Shape s : Shape.values()) {
                if (f.startsWith(s.id() + ":")) {
                    seen.merge(s, 1, Integer::sum);
                }
            }
        }
        assertEquals(Set.of(Shape.WHILE, Shape.FOR, Shape.DO_WHILE), seen.keySet());
    }
}
