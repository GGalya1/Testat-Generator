package de.ro.testat.rv;

import java.util.List;

/** Befehlsformate von RV32I mit Feldlayout. */
public enum Format {
    R("R-Typ", "R-Typ (Register-Register-Arithmetik)", Layouts.R, null),
    I("I-Typ", "I-Typ (Arithmetik mit Immediate)", Layouts.I, new ImmRow(11, 0)),
    IS("I-Typ", "I-Typ (Shift mit Immediate)", Layouts.IS, null),
    IL("I-Typ", "I-Typ (Load)", Layouts.I, new ImmRow(11, 0)),
    IJ("I-Typ", "I-Typ (jalr)", Layouts.I, new ImmRow(11, 0)),
    S("S-Typ", "S-Typ (Store)", Layouts.S, new ImmRow(11, 0)),
    B("B-Typ", "B-Typ (bedingte Verzweigung)", Layouts.B, new ImmRow(12, 0)),
    U("U-Typ", "U-Typ", Layouts.U, new ImmRow(31, 12)),
    J("J-Typ", "J-Typ (jal)", Layouts.J, new ImmRow(20, 0));

    /** Welche Immediate-Bits im Schema als Zwischenzeile gezeigt werden. */
    public record ImmRow(int hi, int lo) {
        public int length() {
            return hi - lo + 1;
        }
    }

    private final String shortName;
    private final String longName;
    private final List<FieldSpec> layout;
    private final ImmRow immRow;

    Format(String shortName, String longName, List<FieldSpec> layout, ImmRow immRow) {
        this.shortName = shortName;
        this.longName = longName;
        this.layout = layout;
        this.immRow = immRow;
    }

    public String shortName() {
        return shortName;
    }

    public String longName() {
        return longName;
    }

    /** Felder vom hoechsten zum niedrigsten Bit. */
    public List<FieldSpec> layout() {
        return layout;
    }

    /** null, wenn das Format kein Immediate hat (R, Shift). */
    public ImmRow immRow() {
        return immRow;
    }

    /** B- und J-Typ: Sprung relativ zum PC. */
    public boolean isPcRelative() {
        return this == B || this == J;
    }

    private static final class Layouts {
        static final List<FieldSpec> R = List.of(
                FieldSpec.of("funct7", 31, 25), FieldSpec.of("rs2", 24, 20), FieldSpec.of("rs1", 19, 15),
                FieldSpec.of("funct3", 14, 12), FieldSpec.of("rd", 11, 7), FieldSpec.of("opcode", 6, 0));
        static final List<FieldSpec> I = List.of(
                FieldSpec.imm(31, 20, 11, 0), FieldSpec.of("rs1", 19, 15), FieldSpec.of("funct3", 14, 12),
                FieldSpec.of("rd", 11, 7), FieldSpec.of("opcode", 6, 0));
        static final List<FieldSpec> IS = List.of(
                FieldSpec.of("funct7", 31, 25), FieldSpec.of("shamt", 24, 20), FieldSpec.of("rs1", 19, 15),
                FieldSpec.of("funct3", 14, 12), FieldSpec.of("rd", 11, 7), FieldSpec.of("opcode", 6, 0));
        static final List<FieldSpec> S = List.of(
                FieldSpec.imm(31, 25, 11, 5), FieldSpec.of("rs2", 24, 20), FieldSpec.of("rs1", 19, 15),
                FieldSpec.of("funct3", 14, 12), FieldSpec.imm(11, 7, 4, 0), FieldSpec.of("opcode", 6, 0));
        static final List<FieldSpec> B = List.of(
                FieldSpec.imm(31, 31, 12, 12), FieldSpec.imm(30, 25, 10, 5), FieldSpec.of("rs2", 24, 20),
                FieldSpec.of("rs1", 19, 15), FieldSpec.of("funct3", 14, 12), FieldSpec.imm(11, 8, 4, 1),
                FieldSpec.imm(7, 7, 11, 11), FieldSpec.of("opcode", 6, 0));
        static final List<FieldSpec> U = List.of(
                FieldSpec.imm(31, 12, 31, 12), FieldSpec.of("rd", 11, 7), FieldSpec.of("opcode", 6, 0));
        static final List<FieldSpec> J = List.of(
                FieldSpec.imm(31, 31, 20, 20), FieldSpec.imm(30, 21, 10, 1), FieldSpec.imm(20, 20, 11, 11),
                FieldSpec.imm(19, 12, 19, 12), FieldSpec.of("rd", 11, 7), FieldSpec.of("opcode", 6, 0));
    }
}
