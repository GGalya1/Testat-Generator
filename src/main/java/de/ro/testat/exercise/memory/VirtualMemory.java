package de.ro.testat.exercise.memory;

import de.ro.testat.exercise.Exercise;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * "Virtueller Speicher" (Harris &amp; Harris, Kap. 8.4): Bitbreiten von Seitenoffset, VPN und PPN,
 * Groesse der Seitentabelle und Uebersetzung virtueller Adressen mit TLB und Seitentabelle
 * (Treffer im TLB, Treffer in der Seitentabelle, Seitenfehler).
 *
 * <p>Kontrolle: Uebersetzung per Bitoperationen == per Division/Rest, physische Adresse passt
 * in die physische Adressbreite, TLB-Eintraege stimmen mit der Seitentabelle ueberein.
 */
public final class VirtualMemory implements Exercise {

    record Eintrag(int vpn, boolean gueltig, int ppn) {
    }

    @Override
    public String id() {
        return "vm";
    }

    @Override
    public List<String> preamble() {
        return List.of("\\usepackage{amsmath}");
    }

    @Override
    public Part generate(Random rng) {
        int vaBits = 32;
        int paBits = List.of(24, 26, 28, 30).get(rng.nextInt(4));
        int off = 10 + rng.nextInt(5);                 // 1 KiB .. 16 KiB
        int vpnBits = vaBits - off;
        int ppnBits = paBits - off;
        long seite = 1L << off;

        // Seitentabellen-Ausschnitt: 6 Eintraege, 4 gueltig
        Map<Integer, Eintrag> tabelle = new LinkedHashMap<>();
        while (tabelle.size() < 6) {
            int vpn = rng.nextInt(1 << vpnBits);
            if (!tabelle.containsKey(vpn)) {
                boolean v = tabelle.size() < 4;
                tabelle.put(vpn, new Eintrag(vpn, v, v ? rng.nextInt(1 << ppnBits) : 0));
            }
        }
        List<Eintrag> gueltig = tabelle.values().stream().filter(Eintrag::gueltig).toList();
        List<Eintrag> ungueltig = tabelle.values().stream().filter(e -> !e.gueltig()).toList();
        List<Eintrag> tlb = List.of(gueltig.get(0), gueltig.get(1));
        // Uebersetzungen: 1 TLB-Treffer, 2 nur Seitentabelle, 1 Seitenfehler
        List<Eintrag> ziele = new ArrayList<>(List.of(gueltig.get(rng.nextInt(2)), gueltig.get(2), gueltig.get(3),
                ungueltig.get(rng.nextInt(2))));
        Collections.shuffle(ziele, rng);
        List<Integer> va = new ArrayList<>();
        for (Eintrag e : ziele) {
            va.add((int) (((long) e.vpn() << off) | rng.nextInt((int) seite)));
        }
        List<Eintrag> zeigen = new ArrayList<>(tabelle.values());
        zeigen.sort((x, y) -> Integer.compareUnsigned(x.vpn(), y.vpn()));

        int vpnHex = (vpnBits + 3) / 4;
        int ppnHex = Math.max(1, (ppnBits + 3) / 4);
        StringBuilder a = new StringBuilder();
        a.append("Ein System hat 32-Bit-virtuelle Adressen, ").append(paBits).append("-Bit-physische Adressen und Seiten der Gr\\\"o\\ss{}e ")
                .append(CacheModel.groesse(seite)).append(". Ein Seitentabelleneintrag besteht aus einem Valid-Bit und der "
                + "physischen Seitennummer (PPN).\n");
        a.append("\\begin{enumerate}[label=\\alph*)]\n");
        a.append("\\item Wie viele Bit haben Seitenoffset, virtuelle Seitennummer (VPN) und PPN? Wie viele virtuelle und wie viele "
                + "physische Seiten gibt es?\n");
        a.append("\\item Wie viele Eintr\\\"age hat die Seitentabelle, und wie viele Bit belegt sie insgesamt?\n");
        a.append("\\item Ausschnitt der Seitentabelle (links) und Inhalt des TLB (rechts, voll assoziativ):\n");
        a.append("\\begin{center}\n").append(seitentabelle(zeigen, vpnHex, ppnHex)).append("\\hspace{1.5cm}\n")
                .append(tlbTabelle(tlb, vpnHex, ppnHex)).append("\\end{center}\n");
        a.append("\\\"Ubersetzen Sie die virtuellen Adressen. Geben Sie VPN, Offset, ob der TLB trifft und die physische Adresse "
                + "an (oder \\enquote{Seitenfehler}).\n");
        a.append(uebersetzung(va, null, off, vpnHex, tabelle, tlb, paBits));
        a.append("\\end{enumerate}\n");

        long eintraege = 1L << vpnBits;
        long bits = eintraege * (1 + ppnBits);
        StringBuilder l = new StringBuilder("\\begin{enumerate}[label=\\alph*)]\n");
        l.append("\\item Offset $\\log_2(").append(MemTex.zahl(seite)).append(") = ").append(off).append("$~Bit, VPN $32 - ").append(off)
                .append(" = ").append(vpnBits).append("$~Bit, PPN $").append(paBits).append(" - ").append(off).append(" = ").append(ppnBits)
                .append("$~Bit. $2^{").append(vpnBits).append("}$ virtuelle Seiten, $2^{").append(ppnBits).append("} = ")
                .append(MemTex.zahl(1L << ppnBits)).append("$ physische Seiten.\n");
        l.append("\\item $2^{").append(vpnBits).append("} = ").append(MemTex.zahl(eintraege)).append("$ Eintr\\\"age zu je $1 + ")
                .append(ppnBits).append(" = ").append(1 + ppnBits).append("$~Bit, zusammen $2^{").append(vpnBits).append("} \\cdot ")
                .append(1 + ppnBits).append(" = ").append(MemTex.zahl(bits)).append("$~Bit.\n");
        l.append("\\item Die unteren ").append(off).append("~Bit bleiben, die VPN wird durch die PPN ersetzt.\n")
                .append(uebersetzung(va, ziele, off, vpnHex, tabelle, tlb, paBits));
        l.append("\\end{enumerate}\n");

        StringBuilder kurz = new StringBuilder();
        for (int i = 0; i < va.size(); i++) {
            Eintrag e = ziele.get(i);
            kurz.append(CacheModel.hex8(va.get(i))).append("->")
                    .append(e.gueltig() ? CacheModel.hex8((int) pa(va.get(i), e.ppn(), off)) : "PF").append(' ');
        }
        String frage = "PA " + paBits + " Bit, Seite " + seite + " B, VAs " + va.stream().map(CacheModel::hex8).toList();
        String antwort = "off/VPN/PPN=" + off + "/" + vpnBits + "/" + ppnBits + ", PT " + bits + " Bit; " + kurz.toString().trim();
        return new Part("Virtueller Speicher", a.toString(), l.toString(), frage, antwort);
    }

