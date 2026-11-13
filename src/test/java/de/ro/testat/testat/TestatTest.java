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
        List<Exercise.Part> a = t.generate(42L);
        assertEquals(a, t.generate(42L));
        assertNotEquals(a, t.generate(43L));
        assertEquals(2, a.size());
    }

    @Test
    void exercisesKeepOrder() {
        Testat t = Testat.builder("test", "Test")
                .add(new MachineCodeToAsm("R"))
                .add(new AsmToMachineCode("B"))
                .build();
        List<Exercise.Part> r = t.generate(1L);
        assertTrue(r.get(0).titel().startsWith("Maschinencode"));
        assertTrue(r.get(1).titel().startsWith("Assembler"));
    }

    @Test
    void changingALaterExerciseKeepsEarlierOnesUnchanged() {
        Testat a = Testat.builder("test", "Test").add(new AsmToMachineCode()).add(new AsmToMachineCode("B")).build();
        Testat b = Testat.builder("test", "Test").add(new AsmToMachineCode()).add(new MachineCodeToAsm("J")).build();
        assertEquals(a.generate(99L).get(0), b.generate(99L).get(0));
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
            assertTrue(!t.generate(5L).get(0).aufgabe().isBlank());
        });
    }

    @Test
    void preambleIsCollectedWithoutDuplicates() {
        Testat t = Templates.get("kodierung-stufen").orElseThrow();
        List<String> pre = t.preamble();
        assertEquals((long) pre.size(), pre.stream().distinct().count());
        assertTrue(pre.contains("\\usepackage{tikz}"));
    }

    @Test
    void latexUsesDotAsDecimalSeparatorEvenWithGermanLocale() {
        Locale old = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            Testat t = Testat.builder("test", "Test").add(new AsmToMachineCode("B")).build();
            String tex = t.generate(7L).get(0).loesung();
            assertTrue(tex.contains("(0.25,"), "TikZ-Koordinaten muessen einen Punkt verwenden");
            assertTrue(!tex.matches("(?s).*\\(\\d+,\\d+,.*"), "Komma als Dezimaltrenner in Koordinaten gefunden");
        } finally {
            Locale.setDefault(old);
        }
    }
}
