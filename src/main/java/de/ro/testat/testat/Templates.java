package de.ro.testat.testat;

import de.ro.testat.exercise.control.AssemblerToC;
import de.ro.testat.exercise.control.CToAssembler;
import de.ro.testat.exercise.cpu.MulticycleFsm;
import de.ro.testat.exercise.functions.ArrayToAssembler;
import de.ro.testat.exercise.functions.CallToAssembler;
import de.ro.testat.exercise.memory.AccessTime;
import de.ro.testat.exercise.memory.CacheParameters;
import de.ro.testat.exercise.memory.CacheProgram;
import de.ro.testat.exercise.memory.CacheSimulation;
import de.ro.testat.exercise.memory.VirtualMemory;
import de.ro.testat.exercise.functions.RecursionToAssembler;
import de.ro.testat.exercise.functions.StackTrace;
import de.ro.testat.exercise.cpu.NewInstructionFsm;
import de.ro.testat.exercise.cpu.PipelineHazards;
import de.ro.testat.exercise.cpu.SingleCycleTiming;
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

        // Wie im Beispieltestat: C -> RISC-V und RISC-V -> C, beliebige Kontrollstruktur
        register("kontrollstrukturen", () -> Testat.builder("kontrollstrukturen", "Kontrollstrukturen")
                .add(new CToAssembler())
                .add(new AssemblerToC())
                .build());

        // Nur Verzweigungen (if-if, if / else if / else, if-else)
        register("verzweigungen", () -> Testat.builder("verzweigungen", "Verzweigungen")
                .add(new CToAssembler("if"))
                .add(new AssemblerToC("if"))
                .build());

        // Nur Schleifen (while, for, do-while)
        register("schleifen", () -> Testat.builder("schleifen", "Schleifen")
                .add(new CToAssembler("schleife"))
                .add(new AssemblerToC("schleife"))
                .build());

        // Funktionen & Speicher (Testat 2): Rekursion, Funktionsaufruf, Stack, Arrays; "shift" bei Bedarf per --aufgaben
        register("funktionen", () -> Testat.builder("funktionen", "Funktionen und Speicher")
                .add(new RecursionToAssembler())
                .add(new CallToAssembler())
                .add(new StackTrace())
                .add(new ArrayToAssembler())
                .build());

        // Speicherhierarchie (Testat 4): Cache-Parameter, Zugriffsfolge, Programm, AMAT, virtueller Speicher;
        // "schreiben" (Schreibstrategien) bei Bedarf per --aufgaben
        register("speicherhierarchie", () -> Testat.builder("speicherhierarchie", "Speicherhierarchie")
                .add(new CacheParameters())
                .add(new CacheSimulation())
                .add(new CacheProgram())
                .add(new AccessTime())
                .add(new VirtualMemory())
                .build());

        // Prozessor (wie das bisherige Testat 2): Eintakt-Timing, Mehrtakt-FSM, Pipeline-Konflikte
        register("prozessor", () -> Testat.builder("prozessor", "Prozessor")
                .add(new SingleCycleTiming())
                .add(new MulticycleFsm())
                .add(new PipelineHazards())
                .build());

        // Variante: statt "Befehl aus der Vorlesung ergaenzen" einen neuen (ausgedachten) Befehl einbauen
        register("prozessor-neu", () -> Testat.builder("prozessor-neu", "Prozessor")
                .add(new SingleCycleTiming())
                .add(new NewInstructionFsm())
                .add(new PipelineHazards())
                .build());
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
