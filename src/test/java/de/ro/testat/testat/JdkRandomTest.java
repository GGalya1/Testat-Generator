package de.ro.testat.testat;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** JdkRandom muss auf der JVM genau dieselben Zahlen liefern wie java.util.Random. */
class JdkRandomTest {

    private static final int[] GRENZEN = {1, 2, 3, 5, 7, 8, 10, 16, 31, 32, 100, 1000, 4096, 65536,
            1 << 30, (1 << 30) + 1, Integer.MAX_VALUE};

    @Test
    void gleicheFolgeWieJdk() {
        Random seeds = new Random(42);
        for (int s = 0; s < 500; s++) {
            long seed = s < 3 ? new long[] {0, -1, Long.MIN_VALUE}[s] : seeds.nextLong();
            Random jdk = new Random(seed);
            Random eigen = new JdkRandom(seed);
            Random wahl = new Random(seed ^ 0x1234);
            for (int i = 0; i < 400; i++) {
                switch (wahl.nextInt(4)) {
                    case 0 -> assertEquals(jdk.nextInt(), eigen.nextInt());
                    case 1 -> {
                        int g = GRENZEN[wahl.nextInt(GRENZEN.length)];
                        assertEquals(jdk.nextInt(g), eigen.nextInt(g), "nextInt(" + g + ")");
                    }
                    case 2 -> assertEquals(jdk.nextBoolean(), eigen.nextBoolean());
                    default -> assertEquals(jdk.nextLong(), eigen.nextLong());
                }
            }
        }
    }

    @Test
    void setSeedWieJdk() {
        Random jdk = new Random(1);
        Random eigen = new JdkRandom(1);
        jdk.nextInt();
        eigen.nextInt();
        jdk.setSeed(99);
        eigen.setSeed(99);
        for (int i = 0; i < 50; i++) {
            assertEquals(jdk.nextInt(1000), eigen.nextInt(1000));
        }
    }

    @Test
    void mischenWieCollectionsShuffle() {
        for (int n = 0; n <= 40; n++) {
            for (long seed = 0; seed < 30; seed++) {
                List<Integer> a = new ArrayList<>();
                List<Integer> b = new ArrayList<>();
                List<Integer> c = new LinkedList<>();
                for (int i = 0; i < n; i++) {
                    a.add(i);
                    b.add(i);
                    c.add(i);
                }
                Collections.shuffle(a, new Random(seed));
                JdkRandom.mischen(b, new JdkRandom(seed));
                JdkRandom.mischen(c, new JdkRandom(seed));
                assertEquals(a, b, "n=" + n + " seed=" + seed);
                assertEquals(a, c, "LinkedList n=" + n);
            }
        }
    }
}
