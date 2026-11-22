package de.ro.testat.exercise.memory;

import de.ro.testat.exercise.Exercise;
import de.ro.testat.latex.Tex;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * "Schreibstrategien": Eine Folge von lw/sw auf einem kleinen direkt abgebildeten Cache,
 * einmal mit Write-Through ohne Write-Allocate (A), einmal mit Write-Back mit Write-Allocate (B).
 * Gefragt: Hit/Miss je Zugriff, Rueckschreiben in B, Anzahl der Hauptspeicherzugriffe und die
 * am Ende schmutzigen Bloecke.
 *
 * <p>Kontrolle: unabhaengige Zaehlung ueber eine eigene Tabelle Satz -&gt; (Tag, dirty) und
 * Invarianten (A schreibt jedes sw genau einmal in den Speicher; B liest bei jedem Miss).
 */
public final class WritePolicy implements Exercise {

    record Op(boolean schreiben, int adresse) {
        String text() {
            return (schreiben ? "\\texttt{sw}" : "\\texttt{lw}") + " \\texttt{" + Tex.hex(adresse) + "}";
        }
    }

    @Override
    public String id() {
        return "schreiben";
    }

    @Override
    public List<String> preamble() {
        return List.of("\\usepackage{amsmath}");
    }

    @Override
    public Part generate(Random rng) {
        for (int versuch = 0; versuch < 3000; versuch++) {
            int blockWorte = rng.nextBoolean() ? 1 : 2;
            int bloecke = 4;
            List<Op> ops = folge(rng, blockWorte, bloecke);
            Part p = versuche(ops, blockWorte, bloecke);
            if (p != null) {
                return p;
            }
        }
        throw new IllegalStateException("schreiben: keine geeignete Folge gefunden");
    }

