package de.ro.testat.rv;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Ein konkreter Befehl mit Operanden. Nicht benutzte Teile sind null.
 *
 * @param imm    Immediate; bei B/J der Offset, bei U der 20-Bit-Wert
 * @param pc     Adresse des Befehls (nur B/J)
 * @param target Adresse der Marke "ziel" (nur B/J)
 */
public record Instance(Mnemonic mn, Integer rd, Integer rs1, Integer rs2,
                       Integer imm, Integer shamt, Long pc, Long target) {

    /** Ein Teil der Assemblerzeile. kind = mn, rd, rs1, rs2, imm, shamt oder null (Trennzeichen). */
    public record Token(String text, String kind) {
    }

    public Format format() {
        return mn.format();
    }

    public int reg(String field) {
        return switch (field) {
            case "rd" -> rd;
            case "rs1" -> rs1;
            case "rs2" -> rs2;
            default -> throw new IllegalArgumentException(field);
        };
    }

    /** Assemblerzeile als Liste von Teilen (fuer die Einfaerbung). numeric: bei B/J Offset statt "ziel". */
    public List<Token> tokens(boolean numeric) {
        List<Token> t = new ArrayList<>();
        Token sep = new Token(", ", null);
        t.add(new Token(mn.text(), "mn"));
        switch (format()) {
            case R -> {
                t.add(reg("rd", rd)); t.add(sep); t.add(reg("rs1", rs1)); t.add(sep); t.add(reg("rs2", rs2));
            }
            case I -> {
                t.add(reg("rd", rd)); t.add(sep); t.add(reg("rs1", rs1)); t.add(sep);
                t.add(new Token(Integer.toString(imm), "imm"));
            }
            case IS -> {
                t.add(reg("rd", rd)); t.add(sep); t.add(reg("rs1", rs1)); t.add(sep);
                t.add(new Token(Integer.toString(shamt), "shamt"));
            }
            case IL, IJ -> {
                t.add(reg("rd", rd)); t.add(sep); t.add(new Token(Integer.toString(imm), "imm"));
                t.add(new Token("(", null)); t.add(reg("rs1", rs1)); t.add(new Token(")", null));
            }
            case S -> {
                t.add(reg("rs2", rs2)); t.add(sep); t.add(new Token(Integer.toString(imm), "imm"));
                t.add(new Token("(", null)); t.add(reg("rs1", rs1)); t.add(new Token(")", null));
            }
            case B -> {
                t.add(reg("rs1", rs1)); t.add(sep); t.add(reg("rs2", rs2)); t.add(sep);
                t.add(new Token(numeric ? Integer.toString(imm) : "ziel", "imm"));
            }
            case U -> {
                t.add(reg("rd", rd)); t.add(sep);
                t.add(new Token(String.format(Locale.ROOT, "0x%05X", imm), "imm"));
            }
            case J -> {
                t.add(reg("rd", rd)); t.add(sep);
                t.add(new Token(numeric ? Integer.toString(imm) : "ziel", "imm"));
            }
        }
        return t;
    }

    /** z. B. "beq a0, a1, ziel" bzw. mit numeric=true "beq a0, a1, -12". */
    public String asm(boolean numeric) {
        List<Token> t = tokens(numeric);
        StringBuilder sb = new StringBuilder(t.get(0).text()).append(' ');
        for (int i = 1; i < t.size(); i++) {
            sb.append(t.get(i).text());
        }
        return sb.toString();
    }

    private static Token reg(String kind, int r) {
        return new Token(Registers.abi(r), kind);
    }
}
