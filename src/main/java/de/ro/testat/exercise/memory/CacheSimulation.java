package de.ro.testat.exercise.memory;

import de.ro.testat.exercise.Auswahl;
import de.ro.testat.exercise.Exercise;
import de.ro.testat.latex.Tex;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * "Cache-Zugriffe nachvollziehen": Eine Folge von Lesezugriffen auf einen kleinen Cache
 * (direkt abgebildet, satzassoziativ oder voll assoziativ, LRU). Gefragt sind Satz, Tag,
 * Hit/Miss und Art des Misses je Zugriff, der Endinhalt und die Miss-Rate.
 *
 * <p>Die Folge wird so gezogen, dass Treffer, Verdraengungen und mindestens ein Conflict- (bzw.
 * bei voll assoziativ ein Capacity-) Miss vorkommen. Kontrolle: Treffer laut Modell ==
 * Treffer einer zweiten, unabhaengig geschriebenen LRU-Simulation (Listen je Satz).
 */
public final class CacheSimulation implements Exercise {

    /** Kapazitaet und Blockgroesse in Woertern, Wege. */
    record Konfig(int kapWorte, int blockWorte, int wege) {
        CacheModel cache() {
            return new CacheModel(4 * kapWorte, 4 * blockWorte, wege);
        }
    }

    static final List<Konfig> DIREKT = List.of(new Konfig(8, 1, 1), new Konfig(8, 2, 1), new Konfig(16, 2, 1), new Konfig(16, 4, 1));
    static final List<Konfig> ASSOZIATIV = List.of(new Konfig(8, 1, 2), new Konfig(16, 1, 2), new Konfig(16, 2, 2),
            new Konfig(16, 1, 4), new Konfig(16, 2, 4));
    static final List<Konfig> VOLL = List.of(new Konfig(4, 1, 4), new Konfig(8, 2, 4), new Konfig(16, 4, 4), new Konfig(8, 1, 8));

    private final String auswahl;

    public CacheSimulation() {
        this("zufall");
    }

    /** @param auswahl "direkt", "assoziativ", "voll" oder "zufall" */
    public CacheSimulation(String auswahl) {
        this.auswahl = Auswahl.variante(auswahl, "cache-sim", "direkt", "assoziativ", "voll");
    }

    @Override
    public String id() {
        return "cache-sim";
    }

    @Override
    public List<String> preamble() {
        return List.of("\\usepackage{amsmath}", "\\usepackage{tikz}", "\\usetikzlibrary{arrows.meta}", "\\usetikzlibrary{positioning,shapes.geometric,calc}",
                "\\providecommand{\\zmark}[1]{\\tikz[remember picture,overlay]\\coordinate (#1);}");
    }

    @Override
    public String describe() {
        return id() + "(" + auswahl + ")";
    }

