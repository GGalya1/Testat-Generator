package de.ro.testat.probelauf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Liest RISC-V-Assembler (RV32I + M-Extension, uebliche Pseudobefehle, .text/.data) wie in RARS.
 * Sammelt alle Fehler mit Zeilennummer, statt beim ersten abzubrechen.
 */
public final class RiscvParser {

    public static final int TEXT_BASE = 0x0040_0000;
    public static final int DATA_BASE = 0x1001_0000;

    enum Op {
        ADD, SUB, AND, OR, XOR, SLL, SRL, SRA, SLT, SLTU,
        MUL, MULH, MULHU, MULHSU, DIV, DIVU, REM, REMU,
        ADDI, ANDI, ORI, XORI, SLTI, SLTIU, SLLI, SRLI, SRAI,
        LUI, AUIPC, LB, LH, LW, LBU, LHU, SB, SH, SW,
        BEQ, BNE, BLT, BGE, BLTU, BGEU, JAL, JALR, ECALL, LI, LA
    }

    /** Ein ausfuehrbarer Befehl. imm = Direktwert, bei Spruengen nach dem Aufloesen der Befehlsindex des Ziels. */
    record Befehl(int zeile, String text, Op op, int rd, int rs1, int rs2, int imm, String marke) {
    }

    /**
     * Ergebnis des Einlesens. adressen: Adresse jedes Befehls wie in RARS (li/la/call/tail koennen dort
     * zwei echte Befehle sein), dazu am Ende die Adresse direkt hinter dem letzten Befehl.
     */
    public record Programm(List<Befehl> befehle, Map<Integer, Byte> daten, Map<String, Integer> marken,
                           List<Meldung> meldungen, List<Integer> adressen) {

        /** Adresse des Befehls mit diesem Index (index = Anzahl Befehle: Programmende). */
        public int adresse(int index) {
            return adressen.get(index);
        }

        /** Befehlsindex zu einer Adresse, -1 wenn dort kein Befehl beginnt (auch mitten in einem Pseudobefehl). */
        public int index(int adresse) {
            int i = java.util.Collections.binarySearch(adressen, adresse);
            return i < 0 ? -1 : i;
        }

        public boolean fehlerfrei() {
            return meldungen.stream().noneMatch(m -> m.art() == Meldung.Art.FEHLER);
        }

        /** Index des Befehls, der in dieser Quelltextzeile steht, sonst -1. */
        public int befehlInZeile(int zeile) {
            for (int i = 0; i < befehle.size(); i++) {
                if (befehle.get(i).zeile() == zeile) {
                    return i;
                }
            }
            return -1;
        }
    }

    static final String[] ABI = {"zero", "ra", "sp", "gp", "tp", "t0", "t1", "t2", "s0", "s1",
            "a0", "a1", "a2", "a3", "a4", "a5", "a6", "a7", "s2", "s3", "s4", "s5", "s6", "s7",
            "s8", "s9", "s10", "s11", "t3", "t4", "t5", "t6"};

    private static final Map<String, Integer> REG = new HashMap<>();

    static {
        for (int i = 0; i < 32; i++) {
            REG.put("x" + i, i);
            REG.put(ABI[i], i);
        }
        REG.put("fp", 8);
    }

    private static final Pattern LABEL = Pattern.compile("^\\s*([A-Za-z_.$][\\w.$]*)\\s*:");
    private static final Pattern MEM = Pattern.compile("^(.*)\\((\\w+)\\)$");

    private final List<Meldung> meldungen = new ArrayList<>();
    private final List<Befehl> befehle = new ArrayList<>();
    private final Map<Integer, Byte> daten = new LinkedHashMap<>();
    private final Map<String, Integer> marken = new LinkedHashMap<>();      // Name -> Adresse
    private final Map<String, Integer> markenZeile = new HashMap<>();
    private final List<Integer> adressen = new ArrayList<>();
    private int datenAdresse = DATA_BASE;
    private int zeile;

    private RiscvParser() {
    }

