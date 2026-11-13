package de.ro.testat.exercise;

import java.util.Random;

/**
 * Eine einzelne Aufgabe auf dem Testatblatt.
 *
 * Neue Aufgabentypen implementieren dieses Interface und werden in
 * {@link Exercises} registriert. Danach koennen sie in Vorlagen
 * ({@code Testat.builder(...).add(new MeineAufgabe())}) oder direkt per
 * Kommandozeile ({@code --aufgaben "meine-id:parameter"}) verwendet werden.
 *
 * Regel: {@link #generate} darf Zufall nur aus dem uebergebenen {@code rng}
 * ziehen. Nur dann ist jede Aufgabe ueber den Seed reproduzierbar.
 */
public interface Exercise {

    /** Kurzname fuer Kommandozeile und Zuordnungsdatei, z. B. "asm2bin". */
    String id();

    /** Beschreibung mit Parametern, z. B. "asm2bin(B,J)". Landet in der Zuordnungsdatei. */
    default String describe() {
        return id();
    }

    /**
     * Erzeugt die Aufgabe.
     *
     * @param rng eigener Zufallsgenerator nur fuer diese Aufgabe
     * @param nr  Nummer der Aufgabe auf dem Blatt (1, 2, ...)
     */
    Part generate(Random rng, int nr);

    /**
     * @param aufgabe LaTeX fuer das Aufgabenblatt (beginnt am besten mit \subsection*{Aufgabe nr: ...})
     * @param loesung LaTeX fuer das Loesungsblatt
     * @param frage   Kurzfassung der Aufgabe fuer die Zuordnungsdatei
     * @param antwort Kurzfassung der Loesung fuer die Zuordnungsdatei
     */
    record Part(String aufgabe, String loesung, String frage, String antwort) {
    }
}
