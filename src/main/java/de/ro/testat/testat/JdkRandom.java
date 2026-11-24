package de.ro.testat.testat;

import java.util.List;
import java.util.Random;

/**
 * Zufallszahlen genau wie {@code java.util.Random} des JDK (48-Bit-LCG, gleiche Algorithmen fuer
 * nextInt, nextInt(bound), nextBoolean, nextLong) - aber selbst implementiert.
 *
 * Grund: Im Browser (TeaVM) liefert {@code new Random(seed)} eine andere Folge als das JDK. Dann
 * wuerde derselbe Seed auf der Webseite andere Aufgaben ergeben als auf der Kommandozeile. Mit
 * dieser Klasse ist die Folge ueberall gleich. Auf der JVM ist sie identisch zu java.util.Random
 * (geprueft in JdkRandomTest), d. h. alle bisherigen Seeds geben dieselben Blaetter.
 *
 * Nur die oben genannten Methoden und {@link #mischen} sind nachgebaut; der Generator benutzt
 * keine anderen (nextDouble, nextGaussian, Streams ...). Neue Methoden hier ergaenzen.
 */
public final class JdkRandom extends Random {

    private static final long MULTIPLIER = 0x5DEECE66DL;
    private static final long ADDEND = 0xBL;
    private static final long MASK = (1L << 48) - 1;

    /** Ohne Initialisierer: der Konstruktor von Random ruft setSeed schon vor unserem Konstruktor. */
    private long zustand;

    public JdkRandom(long seed) {
        super(seed);
        zustand = (seed ^ MULTIPLIER) & MASK;
    }

    @Override
    public void setSeed(long seed) {
        zustand = (seed ^ MULTIPLIER) & MASK;
    }

    @Override
    protected int next(int bits) {
        zustand = (zustand * MULTIPLIER + ADDEND) & MASK;
        return (int) (zustand >>> (48 - bits));
    }

    @Override
    public int nextInt() {
        return next(32);
    }

    @Override
    public int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("bound must be positive");
        }
        int r = next(31);
        int m = bound - 1;
        if ((bound & m) == 0) { // Zweierpotenz
            r = (int) ((bound * (long) r) >> 31);
        } else {
            for (int u = r; u - (r = u % bound) + m < 0; u = next(31)) {
                // verwerfen, damit alle Werte gleich wahrscheinlich sind (wie im JDK)
            }
        }
        return r;
    }

    @Override
    public long nextLong() {
        return ((long) next(32) << 32) + next(32);
    }

    @Override
    public boolean nextBoolean() {
        return next(1) != 0;
    }

    /** Wie {@code Collections.shuffle(liste, rnd)} im JDK (gleiche Vertauschungen). */
    public static <T> void mischen(List<T> liste, Random rnd) {
        for (int i = liste.size(); i > 1; i--) {
            int j = rnd.nextInt(i);
            T t = liste.get(i - 1);
            liste.set(i - 1, liste.get(j));
            liste.set(j, t);
        }
    }
}
