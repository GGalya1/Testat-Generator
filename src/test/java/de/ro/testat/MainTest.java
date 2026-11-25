package de.ro.testat;

import de.ro.testat.testat.Templates;
import de.ro.testat.testat.Testat;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class MainTest {

    @Test
    void seedKontextVorlageIstIhreId() {
        Testat t = Templates.get("funktionen").orElseThrow();
        assertEquals("funktionen", Main.seedKontext(Map.of("vorlage", "funktionen"), t));
    }

    /** Zwei verschiedene eigene Testate duerfen nicht denselben Seed-Kontext haben. */
    @Test
    void seedKontextEigeneTestateVerschieden() {
        Testat t = Templates.get("funktionen").orElseThrow();
        String probe = Main.seedKontext(Map.of("aufgaben", "cache-sim vm", "titel", "Probetestat"), t);
        String echt = Main.seedKontext(Map.of("aufgaben", "cache-sim vm", "titel", "Testat 4"), t);
        String andereListe = Main.seedKontext(Map.of("aufgaben", "cache-sim amat", "titel", "Testat 4"), t);
        assertNotEquals(probe, echt);
        assertNotEquals(echt, andereListe);
        // Leerzeichen in der Liste spielen keine Rolle
        assertEquals(echt, Main.seedKontext(Map.of("aufgaben", " cache-sim   vm ", "titel", "Testat 4"), t));
    }

    @Test
    void seedAusDerZuordnungsdatei() {
        assertEquals(0x7a2b3c4d5e6f7081L, Main.hexSeed("7a2b3c4d5e6f7081"));
        assertEquals(0x1a2bL, Main.hexSeed(" 0x1A2B "));
        IllegalArgumentException e = org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> Main.hexSeed("12g4"));
        org.junit.jupiter.api.Assertions.assertTrue(e.getMessage().contains("Hexzahl"), e.getMessage());
    }
}
