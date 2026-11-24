package de.ro.testat.web;

import de.ro.testat.exercise.Exercise;
import de.ro.testat.exercise.Exercises;
import de.ro.testat.latex.DocumentBuilder;
import de.ro.testat.testat.Templates;
import de.ro.testat.testat.Testat;
import org.teavm.jso.JSExport;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Schnittstelle fuer die Webseite. Wird mit TeaVM nach JavaScript uebersetzt (mvn -Pweb package)
 * und laeuft komplett im Browser - es gibt keinen Server, der rechnet.
 *
 * Nur Uebungsblaetter: Seeds kommen zufaellig von der Webseite. Schluessel, .werk-Dateien und
 * Zuordnungsdateien gibt es hier bewusst nicht, echte Testate entstehen weiter offline.
 *
 * Ueber die Grenze Java/JavaScript gehen nur Strings (Ein- und Ausgabe als JSON-Text), das ist mit
 * TeaVM am einfachsten und robust. Die Klasse laeuft auch normal auf der JVM (siehe WebApiTest).
 */
public final class WebApi {

    /** Hoechstens so viele Blaetter auf einmal. */
    public static final int MAX_BLAETTER = 20;

    /** Statische LaTeX-Dateien (praeambel.tex, ...), von der Webseite hereingereicht. */
    private static final Map<String, String> RESSOURCEN = new HashMap<>();

    private WebApi() {
    }

    /**
     * Alles, was die Webseite fuer die Auswahl braucht:
     * {@code {"vorlagen":[{"id","titel","aufgaben":[...]}], "typen":[{"id","hilfe"}], "ressourcen":[...]}}
     */
    @JSExport
    public static String katalog() {
        StringBuilder sb = new StringBuilder("{\"vorlagen\":[");
        boolean erstes = true;
        for (String id : Templates.all().keySet()) {
            Testat t = Templates.get(id).orElseThrow();
            sb.append(erstes ? "" : ",").append("{\"id\":").append(json(id))
                    .append(",\"titel\":").append(json(t.title())).append(",\"aufgaben\":[");
            List<Exercise> ex = t.exercises();
            for (int i = 0; i < ex.size(); i++) {
                sb.append(i > 0 ? "," : "").append(json(ex.get(i).describe()));
            }
            sb.append("]}");
            erstes = false;
        }
        sb.append("],\"typen\":[");
        erstes = true;
        for (Map.Entry<String, String> e : Exercises.help().entrySet()) {
            sb.append(erstes ? "" : ",").append("{\"id\":").append(json(e.getKey()))
                    .append(",\"hilfe\":").append(json(e.getValue())).append('}');
            erstes = false;
        }
        sb.append("],\"ressourcen\":[");
        List<String> res = DocumentBuilder.resourceNames();
        for (int i = 0; i < res.size(); i++) {
            sb.append(i > 0 ? "," : "").append(json(res.get(i)));
        }
        return sb.append("]}").toString();
    }

    /** Die Webseite laedt praeambel.tex usw. und gibt sie hier einmal herein. */
    @JSExport
    public static void setzeRessource(String name, String inhalt) {
        RESSOURCEN.put(name, inhalt);
    }

    /**
     * Erzeugt Uebungsblaetter.
     *
     * @param vorlage id einer Vorlage, oder leer, dann gilt {@code kette}
     * @param kette   eigene Aufgabenkette wie bei --aufgaben, z. B. "asm2bin:B,J bin2asm"
     * @param titel   Ueberschrift fuer eine eigene Kette (leer: "Uebung")
     * @param seeds   Seeds als Hex, durch Komma getrennt; ein Seed = ein Blatt
     * @return {@code {"ok":true,"name":...,"beschreibung":...,"blaetter":[{"name","seed","fragen":[...]}],
     *         "dateien":{"main.tex":...}}} oder {@code {"ok":false,"fehler":"..."}}
     */
    @JSExport
    public static String erzeugen(String vorlage, String kette, String titel, String seeds) {
        try {
            return erzeugenOderFehler(leer(vorlage), leer(kette), leer(titel), leer(seeds));
        } catch (IllegalArgumentException e) {
            return fehler(e.getMessage());
        } catch (Throwable e) {
            // z. B. IllegalStateException, wenn eine Loesung die Pruefung nicht besteht
            return fehler("Interner Fehler: " + e);
        }
    }

