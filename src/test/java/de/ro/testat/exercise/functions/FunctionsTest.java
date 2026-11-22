package de.ro.testat.exercise.functions;

import de.ro.testat.exercise.Exercise;
import de.ro.testat.probelauf.RiscvParser;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FunctionsTest {

    /** Jede Aufgabe: deterministisch, Selbstkontrolle laeuft (wirft sonst), keine Loesung im Aufgabentext. */
    @Test
    void allExercisesGenerateDeterministically() {
        for (Exercise e : List.of(new RecursionToAssembler(), new CallToAssembler(), new StackTrace(), new ArrayToAssembler(), new ShiftArithmetic())) {
            for (long seed = 0; seed < 300; seed++) {
                long gemischt = seed * 0x9E3779B97F4A7C15L + 12345; // aufeinanderfolgende Seeds liefern sonst fast gleiche erste Zufallszahlen
                Exercise.Part a = e.generate(new Random(gemischt));
                assertEquals(a, e.generate(new Random(gemischt)), e.id() + " seed " + seed);
                assertTrue(!a.aufgabe().contains("Kontrollwert"), e.id());
            }
        }
    }

    /** Jede Rekursionsform mit vielen Konstanten: Selbstkontrolle (C-Interpreter vs. Simulator, streng) laeuft. */
    @Test
    void recursionAllForms() {
        for (RecursionForms.Form form : RecursionForms.Form.values()) {
            RecursionToAssembler ex = new RecursionToAssembler(form.name().toLowerCase());
            for (long seed = 0; seed < 40; seed++) {
                Exercise.Part p = ex.generate(new Random(seed));
                assertTrue(p.loesung().contains("Kontrollwerte"), form.name());
            }
        }
    }

    /** Jede Aufruferform, und dabei jede Blattfunktion mindestens einmal. */
    @Test
    void callChainAllForms() {
        Set<CallChain.Blatt> blaetter = new HashSet<>();
        for (CallChain.Rufer r : CallChain.Rufer.values()) {
            CallToAssembler ex = new CallToAssembler(r.name().toLowerCase());
            for (long seed = 0; seed < 40; seed++) {
                ex.generate(new Random(seed));
                blaetter.add(CallChain.zufall(new Random(seed), List.of(r), List.of(CallChain.Blatt.values())).blatt());
            }
        }
        assertEquals(CallChain.Blatt.values().length, blaetter.size());
    }

    /** Strenger Modus: wer sich nach einem Aufruf auf ein t-Register verlaesst, faellt auf. */
    @Test
    void strictModeCatchesCallerSavedMisuse() {
        String prog = "main:\n addi t1, zero, 5\n jal ra, g\n add a0, a0, t1\n addi a7, zero, 10\n ecall\n"
                + "g:\n addi a0, zero, 1\n jalr zero, 0(ra)\n";
        RiscvParser.Programm p = RiscvParser.parse(prog);
        assertEquals(6L, (long) de.ro.testat.probelauf.RiscvMaschine.run(p, 1000, Map.of()).ende()[10]);
        int falsch = 0;
        for (long seed = 0; seed < 10; seed++) {
            if (de.ro.testat.probelauf.RiscvMaschine.runStreng(p, 1000, Map.of(), seed).ende()[10] != 6) {
                falsch++;
            }
        }
        assertEquals(10L, (long) falsch);
    }

    /** Listing-Adressen wie in RARS: jeder Befehl genau 4 Byte, jal-Ruecksprungadressen passen. */
    @Test
    void stackTraceAddressesMatchParser() {
        for (long seed = 0; seed < 200; seed++) {
            RecursiveFunction f = RecursiveFunction.zufall(new Random(seed), true);
            AsmText t = f.asm(3, true);
            RiscvParser.Programm p = t.programm();
            long befehle = t.zeilen().stream().filter(z -> z.befehl() != null).count();
            assertEquals(befehle, (long) p.befehle().size(), "keine Pseudobefehle, die RARS aufteilt");
            assertTrue(t.listing(true, false).contains("00400000"));
            // erwarteter Stack laesst sich bilden und endet beim Basisfall
            List<StackTrace.Eintrag> st = StackTrace.erwartet(f, 3, 0x7FFF_F000, 42, t);
            assertEquals(0, st.size() % 2);
            assertEquals(0x7FFF_F000 - 4, st.get(0).adresse());
        }
    }

    @Test
    void arrayFormsAllWork() {
        for (ArrayToAssembler.Form form : ArrayToAssembler.Form.values()) {
            ArrayToAssembler ex = new ArrayToAssembler(form.name().toLowerCase());
            for (long seed = 0; seed < 150; seed++) {
                Exercise.Part p = ex.generate(new Random(seed)); // Selbstkontrolle Simulator vs. C-Interpreter
                assertTrue(p.frage().startsWith(form.name().toLowerCase()), p.frage());
            }
        }
    }

    @Test
    void shiftSolutionsForAllConstants() {
        for (int c : ShiftArithmetic.ZWEI_BITS) {
            AsmText t = ShiftArithmetic.mal(c);
            for (int n : ShiftArithmetic.TESTWERTE) {
                assertEquals(n * c, t.run(Map.of(AsmText.reg("a0"), n)).ende()[AsmText.reg("a1")], "n*" + c);
            }
            assertTrue(!t.text().contains("mul"));
        }
        for (int c : ShiftArithmetic.DIFFERENZ) {
            AsmText t = ShiftArithmetic.mal(c);
            for (int n : ShiftArithmetic.TESTWERTE) {
                assertEquals(n * c, t.run(Map.of(AsmText.reg("a0"), n)).ende()[AsmText.reg("a1")], "n*" + c);
            }
        }
        for (int k = 1; k <= 4; k++) {
            AsmText t = ShiftArithmetic.durch(k);
            for (int n = -300; n <= 300; n++) {
                assertEquals(n / (1 << k), t.run(Map.of(AsmText.reg("a0"), n)).ende()[AsmText.reg("a1")], "n/" + (1 << k));
            }
        }
    }
}
