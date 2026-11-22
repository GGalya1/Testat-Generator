package de.ro.testat.exercise.functions;

import de.ro.testat.probelauf.RiscvMaschine;
import de.ro.testat.probelauf.RiscvParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Baut ein Assemblerprogramm Zeile fuer Zeile auf und kann es als Text (fuer den Simulator),
 * als LaTeX-Listing (optional mit Adressen wie in RARS) und als ausgefuehrtes Programm liefern.
 * Jeder Befehl entspricht genau einem Maschinenbefehl (keine Pseudobefehle, die RARS in zwei
 * Befehle uebersetzt), damit die Adressen im Listing stimmen.
 */
final class AsmText {

    /** Eine Zeile: Direktive/Marke (befehl == null) oder Befehl mit optionalem Kommentar. */
    record Zeile(String marke, String befehl, String kommentar, boolean markiert) {
    }

    private final List<Zeile> zeilen = new ArrayList<>();

    AsmText raw(String text) {
        zeilen.add(new Zeile(text, null, null, false));
        return this;
    }

    AsmText marke(String name) {
        zeilen.add(new Zeile(name + ":", null, null, false));
        return this;
    }

    AsmText b(String befehl) {
        return b(befehl, null);
    }

    AsmText b(String befehl, String kommentar) {
        zeilen.add(new Zeile(null, befehl, kommentar, false));
        return this;
    }

    /** Befehl mit Markierung (*) im Listing, z. B. fuer einen Haltepunkt. */
    AsmText markiert(String befehl, String kommentar) {
        zeilen.add(new Zeile(null, befehl, kommentar, true));
        return this;
    }

    /** Haengt alle Zeilen von t an. */
    AsmText anhaengen(AsmText t) {
        zeilen.addAll(t.zeilen);
        return this;
    }

    List<Zeile> zeilen() {
        return zeilen;
    }

    /** Quelltext; Zeile i (ab 1) entspricht zeilen().get(i-1). */
    String text() {
        StringBuilder sb = new StringBuilder();
        for (Zeile z : zeilen) {
            if (z.befehl() == null) {
                sb.append(z.marke());
            } else {
                sb.append("    ").append(z.befehl());
                if (z.kommentar() != null) {
                    sb.append(" ".repeat(Math.max(1, 22 - z.befehl().length()))).append("# ").append(z.kommentar());
                }
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    /** Quelltextzeile (ab 1) der markierten Zeile. */
    int markierteZeile() {
        for (int i = 0; i < zeilen.size(); i++) {
            if (zeilen.get(i).markiert()) {
                return i + 1;
            }
        }
        throw new IllegalStateException("keine Zeile markiert");
    }

    /** LaTeX-Listing; mitAdressen: Adresse vor jedem Befehl (ab 0x00400000, nur .text). */
    String listing(boolean mitAdressen, boolean kommentare) {
        StringBuilder sb = new StringBuilder("\\begin{lstlisting}\n");
        int adr = RiscvParser.TEXT_BASE;
        boolean text = true;
        for (Zeile z : zeilen) {
            if (z.befehl() == null) {
                if (z.marke().trim().equals(".data")) {
                    text = false;
                } else if (z.marke().trim().equals(".text")) {
                    text = true;
                }
                sb.append(mitAdressen ? " ".repeat(10) : "").append(z.marke()).append('\n');
                continue;
            }
            if (mitAdressen && text) {
                sb.append(String.format("%08X  ", adr));
                adr += 4;
            }
            String zeile = "    " + z.befehl();
            String k = kommentare && z.kommentar() != null ? z.kommentar() : null;
            if (z.markiert()) {
                k = "(*)" + (k == null ? "" : " " + k);
            }
            if (k != null) {
                zeile += " ".repeat(Math.max(1, 26 - zeile.length())) + "# " + k;
            }
            sb.append(lst(zeile)).append('\n');
        }
        return sb.append("\\end{lstlisting}\n").toString();
    }

    RiscvParser.Programm programm() {
        RiscvParser.Programm p = RiscvParser.parse(text());
        if (!p.fehlerfrei()) {
            throw new IllegalStateException("Interner Fehler: Musterloesung nicht uebersetzbar: " + p.meldungen() + "\n" + text());
        }
        return p;
    }

    RiscvMaschine.Ergebnis run(Map<Integer, Integer> start) {
        RiscvMaschine.Ergebnis e = RiscvMaschine.run(programm(), 1_000_000, start);
        if (e.abgebrochen()) {
            throw new IllegalStateException("Interner Fehler: Musterloesung bricht ab: " + e.meldungen() + "\n" + text());
        }
        return e;
    }

    /**
     * Text fuer ein lstlisting: Die Vorlage setzt escapechar=%, ein % im Code muss deshalb als
     * %\%% geschrieben werden (Escape nach LaTeX, dort \%).
     */
    static String lst(String code) {
        return code.replace("%", "%\\%%");
    }

    static int reg(String name) {
        return RiscvParser.register(name);
    }
}
