package de.ro.testat.exercise.memory;

import de.ro.testat.exercise.Exercise;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryTest {

    /** Jede Aufgabe und jede Variante: deterministisch, Selbstkontrollen laufen, keine Loesung im Aufgabentext. */
    @Test
    void allExercisesGenerateDeterministically() {
        List<Exercise> alle = List.of(
                new CacheParameters("vorwaerts"), new CacheParameters("rueckwaerts"),
                new CacheSimulation("direkt"), new CacheSimulation("assoziativ"), new CacheSimulation("voll"),
                new CacheProgram("schleife"), new CacheProgram("array"),
                new AccessTime(), new WritePolicy(), new VirtualMemory());
        for (Exercise e : alle) {
            for (long seed = 0; seed < 150; seed++) {
                long gemischt = seed * 0x9E3779B97F4A7C15L + 12345; // aufeinanderfolgende Seeds liefern sonst fast gleiche erste Zufallszahlen
                Exercise.Part a = e.generate(new Random(gemischt));
                assertEquals(a, e.generate(new Random(gemischt)), e.describe() + " seed " + seed);
                assertTrue(!a.aufgabe().contains("Miss-Rate $"), e.describe());
            }
        }
    }

    /** Harris &amp; Harris, Beispiel: direkt abgebildet, 8 Woerter, b = 1, Schleife 5x lw 0x4, 0xC, 0x8 -> 3/15. */
    @Test
    void textbookLoopDirectMapped() {
        assertEquals(3L, (long) new CacheModel(32, 4, 1).misses(schleife(5, 0x4, 0xC, 0x8)));
    }

    /** Harris &amp; Harris: lw 0x4, 0x24 im Wechsel, 5x: direkt abgebildet 10/10, 2-fach 2/10. */
    @Test
    void textbookConflictAndTwoWay() {
        List<Integer> f = schleife(5, 0x4, 0x24);
        assertEquals(10L, (long) new CacheModel(32, 4, 1).misses(f));
        assertEquals(2L, (long) new CacheModel(32, 4, 2).misses(f));
        List<CacheModel.MissArt> arten = CacheModel.klassifiziere(new CacheModel(32, 4, 1), f);
        assertEquals(8L, arten.stream().filter(x -> x == CacheModel.MissArt.CONFLICT).count());
    }

    /** Harris &amp; Harris: 8-Wort-Cache, direkt abgebildet, b = 1: Satz 3 Bit, Tag 27 Bit. */
    @Test
    void textbookBitWidths() {
        CacheModel c = new CacheModel(32, 4, 1);
        assertEquals(3L, (long) c.satzBits());
        assertEquals(27L, (long) c.tagBits());
        assertEquals(0L, (long) c.blockOffsetBits());
        CacheModel v = new CacheModel(32, 4, 8);
        assertEquals(0L, (long) v.satzBits());
        assertEquals(30L, (long) v.tagBits());
    }

    /** Cache-Modell gegen die zweite LRU-Simulation fuer viele zufaellige Konfigurationen und Folgen. */
    @Test
    void modelMatchesIndependentLru() {
        Random r = new Random(1);
        for (int i = 0; i < 3000; i++) {
            int blockWorte = 1 << r.nextInt(3);
            int kapWorte = blockWorte << (2 + r.nextInt(3));
            int bloecke = kapWorte / blockWorte;
            int wege = 1 << r.nextInt(Integer.numberOfTrailingZeros(bloecke) + 1);
            CacheSimulation.Konfig k = new CacheSimulation.Konfig(kapWorte, blockWorte, wege);
            List<Integer> f = new ArrayList<>();
            for (int j = 0; j < 40; j++) {
                f.add(4 * r.nextInt(64));
            }
            CacheModel c = k.cache();
            boolean[] ref = CacheSimulation.lruUnabhaengig(k, f);
            for (int j = 0; j < f.size(); j++) {
                assertEquals(ref[j], c.lesen(f.get(j)).hit(), k + " Zugriff " + j);
            }
            // voll assoziativ hat keine Conflict-Misses
            assertTrue(CacheModel.klassifiziere(c.vollAssoziativ(), f).stream().noneMatch(x -> x == CacheModel.MissArt.CONFLICT));
        }
    }

    /** Adresszerlegung fuer viele Parameter und Adressen (wirft bei Abweichung). */
    @Test
    void addressSplitConsistent() {
        Random r = new Random(2);
        for (int i = 0; i < 2000; i++) {
            int kap = 1024 << r.nextInt(9);
            int block = 4 << r.nextInt(5);
            int wege = 1 << r.nextInt(4);
            CacheParameters.pruefe(new CacheModel(kap, block, wege), r.nextInt());
        }
    }

    private static List<Integer> schleife(int n, int... adr) {
        List<Integer> f = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            for (int a : adr) {
                f.add(a);
            }
        }
        return f;
    }

    /** Vergleich: ein record ohne eigenen hashCode, also mit dem automatischen des JDK. */
    private record OhneEigenenHash(int kapWorte, int blockWorte, int wege) {
    }

    @Test
    void konfigHashWieJdkRecord() {
        List<CacheSimulation.Konfig> alle = new ArrayList<>(CacheSimulation.DIREKT);
        alle.addAll(CacheSimulation.ASSOZIATIV);
        alle.addAll(CacheSimulation.VOLL);
        Random r = new Random(7);
        for (int i = 0; i < 1000; i++) {
            alle.add(new CacheSimulation.Konfig(r.nextInt(), r.nextInt(), r.nextInt()));
        }
        for (CacheSimulation.Konfig k : alle) {
            assertEquals(new OhneEigenenHash(k.kapWorte(), k.blockWorte(), k.wege()).hashCode(), k.hashCode(), k.toString());
        }
    }
}
