package de.ro.testat.exercise.control;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimaler RISC-V-Simulator fuer genau die Befehle, die {@link RiscvRenderer} erzeugt.
 * Dient nur zur Selbstkontrolle: Der erzeugte Assemblercode muss dieselben Endwerte
 * liefern wie der Interpreter auf dem C-Programm.
 */
public final class RvSim {

    private RvSim() {
    }

    /**
     * @param code     Assemblercode (Marken "L1:", ein Befehl pro Zeile, # Kommentare)
     * @param init     Startwerte der Register (ABI-Namen)
     * @param maxSteps Abbruch nach so vielen Befehlen
     * @return Registerinhalte am Ende
     */
    public static Map<String, Integer> run(String code, Map<String, Integer> init, int maxSteps) {
        List<String[]> prog = new ArrayList<>();
        Map<String, Integer> labels = new HashMap<>();
        for (String raw : code.split("\n")) {
            String line = raw.contains("#") ? raw.substring(0, raw.indexOf('#')) : raw;
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }
            if (line.endsWith(":")) {
                labels.put(line.substring(0, line.length() - 1), prog.size());
                continue;
            }
            int sp = line.indexOf(' ');
            String op = sp < 0 ? line : line.substring(0, sp);
            String[] args = sp < 0 ? new String[0] : line.substring(sp + 1).split("\\s*,\\s*");
            String[] ins = new String[args.length + 1];
            ins[0] = op;
            for (int i = 0; i < args.length; i++) {
                ins[i + 1] = args[i].trim();
            }
            prog.add(ins);
        }

        Map<String, Integer> r = new HashMap<>(init);
        Map<Integer, Integer> mem = new HashMap<>();
        int pc = 0;
        int steps = 0;
        while (pc < prog.size()) {
            if (++steps > maxSteps) {
                throw new IllegalStateException("Simulation: zu viele Schritte (Endlosschleife?)");
            }
            String[] i = prog.get(pc);
            int next = pc + 1;
            switch (i[0]) {
                case "li" -> set(r, i[1], Integer.parseInt(i[2]));
                case "mv" -> set(r, i[1], get(r, i[2]));
                case "add" -> set(r, i[1], get(r, i[2]) + get(r, i[3]));
                case "sub" -> set(r, i[1], get(r, i[2]) - get(r, i[3]));
                case "mul" -> set(r, i[1], get(r, i[2]) * get(r, i[3]));
                case "addi" -> set(r, i[1], get(r, i[2]) + Integer.parseInt(i[3]));
                case "slli" -> set(r, i[1], get(r, i[2]) << Integer.parseInt(i[3]));
                case "beq" -> next = get(r, i[1]) == get(r, i[2]) ? target(labels, i[3]) : next;
                case "bne" -> next = get(r, i[1]) != get(r, i[2]) ? target(labels, i[3]) : next;
                case "blt" -> next = get(r, i[1]) < get(r, i[2]) ? target(labels, i[3]) : next;
                case "bge" -> next = get(r, i[1]) >= get(r, i[2]) ? target(labels, i[3]) : next;
                case "j" -> next = target(labels, i[1]);
                case "sw" -> mem.put(address(r, i[2]), get(r, i[1]));
                case "lw" -> set(r, i[1], mem.getOrDefault(address(r, i[2]), 0));
                default -> throw new IllegalStateException("Simulation: unbekannter Befehl " + i[0]);
            }
            pc = next;
        }
        return r;
    }

    private static int target(Map<String, Integer> labels, String l) {
        Integer t = labels.get(l);
        if (t == null) {
            throw new IllegalStateException("Simulation: Marke fehlt: " + l);
        }
        return t;
    }

    /** "8(sp)" -&gt; sp + 8 */
    private static int address(Map<String, Integer> r, String a) {
        int p = a.indexOf('(');
        int off = Integer.parseInt(a.substring(0, p));
        return get(r, a.substring(p + 1, a.length() - 1)) + off;
    }

    private static int get(Map<String, Integer> r, String reg) {
        return "zero".equals(reg) ? 0 : r.getOrDefault(reg, 0);
    }

    private static void set(Map<String, Integer> r, String reg, int v) {
        if (!"zero".equals(reg)) {
            r.put(reg, v);
        }
    }
}
