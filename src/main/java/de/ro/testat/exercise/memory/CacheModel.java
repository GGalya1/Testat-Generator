package de.ro.testat.exercise.memory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Cache-Modell nach Harris &amp; Harris (Kap. 8): 32-Bit-Byteadressen, Woerter zu 4 Byte,
 * Bloecke aus b Woertern, N-fach satzassoziativ (N = 1: direkt abgebildet, N = Anzahl
 * Bloecke: voll assoziativ), Ersetzung LRU. Adresse = Tag | Satz | Block-Offset | Byte-Offset.
 *
 * <p>Ein neuer Block kommt in den ersten freien Weg des Satzes (Weg 0 zuerst), sonst ersetzt er
 * den am laengsten nicht benutzten Block. Schreibstrategien: Write-Back mit Write-Allocate oder
 * Write-Through ohne Write-Allocate.
 */
final class CacheModel {

    enum Strategie {
        /** Schreiben nur in den Cache (Dirty-Bit), bei Schreib-Miss Block laden. */
        WRITE_BACK,
        /** Jedes Schreiben geht in den Speicher, bei Schreib-Miss kein Laden des Blocks. */
        WRITE_THROUGH
    }

    /** Ergebnis eines Zugriffs; verdraengt = Tag des ersetzten Blocks oder null. */
    record Zugriff(int adresse, boolean schreiben, int satz, int tag, int weg, boolean hit, Integer verdraengt,
                   boolean verdraengtDirty, int bloeckeGelesen, int worteGeschrieben, int bloeckeGeschrieben) {
    }

    private static final class Zeile {
        boolean gueltig;
        int tag;
        boolean dirty;
        long zuletzt;
    }

    final int kapazitaet;
    final int blockBytes;
    final int wege;
    final int saetze;
    final Strategie strategie;
    private final Zeile[][] zeilen;
    private long uhr;

    CacheModel(int kapazitaetBytes, int blockBytes, int wege, Strategie strategie) {
        if (Integer.bitCount(kapazitaetBytes) != 1 || Integer.bitCount(blockBytes) != 1 || Integer.bitCount(wege) != 1
                || blockBytes < 4 || kapazitaetBytes < blockBytes * wege) {
            throw new IllegalArgumentException("Ungueltige Cache-Parameter");
        }
        this.kapazitaet = kapazitaetBytes;
        this.blockBytes = blockBytes;
        this.wege = wege;
        this.saetze = kapazitaetBytes / blockBytes / wege;
        this.strategie = strategie;
        this.zeilen = new Zeile[saetze][wege];
        for (Zeile[] s : zeilen) {
            for (int w = 0; w < wege; w++) {
                s[w] = new Zeile();
            }
        }
    }

    CacheModel(int kapazitaetBytes, int blockBytes, int wege) {
        this(kapazitaetBytes, blockBytes, wege, Strategie.WRITE_BACK);
    }

    /** Neuer, leerer Cache mit denselben Parametern. */
    CacheModel leer() {
        return new CacheModel(kapazitaet, blockBytes, wege, strategie);
    }

    /** Misses einer Lesefolge auf einem leeren Cache mit diesen Parametern (dieser Cache bleibt unveraendert). */
    int misses(List<Integer> folge) {
        CacheModel c = leer();
        int m = 0;
        for (int a : folge) {
            m += c.lesen(a).hit() ? 0 : 1;
        }
        return m;
    }

    /** Gleiche Groesse, gleicher Block, aber voll assoziativ (fuer die Einteilung der Misses). */
    CacheModel vollAssoziativ() {
        return new CacheModel(kapazitaet, blockBytes, kapazitaet / blockBytes, strategie);
    }

    int bloecke() {
        return kapazitaet / blockBytes;
    }

    int worteProBlock() {
        return blockBytes / 4;
    }

    // ------------------------------------------------------------------ Adresszerlegung

    static final int BYTE_OFFSET_BITS = 2;

    int blockOffsetBits() {
        return log2(blockBytes / 4);
    }

    int satzBits() {
        return log2(saetze);
    }

    int tagBits() {
        return 32 - satzBits() - blockOffsetBits() - BYTE_OFFSET_BITS;
    }

    int blocknummer(int adr) {
        return Integer.divideUnsigned(adr, blockBytes);
    }

    int satz(int adr) {
        return Integer.remainderUnsigned(blocknummer(adr), saetze);
    }

    int tag(int adr) {
        return Integer.divideUnsigned(blocknummer(adr), saetze);
    }

    int blockOffset(int adr) {
        return (adr >>> 2) & (worteProBlock() - 1);
    }

    static int byteOffset(int adr) {
        return adr & 3;
    }

    /** Erste Byteadresse des Blocks mit Tag tag in Satz satz. */
    int blockAdresse(int tag, int satz) {
        return (tag * saetze + satz) * blockBytes;
    }

    // ------------------------------------------------------------------ Zugriffe