    @Override
    public Part generate(Random rng) {
        String art = auswahl.equals("zufall") ? List.of("direkt", "direkt", "assoziativ", "assoziativ", "voll").get(rng.nextInt(5)) : auswahl;
        List<Konfig> pool = art.equals("direkt") ? DIREKT : art.equals("assoziativ") ? ASSOZIATIV : VOLL;
        Konfig k = pool.get(rng.nextInt(pool.size()));
        List<Integer> folge = folge(k, rng);
        CacheModel c = k.cache();
        List<CacheModel.Zugriff> z = new ArrayList<>();
        for (int a : folge) {
            z.add(c.lesen(a));
        }
        List<CacheModel.MissArt> arten = CacheModel.klassifiziere(k.cache(), folge);
        boolean[] kontrolle = lruUnabhaengig(k, folge);
        for (int i = 0; i < folge.size(); i++) {
            if (kontrolle[i] != z.get(i).hit()) {
                throw new IllegalStateException("Interner Fehler: Cache-Modell weicht ab bei Zugriff " + (i + 1));
            }
        }
        int misses = (int) z.stream().filter(x -> !x.hit()).count();

        StringBuilder a = new StringBuilder();
        a.append("Ein Cache hat ").append(c.beschreibung()).append(", Ersetzung LRU, und ist anfangs leer");
        if (c.wege > 1) {
            a.append("; ein neuer Block kommt in den freien Weg mit der kleinsten Nummer");
        }
        a.append(". Adressen sind 32-Bit-Byteadressen. Ein Programm liest (\\texttt{lw}) nacheinander die W\\\"orter an den "
                + "Adressen in der Tabelle.\n");
        a.append("\\begin{enumerate}[label=\\alph*)]\n");
        a.append("\\item F\\\"ullen Sie die Tabelle aus: Satz, Tag (hexadezimal), Hit oder Miss und bei einem Miss dessen Art "
                + "(Compulsory, Capacity oder Conflict).\n");
        a.append(zugriffsTabelle(folge, null, null, c));
        a.append("\\item Geben Sie den Inhalt des Caches nach dem letzten Zugriff an (Tag je Satz und Weg).\n");
        a.append(inhaltTabelle(c, true));
        a.append("\\item Wie hoch ist die Miss-Rate dieser Zugriffsfolge?\n");
        a.append("\\end{enumerate}\n");

        StringBuilder l = new StringBuilder();
        l.append("Zerlegung: Byte-Offset 2~Bit, Block-Offset ").append(c.blockOffsetBits()).append("~Bit, Satz ")
                .append(c.satzBits()).append("~Bit, Rest Tag. Damit Satz $= (\\text{Adresse} / ").append(c.blockBytes)
                .append(") \\bmod ").append(c.saetze).append("$, Tag $= \\text{Adresse} / ").append(c.blockBytes * c.saetze).append("$.\n");
        l.append("Um die Tabelle auszuf\\\"ullen, kann man diesen Ablauf auf jeden Zugriff anwenden:\n");
        l.append(ABLAUF);
        String praefix = "cs" + Integer.toHexString((folge.hashCode() * 31 + k.hashCode()) & 0x7FFFFFFF);
        List<Pfeil> pfeile = abhaengigkeiten(k.cache(), folge, arten);
        l.append("\\begin{enumerate}[label=\\alph*)]\n");
        l.append("\\item Ausgef\\\"ullte Tabelle; die Pfeile rechts zeigen, woher ein Treffer kommt bzw.\\ warum ein Block "
                + "nicht mehr im Cache war:\n").append(loesungsTabelle(folge, z, arten, pfeile, praefix)
                        .replace("\\end{tabular}\n", "\\end{tabular}%\n" + pfeilBild(pfeile, praefix)));
        l.append("{\\small \\textcolor{blue}{Blau}: Hit, der Block wurde zuletzt beim Zugriff am Pfeilanfang benutzt. "
                + "Andere Farben: Miss, obwohl der Block schon einmal im Cache war -- durchgezogen vom Zugriff, der ihn "
                + "verdr\\\"angt hat, gestrichelt vom letzten Zugriff auf ihn. Compulsory: erster Zugriff auf den Block. "
                + "Capacity: auch ein gleich gro\\ss{}er voll assoziativer LRU-Cache h\\\"atte den Block nicht mehr. "
                + "Conflict: alle anderen Misses.\\par}\n");
        l.append("\\item Inhalt nach dem letzten Zugriff (Tag und Adressbereich des Blocks):\n").append(inhaltTabelle(c, false));
        l.append("\\item Miss-Rate ").append(MemTex.rate(misses, folge.size())).append(".\n");
        l.append("\\end{enumerate}\n");

        StringBuilder hm = new StringBuilder();
        for (int i = 0; i < z.size(); i++) {
            hm.append(switch (arten.get(i)) {
                case HIT -> 'H';
                case COMPULSORY -> 'C';
                case CAPACITY -> 'A';
                case CONFLICT -> 'K';
            });
        }
        String frage = c.organisation() + ", " + k.kapWorte() + " W, Block " + k.blockWorte() + " W: "
                + String.join(" ", folge.stream().map(Tex::hex).toList());
        String antwort = hm + " (H=Hit, C=Compulsory, A=Capacity, K=Conflict), Miss-Rate " + misses + "/" + folge.size();
        return new Part("Cache-Zugriffe nachvollziehen", a.toString(), l.toString(), frage, antwort);
    }

    /** Zugriffsfolge: einige Bloecke im selben Satz (Konflikte), etwas Lokalitaet. */
    static List<Integer> folge(Konfig k, Random rng) {
        CacheModel muster = k.cache();
        for (int versuch = 0; versuch < 2000; versuch++) {
            int s0 = rng.nextInt(muster.saetze);
            List<Integer> bloecke = new ArrayList<>(); // Blocknummern
            int imSatz = muster.wege + 1 + (muster.saetze == 1 ? rng.nextInt(2) : rng.nextInt(2));
            Set<Integer> tags = new HashSet<>();
            while (tags.size() < imSatz) {
                tags.add(rng.nextInt(Math.max(8, imSatz + 4)));
            }
            for (int t : tags) {
                bloecke.add(t * muster.saetze + s0);
            }
            if (muster.saetze > 1) {
                int extra = 1 + rng.nextInt(2);
                for (int e = 0; e < extra; e++) {
                    int s = rng.nextInt(muster.saetze);
                    int b = rng.nextInt(8) * muster.saetze + s;
                    if (!bloecke.contains(b)) {
                        bloecke.add(b);
                    }
                }
            }
            int laenge = 10 + rng.nextInt(3);
            List<Integer> f = new ArrayList<>();
            int letzter = bloecke.get(0);
            for (int i = 0; i < laenge; i++) {
                int b = rng.nextInt(10) < 3 && i > 0 ? letzter : bloecke.get(rng.nextInt(bloecke.size()));
                int adr = b * muster.blockBytes + 4 * rng.nextInt(k.blockWorte());
                if (!f.isEmpty() && f.get(f.size() - 1) == adr) {
                    i--;
                    continue;
                }
                f.add(adr);
                letzter = b;
            }
            if (geeignet(k, f)) {
                return f;
            }
        }
        throw new IllegalStateException("Keine geeignete Zugriffsfolge gefunden fuer " + k);
    }