    public static Programm parse(String quelltext) {
        RiscvParser p = new RiscvParser();
        p.lies(quelltext);
        p.meldungen.sort(java.util.Comparator.comparingInt(Meldung::zeile));
        return new Programm(List.copyOf(p.befehle), p.daten, p.marken, p.meldungen, List.copyOf(p.adressen));
    }

    private void lies(String quelltext) {
        String[] zeilen = quelltext.replace("\r", "").split("\n", -1);
        boolean text = true;
        // 1. Durchgang: Marken (Befehle zaehlen, Daten anlegen)
        List<String[]> rest = new ArrayList<>();   // {zeilennummer, inhalt}
        int textAdresse = TEXT_BASE;
        for (int i = 0; i < zeilen.length; i++) {
            zeile = i + 1;
            String z = ohneKommentar(zeilen[i]);
            Matcher m;
            while ((m = LABEL.matcher(z)).find()) {
                String name = m.group(1);
                if (marken.containsKey(name)) {
                    fehler("Marke '" + name + "' ist doppelt definiert (zuerst in Zeile " + markenZeile.get(name) + ")");
                } else {
                    marken.put(name, text ? textAdresse : datenAdresse);
                    markenZeile.put(name, zeile);
                }
                z = z.substring(m.end());
            }
            z = z.trim();
            if (z.isEmpty()) {
                continue;
            }
            if (z.startsWith(".")) {
                String dir = z.split("\\s+")[0].toLowerCase(Locale.ROOT);
                if (dir.equals(".text")) {
                    text = true;
                    continue;
                }
                if (dir.equals(".data")) {
                    text = false;
                    continue;
                }
                if (!text) {
                    datenDirektive(dir, z.substring(dir.length()).trim());
                } else if (!dir.equals(".globl") && !dir.equals(".global") && !dir.equals(".align")) {
                    hinweis("Direktive " + dir + " im .text-Bereich wird ignoriert");
                }
                continue;
            }
            if (!text) {
                fehler("Befehl im .data-Bereich; vorher .text schreiben");
                continue;
            }
            rest.add(new String[]{Integer.toString(zeile), z});
            adressen.add(textAdresse);
            textAdresse += 4 * groesse(z);
        }
        adressen.add(textAdresse); // Programmende
        // 2. Durchgang: Befehle
        for (String[] r : rest) {
            zeile = Integer.parseInt(r[0]);
            try {
                befehl(r[1]);
            } catch (Fehler f) {
                fehler(f.getMessage());
                befehle.add(new Befehl(zeile, r[1], Op.ADDI, 0, 0, 0, 0, null)); // Platzhalter, damit Indizes stimmen
            }
        }
        // Sprungziele aufloesen
        for (int i = 0; i < befehle.size(); i++) {
            Befehl b = befehle.get(i);
            if (b.marke() == null) {
                continue;
            }
            Integer adr = marken.get(b.marke());
            zeile = b.zeile();
            if (adr == null) {
                fehler("Marke '" + b.marke() + "' ist nicht definiert");
                continue;
            }
            boolean sprung = b.op() != Op.LA;
            int ziel = sprung ? java.util.Collections.binarySearch(adressen, adr) : adr;
            if (sprung && ziel < 0) {
                fehler("'" + b.marke() + "' ist eine Datenmarke und kein Sprungziel");
                continue;
            }
            befehle.set(i, new Befehl(b.zeile(), b.text(), b.op(), b.rd(), b.rs1(), b.rs2(), ziel, b.marke()));
        }
    }

    /**
     * Wie viele echte Befehle RARS fuer diese Zeile erzeugt: la, call, tail immer zwei (auipc + ...),
     * li zwei, wenn der Wert nicht in 12 Bit passt (lui + addi). Danach richten sich die Adressen.
     */
    private static int groesse(String z) {
        String[] teile = z.trim().split("[,\\s]+");
        switch (teile[0].toLowerCase(Locale.ROOT)) {
            case "la", "call", "tail":
                return 2;
            case "li":
                if (teile.length != 3) {
                    return 1;
                }
                try {
                    int v = (int) zahl(teile[2], Integer.MIN_VALUE, 0xFFFF_FFFFL);
                    return v >= -2048 && v <= 2047 ? 1 : 2;
                } catch (Fehler f) {
                    return 1; // Fehler meldet der 2. Durchgang
                }
            default:
                return 1;
        }
    }