    Zugriff lesen(int adr) {
        return zugriff(adr, false);
    }

    Zugriff zugriff(int adr, boolean schreiben) {
        uhr++;
        int s = satz(adr);
        int t = tag(adr);
        Zeile[] satz = zeilen[s];
        for (int w = 0; w < wege; w++) {
            if (satz[w].gueltig && satz[w].tag == t) {
                satz[w].zuletzt = uhr;
                int worte = 0;
                if (schreiben) {
                    if (strategie == Strategie.WRITE_BACK) {
                        satz[w].dirty = true;
                    } else {
                        worte = 1;
                    }
                }
                return new Zugriff(adr, schreiben, s, t, w, true, null, false, 0, worte, 0);
            }
        }
        // Miss
        if (schreiben && strategie == Strategie.WRITE_THROUGH) {
            return new Zugriff(adr, true, s, t, -1, false, null, false, 0, 1, 0);
        }
        int ziel = -1;
        for (int w = 0; w < wege && ziel < 0; w++) {
            if (!satz[w].gueltig) {
                ziel = w;
            }
        }
        if (ziel < 0) {
            ziel = 0;
            for (int w = 1; w < wege; w++) {
                if (satz[w].zuletzt < satz[ziel].zuletzt) {
                    ziel = w;
                }
            }
        }
        Zeile z = satz[ziel];
        Integer alt = z.gueltig ? z.tag : null;
        boolean altDirty = z.gueltig && z.dirty;
        z.gueltig = true;
        z.tag = t;
        z.dirty = schreiben && strategie == Strategie.WRITE_BACK;
        z.zuletzt = uhr;
        return new Zugriff(adr, schreiben, s, t, ziel, false, alt, altDirty, 1, 0, altDirty ? 1 : 0);
    }

    /** Tag in Satz s, Weg w oder null (ungueltig). */
    Integer inhalt(int s, int w) {
        return zeilen[s][w].gueltig ? zeilen[s][w].tag : null;
    }

    boolean dirty(int s, int w) {
        return zeilen[s][w].gueltig && zeilen[s][w].dirty;
    }

    // ------------------------------------------------------------------ 3C-Einteilung

    enum MissArt {
        HIT, COMPULSORY, CAPACITY, CONFLICT;

        String text() {
            return switch (this) {
                case HIT -> "--";
                case COMPULSORY -> "Compulsory";
                case CAPACITY -> "Capacity";
                case CONFLICT -> "Conflict";
            };
        }
    }

    /**
     * Lesefolge auf einem frischen Cache mit diesen Parametern; Einteilung der Misses: erster
     * Zugriff auf den Block = compulsory; sonst capacity, wenn auch ein gleich grosser voll
     * assoziativer LRU-Cache verfehlt, sonst conflict.
     */
    static List<MissArt> klassifiziere(CacheModel leer, List<Integer> adressen) {
        CacheModel c = leer.leer();
        CacheModel voll = c.vollAssoziativ();
        Set<Integer> gesehen = new HashSet<>();
        List<MissArt> out = new ArrayList<>();
        for (int a : adressen) {
            boolean hit = c.lesen(a).hit();
            boolean vollHit = voll.lesen(a).hit();
            boolean neu = gesehen.add(c.blocknummer(a));
            out.add(hit ? MissArt.HIT : neu ? MissArt.COMPULSORY : !vollHit ? MissArt.CAPACITY : MissArt.CONFLICT);
        }
        return out;
    }

    // ------------------------------------------------------------------ Hilfen

    static int log2(int x) {
        if (Integer.bitCount(x) != 1) {
            throw new IllegalArgumentException("keine Zweierpotenz: " + x);
        }
        return Integer.numberOfTrailingZeros(x);
    }

    /** "16 KiB", "64 Byte", "1 MiB" */
    static String groesse(long bytes) {
        if (bytes >= 1L << 30 && bytes % (1L << 30) == 0) {
            return (bytes >> 30) + "~GiB";
        }
        if (bytes >= 1L << 20 && bytes % (1L << 20) == 0) {
            return (bytes >> 20) + "~MiB";
        }
        if (bytes >= 1024 && bytes % 1024 == 0) {
            return (bytes >> 10) + "~KiB";
        }
        return bytes + "~Byte";
    }

    /** "direkt abgebildet", "2-fach satzassoziativ", "voll assoziativ" */
    String organisation() {
        if (wege == 1) {
            return "direkt abgebildet";
        }
        if (saetze == 1) {
            return "voll assoziativ";
        }
        return wege + "-fach satzassoziativ";
    }

    /** Kurzbeschreibung fuer Aufgabentexte. */
    String beschreibung() {
        return groesse(kapazitaet) + " (" + (kapazitaet / 4) + "~W\\\"orter), " + organisation() + ", Blockgr\\\"o\\ss{}e "
                + (blockBytes / 4) + (blockBytes == 4 ? "~Wort" : "~W\\\"orter") + " (" + blockBytes + "~Byte)";
    }
}