    private static boolean geeignet(Konfig k, List<Integer> f) {
        CacheModel c = k.cache();
        int hits = 0;
        int verdraengt = 0;
        for (int a : f) {
            CacheModel.Zugriff z = c.lesen(a);
            hits += z.hit() ? 1 : 0;
            verdraengt += z.verdraengt() != null ? 1 : 0;
        }
        List<CacheModel.MissArt> arten = CacheModel.klassifiziere(k.cache(), f);
        long konflikt = arten.stream().filter(x -> x == CacheModel.MissArt.CONFLICT).count();
        long kapazitaet = arten.stream().filter(x -> x == CacheModel.MissArt.CAPACITY).count();
        boolean voll = c.saetze == 1;
        return hits >= 2 && f.size() - hits >= 4 && verdraengt >= 1 && (voll ? kapazitaet >= 1 : konflikt >= 1);
    }

    /** Zweite LRU-Simulation, bewusst anders geschrieben: je Satz eine Liste, juengster Block hinten. */
    static boolean[] lruUnabhaengig(Konfig k, List<Integer> folge) {
        int saetze = k.kapWorte() / k.blockWorte() / k.wege();
        List<LinkedList<Integer>> satz = new ArrayList<>();
        for (int s = 0; s < saetze; s++) {
            satz.add(new LinkedList<>());
        }
        boolean[] hit = new boolean[folge.size()];
        for (int i = 0; i < folge.size(); i++) {
            int block = folge.get(i) / (4 * k.blockWorte());
            LinkedList<Integer> l = satz.get(block % saetze);
            hit[i] = l.remove((Integer) block);
            l.addLast(block);
            if (l.size() > k.wege()) {
                l.removeFirst();
            }
        }
        return hit;
    }

    /** Pfeil von Zugriff von nach Zugriff nach (Indizes ab 0). */
    record Pfeil(int von, int nach, boolean gestrichelt, String farbe) {
    }

    private static final String[] FARBEN = {"red", "orange", "violet", "brown", "teal", "magenta", "olive", "cyan!60!black"};

    /**
     * Hit: Pfeil vom letzten Zugriff auf denselben Block. Miss auf einen schon einmal geladenen
     * Block: durchgezogen vom verdraengenden Zugriff, gestrichelt vom letzten Zugriff auf den Block.
     */
    static List<Pfeil> abhaengigkeiten(CacheModel c, List<Integer> folge, List<CacheModel.MissArt> arten) {
        Map<Integer, Integer> zuletzt = new HashMap<>();
        Map<Integer, Integer> verdraengtDurch = new HashMap<>();
        List<Pfeil> out = new ArrayList<>();
        int farbe = 0;
        for (int i = 0; i < folge.size(); i++) {
            int a = folge.get(i);
            CacheModel.Zugriff z = c.lesen(a);
            int block = c.blocknummer(a);
            if (z.hit()) {
                out.add(new Pfeil(zuletzt.get(block), i, false, "blue"));
            } else if (arten.get(i) != CacheModel.MissArt.COMPULSORY) {
                String f = FARBEN[farbe++ % FARBEN.length];
                out.add(new Pfeil(verdraengtDurch.get(block), i, false, f));
                out.add(new Pfeil(zuletzt.get(block), i, true, f));
            }
            if (z.verdraengt() != null) {
                verdraengtDurch.put(z.verdraengt() * c.saetze + z.satz(), i);
            }
            zuletzt.put(block, i);
        }
        return out;
    }

