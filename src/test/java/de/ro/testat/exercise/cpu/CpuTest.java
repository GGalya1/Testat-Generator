package de.ro.testat.exercise.cpu;

import de.ro.testat.exercise.Exercise;
import de.ro.testat.exercise.cpu.PipelineHazards.Instr;
import de.ro.testat.exercise.cpu.PipelineHazards.Op;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CpuTest {

    // ------------------------------------------------------------------ Eintakt

    @Test
    void singleCycleCriticalPathIsAlwaysLoad() {
        SingleCycleTiming ex = new SingleCycleTiming();
        for (long seed = 0; seed < 500; seed++) {
            Exercise.Part a = ex.generate(new Random(seed));
            assertEquals(a, ex.generate(new Random(seed)), "deterministisch");
            assertTrue(a.antwort().contains("(lw)"), a.antwort());
            // Gesamtzeit = Takte * Tc
            String s = a.antwort();
            int n = Integer.parseInt(s.substring(0, s.indexOf(' ')));
            int tc = Integer.parseInt(s.substring(s.indexOf("Tc=") + 3, s.indexOf("ps")));
            int total = Integer.parseInt(s.substring(s.indexOf("Gesamt=") + 7, s.lastIndexOf("ps")));
            assertEquals(n * tc, total);
            assertTrue(n >= 3 && n <= 5);
        }
    }

    @Test
    void singleCyclePathSums() {
        Map<SingleCycleTiming.Comp, Integer> d = new EnumMap<>(SingleCycleTiming.Comp.class);
        for (SingleCycleTiming.Comp c : SingleCycleTiming.Comp.values()) {
            d.put(c, 10);
        }
        d.put(SingleCycleTiming.Comp.MEM, 200);
        assertEquals(10 * 5 + 200, SingleCycleTiming.Kind.ALU_OP.delay(d));
        assertEquals(10 * 5 + 400, SingleCycleTiming.Kind.LW.delay(d));
        assertEquals(10 * 7 + 200, SingleCycleTiming.Kind.BEQ.delay(d));
    }

    // ------------------------------------------------------------------ Mehrtakt

    @Test
    void multicycleCycleCountsAsInLecture() {
        Map<MulticycleFsm.Kind, Integer> expected = Map.of(
                MulticycleFsm.Kind.LW, 5, MulticycleFsm.Kind.SW, 4, MulticycleFsm.Kind.R, 4,
                MulticycleFsm.Kind.I, 4, MulticycleFsm.Kind.JAL, 4, MulticycleFsm.Kind.BEQ, 3);
        for (MulticycleFsm.Kind k : MulticycleFsm.Kind.values()) {
            assertEquals(expected.get(k), k.path.size(), k.name());
            assertEquals(MulticycleFsm.State.S0, k.path.get(0));
            assertEquals(MulticycleFsm.State.S1, k.path.get(1));
        }
        // jedes Signal der FSM hat eine Erklaerung
        for (MulticycleFsm.State s : MulticycleFsm.State.values()) {
            for (String sig : s.signals) {
                assertTrue(MulticycleFsm.MEANING.containsKey(sig), sig);
            }
        }
    }

    @Test
    void multicycleCoversAllTargetsAndSelection() {
        MulticycleFsm ex = new MulticycleFsm();
        Set<String> seen = new HashSet<>();
        for (long seed = 0; seed < 600; seed++) {
            Exercise.Part a = ex.generate(new Random(seed));
            assertEquals(a, ex.generate(new Random(seed)));
            seen.add(a.frage().substring(a.frage().lastIndexOf(' ') + 1));
            assertTrue(a.loesung().contains("fsmNeu, very thick"), "neue Teile markiert");
            assertTrue(!a.aufgabe().contains("fsmNeu, very thick") && !a.aufgabe().contains("[st,neu]"),
                    "Aufgabe verraet nichts");
        }
        assertEquals(MulticycleFsm.TARGETS.size(), seen.size(), "alle Befehle kommen vor: " + seen);
        assertEquals(5, MulticycleFsm.resolve("R").size());
        assertEquals(2, MulticycleFsm.resolve("sw,beq").size());
    }

    // ------------------------------------------------------------------ Pipeline

    /** Unabhaengige Zyklus-Simulation der Pipeline mit Hazard-Einheit (Harris &amp; Harris). */
    static int simulateWithForwarding(List<Instr> p) {
        Integer[] st = new Integer[5]; // IF, ID, EX, MEM, WB: Index des Befehls
        int next = 0, cycle = 0, done = 0;
        while (done < p.size()) {
            cycle++;
            // Stall-Entscheidung auf Basis des aktuellen Zustands (vor dem Takt)
            boolean stall = st[1] != null && st[2] != null && p.get(st[2]).op() == Op.LOAD
                    && p.get(st[1]).reads(p.get(st[2]).rd());
            Integer[] n = new Integer[5];
            n[4] = st[3];
            n[3] = st[2];
            if (stall) {
                n[2] = null;
                n[1] = st[1];
                n[0] = st[0];
            } else {
                n[2] = st[1];
                n[1] = st[0];
                n[0] = next < p.size() ? next++ : null;
            }
            st = n;
            if (st[4] != null) {
                done++;
            }
        }
        return cycle;
    }

    /** Ohne Forwarding: jeder Leser muss in ID sein, wenn sein Erzeuger in WB ist oder schon fertig. */
    static boolean correctWithoutForwarding(List<Instr> withNops) {
        Map<String, Integer> wbCycle = new HashMap<>();
        for (int k = 0; k < withNops.size(); k++) {
            Instr ins = withNops.get(k);
            int id = k + 2;
            for (String r : ins.srcs()) {
                Integer wb = wbCycle.get(r);
                if (wb != null && wb > id) {
                    return false;
                }
            }
            if (ins.rd() != null) {
                wbCycle.put(ins.rd(), k + 5);
            }
        }
        return true;
    }

    static List<Instr> insertNops(List<Instr> p, int[] nops) {
        List<Instr> out = new ArrayList<>();
        Instr nop = new Instr("nop", Op.ALU, null, List.of());
        for (int j = 0; j < p.size(); j++) {
            for (int k = 0; k < nops[j]; k++) {
                out.add(nop);
            }
            out.add(p.get(j));
        }
        return out;
    }

    /** Fuehrt das Programm aus (Werte); Speicher als Map Adresse -> Wert. */
    static Map<String, Integer> execute(List<Instr> p, long seed) {
        Random r = new Random(seed);
        Map<String, Integer> reg = new HashMap<>();
        for (String x : new String[]{"t0", "t1", "t2", "t3", "s0", "s1", "a0", "a1"}) {
            reg.put(x, r.nextInt(16) * 4);
        }
        Map<Integer, Integer> mem = new HashMap<>();
        for (Instr ins : p) {
            String[] t = ins.text().replace(",", " ").replace("(", " ").replace(")", " ").trim().split("\\s+");
            switch (t[0]) {
                case "lw" -> reg.put(t[1], mem.getOrDefault(reg.get(t[3]) + Integer.parseInt(t[2]), reg.get(t[3]) * 7 + 3));
                case "sw" -> mem.put(reg.get(t[3]) + Integer.parseInt(t[2]), reg.get(t[1]));
                case "add" -> reg.put(t[1], reg.get(t[2]) + reg.get(t[3]));
                case "sub" -> reg.put(t[1], reg.get(t[2]) - reg.get(t[3]));
                case "and" -> reg.put(t[1], reg.get(t[2]) & reg.get(t[3]));
                case "or" -> reg.put(t[1], reg.get(t[2]) | reg.get(t[3]));
                case "xor" -> reg.put(t[1], reg.get(t[2]) ^ reg.get(t[3]));
                case "addi" -> reg.put(t[1], reg.get(t[2]) + Integer.parseInt(t[3]));
                case "andi" -> reg.put(t[1], reg.get(t[2]) & Integer.parseInt(t[3]));
                case "ori" -> reg.put(t[1], reg.get(t[2]) | Integer.parseInt(t[3]));
                case "slli" -> reg.put(t[1], reg.get(t[2]) << Integer.parseInt(t[3]));
                default -> throw new IllegalStateException(ins.text());
            }
        }
        Map<String, Integer> out = new HashMap<>(reg);
        mem.forEach((k, v) -> out.put("M" + k, v));
        return out;
    }

    @Test
    void pipelineTimingMatchesCycleSimulation() {
        for (long seed = 0; seed < 3000; seed++) {
            List<Instr> p = PipelineHazards.program(new Random(seed));
            int stalls = PipelineHazards.stalls(p);
            assertEquals(simulateWithForwarding(p), PipelineHazards.cycles(p.size(), stalls), p.toString());
            int[] nops = PipelineHazards.nopsWithoutForwarding(p);
            List<Instr> withNops = insertNops(p, nops);
            assertTrue(correctWithoutForwarding(withNops), "nops reichen: " + withNops);
            // minimal: jeden einzelnen nop weglassen -> falsch
            for (int j = 0; j < nops.length; j++) {
                if (nops[j] > 0) {
                    int[] less = nops.clone();
                    less[j]--;
                    assertTrue(!correctWithoutForwarding(insertNops(p, less)), "nop ueberfluessig: " + p);
                }
            }
        }
    }

    @Test
    void reorderingKeepsSemanticsAndRemovesStalls() {
        PipelineHazards ex = new PipelineHazards();
        for (long seed = 0; seed < 400; seed++) {
            Exercise.Part a = ex.generate(new Random(seed));
            assertEquals(a, ex.generate(new Random(seed)));
        }
        int checked = 0;
        for (long seed = 0; seed < 3000; seed++) {
            List<Instr> p = PipelineHazards.program(new Random(seed));
            for (List<Integer> order : PipelineHazards.validOrders(p)) {
                List<Instr> q = PipelineHazards.permute(p, order);
                for (long v = 0; v < 3; v++) {
                    assertEquals(execute(p, v), execute(q, v), p + " -> " + q);
                }
                checked++;
            }
            List<Integer> best = PipelineHazards.bestOrder(p);
            assertTrue(PipelineHazards.stalls(PipelineHazards.permute(p, best)) <= PipelineHazards.stalls(p));
        }
        assertTrue(checked > 3000);
    }

    @Test
    void generatedHazardTasksMeetConstraints() {
        PipelineHazards ex = new PipelineHazards();
        for (long seed = 0; seed < 300; seed++) {
            Exercise.Part a = ex.generate(new Random(seed));
            String s = a.antwort();
            int stalls = Integer.parseInt(s.substring(s.indexOf("Stalls mit FW=") + 14, s.indexOf(" (", s.indexOf("Stalls"))));
            assertTrue(stalls >= 1);
            assertTrue(a.loesung().contains("0 Stalls"));
        }
    }
}
