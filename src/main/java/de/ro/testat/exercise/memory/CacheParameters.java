package de.ro.testat.exercise.memory;

import de.ro.testat.exercise.Auswahl;
import de.ro.testat.exercise.Exercise;
import de.ro.testat.latex.Tex;

import java.util.List;
import java.util.Random;

/**
 * "Cache-Parameter und Adresszerlegung": Aus Kapazitaet, Blockgroesse und Assoziativitaet
 * sind Bloecke, Saetze, die Bitbreiten von Tag/Satz/Block-Offset/Byte-Offset und der
 * Speicherbedarf zu bestimmen, dazu die Zerlegung einer Adresse (vorwaerts). Oder umgekehrt:
 * aus der Aufteilung der Adresse die Cache-Parameter (rueckwaerts).
 *
 * <p>Kontrolle: Bitbreiten ergeben 32, Saetze · Wege · Blockgroesse = Kapazitaet, und die
 * Zerlegung per Division (Modell) stimmt mit der Zerlegung per Bitmaske ueberein und ergibt
 * zusammengesetzt wieder die Adresse.
 */
public final class CacheParameters implements Exercise {

    private final String auswahl;

    public CacheParameters() {
        this("zufall");
    }

    /** @param auswahl "vorwaerts", "rueckwaerts" oder "zufall" */
    public CacheParameters(String auswahl) {
        this.auswahl = Auswahl.variante(auswahl, "cache-bits", "vorwaerts", "rueckwaerts");
    }

    @Override
    public String id() {
        return "cache-bits";
    }

    @Override
    public String describe() {
        return id() + "(" + auswahl + ")";
    }

    @Override
    public Part generate(Random rng) {
        boolean rueck = auswahl.equals("rueckwaerts") || (auswahl.equals("zufall") && rng.nextInt(3) == 0);
        int kap = 1024 << rng.nextInt(9);                 // 1 KiB .. 256 KiB
        int block = 4 << (1 + rng.nextInt(4));            // 8 .. 64 Byte
        int[] wahl = {1, 1, 2, 4, 8, 0};
        int n = wahl[rng.nextInt(wahl.length)];
        if (n == 0) {
            if (rueck) {
                n = 4;
            } else {                                      // voll assoziativ: nur kleine Caches (bis 4 KiB)
                kap = Math.min(kap, 4096);
                n = kap / block;
            }
        }
        boolean writeBack = rng.nextBoolean();
        CacheModel c = new CacheModel(kap, block, n);
        int adresse = rng.nextInt();
        pruefe(c, adresse);
        return rueck ? rueckwaerts(c, rng) : vorwaerts(c, writeBack, adresse);
    }

