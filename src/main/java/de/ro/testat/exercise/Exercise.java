package de.ro.testat.exercise;

import java.util.List;
import java.util.Random;

/**
 * Eine einzelne Aufgabe auf dem Testatblatt.
 *
 * Neue Aufgabentypen implementieren dieses Interface und werden in
 * {@link Exercises} registriert. Danach koennen sie in Vorlagen
 * ({@code Testat.builder(...).add(new MeineAufgabe())}) oder direkt per
 * Kommandozeile ({@code --aufgaben "meine-id:parameter"}) verwendet werden.
 *
 * Das Blatt basiert auf der TU-Klasse {@code tudaexercise}: Der Generator setzt
 * {@code \begin{task}{titel}} und {@code \begin{solution}} selbst; eine Aufgabe
 * liefert nur Titel, Aufgabentext und Loesungstext.
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
     * Zusaetzliche Praeambel-Zeilen, die diese Aufgabe braucht (Pakete, Farben, Befehle),
     * z. B. {@code "\\usepackage{tikz}"}. Gleiche Eintraege mehrerer Aufgaben werden nur
     * einmal eingefuegt. Sie landen in {@code zusatz.tex} im ShareLaTeX-Projekt.
     */
    default List<String> preamble() {
        return List.of();
    }

    /**
     * Erzeugt die Aufgabe.
     *
     * @param rng eigener Zufallsgenerator nur fuer diese Aufgabe
     */
    Part generate(Random rng);

    /**
     * @param titel   Ueberschrift; die Klasse setzt "Aufgabe N:" davor
     * @param aufgabe LaTeX fuer den Aufgabentext (ohne \begin{task})
     * @param loesung LaTeX fuer die Loesung (ohne \begin{solution})
     * @param frage   Kurzfassung der Aufgabe fuer die Zuordnungsdatei
     * @param antwort Kurzfassung der Loesung fuer die Zuordnungsdatei
     */
    record Part(String titel, String aufgabe, String loesung, String frage, String antwort) {
    }
}
