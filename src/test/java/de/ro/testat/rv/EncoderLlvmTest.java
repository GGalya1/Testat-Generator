package de.ro.testat.rv;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Vergleicht die eigene Kodierung mit dem LLVM-Assembler.
 * Wird uebersprungen, wenn llvm-mc nicht installiert ist.
 */
class EncoderLlvmTest {

    private static final int PER_MNEMONIC = 60;

    @Test
    void allMnemonicsMatchLlvmMc() throws Exception {
        assumeTrue(llvmAvailable(), "llvm-mc nicht gefunden - Test uebersprungen");

        List<String> asm = new ArrayList<>();
        List<Long> expected = new ArrayList<>();
        Random rng = new Random(12345);
        for (Mnemonic mn : Mnemonic.values()) {
            for (int i = 0; i < PER_MNEMONIC; i++) {
                Instance in = InstanceGenerator.generate(mn, rng);
                asm.add(in.asm(true));
                expected.add(Encoder.encode(in).word());
            }
        }

        Process p = new ProcessBuilder("llvm-mc", "-triple=riscv32", "-show-encoding", "-riscv-no-aliases")
                .redirectErrorStream(true).start();
        p.getOutputStream().write((String.join("\n", asm) + "\n").getBytes(StandardCharsets.UTF_8));
        p.getOutputStream().close();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        p.waitFor();

        Matcher m = Pattern.compile("encoding: \\[([^\\]]+)\\]").matcher(out);
        List<Long> got = new ArrayList<>();
        while (m.find()) {
            String[] b = m.group(1).split(",");
            long w = 0;
            for (int i = 0; i < 4; i++) {
                w |= Long.decode(b[i].trim()) << (8 * i);
            }
            got.add(w);
        }
        assertEquals(asm.size(), got.size(), "llvm-mc hat nicht alle Befehle kodiert:\n" + out);
        for (int i = 0; i < asm.size(); i++) {
            assertEquals(expected.get(i), got.get(i), "Falsche Kodierung fuer: " + asm.get(i));
        }
    }

    private static boolean llvmAvailable() {
        try {
            Process p = new ProcessBuilder("llvm-mc", "--version").redirectErrorStream(true).start();
            p.getInputStream().readAllBytes();
            return p.waitFor() == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }
}
