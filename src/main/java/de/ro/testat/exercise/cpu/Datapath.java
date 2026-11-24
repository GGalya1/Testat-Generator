package de.ro.testat.exercise.cpu;

import de.ro.testat.testat.JdkRandom;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Modell des Mehrtakt-Datenpfads aus der Vorlesung (Harris &amp; Harris, RISC-V Edition).
 *
 * <p>Ein Takt ({@link Step}) waehlt die Steuersignale. Pro Takt gilt wie in der Vorlesung:
 * hoechstens eine ALU-Operation, ein Speicherzugriff (Adresse = Result, AdrSrc = 1) und ein
 * Schreibzugriff auf das Registerfeld. Das Ergebnis der ALU darf im selben Takt nur in den PC
 * (wie in Fetch), nicht ins Registerfeld oder als Speicheradresse - dafuer gibt es ALUOut.
 * Die Register ALUOut, Data, A und WriteData uebernehmen in jedem Takt einen neuen Wert.
 *
 * <p>{@link #shortest} sucht ab dem Zustand nach Decode alle kuerzesten Steuerfolgen, die die
 * Wirkung eines Befehls ({@link Goal}) erzeugen. {@link #simulate} fuehrt eine Folge mit
 * konkreten Zahlen aus (unbenutzte Signale = Zufall) und dient als unabhaengige Kontrolle.
 */
final class Datapath {

    enum SrcA {
        PC("00"), OLDPC("01"), A("10");
        final String code;

        SrcA(String code) {
            this.code = code;
        }
    }

    enum SrcB {
        WD("00"), IMM("01"), FOUR("10");
        final String code;

        SrcB(String code) {
            this.code = code;
        }
    }

    enum Res {
        ALUOUT("00"), DATA("01"), ALURESULT("10");
        final String code;

        Res(String code) {
            this.code = code;
        }
    }

    /** Ein Takt: ALU-Quellen (null = ALU unbenutzt), Result-Quelle (null = unbenutzt) und was mit Result geschieht. */
    record Step(SrcA a, SrcB b, Res res, boolean read, boolean regWrite, boolean pcUpdate, boolean memWrite) {

        /** Steuersignale in der Schreibweise der Vorlesung (nicht genannte = 0 bzw. beliebig). */
        List<String> signals() {
            List<String> s = new ArrayList<>();
            if (a != null) {
                s.add("ALUSrcA = " + a.code);
                s.add("ALUSrcB = " + b.code);
                s.add("ALUOp = 00");
            }
            if (res != null) {
                s.add("ResultSrc = " + res.code);
            }
            if (read || memWrite) {
                s.add("AdrSrc = 1");
            }
            if (memWrite) {
                s.add("MemWrite");
            }
            if (regWrite) {
                s.add("RegWrite");
            }
            if (pcUpdate) {
                s.add("PCUpdate");
            }
            return s;
        }
    }

    /**
     * Wirkung eines Befehls, Werte symbolisch: Summen aus pc, rs1, rs2, imm, 4 ("pc+4") oder
     * Speicherinhalte ("M[rs1+imm]"). pc = Adresse des Befehls (OldPC). null = keine Wirkung.
     */
    record Goal(String rd, String pc, String memAddr, String memVal) {
        Goal {
            rd = canon(rd);
            pc = canon(pc);
            memAddr = canon(memAddr);
            memVal = canon(memVal);
        }
    }

    // ------------------------------------------------------------------
    // Symbolische Werte
    // ------------------------------------------------------------------

    static final String UNKNOWN = "?";

    static String canon(String v) {
        if (v == null) {
            return null;
        }
        v = v.replace(" ", "");
        if (v.startsWith("M[")) {
            return "M[" + canon(v.substring(2, v.length() - 1)) + "]";
        }
        String[] atoms = v.split("\\+");
        Arrays.sort(atoms);
        return String.join("+", atoms);
    }

    static boolean isSum(String v) {
        return !v.equals(UNKNOWN) && !v.startsWith("M[");
    }

    static String add(String x, String y) {
        return isSum(x) && isSum(y) ? canon(x + "+" + y) : UNKNOWN;
    }

    static String mem(String x) {
        return x.equals(UNKNOWN) ? UNKNOWN : "M[" + x + "]";
    }

    /** Zustand der Register (symbolisch) und erledigte Wirkungen. */
    record Sym(String pcReg, String aluOut, String data, String a, String wd,
               boolean rdDone, boolean pcDone, boolean memDone) {
    }

    /** Nach S0 (Fetch) und S1 (Decode): PC = pc+4, ALUOut = pc+imm, A = rs1, WriteData = rs2. */
    static Sym afterDecode() {
        return new Sym("4+pc", "imm+pc", UNKNOWN, "rs1", "rs2", false, false, false);
    }

    // ------------------------------------------------------------------
    // Suche
    // ------------------------------------------------------------------

    /** Alle kuerzesten Steuerfolgen (nach Decode), leer wenn bis maxSteps keine existiert. */
    static List<List<Step>> shortest(Goal g, int maxSteps) {
        Set<String> sums = new LinkedHashSet<>();
        Set<String> mems = new LinkedHashSet<>();
        for (String v : new String[]{g.rd(), g.pc(), g.memAddr(), g.memVal()}) {
            collect(v, sums, mems);
        }
        for (int d = 1; d <= maxSteps; d++) {
            List<List<Step>> out = new ArrayList<>();
            dfs(afterDecode(), g, d, new ArrayList<>(), out, sums, mems);
            if (!out.isEmpty()) {
                return out;
            }
        }
        return List.of();
    }

    private static void collect(String v, Set<String> sums, Set<String> mems) {
        if (v == null) {
            return;
        }
        if (v.startsWith("M[")) {
            mems.add(v);
            collect(v.substring(2, v.length() - 1), sums, mems);
        } else {
            sums.add(v);
        }
    }

    private static boolean done(Sym s, Goal g) {
        return (g.rd() == null || s.rdDone()) && (g.pc() == null || s.pcDone()) && (g.memAddr() == null || s.memDone());
    }

    private static void dfs(Sym s, Goal g, int left, List<Step> path, List<List<Step>> out, Set<String> sums, Set<String> mems) {
        if (done(s, g)) {
            out.add(List.copyOf(path));
            return;
        }
        if (left == 0) {
            return;
        }
        for (Step st : options(s, g, sums, mems)) {
            path.add(st);
            dfs(apply(s, st), g, left - 1, path, out, sums, mems);
            path.remove(path.size() - 1);
        }
    }

    static String valA(Sym s, SrcA a) {
        return switch (a) {
            case PC -> s.pcReg();
            case OLDPC -> "pc";
            case A -> s.a();
        };
    }

    static String valB(Sym s, SrcB b) {
        return switch (b) {
            case WD -> s.wd();
            case IMM -> "imm";
            case FOUR -> "4";
        };
    }

    /** Sinnvolle Takte: die ALU rechnet nur benoetigte Summen, Result wird nur fuer passende Wirkungen genutzt. */
    static List<Step> options(Sym s, Goal g, Set<String> sums, Set<String> mems) {
        List<Step> out = new ArrayList<>();
        List<SrcA> as = new ArrayList<>(Arrays.asList(SrcA.values()));
        as.add(0, null);
        for (SrcA a : as) {
            for (SrcB b : a == null ? new SrcB[]{null} : SrcB.values()) {
                String alu = a == null ? UNKNOWN : add(valA(s, a), valB(s, b));
                if (a != null && !sums.contains(alu)) {
                    continue;
                }
                for (Res res : new Res[]{null, Res.ALUOUT, Res.DATA, Res.ALURESULT}) {
                    if (res == null) {
                        if (a != null) {
                            out.add(new Step(a, b, null, false, false, false, false));
                        }
                        continue;
                    }
                    if (res == Res.ALURESULT && a == null) {
                        continue;
                    }
                    String r = switch (res) {
                        case ALUOUT -> s.aluOut();
                        case DATA -> s.data();
                        case ALURESULT -> alu;
                    };
                    if (r.equals(UNKNOWN)) {
                        continue;
                    }
                    boolean viaReg = res != Res.ALURESULT; // ALU-Ergebnis nur direkt in den PC
                    boolean canRead = viaReg && mems.contains(mem(r));
                    boolean canReg = viaReg && g.rd() != null && !s.rdDone() && g.rd().equals(r);
                    boolean canPc = g.pc() != null && !s.pcDone() && g.pc().equals(r);
                    boolean canMem = viaReg && g.memAddr() != null && !s.memDone() && g.memAddr().equals(r)
                            && g.memVal().equals(s.wd());
                    for (int m = 1; m < 16; m++) {
                        boolean rd = (m & 1) != 0, rw = (m & 2) != 0, pu = (m & 4) != 0, mw = (m & 8) != 0;
                        if ((rd && !canRead) || (rw && !canReg) || (pu && !canPc) || (mw && !canMem) || (rd && mw)) {
                            continue;
                        }
                        out.add(new Step(a, b, res, rd, rw, pu, mw));
                    }
                }
            }
        }
        return out;
    }

    static Sym apply(Sym s, Step st) {
        String alu = st.a() == null ? UNKNOWN : add(valA(s, st.a()), valB(s, st.b()));
        String r = st.res() == null ? UNKNOWN : switch (st.res()) {
            case ALUOUT -> s.aluOut();
            case DATA -> s.data();
            case ALURESULT -> alu;
        };
        boolean w = st.regWrite();
        return new Sym(st.pcUpdate() ? r : s.pcReg(), alu, st.read() ? mem(r) : UNKNOWN,
                w ? UNKNOWN : s.a(), w ? UNKNOWN : s.wd(),
                s.rdDone() || w, s.pcDone() || st.pcUpdate(), s.memDone() || st.memWrite());
    }

    /** Wirkung eines Taktes als Registertransfer, z. B. "ALUOut <- rs1 + imm" (LaTeX). */
    static List<String> effects(Sym s, Step st) {
        List<String> e = new ArrayList<>();
        String alu = st.a() == null ? UNKNOWN : add(valA(s, st.a()), valB(s, st.b()));
        String r = st.res() == null ? UNKNOWN : switch (st.res()) {
            case ALUOUT -> s.aluOut();
            case DATA -> s.data();
            case ALURESULT -> alu;
        };
        if (st.read()) {
            e.add("Data $\\leftarrow$ " + tex(mem(r)));
        }
        if (st.memWrite()) {
            e.add(tex(mem(r)) + " $\\leftarrow$ rs2");
        }
        if (st.regWrite()) {
            e.add("rd $\\leftarrow$ " + tex(r));
        }
        if (st.pcUpdate()) {
            e.add("PC $\\leftarrow$ " + tex(r) + (st.res() == Res.ALURESULT ? " (direkt aus der ALU)" : ""));
        }
        if (st.a() != null && st.res() != Res.ALURESULT) {
            e.add("ALUOut $\\leftarrow$ " + tex(alu));
        }
        return e;
    }

    private static final List<String> ORDER = List.of("pc", "rs1", "rs2", "imm", "4");

    /** Symbolischer Wert lesbar: "M[imm+rs1]" -> "Mem[rs1 + imm]", pc -> PC. */
    static String tex(String v) {
        if (v == null || v.equals(UNKNOWN)) {
            return "?";
        }
        if (v.startsWith("M[")) {
            return "Mem[" + tex(v.substring(2, v.length() - 1)) + "]";
        }
        return Arrays.stream(v.split("\\+"))
                .sorted((x, y) -> Integer.compare(ORDER.indexOf(x), ORDER.indexOf(y)))
                .map(x -> x.equals("pc") ? "PC" : x)
                .collect(Collectors.joining(" + "));
    }

    // ------------------------------------------------------------------
    // Konkrete Simulation (Selbstkontrolle)
    // ------------------------------------------------------------------

    /** Fuehrt Fetch, Decode und die Folge aus und vergleicht mit der Wirkung des Befehls. */
    static boolean simulate(Goal g, List<Step> steps, long seed) {
        Random r = new JdkRandom(seed);
        int pc = 4 * r.nextInt(1 << 16);
        int rs1 = r.nextInt();
        int rs2 = r.nextInt();
        int imm = r.nextInt(4096) - 2048;
        Map<Integer, Integer> memory = new HashMap<>();
        long salt = r.nextLong();
        java.util.function.IntUnaryOperator initial = adr -> (int) ((adr * 0x9E3779B97F4A7C15L) ^ salt);

        // Zustand nach Fetch (PC = PC + 4) und Decode (ALUOut = OldPC + ImmExt)
        int pcReg = pc + 4;
        int aluOut = pc + imm;
        int data = r.nextInt();
        int a = rs1;
        int wd = rs2;
        Integer rdVal = null;
        Map<Integer, Integer> writes = new HashMap<>();
        for (Step st : steps) {
            int srcA = st.a() == null ? r.nextInt() : switch (st.a()) {
                case PC -> pcReg;
                case OLDPC -> pc;
                case A -> a;
            };
            int srcB = st.b() == null ? r.nextInt() : switch (st.b()) {
                case WD -> wd;
                case IMM -> imm;
                case FOUR -> 4;
            };
            int aluRes = st.a() == null ? r.nextInt() : srcA + srcB;
            int result = st.res() == null ? r.nextInt() : switch (st.res()) {
                case ALUOUT -> aluOut;
                case DATA -> data;
                case ALURESULT -> aluRes;
            };
            int adr = st.read() || st.memWrite() ? result : pcReg;
            int readVal = memory.getOrDefault(adr, initial.applyAsInt(adr));
            // Taktflanke
            if (st.memWrite()) {
                memory.put(adr, wd);
                writes.put(adr, wd);
            }
            if (st.regWrite()) {
                rdVal = result;
            }
            if (st.pcUpdate()) {
                pcReg = result;
            }
            aluOut = aluRes;
            data = readVal;
            a = rs1;
            wd = rs2;
        }
        Map<String, Integer> atoms = Map.of("pc", pc, "rs1", rs1, "rs2", rs2, "imm", imm, "4", 4);
        java.util.function.Function<String, Integer> eval = new java.util.function.Function<>() {
            @Override
            public Integer apply(String v) {
                if (v.startsWith("M[")) {
                    return initial.applyAsInt(apply(v.substring(2, v.length() - 1)));
                }
                return Arrays.stream(v.split("\\+")).mapToInt(atoms::get).sum();
            }
        };
        Integer expRd = g.rd() == null ? null : eval.apply(g.rd());
        int expPc = g.pc() == null ? pc + 4 : eval.apply(g.pc());
        Map<Integer, Integer> expWrites = g.memAddr() == null ? Map.of()
                : Map.of(eval.apply(g.memAddr()), eval.apply(g.memVal()));
        return Objects.equals(expRd, rdVal) && expPc == pcReg && expWrites.equals(writes);
    }
}