    private static List<Op> folge(Random rng, int blockWorte, int bloecke) {
        int blockBytes = 4 * blockWorte;
        int s0 = rng.nextInt(bloecke);
        List<Integer> nummern = new ArrayList<>();
        nummern.add(s0 + bloecke * rng.nextInt(4));
        int zweiter;
        do {
            zweiter = s0 + bloecke * rng.nextInt(4);
        } while (zweiter == nummern.get(0));
        nummern.add(zweiter);
        while (nummern.size() < 4) {
            int b = rng.nextInt(4 * bloecke);
            if (!nummern.contains(b)) {
                nummern.add(b);
            }
        }
        int n = 8 + rng.nextInt(3);
        List<Op> ops = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            int b = nummern.get(rng.nextInt(nummern.size()));
            ops.add(new Op(rng.nextInt(5) < 2, b * blockBytes + 4 * rng.nextInt(blockWorte)));
        }
        return ops;
    }

    private Part versuche(List<Op> ops, int blockWorte, int bloecke) {
        CacheModel a = new CacheModel(4 * blockWorte * bloecke, 4 * blockWorte, 1, CacheModel.Strategie.WRITE_THROUGH);
        CacheModel b = new CacheModel(4 * blockWorte * bloecke, 4 * blockWorte, 1, CacheModel.Strategie.WRITE_BACK);
        List<CacheModel.Zugriff> za = new ArrayList<>();
        List<CacheModel.Zugriff> zb = new ArrayList<>();
        for (Op o : ops) {
            za.add(a.zugriff(o.adresse(), o.schreiben()));
            zb.add(b.zugriff(o.adresse(), o.schreiben()));
        }
        int lesenA = za.stream().mapToInt(CacheModel.Zugriff::bloeckeGelesen).sum();
        int schreibenA = za.stream().mapToInt(CacheModel.Zugriff::worteGeschrieben).sum();
        int lesenB = zb.stream().mapToInt(CacheModel.Zugriff::bloeckeGelesen).sum();
        int schreibenB = zb.stream().mapToInt(CacheModel.Zugriff::bloeckeGeschrieben).sum();
        long stores = ops.stream().filter(Op::schreiben).count();
        long loads = ops.size() - stores;
        boolean unterschied = false;
        boolean schreibHit = false;
        boolean schreibMiss = false;
        for (int i = 0; i < ops.size(); i++) {
            unterschied |= za.get(i).hit() != zb.get(i).hit();
            schreibHit |= ops.get(i).schreiben() && zb.get(i).hit();
            schreibMiss |= ops.get(i).schreiben() && !zb.get(i).hit();
        }
        if (stores < 3 || loads < 3 || !unterschied || schreibenB < 1 || !schreibHit || !schreibMiss) {
            return null;
        }
        unabhaengig(ops, blockWorte, bloecke, lesenA, schreibenA, lesenB, schreibenB);
        if (schreibenA != stores || lesenB != zb.stream().filter(x -> !x.hit()).count()) {
            throw new IllegalStateException("Interner Fehler: Invarianten der Schreibstrategien");
        }

        StringBuilder t = new StringBuilder();
        t.append("Ein direkt abgebildeter Cache hat ").append(bloecke).append(" Bl\\\"ocke zu je ").append(blockWorte)
                .append(blockWorte == 1 ? "~Wort" : "~W\\\"ortern").append(" und ist anfangs leer. Ein Programm f\\\"uhrt die Zugriffe "
                + "in der Tabelle aus (32-Bit-Byteadressen, jeweils ein Wort). Vergleichen Sie zwei Schreibstrategien:\\\\[2pt]\n");
        t.append("\\textbf{A}: Write-Through ohne Write-Allocate (jedes \\texttt{sw} schreibt das Wort in den Hauptspeicher; "
                + "bei einem Schreib-Miss wird der Block nicht geladen).\\\\\n");
        t.append("\\textbf{B}: Write-Back mit Write-Allocate (\\texttt{sw} schreibt nur in den Cache und setzt das Dirty-Bit; "
                + "bei einem Schreib-Miss wird der Block geladen; ein verdr\\\"angter Dirty-Block wird zur\\\"uckgeschrieben).\n");
        t.append("\\begin{enumerate}[label=\\alph*)]\n");
        t.append("\\item Tragen Sie f\\\"ur jeden Zugriff Hit oder Miss bei A und bei B ein und geben Sie an, ob bei B ein Block "
                + "zur\\\"uckgeschrieben wird (welcher?).\n");
        t.append(tabelle(ops, null, null));
        t.append("\\item Wie viele Bl\\\"ocke werden bei A und bei B aus dem Hauptspeicher gelesen? Wie viele W\\\"orter schreibt A, "
                + "wie viele Bl\\\"ocke schreibt B in den Hauptspeicher (bis zum letzten Zugriff)?\n");
        t.append("\\item Welche Bl\\\"ocke sind bei B nach dem letzten Zugriff dirty?\n");
        t.append("\\end{enumerate}\n");

        StringBuilder dirty = new StringBuilder();
        for (int s = 0; s < b.saetze; s++) {
            if (b.dirty(s, 0)) {
                int start = b.blockAdresse(b.inhalt(s, 0), s);
                dirty.append(dirty.length() == 0 ? "" : ", ").append("Satz ").append(s).append(" (\\texttt{")
                        .append(Tex.hex(start)).append(blockWorte > 1 ? "--" + Tex.hex(start + 4 * blockWorte - 1) : "")
                        .append("})");
            }
        }
        StringBuilder l = new StringBuilder();
        l.append("Satz $= (\\text{Adresse} / ").append(4 * blockWorte).append(") \\bmod ").append(bloecke).append("$.\n");
        l.append("\\begin{enumerate}[label=\\alph*)]\n");
        l.append("\\item Ausgef\\\"ullte Tabelle:\n").append(tabelle(ops, za, zb));
        l.append("\\item A: ").append(lesenA).append(" Bl\\\"ocke gelesen (nur Lese-Misses laden), ").append(schreibenA)
                .append(" W\\\"orter geschrieben (jedes \\texttt{sw}). B: ").append(lesenB).append(" Bl\\\"ocke gelesen (jeder Miss), ")
                .append(schreibenB).append(schreibenB == 1 ? " Block" : " Bl\\\"ocke").append(" zur\\\"uckgeschrieben.\n");
        l.append("\\item ").append(dirty.length() == 0 ? "keiner" : dirty).append(" -- diese m\\\"ussen sp\\\"ater noch "
                + "zur\\\"uckgeschrieben werden.\n");
        l.append("\\end{enumerate}\n");

        StringBuilder kurz = new StringBuilder();
        for (Op o : ops) {
            kurz.append(o.schreiben() ? "sw " : "lw ").append(Tex.hex(o.adresse())).append("; ");
        }
        String frage = "DM 4 Bloecke x " + blockWorte + " W: " + kurz;
        String antwort = "A: " + lesenA + " Bl gelesen, " + schreibenA + " W geschrieben; B: " + lesenB + " Bl gelesen, " + schreibenB
                + " Bl zurueck; dirty: " + dirty.toString().replaceAll("\\\\texttt\\{|\\}", "");
        return new Part("Schreibstrategien", t.toString(), l.toString(), frage, antwort);
    }

    private static String tabelle(List<Op> ops, List<CacheModel.Zugriff> za, List<CacheModel.Zugriff> zb) {
        List<List<String>> zeilen = new ArrayList<>();
        for (int i = 0; i < ops.size(); i++) {
            if (za == null) {
                zeilen.add(List.of(Integer.toString(i + 1), ops.get(i).text(), "", "", ""));
            } else {
                CacheModel.Zugriff b = zb.get(i);
                String rueck = b.verdraengtDirty() ? "ja, Tag \\texttt{" + Tex.hex(b.verdraengt()) + "} aus Satz " + b.satz() : "--";
                zeilen.add(List.of(Integer.toString(i + 1), ops.get(i).text(), za.get(i).hit() ? "Hit" : "Miss",
                        b.hit() ? "Hit" : "Miss", rueck));
            }
        }
        return MemTex.tabelle("|r|l|p{1.6cm}|p{1.6cm}|p{4.6cm}|", List.of("Nr.", "Zugriff", "A", "B", "B: zur\\\"uckgeschrieben"),
                zeilen, za == null);
    }

    /** Zweite Zaehlung: eigene Tabelle Satz -> {Tag, dirty}. */
    private static void unabhaengig(List<Op> ops, int blockWorte, int bloecke, int lesenA, int schreibenA, int lesenB, int schreibenB) {
        Map<Integer, Integer> wt = new HashMap<>();
        Map<Integer, int[]> wb = new HashMap<>();
        int la = 0;
        int sa = 0;
        int lb = 0;
        int sbk = 0;
        for (Op o : ops) {
            int block = o.adresse() / (4 * blockWorte);
            int satz = block % bloecke;
            int tag = block / bloecke;
            boolean hitA = wt.containsKey(satz) && wt.get(satz) == tag;
            if (o.schreiben()) {
                sa++;
            } else if (!hitA) {
                la++;
                wt.put(satz, tag);
            }
            int[] e = wb.get(satz);
            boolean hitB = e != null && e[0] == tag;
            if (!hitB) {
                lb++;
                if (e != null && e[1] == 1) {
                    sbk++;
                }
                e = new int[]{tag, 0};
                wb.put(satz, e);
            }
            if (o.schreiben()) {
                e[1] = 1;
            }
        }
        if (la != lesenA || sa != schreibenA || lb != lesenB || sbk != schreibenB) {
            throw new IllegalStateException("Interner Fehler: Schreibstrategien weichen ab");
        }
    }
}
