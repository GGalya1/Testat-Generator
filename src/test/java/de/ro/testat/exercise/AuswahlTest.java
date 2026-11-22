package de.ro.testat.exercise;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuswahlTest {

    enum Farbe { ROT, GRUEN_HELL, GRUEN, BLAU }

    @Test
    void leerUndZufallLiefernAlles() {
        assertEquals(List.of(Farbe.values()), Auswahl.formen(null, Farbe.class, false, "test"));
        assertEquals(List.of(Farbe.values()), Auswahl.formen("  ", Farbe.class, false, "test"));
        assertEquals(List.of(Farbe.values()), Auswahl.formen("ZUFALL", Farbe.class, false, "test"));
    }

    @Test
    void listeMitBindestrichUndGrossschreibung() {
        assertEquals(List.of(Farbe.BLAU, Farbe.GRUEN_HELL), Auswahl.formen("Blau, gruen-hell", Farbe.class, false, "test"));
    }

    /** Exakter Name hat Vorrang vor einem Praefix (gruen ist auch Praefix von gruen_hell). */
    @Test
    void praefixNurWennKeinExakterTreffer() {
        assertEquals(List.of(Farbe.GRUEN), Auswahl.formen("gruen", Farbe.class, true, "test"));
        assertEquals(List.of(Farbe.BLAU), Auswahl.formen("bl", Farbe.class, true, "test"));
        assertThrows(IllegalArgumentException.class, () -> Auswahl.formen("bl", Farbe.class, false, "test"));
    }

    @Test
    void fehlermeldungNenntErlaubteWerte() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> Auswahl.formen("lila", Farbe.class, false, "test"));
        assertTrue(e.getMessage().contains("rot, gruen_hell, gruen, blau"), e.getMessage());
    }

    @Test
    void variante() {
        assertEquals("zufall", Auswahl.variante(null, "t", "a", "b"));
        assertEquals("b", Auswahl.variante(" B ", "t", "a", "b"));
        assertThrows(IllegalArgumentException.class, () -> Auswahl.variante("c", "t", "a", "b"));
    }
}
