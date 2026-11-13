package de.ro.testat.exercise.encoding;

import de.ro.testat.latex.Tex.ImmGroup;
import de.ro.testat.rv.Encoder;
import de.ro.testat.rv.Encoder.Encoded;
import de.ro.testat.rv.Encoder.Field;
import de.ro.testat.rv.Format;
import de.ro.testat.rv.Instance;
import de.ro.testat.rv.Mnemonic;
import de.ro.testat.rv.Registers;
import de.ro.testat.latex.MappingDiagram;
import de.ro.testat.latex.NibbleDiagram;
import de.ro.testat.latex.Tex;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static de.ro.testat.latex.Tex.f;

/** Aufgabentext und Loesung fuer die Kodier-Aufgaben. */
public final class EncodingTex {

    private EncodingTex() {
    }

    // ------------------------------------------------------------------
    // Aufgabe (fuer das Blatt der Studierenden)
    // ------------------------------------------------------------------

    /** Ueberschrift der Aufgabe (die Klasse tudaexercise setzt "Aufgabe N:" davor). */
    public static String titel(Direction dir) {
        return dir == Direction.ASM2BIN ? "Assembler $\\rightarrow$ Maschinencode"
                : "Maschinencode $\\rightarrow$ Assembler";
    }

    public static String aufgabe(Instance in, Encoded enc, Direction dir) {
        StringBuilder L = new StringBuilder();
        if (dir == Direction.ASM2BIN) {
            L.append(f("Berechnen Sie den Maschinencode f\\\"ur den Befehl %s.%n", Tex.asm(in, false, false)));
            if (in.format().isPcRelative()) {
                L.append(f("Der Befehl steht an Adresse \\texttt{%s}, die Marke \\texttt{ziel} an Adresse \\texttt{%s}.%n",
                        Tex.hex8(in.pc()), Tex.hex8(in.target())));
            }
            L.append("\\par Geben Sie das Ergebnis sowohl bin\\\"ar als auch hexadezimal an.\n");
        } else {
            L.append(f("Bestimmen Sie den Assemblerbefehl zum Maschinencode \\texttt{%s}.%n", enc.hex()));
            L.append("Verwenden Sie ABI-Registernamen (z.\\,B. \\texttt{a0}).\n");
            if (in.format().isPcRelative()) {
                L.append(f("\\par Der Befehl steht an Adresse \\texttt{%s}. "
                        + "Geben Sie zus\\\"atzlich die Zieladresse des Sprungs an.%n", Tex.hex8(in.pc())));
            }
        }
        return L.toString();
    }

    // ------------------------------------------------------------------
    // Loesung (fuer Tutor:innen)
    // ------------------------------------------------------------------

    public static String loesung(Instance in, Encoded enc, Direction dir) {
        List<Field> fields = enc.fields();
        Mnemonic mn = in.mn();
        List<String> steps = new ArrayList<>();
        StringBuilder L = new StringBuilder();

        if (dir == Direction.ASM2BIN) {
            String s = f("\\textbf{Format bestimmen.} \\texttt{%s} ist ein %s: opcode = \\texttt{%s}",
                    mn.text(), in.format().longName(), Encoder.binary(mn.opcode(), 7));
            if (mn.hasFunct3()) {
                s += ", funct3 = \\texttt{" + Encoder.binary(mn.funct3(), 3) + "}";
            }
            if (mn.hasFunct7()) {
                s += ", funct7 = \\texttt{" + Encoder.binary(mn.funct7(), 7) + "}";
            }
            steps.add(s + ".");
            String regs = regsText(in, fields);
            if (!regs.isEmpty()) {
                steps.add("\\textbf{Register kodieren.} " + regs + ".");
            }
            String imm = immTextAsm2Bin(in);
            if (imm != null) {
                steps.add("\\textbf{Immediate.} " + imm);
            }
            steps.add("\\textbf{Felder einsetzen} (siehe Schema unten):\\\\[4pt]\n" + Tex.fieldsTable(in, fields));
            steps.add(f("\\textbf{Ergebnis.} Bin\\\"ar nach Feldern: %s. "
                            + "In 4er-Gruppen zerlegt, jede Gruppe ergibt eine Hex-Ziffer:"
                            + "\\begin{center}%n%s\\\\[10pt]\\fbox{\\Large\\texttt{%s}}\\end{center}",
                    Tex.groupedBinary(fields), NibbleDiagram.render(enc.word()), enc.hex()));
        } else {
            steps.add("\\textbf{Hex $\\rightarrow$ bin\\\"ar.} Jede Hex-Ziffer entspricht 4 Bit:"
                    + "\\begin{center}\n" + NibbleDiagram.render(enc.word()) + "\\end{center}");
            String s = f("\\textbf{Format bestimmen.} Bits 6--0 (opcode) = \\texttt{%s} $\\rightarrow$ %s",
                    Encoder.binary(mn.opcode(), 7), Tex.opcodeMeaning(in));
            if (mn.hasFunct3()) {
                s += "; funct3 = \\texttt{" + Encoder.binary(mn.funct3(), 3) + "}";
            }
            if (mn.hasFunct7()) {
                s += ", funct7 = \\texttt{" + Encoder.binary(mn.funct7(), 7) + "}";
            }
            steps.add(s + " $\\rightarrow$ \\texttt{" + mn.text() + "}.");
            steps.add("\\textbf{Felder zerlegen.}\\\\[4pt]\n" + Tex.fieldsTable(in, fields));
            String imm = immTextBin2Asm(in, fields);
            if (imm != null) {
                steps.add("\\textbf{Immediate.} " + imm);
            }
            String res = "\\textbf{Ergebnis.} \\fbox{" + Tex.asm(in, false, false) + "}";
            if (in.format().isPcRelative()) {
                res += f(" \\ mit \\texttt{ziel} = \\texttt{%s} (Offset %s)", Tex.hex8(in.target()), Tex.num(in.imm()));
            }
            steps.add(res);
        }

        L.append("\\begin{sloppypar}\n\\begin{enumerate}\n");
        for (String st : steps) {
            L.append("\\item ").append(st).append('\n');
        }
        L.append("\\end{enumerate}\n\\end{sloppypar}\n");

        L.append("\\par\\medskip\\textbf{Zuordnungsschema}\\par\\smallskip\n");
        L.append("\\fbox{\\parbox{0.97\\linewidth}{\\small\\textbf{Hinweis f\\\"ur Tutor:innen:} "
                + "Nur bei Bedarf den Studierenden zeigen -- nicht Teil der Aufgabe.}}\\par\\smallskip\n");
        L.append("{\\small Gleiche Farbe = gleiche Herkunft. Pfeile zeigen, wohin die Bits wandern")
                .append(dir == Direction.BIN2ASM ? " (beim Dekodieren in Gegenrichtung lesen)." : ".")
                .append("}\n");
        L.append("\\begin{center}\n")
                .append(MappingDiagram.render(in, fields, dir == Direction.BIN2ASM))
                .append("\n\\end{center}\n");
        return L.toString();
    }

