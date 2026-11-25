package de.ro.testat.probelauf;

import de.ro.testat.probelauf.RiscvParser.Befehl;
import de.ro.testat.probelauf.RiscvParser.Programm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Fuehrt ein eingelesenes RISC-V-Programm aus. Startwerte wie in RARS: alle Register 0,
 * sp = 0x7FFFEFFC, gp = 0x10008000, ra = 0 (ret am Ende beendet das Programm).
 * Ende: letzte Zeile erreicht, ret/Sprung nach Adresse 0, ecall 10/93 oder Schrittlimit.
 */
public final class RiscvMaschine {

    public static final int SP_START = 0x7FFF_EFFC;
    public static final int GP_START = 0x1000_8000;

    /** Ergebnis eines Laufs. */
    public record Ergebnis(int[] start, int[] ende, Map<Integer, Integer> geschrieben, String ausgabe,
                           int schritte, String endeGrund, boolean abgebrochen, List<Meldung> meldungen,
                           List<String> trace) {
    }

    private final Programm p;
    private final int[] x = new int[32];
    private final Map<Integer, Byte> mem = new HashMap<>();
    private final Set<Integer> geschriebeneWorte = new LinkedHashSet<>();
    private final StringBuilder ausgabe = new StringBuilder();
    private final List<Meldung> meldungen = new ArrayList<>();
    private final List<String> trace = new ArrayList<>();
    private final int traceMax;
    private int hinweiseLesen;

    private RiscvMaschine(Programm p, int traceMax) {
        this.p = p;
        this.traceMax = traceMax;
        mem.putAll(p.daten());
        x[2] = SP_START;
        x[3] = GP_START;
    }

    public static Ergebnis run(Programm p, int maxSchritte, int traceMax) {
        return new RiscvMaschine(p, traceMax).lauf(maxSchritte);
    }

    /** Mit vorgegebenen Startwerten einzelner Register (Registernummer -> Wert). */
    public static Ergebnis run(Programm p, int maxSchritte, Map<Integer, Integer> startwerte) {
        RiscvMaschine m = new RiscvMaschine(p, 0);
        startwerte.forEach((r, v) -> m.x[r] = v);
        return m.lauf(maxSchritte);
    }

    /**
     * Haelt an, bevor der Befehl mit Index befehl zum mal-ten Mal ausgefuehrt wird (Haltepunkt).
     * endeGrund ist dann "Haltepunkt"; ende() und geschrieben() zeigen den Zustand in diesem Moment.
     */
    public static Ergebnis runBis(Programm p, int maxSchritte, Map<Integer, Integer> startwerte, int befehl, int mal) {
        RiscvMaschine m = new RiscvMaschine(p, 0);
        startwerte.forEach((r, v) -> m.x[r] = v);
        m.halt = befehl;
        m.haltMal = mal;
        return m.lauf(maxSchritte);
    }

    private int halt = -1;
    private int haltMal;
    private java.util.Random streng;

    /**
     * Strenger Modus fuer die Pruefung der Aufrufkonvention: Bei jedem Ruecksprung
     * (jalr zero, 0(ra)) werden alle Register, die eine Funktion nicht erhalten muss
     * (t0-t6, a1-a7), mit Zufallswerten ueberschrieben. Ein Aufrufer, der sich nach einem
     * Aufruf noch auf diese Register verlaesst, liefert dann ein falsches Ergebnis.
     */
    public static Ergebnis runStreng(Programm p, int maxSchritte, Map<Integer, Integer> startwerte, long seed) {
        RiscvMaschine m = new RiscvMaschine(p, 0);
        startwerte.forEach((r, v) -> m.x[r] = v);
        m.streng = new java.util.Random(seed);
        return m.lauf(maxSchritte);
    }

    private List<int[]> zugriffe;