    private static String ohneKommentar(String z) {
        boolean str = false;
        for (int i = 0; i < z.length(); i++) {
            char c = z.charAt(i);
            if (c == '"' && (i == 0 || z.charAt(i - 1) != '\\')) {
                str = !str;
            }
            if (!str && (c == '#' || (c == '/' && i + 1 < z.length() && z.charAt(i + 1) == '/'))) {
                return z.substring(0, i);
            }
        }
        return z;
    }

    // ------------------------------------------------------------------
    // .data
    // ------------------------------------------------------------------

    private void datenDirektive(String dir, String arg) {
        try {
            switch (dir) {
                case ".word" -> {
                    ausrichten(4);
                    for (String w : liste(arg)) {
                        schreibe(datenAdresse, (int) zahl(w, Integer.MIN_VALUE, 0xFFFF_FFFFL), 4);
                        datenAdresse += 4;
                    }
                }
                case ".half" -> {
                    ausrichten(2);
                    for (String w : liste(arg)) {
                        schreibe(datenAdresse, (int) zahl(w, -32768, 65535), 2);
                        datenAdresse += 2;
                    }
                }
                case ".byte" -> {
                    for (String w : liste(arg)) {
                        schreibe(datenAdresse, (int) zahl(w, -128, 255), 1);
                        datenAdresse += 1;
                    }
                }
                case ".space", ".zero" -> datenAdresse += (int) zahl(arg, 0, 1 << 20);
                case ".align" -> ausrichten(1 << (int) zahl(arg, 0, 12));
                case ".asciz", ".string", ".ascii" -> {
                    String s = zeichenkette(arg);
                    for (char c : s.toCharArray()) {
                        schreibe(datenAdresse++, c, 1);
                    }
                    if (!dir.equals(".ascii")) {
                        schreibe(datenAdresse++, 0, 1);
                    }
                }
                case ".globl", ".global", ".eqv" -> {
                }
                default -> hinweis("Direktive " + dir + " wird nicht unterstuetzt und ignoriert");
            }
        } catch (Fehler f) {
            fehler(f.getMessage());
        }
    }

    private void ausrichten(int n) {
        while (datenAdresse % n != 0) {
            datenAdresse++;
        }
    }

    private void schreibe(int adr, int wert, int bytes) {
        for (int i = 0; i < bytes; i++) {
            daten.put(adr + i, (byte) (wert >>> (8 * i)));
        }
    }

    private static List<String> liste(String arg) {
        List<String> out = new ArrayList<>();
        for (String s : arg.split(",")) {
            if (!s.isBlank()) {
                out.add(s.trim());
            }
        }
        if (out.isEmpty()) {
            throw new Fehler("Werte fehlen");
        }
        return out;
    }

    static String zeichenkette(String arg) {
        String a = arg.trim();
        if (a.length() < 2 || !a.startsWith("\"") || !a.endsWith("\"")) {
            throw new Fehler("Zeichenkette in \"...\" erwartet");
        }
        return a.substring(1, a.length() - 1).replace("\\n", "\n").replace("\\t", "\t")
                .replace("\\\"", "\"").replace("\\0", "\0").replace("\\\\", "\\");
    }

    // ------------------------------------------------------------------
    // Befehle
    // ------------------------------------------------------------------

    /** Interner Fehler mit Text fuer die Meldung. */
    static final class Fehler extends RuntimeException {
        Fehler(String text) {
            super(text);
        }
    }

