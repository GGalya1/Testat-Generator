package de.ro.testat.exercise.encoding;

/**
 * "Berechnen Sie den Maschinencode fuer den Befehl ..."
 *
 * <pre>
 *   new AsmToMachineCode()          // beliebiger RV32I-Befehl
 *   new AsmToMachineCode("B,J")     // nur Spruenge
 *   new AsmToMachineCode("sw")      // genau sw
 * </pre>
 */
public final class AsmToMachineCode extends EncodingExercise {

    public AsmToMachineCode() {
        this("zufall");
    }

    public AsmToMachineCode(String auswahl) {
        super(Direction.ASM2BIN, auswahl);
    }

    @Override
    public String id() {
        return "asm2bin";
    }
}