    /**
     * Fuehrt das Programm aus und liefert alle Datenspeicherzugriffe in Reihenfolge als
     * {Adresse, 0 = lesen / 1 = schreiben} (fuer Cache-Aufgaben).
     */
    public static List<int[]> speicherzugriffe(Programm p, int maxSchritte) {
        RiscvMaschine m = new RiscvMaschine(p, 0);
        m.zugriffe = new ArrayList<>();
        Ergebnis e = m.lauf(maxSchritte);
        if (e.abgebrochen()) {
            throw new IllegalStateException("Programm bricht ab: " + e.meldungen());
        }
        return m.zugriffe;
    }

    private static final int[] FLUECHTIG = {5, 6, 7, 28, 29, 30, 31, 11, 12, 13, 14, 15, 16, 17};

    private Ergebnis lauf(int maxSchritte) {
        int[] start = x.clone();
        List<Befehl> b = p.befehle();
        int pc = 0;
        int schritte = 0;
        String grund = "Programmende erreicht (nach dem letzten Befehl)";
        boolean abbruch = false;
        while (pc >= 0 && pc < b.size()) {
            if (schritte >= maxSchritte) {
                grund = "nach " + maxSchritte + " Schritten abgebrochen - vermutlich Endlosschleife";
                abbruch = true;
                meldungen.add(Meldung.fehler(b.get(pc).zeile(), "Schrittlimit erreicht (Endlosschleife?), zuletzt hier"));
                break;
            }
            if (pc == halt && --haltMal == 0) {
                grund = "Haltepunkt";
                break;
            }
            Befehl i = b.get(pc);
            aktuelleZeile = i.zeile();
            schritte++;
            int[] vorher = traceMax > trace.size() ? x.clone() : null;
            int next;
            try {
                next = schritt(i, pc);
            } catch (Laufzeitfehler f) {
                meldungen.add(Meldung.fehler(i.zeile(), f.getMessage()));
                grund = "Laufzeitfehler in Zeile " + i.zeile();
                abbruch = true;
                break;
            }
            if (vorher != null) {
                trace.add(traceZeile(i, pc, vorher));
            }
            if (next == ENDE_ECALL) {
                grund = "Programmende durch ecall (Exit)";
                break;
            }
            if (next == ENDE_RET) {
                grund = "Programmende durch Sprung nach Adresse 0 (ret aus dem Hauptprogramm)";
                break;
            }
            pc = next;
        }
        List<int[]> aufzeichnung = zugriffe;
        zugriffe = null;
        Map<Integer, Integer> worte = new TreeMap<>();
        for (int adr : geschriebeneWorte) {
            worte.put(adr, lade(adr, 4, false));
        }
        zugriffe = aufzeichnung;
        return new Ergebnis(start, x.clone(), worte, ausgabe.toString(), schritte, grund, abbruch, meldungen, trace);
    }

    private static final int ENDE_ECALL = -2;
    private static final int ENDE_RET = -3;

    static final class Laufzeitfehler extends RuntimeException {
        Laufzeitfehler(String text) {
            super(text);
        }
    }

    private static int adresse(int index) {
        return RiscvParser.TEXT_BASE + 4 * index;
    }

