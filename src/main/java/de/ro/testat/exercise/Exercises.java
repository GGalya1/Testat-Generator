package de.ro.testat.exercise;

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
import de.ro.testat.exercise.memory.WritePolicy;
import de.ro.testat.exercise.functions.RecursionToAssembler;
import de.ro.testat.exercise.functions.ShiftArithmetic;
import de.ro.testat.exercise.functions.StackTrace;
import de.ro.testat.exercise.cpu.NewInstructionFsm;
import de.ro.testat.exercise.cpu.PipelineHazards;
import de.ro.testat.exercise.cpu.SingleCycleTiming;
import de.ro.testat.exercise.encoding.AsmToMachineCode;
import de.ro.testat.exercise.encoding.MachineCodeToAsm;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Verzeichnis aller Aufgabentypen, damit sie auch ohne Programmieren per
 * Kommandozeile kombiniert werden koennen. Neuen Typ hier mit einer Zeile eintragen.
 */
public final class Exercises {

    /** id -> Fabrik, die den Parameter-Text (nach dem Doppelpunkt, evtl. leer) bekommt. */
    private static final Map<String, Function<String, Exercise>> REGISTRY = new LinkedHashMap<>();
    private static final Map<String, String> HELP = new LinkedHashMap<>();

    static {
        register("asm2bin", "Assembler -> Maschinencode, Parameter: Befehlsauswahl (z. B. B,J)", AsmToMachineCode::new);
        register("bin2asm", "Maschinencode -> Assembler, Parameter: Befehlsauswahl (z. B. R,I)", MachineCodeToAsm::new);
        register("c2asm", "C -> RISC-V, Parameter: Form, z. B. if, schleife, while,for oder if-else", CToAssembler::new);
        register("asm2c", "RISC-V -> C, Parameter wie c2asm", AssemblerToC::new);
        register("eintakt", "Eintakt-Prozessor: Takte, kritischer Pfad, Tc, Gesamtzeit (ohne Parameter)", arg -> new SingleCycleTiming());
        register("mehrtakt", "Mehrtakt-Steuerwerk erweitern, Parameter: Befehle/Klassen (z. B. sw,beq,mul oder R,I)", MulticycleFsm::new);
        register("mehrtakt-neu", "Neuer (ausgedachter) Befehl ins Mehrtakt-Steuerwerk, Parameter: Befehle (z. B. jalm,lwi) oder zufall",
                NewInstructionFsm::new);
        register("rekursion", "Rekursive C-Funktion nach RISC-V (Aufrufkonvention, Stack), Parameter: prod, summe, linear, fib, "
                + "fib_mul, potenz, ggt, ziffern, bits, collatz, binom, bereich oder zufall", RecursionToAssembler::new);
        register("aufruf", "Nicht rekursive Funktionen (Blattfunktion + Aufrufer mit Schleife/Bedingung) nach RISC-V, "
                + "Parameter: summe, zaehlen, iteration, zwei_aufrufe, bedingt oder zufall", CallToAssembler::new);
        register("stack", "Stackinhalt bei rekursiver Funktion nachvollziehen, ohne Parameter", arg -> new StackTrace());
        register("array", "Array-Schleife C -> RISC-V, Parameter: skalarprodukt, summe_groesser, maximum, zaehlen, umrechnen, "
                + "umdrehen, praefixsumme oder zufall", ArrayToAssembler::new);
        register("shift", "Multiplikation/Division durch Shift, ohne Parameter", arg -> new ShiftArithmetic());
        register("cache-bits", "Cache: Bloecke, Saetze, Bitbreiten, Speicherbedarf, Adresszerlegung; Parameter: vorwaerts, "
                + "rueckwaerts oder zufall", CacheParameters::new);
        register("cache-sim", "Cache-Zugriffsfolge: Satz, Tag, Hit/Miss, Miss-Art, Endinhalt, Miss-Rate; Parameter: direkt, "
                + "assoziativ, voll oder zufall", CacheSimulation::new);
        register("cache-prog", "Miss-Rate einer Schleife (RISC-V) oder Array-Schleife (C) fuer zwei Caches; Parameter: schleife, "
                + "array oder zufall", CacheProgram::new);
        register("amat", "Mittlere Zugriffszeit mit L1/L2, noetige Miss-Rate, CPI mit Speicher-Stalls (ohne Parameter)", arg -> new AccessTime());
        register("schreiben", "Write-Through/no-allocate vs. Write-Back/allocate: Hits, Speicherzugriffe, Dirty-Bloecke (ohne Parameter)",
                arg -> new WritePolicy());
        register("vm", "Virtueller Speicher: Offset/VPN/PPN, Seitentabellengroesse, Uebersetzung mit TLB (ohne Parameter)",
                arg -> new VirtualMemory());
        register("hazards", "Pipeline: RAW, nops, Forwarding/Stalls, Umordnung (ohne Parameter)", arg -> new PipelineHazards());
    }

    private Exercises() {
    }

    public static void register(String id, String help, Function<String, Exercise> factory) {
        REGISTRY.put(id, factory);
        HELP.put(id, help);
    }

    public static Set<String> ids() {
        return REGISTRY.keySet();
    }

    public static Map<String, String> help() {
        return HELP;
    }

    public static Exercise create(String id, String args) {
        Function<String, Exercise> f = REGISTRY.get(id.toLowerCase(Locale.ROOT));
        if (f == null) {
            throw new IllegalArgumentException("Unbekannter Aufgabentyp: " + id + " (verfuegbar: " + ids() + ")");
        }
        return f.apply(args == null ? "" : args);
    }

    /**
     * Kette aus der Kommandozeile, durch Leerzeichen getrennt:
     * {@code "asm2bin:B,J asm2bin:S bin2asm"}.
     */
    public static List<Exercise> parseChain(String spec) {
        List<Exercise> out = new ArrayList<>();
        for (String item : spec.trim().split("\\s+")) {
            if (item.isEmpty()) {
                continue;
            }
            int colon = item.indexOf(':');
            String id = colon < 0 ? item : item.substring(0, colon);
            String args = colon < 0 ? "" : item.substring(colon + 1);
            out.add(create(id, args));
        }
        if (out.isEmpty()) {
            throw new IllegalArgumentException("Leere Aufgabenkette.");
        }
        return out;
    }
}
