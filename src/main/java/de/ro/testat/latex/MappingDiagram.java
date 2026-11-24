package de.ro.testat.latex;

import de.ro.testat.latex.Tex.ImmGroup;
import de.ro.testat.rv.Encoder;
import de.ro.testat.rv.Encoder.Field;
import de.ro.testat.rv.FieldSpec;
import de.ro.testat.rv.Format;
import de.ro.testat.rv.Instance;
import de.ro.testat.rv.Registers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static de.ro.testat.latex.Tex.f;

/**
 * TikZ-Schema: oben die Immediate-Bits, unten das Befehlswort; Pfeile zeigen,
 * wohin welche Bits wandern. Gleiche Farbe = gleiche Herkunft.
 */
public final class MappingDiagram {

    private static final double W = 0.5;        // Breite einer Bitzelle in cm
    private static final double X_WORD = 32 * W; // Breite des Befehlsworts
    private static final double Y_IMM = 2.95;    // Unterkante der Immediate-Zeile
    private static final double[] LEVELS = {2.62, 2.44, 2.26, 2.08}; // Hoehen der waagrechten Pfeilstuecke

    private MappingDiagram() {
    }

    private static double bitX(int b) {
        return (31 - b) * W;
    }

    private static double centerX(FieldSpec s) {
        return (bitX(s.hi()) + bitX(s.lo()) + W) / 2;
    }