    /** Fuehrt einen Befehl aus, liefert den naechsten Befehlsindex. */
    private int schritt(Befehl i, int pc) {
        int a = x[i.rs1()], c = x[i.rs2()], imm = i.imm();
        int next = pc + 1;
        switch (i.op()) {
            case ADD -> set(i.rd(), a + c);
            case SUB -> set(i.rd(), a - c);
            case AND -> set(i.rd(), a & c);
            case OR -> set(i.rd(), a | c);
            case XOR -> set(i.rd(), a ^ c);
            case SLL -> set(i.rd(), a << (c & 31));
            case SRL -> set(i.rd(), a >>> (c & 31));
            case SRA -> set(i.rd(), a >> (c & 31));
            case SLT -> set(i.rd(), a < c ? 1 : 0);
            case SLTU -> set(i.rd(), Integer.compareUnsigned(a, c) < 0 ? 1 : 0);
            case MUL -> set(i.rd(), a * c);
            case MULH -> set(i.rd(), (int) (((long) a * (long) c) >> 32));
            case MULHU -> set(i.rd(), (int) ((Integer.toUnsignedLong(a) * Integer.toUnsignedLong(c)) >>> 32));
            case MULHSU -> set(i.rd(), (int) (((long) a * Integer.toUnsignedLong(c)) >> 32));
            case DIV -> {
                if (c == 0) {
                    hinweisNullDivision(i);
                }
                set(i.rd(), c == 0 ? -1 : (a == Integer.MIN_VALUE && c == -1 ? a : a / c));
            }
            case DIVU -> {
                if (c == 0) {
                    hinweisNullDivision(i);
                }
                set(i.rd(), c == 0 ? -1 : Integer.divideUnsigned(a, c));
            }
            case REM -> {
                if (c == 0) {
                    hinweisNullDivision(i);
                }
                set(i.rd(), c == 0 ? a : (a == Integer.MIN_VALUE && c == -1 ? 0 : a % c));
            }
            case REMU -> {
                if (c == 0) {
                    hinweisNullDivision(i);
                }
                set(i.rd(), c == 0 ? a : Integer.remainderUnsigned(a, c));
            }
            case ADDI -> set(i.rd(), a + imm);
            case ANDI -> set(i.rd(), a & imm);
            case ORI -> set(i.rd(), a | imm);
            case XORI -> set(i.rd(), a ^ imm);
            case SLTI -> set(i.rd(), a < imm ? 1 : 0);
            case SLTIU -> set(i.rd(), Integer.compareUnsigned(a, imm) < 0 ? 1 : 0);
            case SLLI -> set(i.rd(), a << imm);
            case SRLI -> set(i.rd(), a >>> imm);
            case SRAI -> set(i.rd(), a >> imm);
            case LUI -> set(i.rd(), imm << 12);
            case AUIPC -> set(i.rd(), adresse(pc) + (imm << 12));
            case LI, LA -> set(i.rd(), imm);
            case LB -> set(i.rd(), lade(a + imm, 1, true));
            case LBU -> set(i.rd(), lade(a + imm, 1, false));
            case LH -> set(i.rd(), lade(a + imm, 2, true));
            case LHU -> set(i.rd(), lade(a + imm, 2, false));
            case LW -> set(i.rd(), lade(a + imm, 4, false));
            case SB -> speichere(a + imm, c, 1);
            case SH -> speichere(a + imm, c, 2);
            case SW -> speichere(a + imm, c, 4);
            case BEQ -> next = a == c ? imm : next;
            case BNE -> next = a != c ? imm : next;
            case BLT -> next = a < c ? imm : next;
            case BGE -> next = a >= c ? imm : next;
            case BLTU -> next = Integer.compareUnsigned(a, c) < 0 ? imm : next;
            case BGEU -> next = Integer.compareUnsigned(a, c) >= 0 ? imm : next;
            case JAL -> {
                set(i.rd(), adresse(pc + 1));
                next = imm;
            }
            case JALR -> {
                int ziel = (a + imm) & ~1;
                set(i.rd(), adresse(pc + 1));
                next = sprungziel(ziel);
                if (streng != null && i.rd() == 0 && i.rs1() == 1) {
                    for (int r : FLUECHTIG) {
                        x[r] = streng.nextInt();
                    }
                }
            }
            case ECALL -> next = ecall(next);
        }
        return next;
    }

    private int sprungziel(int ziel) {
        if (ziel == 0) {
            return ENDE_RET;
        }
        int off = ziel - RiscvParser.TEXT_BASE;
        if (off < 0 || off % 4 != 0 || off / 4 > p.befehle().size()) {
            throw new Laufzeitfehler(String.format("Sprung an ungueltige Adresse 0x%08X (kein Befehl dort)", ziel));
        }
        return off / 4;
    }