    static long pa(int va, int ppn, int off) {
        long viaBits = ((long) ppn << off) | (va & ((1L << off) - 1));
        long viaDiv = (long) ppn * (1L << off) + Integer.toUnsignedLong(va) % (1L << off);
        if (viaBits != viaDiv) {
            throw new IllegalStateException("Interner Fehler: Uebersetzung");
        }
        return viaBits;
    }

    private static String hexN(long v, int stellen) {
        return "0x" + String.format("%0" + stellen + "X", v);
    }

    private static String seitentabelle(List<Eintrag> zeigen, int vpnHex, int ppnHex) {
        StringBuilder sb = new StringBuilder("\\begin{tabular}[t]{|c|c|c|}\n\\hline\n\\textbf{VPN} & \\textbf{V} & \\textbf{PPN} \\\\\n\\hline\n");
        for (Eintrag e : zeigen) {
            sb.append("\\texttt{").append(hexN(Integer.toUnsignedLong(e.vpn()), vpnHex)).append("} & ").append(e.gueltig() ? 1 : 0)
                    .append(" & ").append(e.gueltig() ? "\\texttt{" + hexN(e.ppn(), ppnHex) + "}" : "--").append(" \\\\\n");
        }
        return sb.append("\\hline\n\\end{tabular}\n").toString();
    }

    private static String tlbTabelle(List<Eintrag> tlb, int vpnHex, int ppnHex) {
        StringBuilder sb = new StringBuilder("\\begin{tabular}[t]{|c|c|c|}\n\\hline\n\\textbf{V} & \\textbf{VPN} & \\textbf{PPN} \\\\\n\\hline\n");
        for (Eintrag e : tlb) {
            sb.append("1 & \\texttt{").append(hexN(Integer.toUnsignedLong(e.vpn()), vpnHex)).append("} & \\texttt{")
                    .append(hexN(e.ppn(), ppnHex)).append("} \\\\\n");
        }
        return sb.append("\\hline\n\\end{tabular}\n").toString();
    }

    private static String uebersetzung(List<Integer> va, List<Eintrag> ziele, int off, int vpnHex, Map<Integer, Eintrag> tabelle,
                                       List<Eintrag> tlb, int paBits) {
        List<List<String>> zeilen = new ArrayList<>();
        int offHex = (off + 3) / 4;
        int paHex = (paBits + 3) / 4;
        for (int i = 0; i < va.size(); i++) {
            int v = va.get(i);
            if (ziele == null) {
                zeilen.add(List.of("\\texttt{" + CacheModel.hex8(v) + "}", "", "", "", ""));
                continue;
            }
            int vpn = v >>> off;
            if (vpn != (int) (Integer.toUnsignedLong(v) / (1L << off)) || vpn != ziele.get(i).vpn()) {
                throw new IllegalStateException("Interner Fehler: VPN");
            }
            Eintrag e = tabelle.get(vpn);
            boolean imTlb = tlb.stream().anyMatch(t -> t.vpn() == vpn);
            if (imTlb && (!e.gueltig() || tlb.stream().filter(t -> t.vpn() == vpn).findFirst().orElseThrow().ppn() != e.ppn())) {
                throw new IllegalStateException("Interner Fehler: TLB passt nicht zur Seitentabelle");
            }
            String ergebnis;
            if (e.gueltig()) {
                long p = pa(v, e.ppn(), off);
                if (p >= 1L << paBits) {
                    throw new IllegalStateException("Interner Fehler: physische Adresse zu gross");
                }
                ergebnis = "\\texttt{" + hexN(p, paHex) + "}";
            } else {
                ergebnis = "Seitenfehler (V = 0)";
            }
            zeilen.add(List.of("\\texttt{" + CacheModel.hex8(v) + "}", "\\texttt{" + hexN(Integer.toUnsignedLong(vpn), vpnHex) + "}",
                    "\\texttt{" + hexN(v & ((1L << off) - 1), offHex) + "}", imTlb ? "ja" : "nein", ergebnis));
        }
        return MemTex.tabelle("|c|p{2cm}|p{1.6cm}|p{1.5cm}|p{3.8cm}|",
                List.of("virt.\\ Adresse", "VPN", "Offset", "TLB-Treffer", "phys.\\ Adresse"), zeilen, ziele == null);
    }
}
