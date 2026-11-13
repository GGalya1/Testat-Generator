package de.ro.testat.testat;

import de.ro.testat.exercise.Exercise;
import de.ro.testat.exercise.Exercises;
import de.ro.testat.exercise.encoding.AsmToMachineCode;
import de.ro.testat.exercise.encoding.MachineCodeToAsm;
import de.ro.testat.io.SeedDeriver;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestatTest {

    @Test
    void seedIsDeterministicAndDependsOnAllInputs() {
        long s = SeedDeriver.derive("schluessel-A", "kodierung", "123456");
        assertEquals(s, SeedDeriver.derive("schluessel-A", "kodierung", "123456"));
        assertNotEquals(s, SeedDeriver.derive("schluessel-B", "kodierung", "123456"));
        assertNotEquals(s, SeedDeriver.derive("schluessel-A", "kodierung", "123457"));
        assertNotEquals(s, SeedDeriver.derive("schluessel-A", "spruenge", "123456"));
        assertTrue(s >= 0);
    }

    @Test
    void sameSeedGivesSameSheet() {
        Testat t = Templates.get("kodierung").orElseThrow();
        Testat.Result a = t.generate(42L);
        assertEquals(a.aufgabe(), t.generate(42L).aufgabe());
        assertEquals(a.loesung(), t.generate(42L).loesung());
        assertNotEquals(a.aufgabe(), t.generate(43L).aufgabe());
        assertEquals(2, a.parts().size());
    }

    @Test
    void exercisesKeepOrderAndNumbering() {
        Testat t = Testat.builder("test", "Test")
                .add(new MachineCodeToAsm("R"))
                .add(new AsmToMachineCode("B"))
                .build();
        Testat.Result r = t.generate(1L);
        assertTrue(r.parts().get(0).aufgabe().contains("Aufgabe 1: Maschinencode"));
        assertTrue(r.parts().get(1).aufgabe().contains("Aufgabe 2: Assembler"));
        assertTrue(r.aufgabe().indexOf("Aufgabe 1") < r.aufgabe().indexOf("Aufgabe 2"));
    }

    @Test
    void changingALaterExerciseKeepsEarlierOnesUnchanged() {
        Testat a = Testat.builder("test", "Test").add(new AsmToMachineCode()).add(new AsmToMachineCode("B")).build();
        Testat b = Testat.builder("test", "Test").add(new AsmToMachineCode()).add(new MachineCodeToAsm("J")).build();
        Exercise.Part pa = a.generate(99L).parts().get(0);
        Exercise.Part pb = b.generate(99L).parts().get(0);
        assertEquals(pa.aufgabe(), pb.aufgabe());
    }

    @Test
    void chainFromCommandLine() {
        List<Exercise> chain = Exercises.parseChain("asm2bin:B,J  asm2bin:sw bin2asm");
        assertEquals(3, chain.size());
        assertEquals("asm2bin(B,J)", chain.get(0).describe());
        assertEquals("asm2bin(sw)", chain.get(1).describe());
        assertEquals("bin2asm(zufall)", chain.get(2).describe());
    }

    @Test
    void allTemplatesBuildAndGenerate() {
        Templates.all().forEach((id, supplier) -> {
            Testat t = supplier.get();
            assertEquals(id, t.id(), "Vorlagen-ID und Builder-ID muessen gleich sein");
            assertTrue(!t.generate(5L).aufgabe().isBlank());
        });
    }

    @Test
    void latexUsesDotAsDecimalSeparatorEvenWithGermanLocale() {
        Locale old = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            Testat t = Testat.builder("test", "Test").add(new AsmToMachineCode("B")).build();
            String tex = t.generate(7L).loesung();
            assertTrue(tex.contains("(0.25,"), "TikZ-Koordinaten muessen einen Punkt verwenden");
            assertTrue(!tex.matches("(?s).*\\(\\d+,\\d+,.*"), "Komma als Dezimaltrenner in Koordinaten gefunden");
        } finally {
            Locale.setDefault(old);
        }
    }
}