    // ------------------------------------------------------------------

    private static String regsText(Instance in, List<Field> fields) {
        Map<String, Integer> order = Map.of("rd", 0, "rs1", 1, "rs2", 2);
        List<String> out = new ArrayList<>();
        fields.stream()
                .filter(fl -> order.containsKey(fl.spec().name()))
                .sorted(Comparator.comparingInt(fl -> order.get(fl.spec().name())))
                .forEach(fl -> {
                    int r = in.reg(fl.spec().name());
                    out.add(f("%s: \\texttt{%s} = \\texttt{x%d} $\\rightarrow$ \\texttt{%s}",
                            fl.spec().name(), Registers.abi(r), r, fl.bits()));
                });
        return String.join("; ", out);
    }

    private static String immTextAsm2Bin(Instance in) {
        Format fmt = in.format();
        if (fmt == Format.R) {
            return null;
        }
        if (fmt == Format.IS) {
            return f("shamt = %d $\\rightarrow$ 5 Bit: \\texttt{%s}. Die oberen Bits 31--25 sind fest "
                            + "(\\texttt{%s} f\\\"ur \\texttt{%s}).",
                    in.shamt(), Encoder.binary(in.shamt(), 5), Encoder.binary(in.mn().funct7(), 7), in.mn().text());
        }
        String bits = Encoder.immRowString(in);
        int n = bits.length();
        if (fmt.isPcRelative()) {
            return f("Offset = ziel $-$ PC = \\texttt{%s} $-$ \\texttt{%s} = %s. "
                            + "Als %d-Bit-Zweierkomplement: \\texttt{%s}. "
                            + "Bit 0 ist immer 0 (Befehle liegen auf geraden Adressen) und wird nicht gespeichert.",
                    Tex.hex8(in.target()), Tex.hex8(in.pc()), Tex.num(in.imm()), n, Tex.grp4(bits));
        }
        if (fmt == Format.U) {
            return f("imm = \\texttt{0x%05X} $\\rightarrow$ 20 Bit: \\texttt{%s}. "
                    + "Diese Bits stehen unver\\\"andert in Bits 31--12 des Befehlsworts.", in.imm(), Tex.grp4(bits));
        }
        return f("imm = %s $\\rightarrow$ %d-Bit-Zweierkomplement: \\texttt{%s}.", Tex.num(in.imm()), n, Tex.grp4(bits));
    }

    private static String immTextBin2Asm(Instance in, List<Field> fields) {
        Format fmt = in.format();
        if (fmt == Format.R) {
            return null;
        }
        if (fmt == Format.IS) {
            return f("Bits 24--20 sind shamt = \\texttt{%s} = %d. Bits 31--25 = \\texttt{%s} unterscheiden "
                            + "\\texttt{srli} und \\texttt{srai}.",
                    Encoder.binary(in.shamt(), 5), in.shamt(), Encoder.binary(in.mn().funct7(), 7));
        }
        List<ImmGroup> groups = new ArrayList<>(Tex.immGroups(fields));
        groups.sort(Comparator.comparingInt((ImmGroup g) -> g.field().spec().immHi()).reversed());
        List<String> parts = new ArrayList<>();
        for (ImmGroup g : groups) {
            parts.add(f("\\textcolor{%s!85!black}{%s = \\texttt{%s}}", g.color(), g.field().spec().label(), g.field().bits()));
        }
        String bits = Encoder.immRowString(in);
        String s = "Immediate-Bits einsammeln: " + String.join(", ", parts);
        if (fmt.isPcRelative()) {
            s += ", imm[0] = \\texttt{0} (implizit). ";
            s += f("Zusammengesetzt: \\texttt{%s} $\\rightarrow$ vorzeichenerweitert = %s. "
                            + "Zieladresse = PC + Offset = \\texttt{%s} + (%s) = \\texttt{%s}.",
                    Tex.grp4(bits), Tex.num(in.imm()), Tex.hex8(in.pc()), Tex.num(in.imm()), Tex.hex8(in.target()));
        } else if (fmt == Format.U) {
            s += f(". Als 20-Bit-Wert: \\texttt{0x%05X}.", in.imm());
        } else {
            s += f(". Zusammengesetzt: \\texttt{%s} $\\rightarrow$ vorzeichenerweitert = %s.", Tex.grp4(bits), Tex.num(in.imm()));
        }
        return s;
    }
}
