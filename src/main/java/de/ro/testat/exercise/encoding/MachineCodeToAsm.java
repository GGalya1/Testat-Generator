package de.ro.testat.exercise.encoding;

/**
 * "Bestimmen Sie den Assemblerbefehl zum Maschinencode 0x..."
 *
 * <pre>
 *   new MachineCodeToAsm()          // beliebiger RV32I-Befehl
 *   new MachineCodeToAsm("R,I")     // nur Arithmetik
 * </pre>
 */
public final class MachineCodeToAsm extends EncodingExercise {

    public MachineCodeToAsm() {
        this("zufall");
    }

    public MachineCodeToAsm(String auswahl) {
        super(Direction.BIN2ASM, auswahl);
    }

    @Override
    public String id() {
        return "bin2asm";
    }
}