    private static String loesungsTabelle(List<Integer> folge, List<CacheModel.Zugriff> z, List<CacheModel.MissArt> arten,
                                          List<Pfeil> pfeile, String praefix) {
        List<List<String>> zeilen = new ArrayList<>();
        for (int i = 0; i < folge.size(); i++) {
            CacheModel.Zugriff x = z.get(i);
            final int nr = i;
            String art = arten.get(i).text();
            Pfeil grund = pfeile.stream().filter(p -> p.nach() == nr && !p.gestrichelt() && !x.hit()).findFirst().orElse(null);
            if (grund != null) {
                art += ": verdr\\\"angt durch \\#" + (grund.von() + 1);
            }
            zeilen.add(List.of("\\#" + (i + 1), "\\texttt{" + Tex.hex(folge.get(i)) + "}", Integer.toString(x.satz()),
                    "\\texttt{" + Tex.hex(x.tag()) + "}", x.hit() ? "Hit" : "\\textbf{Miss}",
                    art + "\\hfill\\zmark{" + praefix + "-" + i + "}"));
        }
        return "\\renewcommand{\\arraystretch}{1.3}\n" + MemTex.tabelle("|r|c|c|c|c|p{4.9cm}|",
                List.of("Nr.", "Adresse", "Satz", "Tag", "Hit/Miss", "Miss-Art"), zeilen, false)
                + "\\renewcommand{\\arraystretch}{1}\n";
    }

    /** Pfeile rechts neben der Tabelle; Spuren so verteilt, dass sich Pfeile nicht ueberdecken. */
    private static String pfeilBild(List<Pfeil> pfeile, String praefix) {
        if (pfeile.isEmpty()) {
            return "";
        }
        List<Pfeil> sortiert = new ArrayList<>(pfeile);
        sortiert.sort((p, q) -> Integer.compare(p.nach() - p.von(), q.nach() - q.von()));
        List<List<Pfeil>> spuren = new ArrayList<>();
        Map<Pfeil, Integer> spur = new HashMap<>();
        for (Pfeil p : sortiert) {
            int s = 0;
            while (s < spuren.size() && spuren.get(s).stream().anyMatch(q -> !(q.nach() < p.von() || p.nach() < q.von()))) {
                s++;
            }
            if (s == spuren.size()) {
                spuren.add(new ArrayList<>());
            }
            spuren.get(s).add(p);
            spur.put(p, s);
        }
        double abstand = Math.min(0.32, 2.0 / spuren.size());
        StringBuilder sb = new StringBuilder("\\begin{tikzpicture}[remember picture, overlay, "
                + "pf/.style={-{Stealth[length=1.8mm]}, thick, rounded corners=3pt}]\n");
        for (Pfeil p : pfeile) {
            double x = 0.3 + abstand * spur.get(p);
            String ende = p.gestrichelt() ? "0.9ex" : "1.6ex";
            sb.append(String.format(java.util.Locale.ROOT,
                    "\\draw[pf, %s%s] ([xshift=7pt,yshift=-0.3ex]%s-%d) -- ++(%.2f,0) |- ([xshift=7pt,yshift=%s]%s-%d);%n",
                    p.farbe(), p.gestrichelt() ? ", dashed" : "", praefix, p.von(), x, ende, praefix, p.nach()));
        }
        return sb.append("\\end{tikzpicture}%\n").toString();
    }