    private Part vorwaerts(CacheModel c, boolean writeBack, int adr) {
        String strategie = writeBack ? "Write-Back (ein Dirty-Bit pro Block)" : "Write-Through (kein Dirty-Bit)";
        StringBuilder a = new StringBuilder();
        a.append("Ein Cache hat folgende Parameter: Kapazit\\\"at ").append(CacheModel.groesse(c.kapazitaet)).append(" (nur Daten), ")
                .append(c.organisation()).append(", Blockgr\\\"o\\ss{}e ").append(c.blockBytes).append("~Byte, Schreibstrategie ")
                .append(strategie).append(", Ersetzung LRU. Adressen sind 32~Bit breit und adressieren Bytes; ein Wort hat 4~Byte.\n");
        a.append("\\begin{enumerate}[label=\\alph*)]\n");
        a.append("\\item Wie viele Bl\\\"ocke $B$ und wie viele S\\\"atze $S$ hat der Cache?\n");
        a.append("\\item Wie viele Bit der Adresse entfallen auf Byte-Offset, Block-Offset, Satzindex und Tag?\n");
        a.append("\\item Wie viele Bit Speicher braucht der Cache insgesamt, wenn jeder Block neben den Daten seinen Tag, ein "
                + "Valid-Bit").append(writeBack ? " und ein Dirty-Bit" : "").append(" speichert (LRU-Bits vernachl\\\"assigen)?\n");
        a.append("\\item Zerlegen Sie die Adresse \\texttt{").append(Tex.hex8(adr))
                .append("}: Tag (hexadezimal), Satzindex, Block-Offset und Byte-Offset (dezimal).\n");
        a.append("\\end{enumerate}\n");

        int bo = c.blockOffsetBits();
        int sb = c.satzBits();
        int tb = c.tagBits();
        int proBlock = 8 * c.blockBytes + tb + 1 + (writeBack ? 1 : 0);
        long gesamt = (long) proBlock * c.bloecke();
        StringBuilder l = new StringBuilder("\\begin{enumerate}[label=\\alph*)]\n");
        l.append("\\item $B = ").append(c.kapazitaet).append(" / ").append(c.blockBytes).append(" = ").append(c.bloecke())
                .append("$ Bl\\\"ocke, $S = B / N = ").append(c.bloecke()).append(" / ").append(c.wege).append(" = ")
                .append(c.saetze).append("$ S\\\"atze").append(c.saetze == 1 ? " (voll assoziativ: ein einziger Satz)" : "").append(".\n");
        l.append("\\item Byte-Offset 2~Bit, Block-Offset $\\log_2(").append(c.worteProBlock()).append(") = ").append(bo)
                .append("$~Bit, Satzindex $\\log_2(").append(c.saetze).append(") = ").append(sb).append("$~Bit, Tag $32 - ")
                .append(sb).append(" - ").append(bo).append(" - 2 = ").append(tb).append("$~Bit.\n");
        l.append("\\item Pro Block $").append(8 * c.blockBytes).append("$ (Daten) $+ ").append(tb).append("$ (Tag) $+ 1$ (V)")
                .append(writeBack ? " $+ 1$ (D)" : "").append(" $= ").append(proBlock).append("$~Bit; insgesamt $")
                .append(c.bloecke()).append(" \\cdot ").append(proBlock).append(" = ").append(MemTex.zahl(gesamt)).append("$~Bit.\n");
        int tag = c.tag(adr);
        l.append("\\item ").append(binaer(adr, tb, sb, bo)).append("\\\\\n");
        l.append("Tag = \\texttt{").append(Tex.hex(tag)).append("}, Satz = ").append(c.satz(adr)).append(", Block-Offset = ")
                .append(c.blockOffset(adr)).append(", Byte-Offset = ").append(CacheModel.byteOffset(adr)).append(".\n");
        l.append("\\end{enumerate}\n");

        String frage = CacheModel.groesse(c.kapazitaet).replace("~", " ") + ", N=" + c.wege + ", Block " + c.blockBytes + " B, "
                + (writeBack ? "WB" : "WT") + ", Adresse " + Tex.hex8(adr);
        String antwort = "B=" + c.bloecke() + " S=" + c.saetze + " Tag/Satz/BO/Byte=" + tb + "/" + sb + "/" + bo + "/2, " + gesamt
                + " Bit, Tag " + Tex.hex(tag) + " Satz " + c.satz(adr) + " BO " + c.blockOffset(adr) + " Byte " + CacheModel.byteOffset(adr);
        return new Part("Cache-Parameter und Adresszerlegung", a.toString(), l.toString(), frage, antwort);
    }

