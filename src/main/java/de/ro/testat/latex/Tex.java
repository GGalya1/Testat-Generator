package de.ro.testat.latex;

import de.ro.testat.rv.Encoder.Field;
import de.ro.testat.rv.FieldSpec;
import de.ro.testat.rv.Format;
import de.ro.testat.rv.Instance;
import de.ro.testat.rv.Registers;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Kleine LaTeX-Hilfen, die mehrere Ausgaben gemeinsam nutzen. */
public final class Tex {

    private Tex() {
    }

    /**
     * String.format immer mit Punkt als Dezimaltrenner. Wichtig: mit deutschem
     * Locale wuerde aus 1.50 sonst "1,50" und TikZ-Koordinaten waeren kaputt.
     */
    public static String f(String fmt, Object... args) {
        return String.format(Locale.ROOT, fmt, args);
    }

    /** Farbe je Herkunft (in der Praeambel definiert). */
    static final Map<String, String> KIND_COLOR = Map.of(
            "opcode", "cMn", "funct3", "cMn", "funct7", "cMn", "mn", "cMn",
            "rd", "cRd", "rs1", "cRsA", "rs2", "cRsB", "shamt", "cSh");
    static final List<String> IMM_COLORS = List.of("cImmA", "cImmB", "cImmC", "cImmD");

    /** Immediate-Feld mit seiner Farbe. */
    public record ImmGroup(Field field, String color) {
    }

    public static List<ImmGroup> immGroups(List<Field> fields) {
        List<Field> imms = fields.stream().filter(fl -> fl.spec().isImm()).toList();
        List<ImmGroup> out = new ArrayList<>();
        if (imms.size() == 1) {
            out.add(new ImmGroup(imms.get(0), "cImmB"));
        } else {
            for (int i = 0; i < imms.size(); i++) {
                out.add(new ImmGroup(imms.get(i), IMM_COLORS.get(i % IMM_COLORS.size())));
            }
        }
        return out;
    }

    public static String fieldColor(Field field, List<ImmGroup> groups) {
        if (field.spec().isImm()) {
            for (ImmGroup g : groups) {
                if (g.field() == field) {
                    return g.color();
                }
            }
        }
        return KIND_COLOR.get(field.spec().name());
    }

    /** Bitstring von rechts in 4er-Gruppen mit schmalem Abstand. */
    public static String grp4(String bits) {
        List<String> parts = new ArrayList<>();
        String s = bits;
        while (!s.isEmpty()) {
            int cut = Math.max(0, s.length() - 4);
            parts.add(0, s.substring(cut));
            s = s.substring(0, cut);
        }
        return String.join("\\,", parts);
    }

    /** Zahl im Mathemodus, damit das Minus richtig gesetzt wird. */
    public static String num(long n) {
        return n >= 0 ? "$" + n + "$" : "$-" + (-n) + "$";
    }

    /** 32-Bit-Wert mit 8 Hexziffern; negative int-Werte als Zweierkomplement ("0xFFFFFFFC"). */
    public static String hex8(long v) {
        return f("0x%08X", v & 0xFFFFFFFFL);
    }

    /** 32-Bit-Wert ohne fuehrende Nullen ("0x4C"). */
    public static String hex(int v) {
        return "0x" + Integer.toHexString(v).toUpperCase(Locale.ROOT);
    }

    /**
     * Freier Text (z. B. eine selbst eingegebene Ueberschrift) fuer LaTeX: Sonderzeichen werden
     * maskiert, Zeilenumbrueche zu Leerzeichen. Umlaute bleiben (die Praeambel liest UTF-8).
     */
    public static String text(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\textbackslash{}");
                case '{', '}', '#', '$', '%', '&', '_' -> sb.append('\\').append(c);
                case '~' -> sb.append("\\textasciitilde{}");
                case '^' -> sb.append("\\textasciicircum{}");
                case '\n', '\r', '\t' -> sb.append(' ');
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    /** Text in \texttt; % wird maskiert (sonst Kommentar in LaTeX). */
    public static String tt(String s) {
        return "\\texttt{" + s.replace("%", "\\%") + "}";
    }

    /** Assemblerzeile in \texttt, optional mit Farben je Operand. */
    public static String asm(Instance in, boolean numeric, boolean colored) {
        long immFields = in.format().layout().stream().filter(FieldSpec::isImm).count();
        String immColor = immFields == 1 ? "cImmB" : "cImmGen";
        StringBuilder sb = new StringBuilder("\\texttt{");
        List<Instance.Token> toks = in.tokens(numeric);
        for (int i = 0; i < toks.size(); i++) {
            Instance.Token t = toks.get(i);
            if (colored && t.kind() != null) {
                String c = "imm".equals(t.kind()) ? immColor : KIND_COLOR.get(t.kind());
                sb.append("\\textcolor{").append(c).append("}{").append(t.text()).append('}');
            } else {
                sb.append(t.text());
            }
            if (i == 0) {
                sb.append(' ');
            }
        }
        return sb.append('}').toString();
    }

    public static String opcodeMeaning(Instance in) {
        return in.format() == Format.U ? "U-Typ (" + in.mn().text() + ")" : in.format().longName();
    }

    static String fieldMeaning(Field fl, Instance in) {
        String n = fl.spec().name();
        return switch (n) {
            case "opcode" -> opcodeMeaning(in);
            case "funct3", "funct7" -> "\\texttt{" + in.mn().text() + "}";
            case "rd", "rs1", "rs2" -> f("\\texttt{x%d} = \\texttt{%s}", in.reg(n), Registers.abi(in.reg(n)));
            case "shamt" -> "Shift um " + in.shamt();
            default -> "Immediate-Bits " + fl.spec().label().substring(3);
        };
    }

    /** Tabelle Feld | Bits | Wert | Bedeutung. */
    public static String fieldsTable(Instance in, List<Field> fields) {
        List<ImmGroup> groups = immGroups(fields);
        StringBuilder sb = new StringBuilder();
        sb.append("\\begin{tabular}{llll}\n");
        sb.append("\\textbf{Feld} & \\textbf{Bits} & \\textbf{Wert} & \\textbf{Bedeutung} \\\\\n\\hline\n");
        for (Field fl : fields) {
            FieldSpec s = fl.spec();
            String bits = s.hi() == s.lo() ? Integer.toString(s.hi()) : s.hi() + "--" + s.lo();
            sb.append(f("\\textcolor{%s!80!black}{%s} & %s & \\texttt{%s} & %s \\\\\n",
                    fieldColor(fl, groups), s.label(), bits, fl.bits(), fieldMeaning(fl, in)));
        }
        return sb.append("\\end{tabular}").toString();
    }

    /** Binaerdarstellung nach Feldern getrennt. */
    public static String groupedBinary(List<Field> fields) {
        StringBuilder sb = new StringBuilder("\\texttt{");
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) {
                sb.append("\\ \\,");
            }
            sb.append(fields.get(i).bits());
        }
        return sb.append('}').toString();
    }
}