    /**
     * Wie {@link #erzeugen}, aber ohne Fehler abzufangen: eine Ausnahme kommt mit dem
     * JavaScript-Stack bei der Webseite an. Nur zur Fehlersuche (web/test/smoke.mjs).
     */
    @JSExport
    public static String erzeugenOhneSchutz(String vorlage, String kette, String titel, String seeds) {
        return erzeugenOderFehler(leer(vorlage), leer(kette), leer(titel), leer(seeds));
    }

    private static String erzeugenOderFehler(String vorlage, String kette, String titel, String seeds) {
        Testat testat;
        String name;
        if (!vorlage.isEmpty()) {
            testat = Templates.get(vorlage).orElseThrow(
                    () -> new IllegalArgumentException("Unbekannte Vorlage: " + vorlage));
            name = "uebung_" + vorlage;
        } else {
            if (kette.isEmpty()) {
                throw new IllegalArgumentException("Bitte mindestens eine Aufgabe auswählen.");
            }
            testat = Testat.builder("eigen", titel.isEmpty() ? "Übung" : titel)
                    .addAll(Exercises.parseChain(kette))
                    .build();
            name = "uebung_eigen";
        }

        List<Long> seedListe = new ArrayList<>();
        for (String s : seeds.split(",")) {
            if (!s.trim().isEmpty()) {
                seedListe.add(hexZuLong(s.trim()));
            }
        }
        if (seedListe.isEmpty()) {
            throw new IllegalArgumentException("Kein Seed angegeben.");
        }
        if (seedListe.size() > MAX_BLAETTER) {
            throw new IllegalArgumentException("Höchstens " + MAX_BLAETTER + " Blätter auf einmal.");
        }

        List<DocumentBuilder.Entry> entries = new ArrayList<>();
        StringBuilder blaetter = new StringBuilder("[");
        for (int b = 0; b < seedListe.size(); b++) {
            long seed = seedListe.get(b);
            String blattName = "Uebung-" + (b + 1);
            List<Exercise.Part> parts = testat.generate(seed);
            entries.add(new DocumentBuilder.Entry(blattName, seed, parts));
            blaetter.append(b > 0 ? "," : "").append("{\"name\":").append(json(blattName))
                    .append(",\"seed\":").append(json(hex16(seed))).append(",\"fragen\":[");
            for (int i = 0; i < parts.size(); i++) {
                blaetter.append(i > 0 ? "," : "").append(json(parts.get(i).frage()));
            }
            blaetter.append("]}");
        }
        blaetter.append(']');

        Map<String, String> dateien = DocumentBuilder.build(testat, name, entries, r -> {
            String inhalt = RESSOURCEN.get(r);
            if (inhalt == null) {
                throw new IllegalStateException("Ressource fehlt: " + r + " (setzeRessource nicht aufgerufen)");
            }
            return inhalt;
        });

        StringBuilder sb = new StringBuilder("{\"ok\":true,\"name\":").append(json(name))
                .append(",\"titel\":").append(json(testat.title()))
                .append(",\"beschreibung\":").append(json(testat.describe()))
                .append(",\"blaetter\":").append(blaetter)
                .append(",\"dateien\":{");
        boolean erstes = true;
        for (Map.Entry<String, String> e : dateien.entrySet()) {
            sb.append(erstes ? "" : ",").append(json(e.getKey())).append(':').append(json(e.getValue()));
            erstes = false;
        }
        return sb.append("}}").toString();
    }

    // ------------------------------------------------------------------ Hilfen

    private static String leer(String s) {
        return s == null ? "" : s.trim();
    }

    private static String fehler(String text) {
        return "{\"ok\":false,\"fehler\":" + json(text == null ? "Unbekannter Fehler" : text) + "}";
    }

    /** Hex-Text (1-16 Stellen, ohne Vorzeichen) -> long. Von Hand, damit es sicher auch in TeaVM geht. */
    static long hexZuLong(String s) {
        if (s.isEmpty() || s.length() > 16) {
            throw new IllegalArgumentException("Ungültiger Seed: " + s);
        }
        long v = 0;
        for (int i = 0; i < s.length(); i++) {
            int d = Character.digit(s.charAt(i), 16);
            if (d < 0) {
                throw new IllegalArgumentException("Ungültiger Seed: " + s);
            }
            v = (v << 4) | d;
        }
        return v;
    }

    /** long -> 16 Hex-Stellen, wie %016x. */
    static String hex16(long v) {
        String h = Long.toHexString(v);
        return "0".repeat(16 - h.length()) + h;
    }

    /** String als JSON-Literal mit Anfuehrungszeichen. */
    static String json(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 16).append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        String h = Integer.toHexString(c);
                        sb.append("\\u").append("0".repeat(4 - h.length())).append(h);
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }
}
