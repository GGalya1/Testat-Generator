package de.ro.testat.exercise;

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
        // register("c2asm", "C -> RISC-V", CToAssembler::new);
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
