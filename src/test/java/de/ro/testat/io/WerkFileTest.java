package de.ro.testat.io;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WerkFileTest {

    @Test
    void readsKeyFromFirstLine() {
        WerkFile w = WerkFile.parse(List.of("geheim-testat3-xyz", "123456", "234567"));
        assertEquals("geheim-testat3-xyz", w.key().orElseThrow());
        assertEquals(List.of("123456", "234567"), w.matrikel());
        assertTrue(w.warnings().isEmpty());
    }

    @Test
    void noKeyWhenFirstLineIsMatrikel() {
        WerkFile w = WerkFile.parse(List.of("123456", "234567"));
        assertFalse(w.key().isPresent());
        assertEquals(2, w.matrikel().size());
    }

    @Test
    void mistypedMatrikelIsNeverTakenAsKey() {
        WerkFile w = WerkFile.parse(List.of("12345", "234567"));
        assertFalse(w.key().isPresent());
        assertEquals(List.of("234567"), w.matrikel());
        assertEquals(1, w.warnings().size());
    }

    @Test
    void ignoresCommentsBlankLinesAndDuplicates() {
        WerkFile w = WerkFile.parse(List.of("﻿# Gruppe A", "", "  geheim-schluessel-1  ", "123456",
                "abc", "123456", " 345678 "));
        assertEquals("geheim-schluessel-1", w.key().orElseThrow());
        assertEquals(List.of("123456", "345678"), w.matrikel());
        assertEquals(2, w.warnings().size()); // "abc" und das Duplikat
    }

    @Test
    void keyOnlyAllowedInFirstLine() {
        WerkFile w = WerkFile.parse(List.of("123456", "geheim-schluessel-2"));
        assertFalse(w.key().isPresent());
        assertEquals(1, w.warnings().size());
    }

    /** Die TU Darmstadt vergibt 7-stellige Matrikelnummern; 6-stellige bleiben erlaubt. */
    @Test
    void siebenstelligeMatrikelnummern() {
        WerkFile w = WerkFile.parse(List.of("geheim-schluessel-7", "2512345", "123456", "12345678"));
        assertEquals(List.of("2512345", "123456"), w.matrikel());
        assertEquals(1, w.warnings().size());
    }

    /** Frueher wurde "1234567  # Anna" in der ersten Zeile still zum Schluessel, und Anna fehlte. */
    @Test
    void matrikelMitKommentarIstKeinSchluessel() {
        WerkFile w = WerkFile.parse(List.of("1234567  # Anna", "2345678\t# Ben", "345678"));
        assertFalse(w.key().isPresent());
        assertEquals(List.of("1234567", "2345678", "345678"), w.matrikel());
        assertTrue(w.warnings().isEmpty(), w.warnings().toString());
    }

    @Test
    void schluesselMitRauteBleibtUnveraendert() {
        WerkFile w = WerkFile.parse(List.of("mein # schluessel 42", "123456"));
        assertEquals("mein # schluessel 42", w.key().orElseThrow());
    }
}
