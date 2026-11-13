package de.ro.testat.rv;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class Registers {

    private Registers() {
    }

    public static final List<String> ABI = List.of(
            "zero", "ra", "sp", "gp", "tp", "t0", "t1", "t2", "s0", "s1",
            "a0", "a1", "a2", "a3", "a4", "a5", "a6", "a7",
            "s2", "s3", "s4", "s5", "s6", "s7", "s8", "s9", "s10", "s11",
            "t3", "t4", "t5", "t6");

    /** Register, die in Aufgaben vorkommen duerfen: ohne zero, gp, tp. */
    public static final List<Integer> POOL = IntStream.range(1, 32)
            .filter(r -> r != 3 && r != 4).boxed().collect(Collectors.toUnmodifiableList());

    public static String abi(int reg) {
        return ABI.get(reg);
    }
}
