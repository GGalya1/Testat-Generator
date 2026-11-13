package de.ro.testat.testat;

import de.ro.testat.exercise.encoding.AsmToMachineCode;
import de.ro.testat.exercise.encoding.MachineCodeToAsm;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Fertige Testat-Vorlagen. Auswahl beim Aufruf mit {@code --vorlage <id>}.
 *
 * Neue Vorlage: eine Zeile {@code register(...)} mit der gewuenschten Kette.
 * Die ID muss mit der ID im Builder uebereinstimmen.
 */
public final class Templates {

    private static final Map<String, Supplier<Testat>> TEMPLATES = new LinkedHashMap<>();

    static {
        // Wie im alten Testat 1: hin und zurueck, beliebiger Befehl
        register("kodierung", () -> Testat.builder("kodierung", "Befehlskodierung")
                .add(new AsmToMachineCode())
                .add(new MachineCodeToAsm())
                .build());

        // Gleiche Schwierigkeit fuer alle: je eine Aufgabe pro Stufe
        register("kodierung-stufen", () -> Testat.builder("kodierung-stufen", "Befehlskodierung")
                .add(new AsmToMachineCode("R,I"))           // leicht: Felder direkt
                .add(new AsmToMachineCode("S,Load,Shift"))  // mittel: geteiltes imm / funct7
                .add(new AsmToMachineCode("B,J"))           // schwer: verwuerfelte imm-Bits, PC-relativ
                .add(new MachineCodeToAsm())
                .build());

        // Schwerpunkt Spruenge
        register("spruenge", () -> Testat.builder("spruenge", "Sprünge und Verzweigungen")
                .add(new AsmToMachineCode("B"))
                .add(new AsmToMachineCode("J"))
                .add(new MachineCodeToAsm("B,J"))
                .build());

        // Schwerpunkt Speicherzugriffe
        register("speicher", () -> Testat.builder("speicher", "Speicherzugriffe")
                .add(new AsmToMachineCode("S"))
                .add(new MachineCodeToAsm("Load"))
                .build());

        // Weitere Testate, sobald die Aufgabentypen existieren, z. B.:
        // register("c-nach-asm", () -> Testat.builder("c-nach-asm", "C nach RISC-V")
        //         .add(new CToAssembler("schleife"))
        //         .add(new AssemblerToC())
        //         .build());
    }

    private Templates() {
    }

    public static void register(String id, Supplier<Testat> factory) {
        TEMPLATES.put(id, factory);
    }

    public static Optional<Testat> get(String id) {
        Supplier<Testat> s = TEMPLATES.get(id);
        return s == null ? Optional.empty() : Optional.of(s.get());
    }

    public static Map<String, Supplier<Testat>> all() {
        return TEMPLATES;
    }
}
