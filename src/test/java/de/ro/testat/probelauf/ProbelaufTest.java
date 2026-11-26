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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProbelaufTest {

    static {
        // wie auf der Kommandozeile: ohne grossen Stack kommt die Java-StackOverflowError vor der
        // eigenen Grenze (2000 Aufrufe), und die Fehlermeldung hat keine Zeilennummer
        GrosserStack.aktivieren();
    }

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

    /** Adressen wie in RARS: la, call, tail sind zwei Befehle, li zwei bei mehr als 12 Bit. */
    @Test
    void pseudobefehleBelegenAdressenWieInRars() {
        RiscvParser.Programm p = RiscvParser.parse("""
                    li    a0, 5000      # 0x00400000, lui + addi
                    li    a3, -7        # 0x00400008, nur addi
                    auipc a1, 0         # 0x0040000C
                    call  f             # 0x00400010, auipc + jalr -> ra = 0x00400018
                    la    a2, f         # 0x00400018, auipc + addi
                    j     ende          # 0x00400020
                f:  ret                 # 0x00400024
                ende:
                """);
        assertTrue(p.fehlerfrei(), p.meldungen().toString());
        RiscvMaschine.Ergebnis e = RiscvMaschine.run(p, 1000, 0);
        assertEquals(0x0040000C, e.ende()[11]);
        assertEquals(0x00400018, e.ende()[1]);
        assertEquals(0x00400024, e.ende()[12]);
        assertEquals(0x00400028, p.adresse(p.befehle().size()));
        // jalr mitten in einen Pseudobefehl: dort beginnt kein Befehl
        RiscvMaschine.Ergebnis mitten = RiscvMaschine.run(
                RiscvParser.parse("la t0, x\naddi t0, t0, -12\njalr zero, 0(t0)\nx:"), 100, 0); // x = 0x00400010
        assertTrue(mitten.meldungen().toString().contains("ungueltige Adresse 0x00400004"), mitten.meldungen().toString());
    }

    @Test
    void oktalzahlenWieInC() {
        CInterpreter.Ergebnis e = CInterpreter.run(CParser.parse("int a = 010;\nint b = 0;\nint c = 0x10;\n"), 1000);
        assertEquals("8", e.variablen().get("a"));
        assertEquals("0", e.variablen().get("b"));
        assertEquals("16", e.variablen().get("c"));
        CParser.SyntaxFehler f = org.junit.jupiter.api.Assertions.assertThrows(CParser.SyntaxFehler.class,
                () -> CParser.parse("int a = 09;"));
        assertTrue(f.getMessage().contains("Oktalzahl"), f.getMessage());
    }

    // ------------------------------------------------------------------ strenger Modus (Aufrufkonvention)

    private static Probelauf.Bericht streng(String asm) {
        return Probelauf.assembler(asm, Probelauf.MAX_SCHRITTE_ASM, 0, true);
    }

    /** Rekursion nach Konvention, t-Register vor dem Aufruf gesichert, tail-Aufruf: kein Verstoss. */
    @Test
    void strengOhneVerstoss() {
        String asm = """
                main:
                    addi a0, zero, 5
                    addi t0, zero, 1
                    addi sp, sp, -4
                    sw   t0, 0(sp)          # t0 ueber den Aufruf retten
                    jal  ra, fak
                    lw   t0, 0(sp)
                    addi sp, sp, 4
                    add  s1, a0, t0
                    call weiter
                    li   a7, 10
                    ecall
                fak:
                    addi t0, zero, 2
                    blt  a0, t0, basis
                    addi sp, sp, -8
                    sw   ra, 4(sp)
                    sw   s0, 0(sp)
                    addi s0, a0, 0
                    addi a0, a0, -1
                    jal  ra, fak
                    mul  a0, a0, s0
                    lw   s0, 0(sp)
                    lw   ra, 4(sp)
                    addi sp, sp, 8
                    jalr zero, 0(ra)
                basis:
                    addi a0, zero, 1
                    jalr zero, 0(ra)
                weiter:
                    tail zurueck            # Endaufruf: zurueck springt direkt zum Aufrufer von weiter
                zurueck:
                    addi a0, zero, 0
                    ret
                """;
        Probelauf.Bericht b = streng(asm);
        assertFalse(b.fehler(), b.text());
        assertTrue(b.text().contains("6 Aufrufe geprueft, keine Verstoesse."), b.text());
        assertTrue(b.text().contains("s1   x9  =         121"), b.text());
    }

    @Test
    void strengMeldetSRegisterSpUndTRegister() throws Exception {
        String asm = new String(ProbelaufTest.class.getResourceAsStream("/probelauf/asm-konvention.s").readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8);
        Probelauf.Bericht b = streng(asm);
        String t = b.text();
        assertTrue(b.fehler(), t);
        assertTrue(t.contains("1 Aufruf geprueft, 4 Verstoesse"), t);
        assertTrue(t.contains("FEHLER in Zeile 12: Aufrufkonvention: t0 wird gelesen, bevor die Funktion f"), t);
        assertTrue(t.contains("FEHLER in Zeile 16: Aufrufkonvention: s0 hat beim Ruecksprung aus f den Wert 4, "
                + "beim Aufruf in Zeile 6 war es 7"), t);
        assertTrue(t.contains("FEHLER in Zeile 16: Aufrufkonvention: sp ist beim Ruecksprung"), t);
        assertTrue(t.contains("FEHLER in Zeile 7: Aufrufkonvention: t0 wird gelesen, wurde aber seit dem Aufruf von f"), t);

        // ohne --streng: keine Meldung, und die Werte sind in beiden Faellen dieselben
        Probelauf.Bericht normal = Probelauf.assembler(asm, Probelauf.MAX_SCHRITTE_ASM, 0);
        assertFalse(normal.fehler(), normal.text());
        assertFalse(normal.text().contains("Aufrufkonvention"), normal.text());
        RiscvParser.Programm p = RiscvParser.parse(asm);
        assertArrayEquals(RiscvMaschine.run(p, 1000, 0).ende(), RiscvMaschine.runMitKonvention(p, 1000, 0).ende());
    }

    /** Nicht gesichertes ra: der Ruecksprung landet nicht hinter dem Aufruf. */
    @Test
    void strengMeldetNichtGesichertesRa() {
        String asm = """
                main:
                    addi a0, zero, 3
                    jal  ra, aussen
                    li   a7, 10
                    ecall
                aussen:
                    jal  ra, innen
                    addi a0, a0, 1
                    jalr zero, 0(ra)
                innen:
                    addi a0, a0, 1
                    jalr zero, 0(ra)
                """;
        String t = streng(asm).text();
        assertTrue(t.contains("FEHLER in Zeile 9: Aufrufkonvention: Ruecksprung nach 0x00400014 (Zeile 8) statt hinter den "
                + "Aufruf von aussen in Zeile 3 (0x00400008)"), t);
        assertEquals(1, t.split("Aufrufkonvention: ", -1).length - 1, "Schleife meldet dieselbe Stelle nur einmal\n" + t);
    }

    /** Alle Musterloesungen mit Funktionsaufrufen halten die Aufrufkonvention auch im strengen Probelauf ein. */
    @Test
    void strengMusterloesungenOhneMeldung() {
        java.util.regex.Pattern listing = java.util.regex.Pattern.compile(
                "\\\\begin\\{lstlisting\\}(\\[[^\\]]*\\])?\\n(.*?)\\\\end\\{lstlisting\\}", java.util.regex.Pattern.DOTALL);
        int aufrufe = 0;
        for (String id : List.of("rekursion", "aufruf", "stack")) {
            for (long seed = 0; seed < 40; seed++) {
                de.ro.testat.exercise.Exercise.Part teil = de.ro.testat.exercise.Exercises.create(id, "")
                        .generate(new de.ro.testat.testat.JdkRandom(seed * 0x9E3779B97F4A7C15L));
                java.util.regex.Matcher m = listing.matcher(teil.aufgabe() + teil.loesung()); // stack: Programm steht in der Aufgabe
                while (m.find()) {
                    // stack: Adressspalte vor jeder Zeile entfernen; %% ist ein maskiertes % (escapechar)
                    if (m.group(2).contains("{")) {
                        continue; // C-Code der Aufgabe
                    }
                    String asm = m.group(2).replace("%%", "%").replaceAll("(?m)^[0-9A-F]{8}  ", "          ");
                    Probelauf.Bericht b = streng(asm);
                    assertTrue(b.text().contains("Syntax: keine Fehler"), id + " " + seed + "\n" + asm + "\n" + b.text());
                    assertFalse(b.fehler(), id + " " + seed + "\n" + asm + "\n" + b.text());
                    if (!b.text().contains("kein Funktionsaufruf")) {
                        aufrufe++;
                    }
                }
            }
        }
        assertTrue(aufrufe >= 120, "Listings mit Aufrufen: " + aufrufe);
    }

    @Test
    void strengOhneAufruf() {
        String t = streng("addi t0, zero, 1\nadd t1, t0, t0\n").text();
        assertTrue(t.contains("kein Funktionsaufruf (jal/jalr/call mit ra) - nichts zu pruefen."), t);
    }
}
