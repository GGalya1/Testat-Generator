package de.ro.testat.rv;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/** Alle RV32I-Befehle, die der Generator kennt. funct3/funct7 = -1, wenn nicht vorhanden. */
public enum Mnemonic {
    ADD(Format.R, Op.R, 0b000, 0b0000000), SUB(Format.R, Op.R, 0b000, 0b0100000),
    SLL(Format.R, Op.R, 0b001, 0b0000000), SLT(Format.R, Op.R, 0b010, 0b0000000),
    SLTU(Format.R, Op.R, 0b011, 0b0000000), XOR(Format.R, Op.R, 0b100, 0b0000000),
    SRL(Format.R, Op.R, 0b101, 0b0000000), SRA(Format.R, Op.R, 0b101, 0b0100000),
    OR(Format.R, Op.R, 0b110, 0b0000000), AND(Format.R, Op.R, 0b111, 0b0000000),

    ADDI(Format.I, Op.I, 0b000, -1), SLTI(Format.I, Op.I, 0b010, -1),
    SLTIU(Format.I, Op.I, 0b011, -1), XORI(Format.I, Op.I, 0b100, -1),
    ORI(Format.I, Op.I, 0b110, -1), ANDI(Format.I, Op.I, 0b111, -1),

    SLLI(Format.IS, Op.I, 0b001, 0b0000000), SRLI(Format.IS, Op.I, 0b101, 0b0000000),
    SRAI(Format.IS, Op.I, 0b101, 0b0100000),

    LB(Format.IL, Op.LOAD, 0b000, -1), LH(Format.IL, Op.LOAD, 0b001, -1),
    LW(Format.IL, Op.LOAD, 0b010, -1), LBU(Format.IL, Op.LOAD, 0b100, -1),
    LHU(Format.IL, Op.LOAD, 0b101, -1),

    JALR(Format.IJ, Op.JALR, 0b000, -1),

    SB(Format.S, Op.STORE, 0b000, -1), SH(Format.S, Op.STORE, 0b001, -1),
    SW(Format.S, Op.STORE, 0b010, -1),

    BEQ(Format.B, Op.BRANCH, 0b000, -1), BNE(Format.B, Op.BRANCH, 0b001, -1),
    BLT(Format.B, Op.BRANCH, 0b100, -1), BGE(Format.B, Op.BRANCH, 0b101, -1),
    BLTU(Format.B, Op.BRANCH, 0b110, -1), BGEU(Format.B, Op.BRANCH, 0b111, -1),

    LUI(Format.U, Op.LUI, -1, -1), AUIPC(Format.U, Op.AUIPC, -1, -1),

    JAL(Format.J, Op.JAL, -1, -1);

    private static final class Op {
        static final int R = 0b0110011, I = 0b0010011, LOAD = 0b0000011, JALR = 0b1100111;
        static final int STORE = 0b0100011, BRANCH = 0b1100011, LUI = 0b0110111, AUIPC = 0b0010111;
        static final int JAL = 0b1101111;
    }

    private final Format format;
    private final int opcode;
    private final int funct3;
    private final int funct7;

    Mnemonic(Format format, int opcode, int funct3, int funct7) {
        this.format = format;
        this.opcode = opcode;
        this.funct3 = funct3;
        this.funct7 = funct7;
    }

    public Format format() {
        return format;
    }

    public int opcode() {
        return opcode;
    }

    public int funct3() {
        return funct3;
    }

    public int funct7() {
        return funct7;
    }

    public boolean hasFunct3() {
        return funct3 >= 0;
    }

    public boolean hasFunct7() {
        return funct7 >= 0;
    }

    /** Schreibweise im Assembler, z. B. "beq". */
    public String text() {
        return name().toLowerCase(Locale.ROOT);
    }

    // ------------------------------------------------------------------
    // Auswahl per Eingabe: Format, Gruppe oder einzelner Befehl
    // ------------------------------------------------------------------

    private static final Map<String, String> ALIASES = Map.of(
            "i-shift", "shift", "is", "shift", "l", "load", "il", "load", "ij", "jalr",
            "random", "zufall", "*", "zufall", "alle", "zufall");

    /**
     * Uebersetzt eine Eingabe wie "B", "load", "beq" oder "zufall" in die moeglichen Befehle.
     * Mehrere Eingaben koennen mit Komma getrennt werden ("B,J,S").
     */
    public static Optional<List<Mnemonic>> resolve(String input) {
        List<Mnemonic> out = new java.util.ArrayList<>();
        for (String part : input.split(",")) {
            String s = part.trim().toLowerCase(Locale.ROOT);
            s = ALIASES.getOrDefault(s, s);
            List<Mnemonic> found = switch (s) {
                case "r" -> byFormat(Format.R);
                case "i" -> byFormat(Format.I);
                case "shift" -> byFormat(Format.IS);
                case "load" -> byFormat(Format.IL);
                case "jalr" -> List.of(JALR);
                case "s" -> byFormat(Format.S);
                case "b" -> byFormat(Format.B);
                case "u" -> byFormat(Format.U);
                case "j" -> byFormat(Format.J);
                case "zufall" -> List.of(values());
                default -> Arrays.stream(values()).filter(m -> m.text().equals(part.trim().toLowerCase(Locale.ROOT)))
                        .collect(Collectors.toList());
            };
            if (found.isEmpty()) {
                return Optional.empty();
            }
            for (Mnemonic m : found) {
                if (!out.contains(m)) {
                    out.add(m);
                }
            }
        }
        return out.isEmpty() ? Optional.empty() : Optional.of(List.copyOf(out));
    }

    private static List<Mnemonic> byFormat(Format f) {
        return Arrays.stream(values()).filter(m -> m.format == f).collect(Collectors.toList());
    }
}
