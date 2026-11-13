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
                        r.matrikel(), r.thema(), clean(r.optionen()), String.format("%016x", r.seed()),
                        r.mitSchluessel() ? "ja" : "nein", r.aufgabe(), clean(r.frage()), clean(r.antwort())));
                w.write("\n");
            }
        }
    }

    private static String clean(String s) {
        return s.replace(";", ",").replace("\n", " ");
    }
}
