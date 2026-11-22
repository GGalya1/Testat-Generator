package de.ro.testat.probelauf;

import de.ro.testat.exercise.control.Ast;
import de.ro.testat.exercise.control.CRenderer;
import de.ro.testat.exercise.control.Interpreter;
import de.ro.testat.exercise.control.ProgramGenerator;
import de.ro.testat.exercise.control.RiscvRenderer;
import de.ro.testat.exercise.control.Shape;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProbelaufTest {

    // ------------------------------------------------------------------ Abgleich mit dem Generator

    /** Jedes vom Generator erzeugte Assemblerprogramm laeuft im Probelauf und liefert die Werte des C-Interpreters. */
    @Test
    void generatedAssemblerRunsWithCorrectValues() {
        List<String> pool = List.of("t0", "a1", "a3");
        int n = 0;
        for (Shape s : Shape.values()) {
            for (long seed = 0; seed < 400; seed++) {
                Ast.Program p = ProgramGenerator.generate(s, new Random(seed * 7 + s.ordinal()));
                Interpreter.Result r = Interpreter.run(p);
                Map<String, String> regs = new LinkedHashMap<>();
                for (int i = 0; i < 3; i++) {
                    regs.put(p.vars().get(i).name(), pool.get(i));
                }
                for (boolean preserve : new boolean[]{true, false}) {
                    String asm = RiscvRenderer.render(p, regs, "t5", preserve, new Random(seed)).text();
                    RiscvParser.Programm prog = RiscvParser.parse(asm);
                    assertTrue(prog.fehlerfrei(), prog.meldungen() + "\n" + asm);
                    RiscvMaschine.Ergebnis e = RiscvMaschine.run(prog, 100_000, 0);
                    assertTrue(!e.abgebrochen(), e.meldungen() + "\n" + asm);
                    for (int i = 0; i < 3; i++) {
                        assertEquals(r.value(p.vars().get(i)), e.ende()[RiscvParser.register(pool.get(i))], s + " " + seed + "\n" + asm);
                    }
                    assertEquals(RiscvMaschine.SP_START, e.ende()[2], "sp wiederhergestellt");
                    n++;
                }
            }
        }
        assertTrue(n >= 4000);
    }

    /** Jedes vom Generator erzeugte C-Programm (ohne Deklarationen, wie in der Aufgabe) laeuft im Probelauf. */
    @Test
    void generatedCRunsWithCorrectValues() {
        for (Shape s : Shape.values()) {
            for (long seed = 0; seed < 400; seed++) {
                Ast.Program p = ProgramGenerator.generate(s, new Random(seed * 13 + s.ordinal()));
                Interpreter.Result r = Interpreter.run(p);
                String c = CRenderer.program(p);
                CInterpreter.Ergebnis e = CInterpreter.run(CParser.parse(c), 1_000_000);
                assertTrue(!e.abgebrochen(), e.meldungen() + "\n" + c);
                for (Ast.Var v : p.vars()) {
                    if (e.variablen().containsKey(v.name())) {
                        assertEquals(Integer.toString(r.value(v)), e.variablen().get(v.name()), s + " " + seed + "\n" + c);
                    }
                }
                assertEquals(Integer.toString(r.value(p.vars().get(2))), e.variablen().get(p.vars().get(2).name()));
            }
        }
    }

    // ------------------------------------------------------------------ Assembler

    private static RiscvMaschine.Ergebnis asm(String code) {
        RiscvParser.Programm p = RiscvParser.parse(code);
        assertTrue(p.fehlerfrei(), p.meldungen().toString());
        return RiscvMaschine.run(p, 100_000, 0);
    }

    private static List<Meldung> asmFehler(String code) {
        return RiscvParser.parse(code).meldungen();
    }

    @Test
    void assemblerInstructionsAndPseudos() {
        RiscvMaschine.Ergebnis e = asm("""
                li t0, -7
                li t1, 3
                div t2, t0, t1      # -2
                rem t3, t0, t1      # -1
                srai t4, t0, 1      # -4
                srli t5, t0, 28     # 15
                li s0, 0x12345678
                lui s1, 0x12345
                addi s1, s1, 0x678
                bgt t1, t0, gross
                li a0, 99
                gross: neg a1, t1
                seqz a2, zero
                not a3, zero
                """);
        int[] x = e.ende();
        assertEquals(-2, x[RiscvParser.register("t2")]);
        assertEquals(-1, x[RiscvParser.register("t3")]);
        assertEquals(-4, x[RiscvParser.register("t4")]);
        assertEquals(15, x[RiscvParser.register("t5")]);
        assertEquals(0x12345678, x[RiscvParser.register("s0")]);
        assertEquals(0x12345678, x[RiscvParser.register("s1")]);
        assertEquals(0, x[RiscvParser.register("a0")], "bgt springt");
        assertEquals(-3, x[RiscvParser.register("a1")]);
        assertEquals(1, x[RiscvParser.register("a2")]);
        assertEquals(-1, x[RiscvParser.register("a3")]);
    }

    @Test
    void assemblerFunctionsStackDataAndEcall() {
        RiscvMaschine.Ergebnis e = asm("""
                .data
                feld: .word 5, 6, 7
                text: .asciz "ok"
                .text
                main:
                    la a0, feld
                    li a1, 3
                    call summe
                    mv s1, a0
                    li a7, 1
                    ecall
                    la a0, text
                    li a7, 4
                    ecall
                    li a7, 10
                    ecall
                    li s1, -1           # wird nicht mehr ausgefuehrt
                summe:
                    addi sp, sp, -4
                    sw ra, 0(sp)
                    li t1, 0
                l:  beqz a1, ende
                    lw t0, 0(a0)
                    add t1, t1, t0
                    addi a0, a0, 4
                    addi a1, a1, -1
                    j l
                ende:
                    mv a0, t1
                    lw ra, 0(sp)
                    addi sp, sp, 4
                    ret
                """);
        assertEquals(18, e.ende()[RiscvParser.register("s1")]);
        assertEquals("18ok", e.ausgabe());
        assertEquals(RiscvMaschine.SP_START, e.ende()[2]);
        assertTrue(e.endeGrund().contains("ecall"));
    }

    @Test
    void assemblerReportsSyntaxErrorsWithLines() {
        List<Meldung> m = asmFehler("""
                li t0, 1
                addi t1, t0, 4096
                add t2, t0
                foo t0, t1, t2
                lw t3, t0
                beq t0, t1, nirgends
                add t7, t0, t1
                L1: nop
                L1: nop
                """);
        assertEquals(List.of(2, 3, 4, 5, 6, 7, 9), m.stream().map(Meldung::zeile).toList(), m.toString());
        assertTrue(m.get(0).text().contains("-2048 .. 2047"));
        assertTrue(m.get(4).text().contains("nicht definiert"), m.get(4).text());
        assertTrue(m.get(5).text().contains("Unbekanntes Register"));
        assertTrue(m.get(6).text().contains("doppelt"));
    }

    @Test
    void assemblerRuntimeErrorsAndEndlessLoop() {
        RiscvMaschine.Ergebnis e = asm("""
                li t0, 1
                sw t0, 2(sp)
                """);
        assertTrue(e.abgebrochen());
        assertEquals(2, e.meldungen().get(0).zeile());
        RiscvMaschine.Ergebnis loop = RiscvMaschine.run(RiscvParser.parse("l: j l"), 1000, 0);
        assertTrue(loop.abgebrochen() && loop.endeGrund().contains("Endlosschleife"));
    }

    // ------------------------------------------------------------------ C

    private static CInterpreter.Ergebnis c(String code) {
        return CInterpreter.run(CParser.parse(code), 1_000_000);
    }

    @Test
    void cFeatures() {
        CInterpreter.Ergebnis e = c("""
                #include <stdio.h>
                int g = 2;
                int fib(int n) { return n < 2 ? n : fib(n - 1) + fib(n - 2); }
                void fuelle(int a[], int n) { for (int i = 0; i < n; i++) a[i] = i * i; }
                int main(void) {
                    int a[4];
                    fuelle(a, 4);
                    int s = 0, k = 0;
                    do { s += a[k]; k++; } while (k < 4);
                    int m = -7 / 2, r = -7 % 2, sh = -8 >> 1;
                    int x = 0;
                    while (1) { x++; if (x == 3) continue; if (x > 5) break; }
                    printf("%d %d %x\\n", fib(10), s, 255);
                    g <<= 3;
                    return s;
                }
                """);
        assertTrue(!e.abgebrochen(), e.meldungen().toString());
        assertEquals("55 14 ff\n", e.ausgabe());
        assertEquals(14, e.rueckgabe());
        assertEquals("{0, 1, 4, 9}", e.variablen().get("a"));
        assertEquals("-3", e.variablen().get("m"));
        assertEquals("-1", e.variablen().get("r"));
        assertEquals("-4", e.variablen().get("sh"));
        assertEquals("6", e.variablen().get("x"));
        assertEquals("16", e.variablen().get("g"));
    }

    @Test
    void cSyntaxErrorsPointToTheRightPlace() {
        assertSyntax("int a = 3\nint b = 4;", 1, "';' fehlt");
        assertSyntax("if (a > 3 {\n a = 1;\n}", 1, "')' fehlt");
        assertSyntax("while (x < 3) {\n x++;\n", 1, "nicht mit '}' geschlossen");
        assertSyntax("float f = 1;", 1, "nur int");
        assertSyntax("a = 3;\nb = a +;", 2, "Ausdruck erwartet");
        assertSyntax("if (a = 3) {}\n3 = a;", 2, "Variable");
        assertSyntax("x = 1;\n} ", 2, "ohne passende");
    }

    private static void assertSyntax(String code, int zeile, String teil) {
        try {
            CParser.parse(code);
        } catch (CParser.SyntaxFehler f) {
            assertEquals(zeile, f.meldung().zeile(), f.getMessage());
            assertTrue(f.getMessage().contains(teil), f.getMessage());
            return;
        }
        throw new AssertionError("Syntaxfehler erwartet: " + code);
    }

    @Test
    void cRuntimeErrors() {
        assertLaufzeit("int a; int b = a + 1;", 1, "bevor sie einen Wert hat");
        assertLaufzeit("a = 1;\nb = c + 1;", 2, "nicht deklariert");
        assertLaufzeit("int a = 5;\nint b = a / (a - 5);", 2, "Division durch 0");
        assertLaufzeit("int f[3];\nf[3] = 1;", 2, "ausserhalb");
        assertLaufzeit("i = 0;\nwhile (i < 3) { i = i * 1; }", 2, "Endlosschleife");
        assertLaufzeit("int f(int n) { return f(n + 1); }\nint main() { return f(0); }", 1, "Rekursion");
    }

    private static void assertLaufzeit(String code, int zeile, String teil) {
        CInterpreter.Ergebnis e = c(code);
        assertTrue(e.abgebrochen(), code);
        Meldung m = e.meldungen().stream().filter(x -> x.art() == Meldung.Art.FEHLER).findFirst().orElseThrow();
        assertEquals(zeile, m.zeile(), m.text());
        assertTrue(m.text().contains(teil), m.text());
    }

    @Test
    void languageDetection() {
        assertEquals(Probelauf.Sprache.C, Probelauf.erkenne("x.txt", "a = 3;\nb = a * 2;"));
        assertEquals(Probelauf.Sprache.ASM, Probelauf.erkenne("x.txt", "li t0, 3   # Kommentar\naddi t0, t0, 1"));
        assertEquals(Probelauf.Sprache.ASM, Probelauf.erkenne("x.s", "a = 3;"));
        assertEquals(Probelauf.Sprache.C, Probelauf.erkenne("x.c", "li t0, 3"));
    }
}
