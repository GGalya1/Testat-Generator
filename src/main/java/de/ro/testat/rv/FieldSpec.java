package de.ro.testat.rv;

/**
 * Ein Feld im 32-Bit-Befehlswort.
 *
 * @param name  opcode, funct3, funct7, rd, rs1, rs2, shamt oder imm
 * @param hi    hoechstes Bit im Befehlswort
 * @param lo    niedrigstes Bit im Befehlswort
 * @param immHi bei imm-Feldern: hoechstes Immediate-Bit, das hier liegt (sonst -1)
 * @param immLo bei imm-Feldern: niedrigstes Immediate-Bit, das hier liegt (sonst -1)
 */
public record FieldSpec(String name, int hi, int lo, int immHi, int immLo) {

    public static FieldSpec of(String name, int hi, int lo) {
        return new FieldSpec(name, hi, lo, -1, -1);
    }

    public static FieldSpec imm(int hi, int lo, int immHi, int immLo) {
        return new FieldSpec("imm", hi, lo, immHi, immLo);
    }

    public boolean isImm() {
        return "imm".equals(name);
    }

    public int width() {
        return hi - lo + 1;
    }

    /** Beschriftung, z. B. "rs1" oder "imm[10:5]". */
    public String label() {
        if (!isImm()) {
            return name;
        }
        return immHi == immLo ? "imm[" + immHi + "]" : "imm[" + immHi + ":" + immLo + "]";
    }
}
