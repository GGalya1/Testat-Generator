package de.ro.testat.io;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OutputWriterTest {

    /** C-Code mit ; bleibt unveraendert (in Anfuehrungszeichen), " wird verdoppelt. */
    @Test
    void csvFeld() {
        assertEquals("einfach", OutputWriter.feld("einfach"));
        assertEquals("\"for (i = 0; i < n; i++)\"", OutputWriter.feld("for (i = 0; i < n; i++)"));
        assertEquals("\"a \"\"b\"\"\"", OutputWriter.feld("a \"b\""));
        assertEquals("zwei zeilen", OutputWriter.feld("zwei\nzeilen"));
    }
}