    private Part rueckwaerts(CacheModel c, Random rng) {
        int bo = c.blockOffsetBits();
        int sb = c.satzBits();
        int tb = c.tagBits();
        StringBuilder a = new StringBuilder();
        a.append("Ein ").append(c.organisation()).append("er Cache (").append(c.wege == 1 ? "$N = 1$" : "$N = " + c.wege + "$")
                .append(") zerlegt 32-Bit-Byteadressen so:\n");
        a.append(MemTex.tabelle("|c|c|c|c|", List.of("Tag", "Satzindex", "Block-Offset", "Byte-Offset"),
                List.of(List.of(tb + " Bit", sb + " Bit", bo + " Bit", "2 Bit")), false));
        a.append("\\begin{enumerate}[label=\\alph*)]\n");
        a.append("\\item Wie gro\\ss{} ist ein Block (in W\\\"ortern und in Byte)?\n");
        a.append("\\item Wie viele S\\\"atze und wie viele Bl\\\"ocke hat der Cache?\n");
        a.append("\\item Wie gro\\ss{} ist die Kapazit\\\"at (nur Daten)?\n");
        a.append("\\item Wie sieht die Aufteilung aus, wenn bei gleicher Kapazit\\\"at und Blockgr\\\"o\\ss{}e die "
                + "Assoziativit\\\"at verdoppelt wird ($N = ").append(2 * c.wege).append("$)?\n");
        a.append("\\end{enumerate}\n");

        CacheModel d = new CacheModel(c.kapazitaet, c.blockBytes, 2 * c.wege);
        pruefe(d, rng.nextInt());
        StringBuilder l = new StringBuilder("\\begin{enumerate}[label=\\alph*)]\n");
        l.append("\\item $2^{").append(bo).append("} = ").append(c.worteProBlock()).append("$ W\\\"orter $= ").append(c.blockBytes)
                .append("$~Byte.\n");
        l.append("\\item $S = 2^{").append(sb).append("} = ").append(c.saetze).append("$ S\\\"atze, $B = S \\cdot N = ")
                .append(c.saetze).append(" \\cdot ").append(c.wege).append(" = ").append(c.bloecke()).append("$ Bl\\\"ocke.\n");
        l.append("\\item $").append(c.bloecke()).append(" \\cdot ").append(c.blockBytes).append("$~Byte $= ")
                .append(MemTex.zahl(c.kapazitaet)).append("$~Byte $=$ ").append(CacheModel.groesse(c.kapazitaet)).append(".\n");
        l.append("\\item Halb so viele S\\\"atze: Satzindex ").append(d.satzBits()).append("~Bit, Tag ").append(d.tagBits())
                .append("~Bit, Block-Offset und Byte-Offset unver\\\"andert (").append(bo).append(" und 2~Bit).\n");
        l.append("\\end{enumerate}\n");
        String frage = "Tag/Satz/BO = " + tb + "/" + sb + "/" + bo + ", N=" + c.wege;
        String antwort = "Block " + c.blockBytes + " B, S=" + c.saetze + ", B=" + c.bloecke() + ", " + c.kapazitaet + " B; 2N: Satz "
                + d.satzBits() + " Tag " + d.tagBits();
        return new Part("Cache-Parameter aus der Adresszerlegung", a.toString(), l.toString(), frage, antwort);
    }

    /** Adresse binaer, Felder durch | getrennt. */
    private static String binaer(int adr, int tb, int sb, int bo) {
        String bits = String.format("%32s", Integer.toBinaryString(adr)).replace(' ', '0');
        StringBuilder sbl = new StringBuilder("\\texttt{");
        int pos = 0;
        int[] felder = {tb, sb, bo, 2};
        for (int i = 0; i < felder.length; i++) {
            if (felder[i] == 0) {
                continue;
            }
            if (pos > 0) {
                sbl.append("\\,|\\,");
            }
            sbl.append(bits, pos, pos + felder[i]);
            pos += felder[i];
        }
        return sbl.append("} (Tag | Satz | Block-Offset | Byte-Offset)").toString();
    }

    /** Zerlegung per Division (Modell) gegen Zerlegung per Bitmaske. */
    static void pruefe(CacheModel c, int adr) {
        int bo = c.blockOffsetBits();
        int sb = c.satzBits();
        int tb = c.tagBits();
        if (tb + sb + bo + 2 != 32 || c.saetze * c.wege * c.blockBytes != c.kapazitaet) {
            throw new IllegalStateException("Interner Fehler: Bitbreiten");
        }
        int byteOff = adr & 3;
        int blockOff = (adr >>> 2) & ((1 << bo) - 1);
        int satz = sb == 0 ? 0 : (adr >>> (2 + bo)) & ((1 << sb) - 1);
        int tag = (adr >>> (2 + bo + sb));
        if (tag != c.tag(adr) || satz != c.satz(adr) || blockOff != c.blockOffset(adr)
                || byteOff != CacheModel.byteOffset(adr)) {
            throw new IllegalStateException("Interner Fehler: Zerlegung von " + Tex.hex8(adr));
        }
        int zusammen = (int) (((((long) tag << sb | satz) << bo | blockOff) << 2) | byteOff);
        if (zusammen != adr) {
            throw new IllegalStateException("Interner Fehler: Zusammensetzen von " + Tex.hex8(adr));
        }
    }
}