    private int ecall(int next) {
        int a7 = x[17], a0 = x[10];
        switch (a7) {
            case 1 -> ausgabe.append(a0);
            case 4 -> {
                int adr = a0;
                for (int n = 0; n < 10_000; n++) {
                    byte ch = mem.getOrDefault(adr++, (byte) 0);
                    if (ch == 0) {
                        break;
                    }
                    ausgabe.append((char) (ch & 0xFF));
                }
            }
            case 11 -> ausgabe.append((char) (a0 & 0xFF));
            case 34 -> ausgabe.append(String.format("0x%08x", a0));
            case 35 -> ausgabe.append(Probelauf.rechts(Integer.toBinaryString(a0), 32).replace(' ', '0'));
            case 36 -> ausgabe.append(Integer.toUnsignedString(a0));
            case 10, 93 -> {
                return ENDE_ECALL;
            }
            default -> throw new Laufzeitfehler("ecall mit a7 = " + a7 + " wird nicht unterstuetzt "
                    + "(moeglich: 1 int ausgeben, 4 String, 11 Zeichen, 34 hex, 10 Ende)");
        }
        return next;
    }

    private void set(int rd, int v) {
        if (rd != 0) {
            x[rd] = v;
        }
    }

    private int lade(int adr, int bytes, boolean vorzeichen) {
        if (adr % bytes != 0) {
            throw new Laufzeitfehler(String.format("Nicht ausgerichteter Zugriff: Adresse 0x%08X ist kein Vielfaches von %d", adr, bytes));
        }
        if (zugriffe != null) {
            zugriffe.add(new int[]{adr, 0});
        }
        int v = 0;
        boolean bekannt = false;
        for (int k = 0; k < bytes; k++) {
            Byte by = mem.get(adr + k);
            bekannt |= by != null;
            v |= ((by == null ? 0 : by) & 0xFF) << (8 * k);
        }
        if (!bekannt && hinweiseLesen++ < 3 ) {
            hinweisLesen(adr);
        }
        if (vorzeichen && bytes < 4) {
            int s = 32 - 8 * bytes;
            v = (v << s) >> s;
        }
        return v;
    }

    private int aktuelleZeile;

    private void hinweisLesen(int adr) {
        meldungen.add(Meldung.hinweis(aktuelleZeile, String.format("Lesen von Adresse 0x%08X, an die nie geschrieben wurde (Wert 0)", adr)));
    }

    private void hinweisNullDivision(Befehl i) {
        meldungen.add(Meldung.hinweis(i.zeile(), "Division durch 0 (RISC-V liefert dabei -1 bzw. den Dividenden als Rest)"));
    }

    private void speichere(int adr, int v, int bytes) {
        if (adr % bytes != 0) {
            throw new Laufzeitfehler(String.format("Nicht ausgerichteter Zugriff: Adresse 0x%08X ist kein Vielfaches von %d", adr, bytes));
        }
        if (zugriffe != null) {
            zugriffe.add(new int[]{adr, 1});
        }
        for (int k = 0; k < bytes; k++) {
            mem.put(adr + k, (byte) (v >>> (8 * k)));
        }
        geschriebeneWorte.add(adr & ~3);
    }

    private String traceZeile(Befehl i, int pc, int[] vorher) {
        StringBuilder sb = new StringBuilder(Probelauf.hex8(adresse(pc)) + "  Z." + Probelauf.links(Integer.toString(i.zeile()), 3)
                + " " + Probelauf.links(i.text(), 28));
        for (int r = 1; r < 32; r++) {
            if (vorher[r] != x[r]) {
                sb.append("  ").append(RiscvParser.ABI[r]).append(": ").append(vorher[r]).append(" -> ").append(x[r]);
            }
        }
        return sb.toString();
    }
}
