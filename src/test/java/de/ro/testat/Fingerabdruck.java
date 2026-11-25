package de.ro.testat;

import de.ro.testat.exercise.Exercises;
import de.ro.testat.testat.Templates;
import de.ro.testat.web.WebApi;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;

/**
 * Fingerabdruck der gesamten Ausgabe, fuer Refactoring: vorher laufen lassen, Ausgabe speichern,
 * nachher wieder laufen lassen und vergleichen. Abweichen darf nur, was absichtlich geaendert wurde.
 *
 * <pre>
 *   je Vorlage   60 Seeds    (alle Blaetter Zeichen fuer Zeichen, ueber WebApi.erzeugen)
 *   je Aufgabentyp 40 Seeds
 *   eine eigene Kette mit Parametern, Titel und mehreren Seeds
 *   Probelauf: alle Beispiele aus src/test/resources/probelauf, jede Sprache, mit/ohne Schritte
 *   Katalog
 * </pre>
 *
 * Kein JUnit-Test (dauert ein paar Sekunden und hat kein "richtig"): in IntelliJ per main starten.
 * Die Kommandozeile (.werk, Zip, CSV) deckt das nicht ab; dafuer vorher/nachher je ein Zip erzeugen
 * und die entpackten Dateien vergleichen (Zeitstempel im Namen ignorieren).
 */
public final class Fingerabdruck {

    private Fingerabdruck() {
    }

    public static void main(String[] args) throws Exception {
        long t0 = System.nanoTime();
        for (String v : Templates.all().keySet()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 60; i++) {
                sb.append(WebApi.erzeugen(v, "", "", seed(i, v))).append('\n');
            }
            System.out.println("vorlage " + v + " " + sha(sb));
        }
        for (String t : Exercises.ids()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 40; i++) {
                sb.append(WebApi.erzeugen("", t, "", seed(i, t))).append('\n');
            }
            System.out.println("typ " + t + " " + sha(sb));
        }
        System.out.println("kette " + sha(WebApi.erzeugen("",
                "asm2bin:B,J asm2bin:S bin2asm:R c2asm:schleife rekursion", "Probe", "1,2,3,ff,123456789abcdef")));
        StringBuilder pl = new StringBuilder();
        for (Path p : beispiele()) {
            String q = Files.readString(p, StandardCharsets.UTF_8);
            for (String sprache : new String[]{"", "asm", "c"}) {
                for (String schritte : new String[]{"", "1"}) {
                    pl.append(WebApi.ausfuehren(q, sprache, schritte)).append('\n');
                }
            }
        }
        System.out.println("probelauf " + sha(pl));
        System.out.println("katalog " + sha(WebApi.katalog()));
        System.err.println("(" + (System.nanoTime() - t0) / 1_000_000 + " ms)");
    }

    /** Feste, gut gemischte Seeds (nicht 0, 1, 2 ...: deren erste Zufallszahlen sind fast gleich). */
    private static String seed(long i, String s) {
        long x = (i + 1) * 0x9E3779B97F4A7C15L ^ s.hashCode() * 0xBF58476D1CE4E5B9L;
        return Long.toHexString(x);
    }

    private static List<Path> beispiele() throws Exception {
        try (Stream<Path> s = Files.list(Path.of(Fingerabdruck.class.getResource("/probelauf").toURI()))) {
            return s.sorted().toList();
        }
    }

    private static String sha(CharSequence s) throws Exception {
        byte[] d = MessageDigest.getInstance("SHA-256").digest(s.toString().getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(d).substring(0, 16);
    }
}
