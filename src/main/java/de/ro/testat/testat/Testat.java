package de.ro.testat.testat;

import de.ro.testat.exercise.Exercise;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Ein Testatblatt: eine geordnete Kette von Aufgaben.
 *
 * <pre>
 *   Testat t = Testat.builder("kodierung", "Befehlskodierung")
 *           .add(new AsmToMachineCode("B,J"))
 *           .add(new MachineCodeToAsm())
 *           .build();
 * </pre>
 *
 * Jede Aufgabe bekommt einen eigenen, aus Seed und Position abgeleiteten
 * Zufallsgenerator. Aendert man Aufgabe 3, bleiben Aufgabe 1 und 2 fuer
 * dieselbe Matrikelnummer unveraendert.
 */
public final class Testat {

    private final String id;
    private final String title;
    private final List<Exercise> exercises;

    private Testat(String id, String title, List<Exercise> exercises) {
        this.id = id;
        this.title = title;
        this.exercises = List.copyOf(exercises);
    }

    /**
     * @param id    Kurzname ohne Leerzeichen (geht in Seed und Dateinamen ein)
     * @param title Ueberschrift auf dem Blatt
     */
    public static Builder builder(String id, String title) {
        return new Builder(id, title);
    }

    public String id() {
        return id;
    }

    public String title() {
        return title;
    }

    public List<Exercise> exercises() {
        return exercises;
    }

    /** z. B. "asm2bin(B,J) bin2asm(zufall)" - fuer die Zuordnungsdatei. */
    public String describe() {
        return exercises.stream().map(Exercise::describe).collect(Collectors.joining(" "));
    }

    /** Alle Praeambel-Zeilen der Aufgaben, ohne Doppelte, in Reihenfolge des ersten Auftretens. */
    public List<String> preamble() {
        // LinkedHashSet statt stream().distinct(): distinct() entfernt in TeaVM (Browser) keine Doppelten
        Set<String> zeilen = new LinkedHashSet<>();
        for (Exercise e : exercises) {
            zeilen.addAll(e.preamble());
        }
        return List.copyOf(zeilen);
    }

    /** Erzeugt alle Aufgaben fuer einen Seed. */
    public List<Exercise.Part> generate(long seed) {
        List<Exercise.Part> parts = new ArrayList<>();
        for (int i = 0; i < exercises.size(); i++) {
            parts.add(exercises.get(i).generate(new Random(subSeed(seed, i))));
        }
        return List.copyOf(parts);
    }

    /** SplitMix64-Mischung: unabhaengige Seeds je Position. */
    static long subSeed(long seed, int index) {
        long z = seed + (index + 1) * 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    public static final class Builder {
        private final String id;
        private final String title;
        private final List<Exercise> exercises = new ArrayList<>();

        private Builder(String id, String title) {
            if (id == null || !id.matches("[a-z0-9-]+")) {
                throw new IllegalArgumentException("Testat-ID nur aus a-z, 0-9 und '-': " + id);
            }
            this.id = id;
            this.title = title;
        }

        public Builder add(Exercise exercise) {
            exercises.add(exercise);
            return this;
        }

        public Builder addAll(List<Exercise> list) {
            exercises.addAll(list);
            return this;
        }

        public Testat build() {
            if (exercises.isEmpty()) {
                throw new IllegalStateException("Testat '" + id + "' hat keine Aufgaben.");
            }
            return new Testat(id, title, exercises);
        }
    }
}
