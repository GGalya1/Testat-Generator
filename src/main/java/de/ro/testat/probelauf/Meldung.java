package de.ro.testat.probelauf;

/**
 * Fehler oder Hinweis zu einer Stelle im Quelltext (Zeile/Spalte ab 1, 0 = unbekannt).
 */
public record Meldung(Art art, int zeile, int spalte, String text) {

    public enum Art { FEHLER, HINWEIS }

    public static Meldung fehler(int zeile, String text) {
        return new Meldung(Art.FEHLER, zeile, 0, text);
    }

    public static Meldung fehler(int zeile, int spalte, String text) {
        return new Meldung(Art.FEHLER, zeile, spalte, text);
    }

    public static Meldung hinweis(int zeile, String text) {
        return new Meldung(Art.HINWEIS, zeile, 0, text);
    }

    /** Formatiert mit der betroffenen Quelltextzeile und (falls bekannt) einem Pfeil auf die Spalte. */
    public String format(String[] quelle) {
        StringBuilder sb = new StringBuilder();
        sb.append(art == Art.FEHLER ? "FEHLER" : "Hinweis");
        if (zeile > 0) {
            sb.append(" in Zeile ").append(zeile);
        }
        sb.append(": ").append(text);
        if (zeile > 0 && zeile <= quelle.length) {
            String z = quelle[zeile - 1].replace("\t", "    ");
            sb.append("\n    ").append(z.stripTrailing());
            if (spalte > 0) {
                sb.append("\n    ").append(" ".repeat(Math.max(0, spalte - 1))).append("^");
            }
        }
        return sb.toString();
    }
}
