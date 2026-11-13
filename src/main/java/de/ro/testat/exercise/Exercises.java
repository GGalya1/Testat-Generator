package de.ro.testat.exercise;

import de.ro.testat.exercise.control.AssemblerToC;
import de.ro.testat.exercise.control.CToAssembler;
import de.ro.testat.exercise.cpu.MulticycleFsm;
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
        register("c2asm", "C -> RISC-V, Parameter: Form[/Fragen], z. B. if, schleife, while,for oder if-else/1",
                arg -> new CToAssembler(form(arg), fragen(arg)));
        register("asm2c", "RISC-V -> C, Parameter wie c2asm", arg -> new AssemblerToC(form(arg), fragen(arg)));
        register("eintakt", "Eintakt-Prozessor: Takte, kritischer Pfad, Tc, Gesamtzeit (ohne Parameter)", arg -> new SingleCycleTiming());
        register("mehrtakt", "Mehrtakt-Steuerwerk erweitern, Parameter: Befehle/Klassen (z. B. sw,beq,mul oder R,I)", MulticycleFsm::new);
        register("hazards", "Pipeline: RAW, nops, Forwarding/Stalls, Umordnung (ohne Parameter)", arg -> new PipelineHazards());
    }

    private Exercises() {
    }

    /** "while,for/1" -> "while,for" */
    private static String form(String arg) {
        int i = arg.indexOf('/');
        return i < 0 ? arg : arg.substring(0, i);
    }

    /** "while,for/1" -> 1, ohne Angabe 2 */
    private static int fragen(String arg) {
        int i = arg.indexOf('/');
        if (i < 0) {
            return 2;
        }
        try {
            return Integer.parseInt(arg.substring(i + 1).trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Anzahl Zusatzfragen ist keine Zahl: " + arg);
        }
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