    /** reverse = true: Pfeile zeigen nach oben (Dekodieren). */
    public static String render(Instance in, List<Field> fields, boolean reverse) {
        String arrow = reverse ? "<-" : "->";
        List<ImmGroup> groups = Tex.immGroups(fields);
        boolean hasImmRow = in.format().immRow() != null;
        StringBuilder L = new StringBuilder();
        L.append("\\begin{tikzpicture}[>=stealth, font=\\small]\n");

        // --- Befehlswort: Zellen
        for (Field fl : fields) {
            String col = Tex.fieldColor(fl, groups);
            FieldSpec s = fl.spec();
            for (int b = s.hi(); b >= s.lo(); b--) {
                int bit = (fl.value() >> (b - s.lo())) & 1;
                double x = bitX(b);
                L.append(f("\\draw[fill=%s!16, draw=black!35] (%.2f,0) rectangle ++(%.2f,0.6);\n", col, x, W));
                L.append(f("\\node at (%.2f,0.3) {\\footnotesize\\texttt{%d}};\n", x + W / 2, bit));
            }
        }
        for (Field fl : fields) {
            L.append(f("\\draw[very thick] (%.2f,0) rectangle (%.2f,0.6);\n",
                    bitX(fl.spec().hi()), bitX(fl.spec().lo()) + W));
        }

        // --- Bitnummern und Feldnamen darunter (bei Platzmangel eine Zeile tiefer)
        double[] rowEnd = {-1e9, -1e9, -1e9};
        for (Field fl : fields) {
            FieldSpec s = fl.spec();
            L.append(f("\\node at (%.2f,-0.2) {\\tiny %d};\n", bitX(s.hi()) + W / 2, s.hi()));
            if (s.lo() != s.hi()) {
                L.append(f("\\node at (%.2f,-0.2) {\\tiny %d};\n", bitX(s.lo()) + W / 2, s.lo()));
            }
            double cx = centerX(s);
            String lab = s.label();
            double wl = 0.13 * lab.length() + 0.15;
            for (int r = 0; r < rowEnd.length; r++) {
                if (cx - wl / 2 > rowEnd[r] + 0.05) {
                    rowEnd[r] = cx + wl / 2;
                    L.append(f("\\node[text=%s!80!black] at (%.2f,%.2f) {\\scriptsize %s};\n",
                            Tex.fieldColor(fl, groups), cx, -0.55 - 0.35 * r, lab));
                    break;
                }
            }
        }

        // --- Kaesten ueber den Nicht-Immediate-Feldern
        for (Field fl : fields) {
            FieldSpec s = fl.spec();
            if (s.isImm()) {
                continue;
            }
            String col = Tex.fieldColor(fl, groups);
            double width = s.width() * W - 0.15;
            String top = switch (s.name()) {
                case "opcode" -> opcodeBoxTitle(in);
                case "funct3", "funct7" -> "\\texttt{" + in.mn().text() + "}";
                case "shamt" -> "shamt = " + in.shamt();
                default -> f("\\texttt{%s} = x%d", Registers.abi(in.reg(s.name())), in.reg(s.name()));
            };
            String id = "box" + s.name() + s.lo();
            L.append(f("\\node[draw=%s, fill=%s!8, rounded corners=2pt, align=center, inner sep=2pt, "
                            + "text width=%.2fcm, anchor=south] (%s) at (%.2f,0.95) "
                            + "{\\scriptsize %s\\\\[-1pt]\\scriptsize\\texttt{%s}};\n",
                    col, col, width, id, centerX(s), top, fl.bits()));
            L.append(f("\\draw[%s, thick, %s] (%s.south) -- (%.2f,0.62);\n", arrow, col, id, centerX(s)));
        }

        // --- Immediate-Zwischenzeile
        if (hasImmRow) {
            List<int[]> bits = Encoder.immRowBits(in);
            int n = bits.size();
            double x0 = groups.size() == 1 ? bitX(groups.get(0).field().spec().hi()) // exakt ueber dem Feld
                    : X_WORD / 2 - n * W / 2;                                         // sonst mittig
            Map<Integer, Double> pos = new HashMap<>();
            for (int k = 0; k < n; k++) {
                int bnum = bits.get(k)[0], bval = bits.get(k)[1];
                double x = x0 + k * W;
                pos.put(bnum, x);
                String col = null;
                for (ImmGroup g : groups) {
                    FieldSpec s = g.field().spec();
                    if (s.immLo() <= bnum && bnum <= s.immHi()) {
                        col = g.color();
                    }
                }
                if (col == null) { // Bit 0 bei B/J: implizit 0, wird nicht gespeichert
                    L.append(f("\\draw[dashed, fill=black!8, draw=black!45] (%.2f,%.2f) rectangle ++(%.2f,0.5);\n",
                            x, Y_IMM, W));
                    L.append(f("\\node[text=black!50] at (%.2f,%.2f) {\\footnotesize\\texttt{%d}};\n",
                            x + W / 2, Y_IMM + 0.25, bval));
                } else {
                    L.append(f("\\draw[fill=%s!16, draw=black!35] (%.2f,%.2f) rectangle ++(%.2f,0.5);\n",
                            col, x, Y_IMM, W));
                    L.append(f("\\node at (%.2f,%.2f) {\\footnotesize\\texttt{%d}};\n", x + W / 2, Y_IMM + 0.25, bval));
                }
                L.append(f("\\node at (%.2f,%.2f) {\\tiny %d};\n", x + W / 2, Y_IMM + 0.65, bnum));
            }
            for (ImmGroup g : groups) {
                FieldSpec s = g.field().spec();
                L.append(f("\\draw[very thick, %s!80!black] (%.2f,%.2f) rectangle (%.2f,%.2f);\n",
                        g.color(), pos.get(s.immHi()), Y_IMM, pos.get(s.immLo()) + W, Y_IMM + 0.5));
            }
            String title;
            if (in.format().isPcRelative()) {
                title = f("Offset = %s (%d Bit, Zweierkomplement; Bit 0 implizit)", Tex.num(in.imm()), n);
            } else if (in.format() == Format.U) {
                title = f("imm = \\texttt{0x%05X} (20 Bit, landet in Bits 31--12)", in.imm());
            } else {
                title = f("imm = %s (%d Bit, Zweierkomplement)", Tex.num(in.imm()), n);
            }
            L.append(f("\\node[anchor=south west, inner sep=0] at (%.2f,%.2f) {\\scriptsize %s};\n",
                    x0, Y_IMM + 0.85, title));

            for (int k = 0; k < groups.size(); k++) {
                ImmGroup g = groups.get(k);
                FieldSpec s = g.field().spec();
                double sx = (pos.get(s.immHi()) + pos.get(s.immLo()) + W) / 2;
                double fx = centerX(s);
                if (Math.abs(sx - fx) < 1e-6) {
                    L.append(f("\\draw[%s, thick, %s!85!black] (%.2f,%.2f) -- (%.2f,0.62);\n",
                            arrow, g.color(), sx, Y_IMM, fx));
                } else {
                    double yk = LEVELS[k % LEVELS.length];
                    L.append(f("\\draw[%s, thick, rounded corners=4pt, %s!85!black] (%.2f,%.2f) -- (%.2f,%.2f) "
                                    + "-- (%.2f,%.2f) -- (%.2f,0.62);\n",
                            arrow, g.color(), sx, Y_IMM, sx, yk, fx, yk, fx));
                }
            }
        }

        // --- Befehl oben
        double yTop = hasImmRow ? Y_IMM + 1.75 : 2.45;
        if (in.format().isPcRelative()) {
            yTop += 0.35;
        }
        L.append(f("\\node at (%.2f,%.2f) {\\large %s};\n", X_WORD / 2, yTop, Tex.asm(in, false, true)));
        if (in.format().isPcRelative()) {
            L.append(f("\\node at (%.2f,%.2f) {\\scriptsize PC = \\texttt{%s}, \\ \\texttt{ziel} = \\texttt{%s}"
                            + " \\ $\\Rightarrow$ \\ Offset = ziel $-$ PC = %s};\n",
                    X_WORD / 2, yTop - 0.45, Tex.hex8(in.pc()), Tex.hex8(in.target()), Tex.num(in.imm())));
        }
        L.append("\\end{tikzpicture}");
        return L.toString();
    }

    private static String opcodeBoxTitle(Instance in) {
        return switch (in.format()) {
            case U -> in.mn().text();
            case J -> "jal";
            case IL -> "Load";
            case IJ -> "jalr";
            default -> in.format().shortName();
        };
    }
}