    private void befehl(String z) {
        String norm = z.replaceAll("\\s*\\(\\s*", "(").replaceAll("\\s*\\)", ")");
        String[] teile = norm.trim().split("[,\\s]+");
        String mn = teile[0].toLowerCase(Locale.ROOT);
        String[] a = new String[teile.length - 1];
        System.arraycopy(teile, 1, a, 0, a.length);
        String t = z.trim().replaceAll("\\s+", " ");
        switch (mn) {
            // R-Typ
            case "add", "sub", "and", "or", "xor", "sll", "srl", "sra", "slt", "sltu",
                 "mul", "mulh", "mulhu", "mulhsu", "div", "divu", "rem", "remu" -> {
                anzahl(mn, a, 3, "rd, rs1, rs2");
                add(t, Op.valueOf(mn.toUpperCase(Locale.ROOT)), reg(a[0]), reg(a[1]), reg(a[2]), 0, null);
            }
            // I-Typ
            case "addi", "andi", "ori", "xori", "slti", "sltiu" -> {
                anzahl(mn, a, 3, "rd, rs1, imm");
                add(t, Op.valueOf(mn.toUpperCase(Locale.ROOT)), reg(a[0]), reg(a[1]), 0, imm12(a[2]), null);
            }
            case "slli", "srli", "srai" -> {
                anzahl(mn, a, 3, "rd, rs1, shamt");
                add(t, Op.valueOf(mn.toUpperCase(Locale.ROOT)), reg(a[0]), reg(a[1]), 0, (int) zahl(a[2], 0, 31), null);
            }
            case "lui", "auipc" -> {
                anzahl(mn, a, 2, "rd, imm (0..0xFFFFF)");
                add(t, Op.valueOf(mn.toUpperCase(Locale.ROOT)), reg(a[0]), 0, 0, (int) zahl(a[1], 0, 0xFFFFF), null);
            }
            case "lb", "lh", "lw", "lbu", "lhu" -> {
                anzahl(mn, a, 2, "rd, offset(rs1)");
                int[] m = speicher(a[1]);
                add(t, Op.valueOf(mn.toUpperCase(Locale.ROOT)), reg(a[0]), m[1], 0, m[0], null);
            }
            case "sb", "sh", "sw" -> {
                anzahl(mn, a, 2, "rs2, offset(rs1)");
                int[] m = speicher(a[1]);
                add(t, Op.valueOf(mn.toUpperCase(Locale.ROOT)), 0, m[1], reg(a[0]), m[0], null);
            }
            // Verzweigungen
            case "beq", "bne", "blt", "bge", "bltu", "bgeu" -> {
                anzahl(mn, a, 3, "rs1, rs2, Marke");
                add(t, Op.valueOf(mn.toUpperCase(Locale.ROOT)), 0, reg(a[0]), reg(a[1]), 0, marke(a[2]));
            }
            case "bgt", "ble", "bgtu", "bleu" -> { // vertauschte Operanden
                anzahl(mn, a, 3, "rs1, rs2, Marke");
                Op op = switch (mn) {
                    case "bgt" -> Op.BLT;
                    case "ble" -> Op.BGE;
                    case "bgtu" -> Op.BLTU;
                    default -> Op.BGEU;
                };
                add(t, op, 0, reg(a[1]), reg(a[0]), 0, marke(a[2]));
            }
            case "beqz", "bnez", "bltz", "bgez" -> {
                anzahl(mn, a, 2, "rs, Marke");
                Op op = switch (mn) {
                    case "beqz" -> Op.BEQ;
                    case "bnez" -> Op.BNE;
                    case "bltz" -> Op.BLT;
                    default -> Op.BGE;
                };
                add(t, op, 0, reg(a[0]), 0, 0, marke(a[1]));
            }
            case "bgtz", "blez" -> {
                anzahl(mn, a, 2, "rs, Marke");
                add(t, mn.equals("bgtz") ? Op.BLT : Op.BGE, 0, 0, reg(a[0]), 0, marke(a[1]));
            }
            // Spruenge
            case "jal" -> {
                if (a.length == 1) {
                    add(t, Op.JAL, 1, 0, 0, 0, marke(a[0]));
                } else {
                    anzahl(mn, a, 2, "rd, Marke");
                    add(t, Op.JAL, reg(a[0]), 0, 0, 0, marke(a[1]));
                }
            }
            case "j" -> {
                anzahl(mn, a, 1, "Marke");
                add(t, Op.JAL, 0, 0, 0, 0, marke(a[0]));
            }
            case "call" -> {
                anzahl(mn, a, 1, "Marke");
                add(t, Op.JAL, 1, 0, 0, 0, marke(a[0]));
            }
            case "tail" -> {
                anzahl(mn, a, 1, "Marke");
                add(t, Op.JAL, 0, 0, 0, 0, marke(a[0]));
            }
            case "jalr" -> {
                if (a.length == 1) {
                    add(t, Op.JALR, 1, reg(a[0]), 0, 0, null);
                } else if (a.length == 2) {
                    int[] m = speicher(a[1]);
                    add(t, Op.JALR, reg(a[0]), m[1], 0, m[0], null);
                } else {
                    anzahl(mn, a, 3, "rd, rs1, imm");
                    add(t, Op.JALR, reg(a[0]), reg(a[1]), 0, imm12(a[2]), null);
                }
            }
            case "jr" -> {
                anzahl(mn, a, 1, "rs");
                add(t, Op.JALR, 0, reg(a[0]), 0, 0, null);
            }
            case "ret" -> {
                anzahl(mn, a, 0, "");
                add(t, Op.JALR, 0, 1, 0, 0, null);
            }
            // weitere Pseudobefehle
            case "li" -> {
                anzahl(mn, a, 2, "rd, Wert");
                add(t, Op.LI, reg(a[0]), 0, 0, (int) zahl(a[1], Integer.MIN_VALUE, 0xFFFF_FFFFL), null);
            }
            case "la" -> {
                anzahl(mn, a, 2, "rd, Marke");
                add(t, Op.LA, reg(a[0]), 0, 0, 0, marke(a[1]));
            }
            case "mv" -> {
                anzahl(mn, a, 2, "rd, rs");
                add(t, Op.ADDI, reg(a[0]), reg(a[1]), 0, 0, null);
            }
            case "not" -> {
                anzahl(mn, a, 2, "rd, rs");
                add(t, Op.XORI, reg(a[0]), reg(a[1]), 0, -1, null);
            }
            case "neg" -> {
                anzahl(mn, a, 2, "rd, rs");
                add(t, Op.SUB, reg(a[0]), 0, reg(a[1]), 0, null);
            }
            case "seqz" -> {
                anzahl(mn, a, 2, "rd, rs");
                add(t, Op.SLTIU, reg(a[0]), reg(a[1]), 0, 1, null);
            }
            case "snez" -> {
                anzahl(mn, a, 2, "rd, rs");
                add(t, Op.SLTU, reg(a[0]), 0, reg(a[1]), 0, null);
            }
            case "sltz" -> {
                anzahl(mn, a, 2, "rd, rs");
                add(t, Op.SLT, reg(a[0]), reg(a[1]), 0, 0, null);
            }
            case "sgtz" -> {
                anzahl(mn, a, 2, "rd, rs");
                add(t, Op.SLT, reg(a[0]), 0, reg(a[1]), 0, null);
            }
            case "nop" -> {
                anzahl(mn, a, 0, "");
                add(t, Op.ADDI, 0, 0, 0, 0, null);
            }
            case "ecall" -> {
                anzahl(mn, a, 0, "");
                add(t, Op.ECALL, 0, 0, 0, 0, null);
            }
            default -> throw new Fehler("Unbekannter Befehl '" + teile[0] + "'" + vorschlag(mn));
        }
    }

