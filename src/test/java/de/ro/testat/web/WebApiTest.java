package de.ro.testat.web;

import de.ro.testat.exercise.Exercises;
import de.ro.testat.latex.DocumentBuilder;
import de.ro.testat.testat.Templates;
import de.ro.testat.testat.Testat;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** WebApi auf der JVM: dieselben Blaetter wie der Generator, gueltiges JSON, saubere Fehler. */
class WebApiTest {

    private static void ressourcenSetzen() throws IOException {
        for (String r : DocumentBuilder.resourceNames()) {
            try (InputStream in = WebApiTest.class.getResourceAsStream("/latex/" + r)) {
                WebApi.setzeRessource(r, new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
    }

    @Test
    void katalogEnthaeltAlleVorlagenUndTypen() {
        String k = WebApi.katalog();
        for (String id : Templates.all().keySet()) {
            assertTrue(k.contains("{\"id\":\"" + id + "\""), id);
        }
        for (String id : Exercises.ids()) {
            assertTrue(k.contains("{\"id\":\"" + id + "\""), id);
        }
        assertTrue(k.contains("\"praeambel.tex\""));
    }

    /** Fuer jede Vorlage: dieselben Dateien wie DocumentBuilder.build mit dem Klassenpfad. */
    @Test
    void vorlagenGleichWieKommandozeile() throws IOException {
        ressourcenSetzen();
        long seed = 0x1234_5678_9abc_def0L;
        for (String id : Templates.all().keySet()) {
            String json = WebApi.erzeugen(id, "", "", WebApi.hex16(seed));
            assertTrue(json.startsWith("{\"ok\":true"), id + ": " + json);
            Testat t = Templates.get(id).orElseThrow();
            Map<String, String> erwartet = DocumentBuilder.build(t, "uebung_" + id,
                    List.of(new DocumentBuilder.Entry("Uebung-1", seed, t.generate(seed))));
            for (Map.Entry<String, String> e : erwartet.entrySet()) {
                assertTrue(json.contains(WebApi.json(e.getKey()) + ":" + WebApi.json(e.getValue())), id + " " + e.getKey());
            }
        }
    }

    @Test
    void jederAufgabentypEinzeln() throws IOException {
        ressourcenSetzen();
        for (String id : Exercises.ids()) {
            for (int i = 0; i < 5; i++) {
                String json = WebApi.erzeugen("", id, "Probe", WebApi.hex16((i + 1) * 0x9E3779B97F4A7C15L));
                assertTrue(json.startsWith("{\"ok\":true"), id + ": " + json);
            }
        }
    }

    @Test
    void mehrereBlaetterUndParameter() throws IOException {
        ressourcenSetzen();
        String json = WebApi.erzeugen("", "asm2bin:B,J bin2asm:R c2asm:if", "Meine Übung", "1,2,ffffffffffffffff");
        assertTrue(json.startsWith("{\"ok\":true"), json);
        assertTrue(json.contains("\"blaetter/Uebung-3.tex\""));
        assertTrue(json.contains("\"seed\":\"ffffffffffffffff\""));
        assertTrue(json.contains("Meine Übung"));
    }

    @Test
    void fehlerAlsJson() {
        assertTrue(WebApi.erzeugen("gibtsnicht", "", "", "1").contains("Unbekannte Vorlage"));
        assertTrue(WebApi.erzeugen("", "gibtsnicht", "", "1").contains("Unbekannter Aufgabentyp"));
        assertTrue(WebApi.erzeugen("", "", "", "1").startsWith("{\"ok\":false"));
        assertTrue(WebApi.erzeugen("kodierung", "", "", "xyz").contains("Ungültiger Seed"));
        assertTrue(WebApi.erzeugen("kodierung", "", "", "").startsWith("{\"ok\":false"));
        assertFalse(WebApi.erzeugen(null, null, null, null).startsWith("{\"ok\":true"));
    }

    @Test
    void hexUndJson() {
        assertEquals(-1L, WebApi.hexZuLong("ffffffffffffffff"));
        assertEquals(0x0123456789abcdefL, WebApi.hexZuLong("0123456789ABCDEF"));
        assertEquals("00000000000000ff", WebApi.hex16(255));
        assertEquals("\"a\\\"b\\\\c\\nd\\u0001\"", WebApi.json("a\"b\\c\nd\u0001"));
    }
}