    /** Ablaufdiagramm zur Einteilung eines Zugriffs (Hinweis in der Musterloesung). */
    static final String ABLAUF = String.join("\n",
            "\\begin{center}",
            "\\begin{tikzpicture}[node distance=0.55cm and 1.1cm, font=\\small,",
            "    startstop/.style={draw, rectangle, rounded corners, minimum width=3cm, minimum height=0.8cm, align=center, fill=gray!10, thick},",
            "    decision/.style={draw, diamond, aspect=2.2, minimum width=3.2cm, align=center, fill=yellow!10, thick, inner sep=1pt},",
            "    process/.style={draw, rectangle, minimum width=2.9cm, minimum height=0.8cm, align=center, fill=blue!5, thick},",
            "    arrow/.style={-Stealth, thick, draw=black!70}]",
            "  \\node (start)  [startstop] {Zugriff: Satz $S$, Tag $T$};",
            "  \\node (dec1)   [decision, below=of start] {Satz $S$ enth\\\"alt $T$?};",
            "  \\node (hit)    [process, right=of dec1] {Hit\\\\\\footnotesize(LRU aktualisieren)};",
            "  \\node (dec2)   [decision, below=of dec1] {Block schon\\\\einmal geladen?};",
            "  \\node (comp)   [process, left=of dec2] {Compulsory Miss};",
            "  \\node (dec3)   [decision, below=of dec2] {Voll assoziativer\\\\Cache: Hit?};",
            "  \\node (conf)   [process, left=of dec3] {Conflict Miss};",
            "  \\node (cap)    [process, right=of dec3] {Capacity Miss};",
            "  \\node (update) [process, below=of dec3] {Block in Satz $S$ laden\\\\\\footnotesize(ersetzt den LRU-Block)};",
            "  \\node (end)    [startstop, below=of update] {n\\\"achster Zugriff};",
            "  \\draw [arrow] (start) -- (dec1);",
            "  \\draw [arrow] (dec1) -- node[anchor=south] {Ja} (hit);",
            "  \\draw [arrow] (dec1) -- node[anchor=east] {Nein} (dec2);",
            "  \\draw [arrow] (dec2) -- node[anchor=south] {Nein} (comp);",
            "  \\draw [arrow] (dec2) -- node[anchor=east] {Ja} (dec3);",
            "  \\draw [arrow] (dec3) -- node[anchor=south] {Ja} (conf);",
            "  \\draw [arrow] (dec3) -- node[anchor=south] {Nein} (cap);",
            "  \\draw [arrow] (comp.west) -- ++(-0.4,0) |- (update.west);",
            "  \\draw [arrow] (conf) |- (update.west);",
            "  \\draw [arrow] (cap) |- (update.east);",
            "  \\draw [arrow] (update) -- (end);",
            "  \\draw [arrow] let \\p1 = (hit.east), \\p2 = (cap.east) in (hit.east) -- ({max(\\x1,\\x2)+0.5cm},\\y1) |- (end.east);",
            "\\end{tikzpicture}",
            "\\end{center}",
            "Der Vergleich mit einem voll assoziativen Cache gleicher Gr\\\"o\\ss{}e (gleiche Zugriffe, LRU) trennt Capacity- von "
                    + "Conflict-Misses; bei einem voll assoziativen Cache selbst gibt es keine Conflict-Misses.\\par",
            "");

    private static String zugriffsTabelle(List<Integer> folge, List<CacheModel.Zugriff> z, List<CacheModel.MissArt> arten, CacheModel c) {
        List<List<String>> zeilen = new ArrayList<>();
        for (int i = 0; i < folge.size(); i++) {
            if (z == null) {
                zeilen.add(List.of(Integer.toString(i + 1), "\\texttt{" + Tex.hex(folge.get(i)) + "}", "", "", "", ""));
            } else {
                CacheModel.Zugriff x = z.get(i);
                zeilen.add(List.of(Integer.toString(i + 1), "\\texttt{" + Tex.hex(folge.get(i)) + "}",
                        Integer.toString(x.satz()), "\\texttt{" + Tex.hex(x.tag()) + "}", x.hit() ? "Hit" : "Miss",
                        arten.get(i).text() + (x.verdraengt() != null ? " (verdr\\\"angt Tag \\texttt{" + Tex.hex(x.verdraengt()) + "})" : "")));
            }
        }
        return MemTex.tabelle("|r|c|p{1.1cm}|p{1.3cm}|p{1.6cm}|p{5.2cm}|",
                List.of("Nr.", "Adresse", "Satz", "Tag", "Hit/Miss", "Miss-Art"), zeilen, z == null);
    }

    private static String inhaltTabelle(CacheModel c, boolean leer) {
        StringBuilder spalten = new StringBuilder("|c|");
        List<String> kopf = new ArrayList<>();
        kopf.add("Satz");
        int breite = c.wege > 4 ? 1 : c.wege > 2 ? 2 : 3;
        for (int w = 0; w < c.wege; w++) {
            spalten.append(c.wege > 4 ? "c|" : "p{" + breite + ".2cm}|");
            kopf.add("Weg " + w);
        }
        List<List<String>> zeilen = new ArrayList<>();
        for (int s = 0; s < c.saetze; s++) {
            List<String> z = new ArrayList<>();
            z.add(Integer.toString(s));
            for (int w = 0; w < c.wege; w++) {
                Integer t = c.inhalt(s, w);
                if (leer) {
                    z.add("");
                } else if (t == null) {
                    z.add("leer");
                } else {
                    int start = c.blockAdresse(t, s);
                    String bereich = c.blockBytes == 4 ? Tex.hex(start)
                            : Tex.hex(start) + "--" + Tex.hex(start + c.blockBytes - 1);
                    z.add("\\texttt{" + Tex.hex(t) + "}" + (c.wege > 4 ? "" : " {\\footnotesize(" + bereich + ")}"));
                }
            }
            zeilen.add(z);
        }
        return MemTex.tabelle(spalten.toString(), kopf, zeilen, leer);
    }
}