    private static String vorschlag(String mn) {
        return switch (mn) {
            case "move" -> " (gemeint: mv?)";
            case "subi" -> " (RISC-V hat kein subi: addi mit negativem Wert)";
            case "muli" -> " (RISC-V hat kein muli: Wert mit li laden, dann mul)";
            case "b" -> " (gemeint: j?)";
            case "bge2", "bgte" -> " (gemeint: bge?)";
            case "push", "pop" -> " (Stack: addi sp, sp, -4 und sw/lw)";
            default -> "";
        };
    }

    private void add(String text, Op op, int rd, int rs1, int rs2, int imm, String marke) {
        befehle.add(new Befehl(zeile, text, op, rd, rs1, rs2, imm, marke));
    }

    private static void anzahl(String mn, String[] a, int n, String form) {
        if (a.length != n) {
            throw new Fehler("'" + mn + "' erwartet " + n + " Operand" + (n == 1 ? "" : "en")
                    + (form.isEmpty() ? "" : " (" + form + ")") + ", gefunden: " + a.length);
        }
    }

    /** Registernummer zu "t0", "x5", "fp" ...; IllegalArgumentException bei unbekanntem Namen. */
    public static int register(String name) {
        Integer r = REG.get(name.toLowerCase(Locale.ROOT));
        if (r == null) {
            throw new IllegalArgumentException("Unbekanntes Register: " + name);
        }
        return r;
    }

