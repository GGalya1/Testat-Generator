package de.ro.testat.web;

import de.ro.testat.exercise.Exercises;
import de.ro.testat.latex.DocumentBuilder;
import de.ro.testat.testat.Templates;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Schreibt Referenz-Ausgaben der WebApi (JVM) fuer feste Seeds und die Probelauf-Beispiele
 * (src/test/resources/probelauf), eine JSON-Zeile pro Fall.
 * web/test/smoke.mjs laesst dieselben Faelle durch das TeaVM-JavaScript laufen und vergleicht
 * Zeichen fuer Zeichen. Laeuft nur mit -Pweb (dort wird die Systemeigenschaft gesetzt).
 */
class WebReferenzTest {

    static final int SEEDS_JE_VORLAGE = 10;
    static final int SEEDS_JE_TYP = 5;

    /** Beispielprogramme fuer den Probelauf (richtige, mit Syntax- und Laufzeitfehlern). */
    static final String PROBELAUF = "/probelauf";

    @Test
    void referenzSchreiben() throws IOException {
        String ziel = System.getProperty("web.referenz");
        assumeTrue(ziel != null && !ziel.isBlank(), "nur mit -Pweb");
        for (String r : DocumentBuilder.resourceNames()) {
            try (InputStream in = WebReferenzTest.class.getResourceAsStream("/latex/" + r)) {
                WebApi.setzeRessource(r, new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        Path pfad = Path.of(ziel);
        Files.createDirectories(pfad.toAbsolutePath().getParent());
        int faelle = 0;
        try (Writer w = Files.newBufferedWriter(pfad, StandardCharsets.UTF_8)) {
            for (String id : Templates.all().keySet()) {
                for (int i = 0; i < SEEDS_JE_VORLAGE; i++) {
                    faelle += fall(w, id, "", seed(i, id));
                }
            }
            for (String typ : Exercises.ids()) {
                for (int i = 0; i < SEEDS_JE_TYP; i++) {
                    faelle += fall(w, "", typ, seed(i, typ));
                }
            }
            for (Path datei : probelaufDateien()) {
                String quelle = Files.readString(datei, StandardCharsets.UTF_8);
                String name = datei.getFileName().toString();
                faelle += probelauf(w, name, quelle, "", "", "");
                if (name.endsWith(".s")) {
                    faelle += probelauf(w, name, quelle, "", "1", "");
                    faelle += probelauf(w, name, quelle, "", "", "1");     // strenger Modus
                    faelle += probelauf(w, name, quelle, "c", "", "");     // Assembler als C: Syntaxfehler
                } else {
                    faelle += probelauf(w, name, quelle, "c", "", "");
                }
            }
        }
        assertTrue(faelle > 0);
    }

    static List<Path> probelaufDateien() throws IOException {
        try (Stream<Path> s = Files.list(Path.of(WebReferenzTest.class.getResource(PROBELAUF).toURI()))) {
            return s.sorted().toList();
        } catch (java.net.URISyntaxException e) {
            throw new IOException(e);
        }
    }

    private static int probelauf(Writer w, String name, String quelle, String sprache, String schritte, String streng)
            throws IOException {
        String aus = WebApi.ausfuehren(quelle, sprache, schritte, streng);
        assertTrue(aus.startsWith("{\"ok\":true"), name + ": " + aus);
        w.write("{\"probelauf\":" + WebApi.json(name) + ",\"quelle\":" + WebApi.json(quelle)
                + ",\"sprache\":" + WebApi.json(sprache) + ",\"schritte\":" + WebApi.json(schritte)
                + ",\"streng\":" + WebApi.json(streng)
                + ",\"ausgabe\":" + WebApi.json(aus) + "}\n");
        return 1;
    }

    private static String seed(int i, String text) {
        return WebApi.hex16((i + 1) * 0x9E3779B97F4A7C15L ^ text.hashCode());
    }

    private static int fall(Writer w, String vorlage, String kette, String seed) throws IOException {
        String aus = WebApi.erzeugen(vorlage, kette, "", seed);
        assertTrue(aus.startsWith("{\"ok\":true"), vorlage + kette + " " + seed + ": " + aus);
        w.write("{\"vorlage\":" + WebApi.json(vorlage) + ",\"kette\":" + WebApi.json(kette)
                + ",\"seed\":" + WebApi.json(seed) + ",\"ausgabe\":" + WebApi.json(aus) + "}\n");
        return 1;
    }
}
