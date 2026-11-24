package de.ro.testat.rv;

import de.ro.testat.testat.JdkRandom;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Erzeugt zufaellige, aber sinnvolle Operanden fuer einen Befehl. */
public final class InstanceGenerator {

    /** Beginn des Textsegments wie in RARS. */
    public static final long TEXT_BASE = 0x00400000L;

    private InstanceGenerator() {
    }

    public static Instance generate(Mnemonic mn, Random rng) {
        List<Integer> pool = new ArrayList<>(Registers.POOL);
        JdkRandom.mischen(pool, rng);
        int a = pool.get(0), b = pool.get(1), c = pool.get(2);

        return switch (mn.format()) {
            case R -> new Instance(mn, a, b, c, null, null, null, null);
            case I -> new Instance(mn, a, b, null, randInt(rng, -2048, 2047), null, null, null);
            case IS -> new Instance(mn, a, b, null, null, randInt(rng, 1, 31), null, null);
            case IL -> new Instance(mn, a, b, null, randInt(rng, -128, 127) * accessSize(mn), null, null, null);
            case IJ -> new Instance(mn, rng.nextBoolean() ? 1 : a, b, null, randInt(rng, -64, 63) * 4, null, null, null);
            case S -> new Instance(mn, null, b, c, randInt(rng, -128, 127) * accessSize(mn), null, null, null);
            case B -> {
                int off = sign(rng) * randInt(rng, 1, 255) * 4;
                long pc = TEXT_BASE + randInt(rng, 256, 1023) * 4L;
                yield new Instance(mn, null, b, c, off, null, pc, pc + off);
            }
            case U -> new Instance(mn, a, null, null, randInt(rng, 0, 0xFFFFF), null, null, null);
            case J -> {
                int off = sign(rng) * randInt(rng, 1, 16383) * 4;
                long pc = TEXT_BASE + randInt(rng, 16384, 32767) * 4L;
                yield new Instance(mn, rng.nextBoolean() ? 1 : a, null, null, off, null, pc, pc + off);
            }
        };
    }

    private static int accessSize(Mnemonic mn) {
        return switch (mn) {
            case LB, LBU, SB -> 1;
            case LH, LHU, SH -> 2;
            default -> 4;
        };
    }

    /** Gleichverteilt in [lo, hi] (beide inklusive). */
    static int randInt(Random rng, int lo, int hi) {
        return lo + rng.nextInt(hi - lo + 1);
    }

    private static int sign(Random rng) {
        return rng.nextBoolean() ? 1 : -1;
    }
}