    static int reg(String s) {
        Integer r = REG.get(s.toLowerCase(Locale.ROOT));
        if (r == null) {
            if (s.matches("-?\\d+|0[xX][0-9a-fA-F]+")) {
                throw new Fehler("Register erwartet, gefunden Zahl '" + s + "' (fuer Konstanten: addi oder li)");
            }
            if (s.contains("(")) {
                throw new Fehler("Register erwartet, gefunden Speicheroperand '" + s + "'");
            }
            throw new Fehler("Unbekanntes Register '" + s + "'");
        }
        return r;
    }

    private static int imm12(String s) {
        return (int) zahl(s, -2048, 2047);
    }

    private static String marke(String s) {
        if (s.matches("-?\\d+|0[xX][0-9a-fA-F]+")) {
            throw new Fehler("Sprungziel bitte als Marke angeben, nicht als Zahl '" + s + "'");
        }
        if (!s.matches("[A-Za-z_.$][\\w.$]*")) {
            throw new Fehler("Ungueltige Marke '" + s + "'");
        }
        return s;
    }

    /** "8(sp)" oder "(sp)" -> {offset, register}. */
    private static int[] speicher(String s) {
        Matcher m = MEM.matcher(s);
        if (!m.matches()) {
            throw new Fehler("Speicheroperand der Form offset(register) erwartet, z. B. 0(" + s + ")"
                    + (s.matches("[A-Za-z_.$][\\w.$]*") && !REG.containsKey(s) ? "; fuer Marken zuerst la verwenden" : ""));
        }
        int off = m.group(1).isBlank() ? 0 : (int) zahl(m.group(1), -2048, 2047);
        return new int[]{off, reg(m.group(2))};
    }

    /** Zahl dezimal, 0x.., 0b.. oder 'c', mit Bereichspruefung. */
    static long zahl(String s, long min, long max) {
        String t = s.trim();
        long v;
        try {
            if (t.length() == 3 && t.startsWith("'") && t.endsWith("'")) {
                v = t.charAt(1);
            } else {
                boolean neg = t.startsWith("-");
                String u = neg || t.startsWith("+") ? t.substring(1) : t;
                if (u.startsWith("0x") || u.startsWith("0X")) {
                    v = Long.parseLong(u.substring(2), 16);
                } else if (u.startsWith("0b") || u.startsWith("0B")) {
                    v = Long.parseLong(u.substring(2), 2);
                } else {
                    v = Long.parseLong(u);
                }
                v = neg ? -v : v;
            }
        } catch (NumberFormatException e) {
            throw new Fehler("Zahl erwartet, gefunden '" + s + "'"
                    + (REG.containsKey(t.toLowerCase(Locale.ROOT)) ? " (Register an dieser Stelle nicht erlaubt)" : ""));
        }
        if (v < min || v > max) {
            throw new Fehler("Wert " + t + " liegt ausserhalb des erlaubten Bereichs " + min + " .. " + max
                    + (max == 2047 ? " (12-Bit-Immediate; groessere Werte mit li laden)" : ""));
        }
        return v;
    }

    private void fehler(String text) {
        meldungen.add(Meldung.fehler(zeile, text));
    }

    private void hinweis(String text) {
        meldungen.add(Meldung.hinweis(zeile, text));
    }
}
