package de.ro.testat.exercise;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Gemeinsame Auswertung des Parameters einer Aufgabe ("typ:parameter" in --aufgaben):
 * leer/null bedeutet "zufall"; sonst eine Liste von Formen aus einem Katalog oder genau eine
 * von mehreren Varianten. Fehlermeldungen nennen immer die erlaubten Werte.
 */
public final class Auswahl {

    private Auswahl() {
    }

    /** null oder leer -&gt; "zufall", sonst ohne Leerzeichen am Rand. */
    public static String normal(String auswahl) {
        return auswahl == null || auswahl.isBlank() ? "zufall" : auswahl.trim();
    }

    /**
     * Elemente des Katalogs, kommagetrennt ausgewaehlt ("zufall" = alle, in Katalogreihenfolge).
     * Namen werden ohne Gross/Klein und mit '-' = '_' verglichen; praefix erlaubt Abkuerzungen
     * (erstes passendes Element).
     */
    public static <T> List<T> liste(String auswahl, List<T> katalog, Function<T, String> name, boolean praefix, String aufgabe) {
        String a = normal(auswahl);
        if (a.equalsIgnoreCase("zufall")) {
            return List.copyOf(katalog);
        }
        List<T> out = new ArrayList<>();
        for (String teil : a.split(",")) {
            String n = schluessel(teil);
            T treffer = katalog.stream().filter(x -> schluessel(name.apply(x)).equals(n)).findFirst()
                    .or(() -> praefix ? katalog.stream().filter(x -> schluessel(name.apply(x)).startsWith(n)).findFirst()
                            : java.util.Optional.empty())
                    .orElseThrow(() -> new IllegalArgumentException("Unbekannte Auswahl fuer " + aufgabe + ": " + teil.trim()
                            + " (erlaubt: " + katalog.stream().map(x -> name.apply(x).toLowerCase(Locale.ROOT))
                            .collect(Collectors.joining(", ")) + " oder zufall)"));
            out.add(treffer);
        }
        return List.copyOf(out);
    }

    /** Wie {@link #liste} fuer alle Werte eines Enums. */
    public static <E extends Enum<E>> List<E> formen(String auswahl, Class<E> typ, boolean praefix, String aufgabe) {
        return liste(auswahl, Arrays.asList(typ.getEnumConstants()), Enum::name, praefix, aufgabe);
    }

    /** Genau eine der erlaubten Varianten oder "zufall" (klein geschrieben zurueck). */
    public static String variante(String auswahl, String aufgabe, String... erlaubt) {
        String a = normal(auswahl).toLowerCase(Locale.ROOT);
        if (a.equals("zufall") || Arrays.asList(erlaubt).contains(a)) {
            return a;
        }
        throw new IllegalArgumentException(aufgabe + ": Parameter " + String.join(", ", erlaubt) + " oder zufall");
    }

    private static String schluessel(String s) {
        return s.trim().toLowerCase(Locale.ROOT).replace('-', '_');
    }
}
