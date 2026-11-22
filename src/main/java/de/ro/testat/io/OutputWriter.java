package de.ro.testat.io;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Schreibt das Zip fuer ShareLaTeX und die Zuordnungsdatei. */
public final class OutputWriter {

    /** Eine Zeile der Zuordnungsdatei. */
    public record MappingRow(String matrikel, String thema, String optionen, long seed, boolean mitSchluessel,
                             String aufgabe, String frage, String antwort) {
    }

    private OutputWriter() {
    }

    public static void writeZip(Path zip, Map<String, String> files) throws IOException {
        try (OutputStream os = Files.newOutputStream(zip); ZipOutputStream zos = new ZipOutputStream(os)) {
            for (Map.Entry<String, String> e : files.entrySet()) {
                zos.putNextEntry(new ZipEntry(e.getKey()));
                zos.write(e.getValue().getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
    }

    /** CSV mit Semikolon (oeffnet sich in deutschem Excel/LibreOffice direkt in Spalten). */
    public static void writeMapping(Path csv, List<MappingRow> rows) throws IOException {
        try (Writer w = Files.newBufferedWriter(csv, StandardCharsets.UTF_8)) {
            w.write("matrikelnummer;thema;optionen;seed;schluessel;aufgabe;frage;antwort\n");
            for (MappingRow r : rows) {
                w.write(String.join(";",
                        feld(r.matrikel()), feld(r.thema()), feld(r.optionen()), String.format("%016x", r.seed()),
                        r.mitSchluessel() ? "ja" : "nein", feld(r.aufgabe()), feld(r.frage()), feld(r.antwort())));
                w.write("\n");
            }
        }
    }

    /**
     * Ein CSV-Feld: Zeilenumbrueche werden zu Leerzeichen (eine Zeile pro Aufgabe); enthaelt das
     * Feld ; oder ", steht es in Anfuehrungszeichen und " wird verdoppelt (RFC 4180). Frueher wurde
     * ; durch , ersetzt - das hat C-Code wie for (i = 0; i < n; i++) verfaelscht.
     */
    static String feld(String s) {
        String t = s.replace("\r\n", " ").replace('\n', ' ').replace('\r', ' ');
        if (t.indexOf(';') < 0 && t.indexOf('"') < 0) {
            return t;
        }
        return '"' + t.replace("\"", "\"\"") + '"';
    }
}
