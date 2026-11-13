package de.ro.testat.exercise.control;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Grundform des Programms. Keine Verschachtelung. */
public enum Shape {
    IF_IF("if-if", false),            // zwei if hintereinander
    IF_ELSEIF_ELSE("if-elseif", false), // if () {} else if () {} else {}
    IF_ELSE("if-else", false),
    WHILE("while", true),
    FOR("for", true),
    DO_WHILE("do-while", true);

    private final String id;
    private final boolean loop;

    Shape(String id, boolean loop) {
        this.id = id;
        this.loop = loop;
    }

    public String id() {
        return id;
    }

    public boolean isLoop() {
        return loop;
    }

    /**
     * "zufall" (alle), "if" (alle Verzweigungen), "schleife" (alle Schleifen)
     * oder eine Liste von IDs, z. B. "while,for".
     */
    public static List<Shape> resolve(String sel) {
        String s = sel == null ? "" : sel.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty() || s.equals("zufall")) {
            return List.of(values());
        }
        List<Shape> out = new ArrayList<>();
        for (String part : s.split(",")) {
            String p = part.trim();
            switch (p) {
                case "if", "verzweigung" -> Arrays.stream(values()).filter(x -> !x.loop).forEach(out::add);
                case "schleife", "loop" -> Arrays.stream(values()).filter(x -> x.loop).forEach(out::add);
                default -> out.add(Arrays.stream(values()).filter(x -> x.id.equals(p)).findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("Unbekannte Form: " + p
                                + " (erlaubt: zufall, if, schleife, " + ids() + ")")));
            }
        }
        return out.stream().distinct().toList();
    }

    public static List<String> ids() {
        return Arrays.stream(values()).map(Shape::id).toList();
    }
}
