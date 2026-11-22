package de.ro.testat.exercise.memory;

import de.ro.testat.exercise.Exercise;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;
import java.util.Random;

/**
 * "Mittlere Zugriffszeit (AMAT)" nach Harris &amp; Harris, Gl. 8.2:
 * AMAT = t_cache + MR_cache (t_MM + MR_MM t_VM), hier ohne virtuellen Speicher.
 * a) eine Cache-Stufe, b) mit L2-Cache, c) welche L1-Miss-Rate fuer eine Ziel-AMAT noetig ist,
 * d) Ausfuehrungszeit eines Programms (CPI mit Speicher-Stalls).
 *
 * <p>Alle Werte werden so gezogen, dass die Ergebnisse endliche Dezimalzahlen sind; gerechnet
 * wird exakt mit BigDecimal, Kontrolle mit einer zweiten Rechnung in Bruechen (Zaehler/Nenner).
 */
public final class AccessTime implements Exercise {

    @Override
    public String id() {
        return "amat";
    }

    @Override
    public List<String> preamble() {
        return List.of("\\usepackage{amsmath}");
    }

    private static BigDecimal p(int prozentMal10) {
        return BigDecimal.valueOf(prozentMal10, 3); // 25 -> 0.025
    }

    @Override
    public Part generate(Random rng) {
        int t1 = 1 + rng.nextInt(2);
        int m1 = List.of(20, 40, 50, 80, 100).get(rng.nextInt(5));            // 2 % .. 10 % (in Promille)
        int t2 = List.of(8, 10, 12, 15, 20).get(rng.nextInt(5));
        int m2 = List.of(100, 200, 250, 400, 500).get(rng.nextInt(5));        // 10 % .. 50 %
        int tm = List.of(50, 80, 100, 120, 150, 200).get(rng.nextInt(6));
        int ziel = List.of(10, 15, 20, 25, 30).get(rng.nextInt(5));          // gesuchte L1-Miss-Rate 1 % .. 3 %
        int lsAnteil = List.of(20, 25, 30, 40).get(rng.nextInt(4));          // Prozent Lade-/Speicherbefehle
        int befehle = List.of(1, 2, 5).get(rng.nextInt(3)) * 1_000_000;
        int takt = List.of(1, 2, 4).get(rng.nextInt(3));                      // GHz -> Taktzeit 1/takt ns

        BigDecimal T1 = BigDecimal.valueOf(t1);
        BigDecimal M1 = p(m1);
        BigDecimal T2 = BigDecimal.valueOf(t2);
        BigDecimal M2 = p(m2);
        BigDecimal TM = BigDecimal.valueOf(tm);
        BigDecimal amatA = T1.add(M1.multiply(TM));
        BigDecimal amatB = T1.add(M1.multiply(T2.add(M2.multiply(TM))));
        BigDecimal zielAmat = T1.add(p(ziel).multiply(TM));
        BigDecimal ls = BigDecimal.valueOf(lsAnteil, 2);
        // CPI: 1 + Anteil Lade/Speicher * (AMAT_b - t1) (Treffer kosten keinen Stall)
        BigDecimal stall = ls.multiply(amatB.subtract(T1));
        BigDecimal cpi = BigDecimal.ONE.add(stall);
        BigDecimal zeitNs = cpi.multiply(BigDecimal.valueOf(befehle)).divide(BigDecimal.valueOf(takt), MathContext.DECIMAL64);
        BigDecimal zeitMs = zeitNs.movePointLeft(6);

        pruefe(t1, m1, t2, m2, tm, ziel, amatA, amatB, zielAmat);

        StringBuilder a = new StringBuilder();
        a.append("Ein Prozessor hat einen L1-Daten-Cache mit einer Zugriffszeit von ").append(t1).append(t1 == 1 ? "~Takt" : "~Takten")
                .append(" und einer Miss-Rate von ").append(MemTex.prozent(M1)).append(". Ein Zugriff auf den Hauptspeicher dauert ")
                .append(tm).append("~Takte.\n");
        a.append("\\begin{enumerate}[label=\\alph*)]\n");
        a.append("\\item Berechnen Sie die mittlere Zugriffszeit (AMAT) in Takten.\n");
        a.append("\\item Zwischen L1 und Hauptspeicher wird ein L2-Cache mit ").append(t2).append("~Takten Zugriffszeit eingebaut. "
                + "Von den Zugriffen, die den L2-Cache erreichen, verfehlen ").append(MemTex.prozent(M2))
                .append(" auch ihn. Wie gro\\ss{} ist die AMAT jetzt?\n");
        a.append("\\item Ohne L2-Cache: Welche L1-Miss-Rate w\\\"are n\\\"otig, damit die AMAT ").append(MemTex.dez(zielAmat))
                .append("~Takte betr\\\"agt?\n");
        a.append("\\item Mit L2-Cache wie in b): Ein Programm hat ").append(MemTex.zahl(befehle)).append(" Befehle, davon ")
                .append(lsAnteil).append("\\,\\% Lade- und Speicherbefehle. Ohne Speicher-Stalls w\\\"are der CPI 1; jeder Datenzugriff "
                + "verursacht zus\\\"atzlich (AMAT $-$ ").append(t1).append(")~Takte Stall (Befehlszugriffe treffen immer). "
                + "Bestimmen Sie den CPI und die Ausf\\\"uhrungszeit bei ").append(takt).append("~GHz.\n");
        a.append("\\end{enumerate}\n");

        StringBuilder l = new StringBuilder("\\begin{enumerate}[label=\\alph*)]\n");
        l.append("\\item $\\text{AMAT} = t_{L1} + MR_{L1} \\cdot t_{MM} = ").append(t1).append(" + ").append(MemTex.dez(M1))
                .append(" \\cdot ").append(tm).append(" = ").append(MemTex.dez(amatA)).append("$~Takte.\n");
        l.append("\\item $\\text{AMAT} = t_{L1} + MR_{L1} \\cdot (t_{L2} + MR_{L2} \\cdot t_{MM}) = ").append(t1).append(" + ")
                .append(MemTex.dez(M1)).append(" \\cdot (").append(t2).append(" + ").append(MemTex.dez(M2)).append(" \\cdot ").append(tm)
                .append(") = ").append(MemTex.dez(amatB)).append("$~Takte.\n");
        l.append("\\item $MR_{L1} = (\\text{AMAT} - t_{L1}) / t_{MM} = (").append(MemTex.dez(zielAmat)).append(" - ").append(t1)
                .append(") / ").append(tm).append(" = ").append(MemTex.dez(p(ziel))).append("$, also ").append(MemTex.prozent(p(ziel)))
                .append(".\n");
        l.append("\\item $\\text{CPI} = 1 + ").append(MemTex.dez(ls)).append(" \\cdot (").append(MemTex.dez(amatB)).append(" - ")
                .append(t1).append(") = ").append(MemTex.dez(cpi)).append("$; Zeit $= ").append(MemTex.zahl(befehle)).append(" \\cdot ")
                .append(MemTex.dez(cpi)).append(" \\cdot ").append(MemTex.dez(BigDecimal.ONE.divide(BigDecimal.valueOf(takt))))
                .append("\\,\\text{ns} = ").append(MemTex.dez(zeitMs)).append("$~ms.\n");
        l.append("\\end{enumerate}\n");

        String frage = "t1=" + t1 + " MR1=" + m1 / 10.0 + "% t2=" + t2 + " MR2=" + m2 / 10.0 + "% tMM=" + tm + " Ziel=" + zielAmat.toPlainString()
                + " LS=" + lsAnteil + "% N=" + befehle + " f=" + takt + "GHz";
        String antwort = "AMAT " + amatA.stripTrailingZeros().toPlainString() + " / " + amatB.stripTrailingZeros().toPlainString()
                + ", MR " + ziel / 10.0 + "%, CPI " + cpi.stripTrailingZeros().toPlainString() + ", " + zeitMs.stripTrailingZeros().toPlainString() + " ms";
        return new Part("Mittlere Zugriffszeit", a.toString(), l.toString(), frage, antwort);
    }

    /** Zweite Rechnung in ganzen Zahlen (Werte * 1000 bzw. * 10^6). */
    private static void pruefe(int t1, int m1, int t2, int m2, int tm, int ziel, BigDecimal amatA, BigDecimal amatB, BigDecimal zielAmat) {
        long a = 1000L * t1 + (long) m1 * tm;                                   // * 1000
        long b = 1_000_000L * t1 + (long) m1 * (1000L * t2 + (long) m2 * tm);   // * 10^6
        long z = 1000L * t1 + (long) ziel * tm;
        if (amatA.movePointRight(3).compareTo(BigDecimal.valueOf(a)) != 0
                || amatB.movePointRight(6).compareTo(BigDecimal.valueOf(b)) != 0
                || zielAmat.movePointRight(3).compareTo(BigDecimal.valueOf(z)) != 0) {
            throw new IllegalStateException("Interner Fehler: AMAT");
        }
    }
}
