package de.ro.testat.exercise.memory;

import java.math.BigDecimal;
import java.util.List;

/** LaTeX-Hilfen fuer die Speicheraufgaben: Tabellen, Zahlen, Prozent. */
final class MemTex {

    private MemTex() {
    }

    /**
     * Tabelle mit Linien. spalten z. B. "|r|c|c|", zeilen als Liste von Zellen; hoch = hoehere
     * Zeilen (zum Ausfuellen auf dem Aufgabenblatt).
     */
    static String tabelle(String spalten, List<String> kopf, List<List<String>> zeilen, boolean hoch) {
        StringBuilder sb = new StringBuilder("\\begin{center}\n").append(hoch ? "\\renewcommand{\\arraystretch}{1.5}\n" : "")
                .append("\\begin{tabular}{").append(spalten).append("}\n\\hline\n");
        sb.append(String.join(" & ", kopf.stream().map(k -> "\\textbf{" + k + "}").toList())).append(" \\\\\n\\hline\n");
        for (List<String> z : zeilen) {
            sb.append(String.join(" & ", z)).append(" \\\\\n\\hline\n");
        }
        return sb.append("\\end{tabular}\n").append(hoch ? "\\renewcommand{\\arraystretch}{1}\n" : "").append("\\end{center}\n").toString();
    }

    /** 19922944 -> "19\,922\,944" */
    static String zahl(long v) {
        String s = Long.toString(Math.abs(v));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (i > 0 && (s.length() - i) % 3 == 0) {
                sb.append("\\,");
            }
            sb.append(s.charAt(i));
        }
        return (v < 0 ? "-" : "") + sb;
    }

    /** Dezimalzahl mit Komma, ohne ueberfluessige Nullen: 3.40 -> "3,4" */
    static String dez(BigDecimal v) {
        BigDecimal s = v.stripTrailingZeros();
        if (s.scale() < 0) {
            s = s.setScale(0);
        }
        return s.toPlainString().replace(".", "{,}");
    }

    /** Anteil (0.05) als Prozent "5\,\%" */
    static String prozent(BigDecimal anteil) {
        return dez(anteil.movePointRight(2)) + "\\,\\%";
    }

    /** Bruch und Prozent: "3/15 = 20\,\%" (Prozent auf 1 Nachkommastelle gerundet, wenn noetig) */
    static String rate(int teil, int ganz) {
        BigDecimal p = BigDecimal.valueOf(teil * 100L).divide(BigDecimal.valueOf(ganz), 1, java.math.RoundingMode.HALF_UP);
        boolean exakt = (teil * 1000L) % ganz == 0;
        return "$" + teil + "/" + ganz + (exakt ? " = " : " \\approx ") + dez(p) + "\\,\\%$";
    }

    static String tt(String s) {
        return "\\texttt{" + s + "}";
    }
}
