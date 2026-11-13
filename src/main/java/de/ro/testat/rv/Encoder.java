package de.ro.testat.rv;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Kodiert einen Befehl in sein 32-Bit-Befehlswort und liefert die Felder fuer die Loesung. */
public final class Encoder {

    private Encoder() {
    }

    /** Ein Feld mit seinem Wert im Befehlswort. */
    public record Field(FieldSpec spec, int value) {
        public String bits() {
            return binary(value, spec.width());
        }
    }

    public record Encoded(long word, List<Field> fields) {
        public String hex() {
            return String.format(Locale.ROOT, "0x%08X", word);
        }

        public String binary() {
            return Encoder.binary(word, 32);
        }
    }

    public static Encoded encode(Instance in) {
        Mnemonic mn = in.mn();
        long immVal = immValue(in);
        long word = 0;
        List<Field> fields = new ArrayList<>();
        for (FieldSpec f : in.format().layout()) {
            int v = switch (f.name()) {
                case "opcode" -> mn.opcode();
                case "funct3" -> mn.funct3();
                case "funct7" -> mn.funct7();
                case "rd", "rs1", "rs2" -> in.reg(f.name());
                case "shamt" -> in.shamt();
                case "imm" -> (int) ((immVal >> f.immLo()) & mask(f.immHi() - f.immLo() + 1));
                default -> throw new IllegalStateException(f.name());
            };
            if (v < 0 || v >= (1L << f.width())) {
                throw new IllegalStateException("Wert passt nicht ins Feld " + f.label() + ": " + v);
            }
            word |= ((long) v) << f.lo();
            fields.add(new Field(f, v));
        }
        return new Encoded(word, List.copyOf(fields));
    }

    /** Immediate so, wie es bitweise verteilt wird (U-Typ: um 12 nach links). */
    static long immValue(Instance in) {
        if (in.imm() == null) {
            return 0;
        }
        return in.format() == Format.U ? ((long) in.imm()) << 12 : in.imm();
    }

    /** Bits der Immediate-Zwischenzeile, MSB zuerst: jeweils {Bitnummer, Wert}. Leer bei R/Shift. */
    public static List<int[]> immRowBits(Instance in) {
        Format.ImmRow row = in.format().immRow();
        List<int[]> out = new ArrayList<>();
        if (row == null) {
            return out;
        }
        long v = immValue(in);
        for (int i = row.hi(); i >= row.lo(); i--) {
            out.add(new int[]{i, (int) ((v >> i) & 1)});
        }
        return out;
    }

    public static String immRowString(Instance in) {
        StringBuilder sb = new StringBuilder();
        for (int[] b : immRowBits(in)) {
            sb.append(b[1]);
        }
        return sb.toString();
    }

    public static String binary(long value, int width) {
        StringBuilder sb = new StringBuilder(width);
        for (int i = width - 1; i >= 0; i--) {
            sb.append((value >> i) & 1);
        }
        return sb.toString();
    }

    private static long mask(int width) {
        return (1L << width) - 1;
    }
}
