package de.ro.testat.io;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Eingabedatei eines Tutors / einer Tutorin.
 *
 * <pre>
 *   # Kommentare und Leerzeilen werden ignoriert
 *   mein-geheimer-schluessel-fuer-testat-3     &lt;- optional, erste Zeile
 *   123456
 *   234567
 * </pre>
 *
 * Ist die erste (relevante) Zeile eine gueltige Matrikelnummer, gibt es keinen
 * Schluessel. Ein Schluessel muss mindestens ein Zeichen enthalten, das keine
 * Ziffer ist - so wird eine vertippte Matrikelnummer nie als Schluessel gelesen.
 */
public record WerkFile(Optional<String> key, List<String> matrikel, List<String> warnings) {

    /** Format der Matrikelnummer. Bei Bedarf hier anpassen (z. B. 7 Stellen). */
    public static final Pattern MATRIKEL = Pattern.compile("\\d{6}");
    private static final Pattern ONLY_DIGITS = Pattern.compile("\\d+");

    public static WerkFile read(Path path) throws IOException {
        return parse(Files.readAllLines(path, StandardCharsets.UTF_8));
    }

    public static WerkFile parse(List<String> lines) {
        Optional<String> key = Optional.empty();
        Set<String> nums = new LinkedHashSet<>();
        List<String> warnings = new ArrayList<>();
        boolean first = true;

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).replace("﻿", "").trim(); // BOM entfernen (Windows-Editoren)
            int lineNo = i + 1;
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            boolean isFirst = first;
            first = false;

            if (MATRIKEL.matcher(line).matches()) {
                if (!nums.add(line)) {
                    warnings.add("Zeile " + lineNo + ": Matrikelnummer " + line + " doppelt, wird nur einmal erzeugt.");
                }
            } else if (isFirst && !ONLY_DIGITS.matcher(line).matches()) {
                key = Optional.of(line);
                if (line.length() < 12) {
                    warnings.add("Zeile " + lineNo + ": Schluessel ist kurz (" + line.length()
                            + " Zeichen). Empfohlen sind mindestens 12 zufaellige Zeichen.");
                }
            } else {
                warnings.add("Zeile " + lineNo + ": \"" + line + "\" ist keine gueltige Matrikelnummer "
                        + "(erwartet: " + MATRIKEL.pattern() + "), wird ignoriert.");
            }
        }
        return new WerkFile(key, List.copyOf(nums), List.copyOf(warnings));
    }
}
