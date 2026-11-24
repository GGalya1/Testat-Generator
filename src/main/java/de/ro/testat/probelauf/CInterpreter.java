package de.ro.testat.probelauf;

import de.ro.testat.probelauf.CParser.Assign;
import de.ro.testat.probelauf.CParser.Binary;
import de.ro.testat.probelauf.CParser.Block;
import de.ro.testat.probelauf.CParser.Break;
import de.ro.testat.probelauf.CParser.Call;
import de.ro.testat.probelauf.CParser.Continue;
import de.ro.testat.probelauf.CParser.Decl;
import de.ro.testat.probelauf.CParser.DeclItem;
import de.ro.testat.probelauf.CParser.DoWhile;
import de.ro.testat.probelauf.CParser.Empty;
import de.ro.testat.probelauf.CParser.Expr;
import de.ro.testat.probelauf.CParser.ExprStmt;
import de.ro.testat.probelauf.CParser.For;
import de.ro.testat.probelauf.CParser.Func;
import de.ro.testat.probelauf.CParser.If;
import de.ro.testat.probelauf.CParser.IncDec;
import de.ro.testat.probelauf.CParser.Index;
import de.ro.testat.probelauf.CParser.Num;
import de.ro.testat.probelauf.CParser.Pos;
import de.ro.testat.probelauf.CParser.Programm;
import de.ro.testat.probelauf.CParser.Return;
import de.ro.testat.probelauf.CParser.Stmt;
import de.ro.testat.probelauf.CParser.Str;
import de.ro.testat.probelauf.CParser.Ternary;
import de.ro.testat.probelauf.CParser.Unary;
import de.ro.testat.probelauf.CParser.Var;
import de.ro.testat.probelauf.CParser.While;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Fuehrt ein {@link CParser.Programm} aus (32-Bit-int wie auf RISC-V). Meldet Laufzeitfehler mit
 * Zeile/Spalte: Lesen ohne Wert, nicht deklarierte Variable, Division durch 0, Index ausserhalb,
 * Endlosschleife (Schrittlimit), zu tiefe Rekursion.
 */
public final class CInterpreter {

    /** Ergebnis: Variablen am Ende (Hauptebene bzw. main), Ausgabe, Rueckgabewert von main. */
    public record Ergebnis(Map<String, String> variablen, String ausgabe, Integer rueckgabe, int schritte,
                           String endeGrund, boolean abgebrochen, List<Meldung> meldungen) {
    }

    /** Speicherplatz einer Variable; arr != null bei Arrays (Elemente null = noch kein Wert). */
    static final class Slot {
        Integer wert;
        Integer[] arr;
    }

    static final class Laufzeitfehler extends RuntimeException {
        final Pos pos;

        Laufzeitfehler(Pos pos, String text) {
            super(text);
            this.pos = pos;
        }
    }

    private static final class ReturnSignal extends RuntimeException {
        final Integer wert;

        ReturnSignal(Integer wert) {
            super(null, null, false, false);
            this.wert = wert;
        }
    }

    private static final class BreakSignal extends RuntimeException {
        BreakSignal() {
            super(null, null, false, false);
        }
    }

    private static final class ContinueSignal extends RuntimeException {
        ContinueSignal() {
            super(null, null, false, false);
        }
    }

    /** Aufrufrahmen: Bloecke von innen nach aussen; der aeusserste nimmt nicht deklarierte Variablen auf. */
    private static final class Rahmen {
        final Deque<Map<String, Slot>> bloecke = new ArrayDeque<>();
        final String funktion;

        Rahmen(String funktion) {
            this.funktion = funktion;
            bloecke.push(new LinkedHashMap<>());
        }
    }

    private final Map<String, Func> funktionen = new HashMap<>();
    private final Map<String, Slot> global = new LinkedHashMap<>();
    private final Deque<Rahmen> stapel = new ArrayDeque<>();
    private final StringBuilder ausgabe = new StringBuilder();
    private final List<Meldung> meldungen = new ArrayList<>();
    private final Set<String> automatisch = new LinkedHashSet<>();
    private final int maxSchritte;
    private int schritte;

    private CInterpreter(int maxSchritte) {
        this.maxSchritte = maxSchritte;
    }

    /** Wo ein Lauf ausgefuehrt wird, z. B. direkt oder in einem Thread mit grossem Stack. */
    public interface Ausfuehrung {
        Ergebnis ausfuehren(java.util.function.Supplier<Ergebnis> lauf);
    }

    /**
     * Standard: direkt im aufrufenden Thread. Das reicht dem Generator (getestet mit 256 KB Stack)
     * und geht auch im Browser (TeaVM), wo es keine echten Threads gibt. Die Kommandozeile schaltet
     * mit {@link GrosserStack#aktivieren()} auf einen Thread mit grossem Stack um, damit im Probelauf
     * auch tiefe Rekursion (bis 2000 Aufrufe) geht.
     */
    private static volatile Ausfuehrung ausfuehrung = java.util.function.Supplier::get;

    public static void setAusfuehrung(Ausfuehrung a) {
        ausfuehrung = java.util.Objects.requireNonNull(a);
    }

    public static Ergebnis run(Programm p, int maxSchritte) {
        return ausfuehrung.ausfuehren(() -> new CInterpreter(maxSchritte).lauf(p));
    }

    private Ergebnis lauf(Programm p) {
        p.funktionen().forEach(f -> funktionen.put(f.name(), f));
        Func main = funktionen.get("main");
        boolean nurDeklarationen = p.oben().stream().allMatch(s -> s instanceof Decl || s instanceof Empty);
        Integer rueck = null;
        String grund;
        boolean abbruch = false;
        Map<String, Slot> sicht;
        try {
            if (main != null && !nurDeklarationen) {
                Stmt s = p.oben().stream().filter(x -> !(x instanceof Decl) && !(x instanceof Empty)).findFirst().orElseThrow();
                throw new Laufzeitfehler(s.pos(), "Anweisung ausserhalb einer Funktion (es gibt eine main-Funktion; "
                        + "Anweisungen gehoeren dort hinein)");
            }
            if (main != null) {
                for (Stmt s : p.oben()) {
                    if (s instanceof Decl d) {
                        deklariere(d, global, true);
                    }
                }
                if (!main.params().isEmpty()) {
                    meldungen.add(Meldung.hinweis(main.pos().zeile(), "Parameter von main werden ignoriert"));
                }
                Rahmen r = new Rahmen("main");
                stapel.push(r);
                try {
                    ausfuehren(main.rumpf().stmts(), r);
                    grund = "Programmende erreicht (Ende von main)";
                } catch (ReturnSignal ret) {
                    rueck = ret.wert;
                    grund = "Programmende erreicht (return in main)";
                }
                sicht = r.bloecke.peekLast();
            } else {
                Rahmen r = new Rahmen("(Hauptebene)");
                stapel.push(r);
                try {
                    ausfuehren(p.oben(), r);
                    grund = "Programmende erreicht (letzte Anweisung)";
                } catch (ReturnSignal ret) {
                    rueck = ret.wert;
                    grund = "Programmende durch return";
                }
                sicht = r.bloecke.peekLast();
            }
        } catch (Laufzeitfehler f) {
            meldungen.add(Meldung.fehler(f.pos.zeile(), f.pos.spalte(), f.getMessage()));
            grund = "Laufzeitfehler in Zeile " + f.pos.zeile();
            abbruch = true;
            sicht = stapel.isEmpty() ? global : stapel.peekLast().bloecke.peekLast();
        } catch (BreakSignal | ContinueSignal s) {
            grund = "break/continue ausserhalb einer Schleife";
            abbruch = true;
            meldungen.add(Meldung.fehler(0, grund));
            sicht = stapel.peekLast().bloecke.peekLast();
        } catch (StackOverflowError e) {
            grund = "Rekursion zu tief";
            abbruch = true;
            meldungen.add(Meldung.fehler(0, "Rekursion zu tief (endet die Rekursion?)"));
            sicht = stapel.isEmpty() ? global : stapel.peekLast().bloecke.peekLast();
        }
        if (!automatisch.isEmpty()) {
            meldungen.add(0, Meldung.hinweis(0, "Ohne Deklaration verwendet und als int angenommen: "
                    + String.join(", ", automatisch)));
        }
        Map<String, String> vars = new LinkedHashMap<>();
        global.forEach((k, v) -> vars.put(k, text(v)));
        if (sicht != null && sicht != global) {
            sicht.forEach((k, v) -> vars.put(k, text(v)));
        }
        return new Ergebnis(vars, ausgabe.toString(), rueck, schritte, grund, abbruch, meldungen);
    }

    private static String text(Slot s) {
        if (s.arr != null) {
            StringBuilder sb = new StringBuilder("{");
            for (int i = 0; i < s.arr.length; i++) {
                sb.append(i == 0 ? "" : ", ").append(s.arr[i] == null ? "?" : s.arr[i]);
                if (i == 19 && s.arr.length > 20) {
                    sb.append(", ... (").append(s.arr.length).append(" Elemente)");
                    break;
                }
            }
            return sb.append("}").toString();
        }
        return s.wert == null ? "(kein Wert)" : Integer.toString(s.wert);
    }

    // ------------------------------------------------------------------ Anweisungen

    private void zaehle(Pos p) {
        if (++schritte > maxSchritte) {
            throw new Laufzeitfehler(p, "Schrittlimit (" + maxSchritte + ") erreicht - vermutlich Endlosschleife, zuletzt hier");
        }
    }

    private void ausfuehren(List<Stmt> stmts, Rahmen r) {
        for (Stmt s : stmts) {
            stmt(s, r);
        }
    }

    private void stmt(Stmt s, Rahmen r) {
        zaehle(s.pos());
        if (s instanceof Decl d) {
            deklariere(d, r.bloecke.peek(), false);
        } else if (s instanceof ExprStmt e) {
            Expr x = e.e();
            if (!(x instanceof Assign) && !(x instanceof IncDec) && !(x instanceof Call)) {
                meldungen.add(Meldung.hinweis(e.pos().zeile(), "Ausdruck ohne Wirkung (fehlt eine Zuweisung?)"));
            }
            wert(x, r);
        } else if (s instanceof If i) {
            if (wahr(i.bed(), r)) {
                unterblock(i.dann(), r);
            } else if (i.sonst() != null) {
                unterblock(i.sonst(), r);
            }
        } else if (s instanceof While w) {
            while (wahr(w.bed(), r)) {
                zaehle(w.pos());
                try {
                    unterblock(w.rumpf(), r);
                } catch (BreakSignal b) {
                    break;
                } catch (ContinueSignal c) {
                    // naechster Durchlauf
                }
            }
        } else if (s instanceof DoWhile d) {
            do {
                zaehle(d.pos());
                try {
                    unterblock(d.rumpf(), r);
                } catch (BreakSignal b) {
                    break;
                } catch (ContinueSignal c) {
                    // naechster Durchlauf
                }
            } while (wahr(d.bed(), r));
        } else if (s instanceof For f) {
            r.bloecke.push(new LinkedHashMap<>());
            try {
                if (!(f.init() instanceof Empty)) {
                    stmt(f.init(), r);
                }
                while (f.bed() == null || wahr(f.bed(), r)) {
                    zaehle(f.pos());
                    try {
                        unterblock(f.rumpf(), r);
                    } catch (BreakSignal b) {
                        break;
                    } catch (ContinueSignal c) {
                        // weiter zum Schritt
                    }
                    if (f.schritt() != null) {
                        wert(f.schritt(), r);
                    }
                }
            } finally {
                r.bloecke.pop();
            }
        } else if (s instanceof Block b) {
            unterblock(b, r);
        } else if (s instanceof Return ret) {
            throw new ReturnSignal(ret.wert() == null ? null : wert(ret.wert(), r));
        } else if (s instanceof Break) {
            throw new BreakSignal();
        } else if (s instanceof Continue) {
            throw new ContinueSignal();
        }
    }

    /** Fuehrt eine Anweisung in einem eigenen Block aus (Variablen darin sind danach nicht mehr sichtbar). */
    private void unterblock(Stmt s, Rahmen r) {
        if (s instanceof Block b) {
            r.bloecke.push(new LinkedHashMap<>());
            try {
                ausfuehren(b.stmts(), r);
            } finally {
                r.bloecke.pop();
            }
        } else {
            stmt(s, r);
        }
    }

    private void deklariere(Decl d, Map<String, Slot> ziel, boolean globalNull) {
        for (DeclItem it : d.items()) {
            if (ziel.containsKey(it.name())) {
                throw new Laufzeitfehler(it.pos(), "Variable '" + it.name() + "' ist in diesem Block schon deklariert");
            }
            if (funktionen.containsKey(it.name())) {
                throw new Laufzeitfehler(it.pos(), "'" + it.name() + "' ist schon der Name einer Funktion");
            }
            Slot s = new Slot();
            Rahmen r = stapel.peek();
            if (it.groesse() != null) {
                int n;
                if (it.groesse() instanceof Num num && num.wert() == -1) {
                    n = it.arrayInit().size();
                } else {
                    n = r == null ? konstante(it.groesse()) : wert(it.groesse(), r);
                }
                if (n <= 0 || n > 100_000) {
                    throw new Laufzeitfehler(it.pos(), "Array-Groesse " + n + " ist nicht erlaubt (1 .. 100000)");
                }
                s.arr = new Integer[n];
                if (it.arrayInit() != null) {
                    if (it.arrayInit().size() > n) {
                        throw new Laufzeitfehler(it.pos(), "Zu viele Werte fuer " + it.name() + "[" + n + "]");
                    }
                    for (int i = 0; i < n; i++) {
                        s.arr[i] = i < it.arrayInit().size() ? (r == null ? konstante(it.arrayInit().get(i))
                                : wert(it.arrayInit().get(i), r)) : 0;
                    }
                } else if (globalNull) {
                    java.util.Arrays.fill(s.arr, 0);
                }
            } else if (it.init() != null) {
                s.wert = r == null ? konstante(it.init()) : wert(it.init(), r);
            } else if (globalNull) {
                s.wert = 0;
            }
            ziel.put(it.name(), s);
        }
    }

    private int konstante(Expr e) {
        Rahmen tmp = new Rahmen("(global)");
        stapel.push(tmp);
        try {
            return wert(e, tmp);
        } finally {
            stapel.pop();
        }
    }

    // ------------------------------------------------------------------ Ausdruecke

    private boolean wahr(Expr e, Rahmen r) {
        return wert(e, r) != 0;
    }

    private Slot suche(String name, Rahmen r) {
        for (Map<String, Slot> b : r.bloecke) {
            Slot s = b.get(name);
            if (s != null) {
                return s;
            }
        }
        return global.get(name);
    }

    private Slot slotFuerZuweisung(String name, Pos p, Rahmen r) {
        Slot s = suche(name, r);
        if (s == null) {
            if (funktionen.containsKey(name)) {
                throw new Laufzeitfehler(p, "'" + name + "' ist eine Funktion, keine Variable");
            }
            s = new Slot();
            r.bloecke.peekLast().put(name, s);
            automatisch.add(name);
        }
        return s;
    }

    private Slot slotZumLesen(String name, Pos p, Rahmen r) {
        Slot s = suche(name, r);
        if (s == null) {
            throw new Laufzeitfehler(p, funktionen.containsKey(name) ? "'" + name + "' ist eine Funktion; Aufruf mit " + name + "(...)"
                    : "Variable '" + name + "' ist nicht deklariert");
        }
        return s;
    }

    private int wert(Expr e, Rahmen r) {
        if (e instanceof Num n) {
            return n.wert();
        }
        if (e instanceof Str st) {
            throw new Laufzeitfehler(st.pos(), "Zeichenketten sind nur in printf erlaubt");
        }
        if (e instanceof Var v) {
            Slot s = slotZumLesen(v.name(), v.pos(), r);
            if (s.arr != null) {
                throw new Laufzeitfehler(v.pos(), "'" + v.name() + "' ist ein Array; Element mit " + v.name() + "[i] lesen");
            }
            if (s.wert == null) {
                throw new Laufzeitfehler(v.pos(), "Variable '" + v.name() + "' wird gelesen, bevor sie einen Wert hat");
            }
            return s.wert;
        }
        if (e instanceof Index i) {
            Slot s = slotZumLesen(i.name(), i.pos(), r);
            int idx = index(s, i, r);
            if (s.arr[idx] == null) {
                throw new Laufzeitfehler(i.pos(), i.name() + "[" + idx + "] wird gelesen, bevor es einen Wert hat");
            }
            return s.arr[idx];
        }
        if (e instanceof Assign a) {
            return zuweisen(a, r);
        }
        if (e instanceof IncDec d) {
            int alt = wert(d.ziel(), r);
            int neu = d.plus() ? alt + 1 : alt - 1;
            schreibe(d.ziel(), neu, r);
            return d.prefix() ? neu : alt;
        }
        if (e instanceof Unary u) {
            int x = wert(u.e(), r);
            return switch (u.op()) {
                case "-" -> -x;
                case "!" -> x == 0 ? 1 : 0;
                default -> ~x;
            };
        }
        if (e instanceof Ternary t) {
            return wahr(t.bed(), r) ? wert(t.ja(), r) : wert(t.nein(), r);
        }
        if (e instanceof Binary b) {
            return binaer(b, r);
        }
        return aufruf((Call) e, r);
    }

    private int index(Slot s, Index i, Rahmen r) {
        if (s.arr == null) {
            throw new Laufzeitfehler(i.pos(), "'" + i.name() + "' ist kein Array");
        }
        int idx = wert(i.index(), r);
        if (idx < 0 || idx >= s.arr.length) {
            throw new Laufzeitfehler(i.pos(), "Index " + idx + " liegt ausserhalb von " + i.name() + "[0.." + (s.arr.length - 1) + "]");
        }
        return idx;
    }

    private int zuweisen(Assign a, Rahmen r) {
        int v = wert(a.wert(), r);
        if (!a.op().equals("=")) {
            int alt = wert(a.ziel(), r);
            v = rechne(a.op().substring(0, a.op().length() - 1), alt, v, a.pos());
        }
        schreibe(a.ziel(), v, r);
        return v;
    }

    private void schreibe(Expr ziel, int v, Rahmen r) {
        if (ziel instanceof Var var) {
            Slot s = slotFuerZuweisung(var.name(), var.pos(), r);
            if (s.arr != null) {
                throw new Laufzeitfehler(var.pos(), "Einem ganzen Array kann nichts zugewiesen werden; Element mit "
                        + var.name() + "[i] = ...");
            }
            s.wert = v;
        } else {
            Index i = (Index) ziel;
            Slot s = slotZumLesen(i.name(), i.pos(), r);
            s.arr[index(s, i, r)] = v;
        }
    }

    private int binaer(Binary b, Rahmen r) {
        if (b.op().equals("&&")) {
            return wahr(b.l(), r) && wahr(b.r(), r) ? 1 : 0;
        }
        if (b.op().equals("||")) {
            return wahr(b.l(), r) || wahr(b.r(), r) ? 1 : 0;
        }
        return rechne(b.op(), wert(b.l(), r), wert(b.r(), r), b.pos());
    }

    private int rechne(String op, int x, int y, Pos p) {
        return switch (op) {
            case "+" -> x + y;
            case "-" -> x - y;
            case "*" -> x * y;
            case "/" -> {
                if (y == 0) {
                    throw new Laufzeitfehler(p, "Division durch 0");
                }
                yield x == Integer.MIN_VALUE && y == -1 ? x : x / y;
            }
            case "%" -> {
                if (y == 0) {
                    throw new Laufzeitfehler(p, "Rest (%) bei Division durch 0");
                }
                yield x == Integer.MIN_VALUE && y == -1 ? 0 : x % y;
            }
            case "<<" -> x << (y & 31);
            case ">>" -> x >> (y & 31);
            case "&" -> x & y;
            case "|" -> x | y;
            case "^" -> x ^ y;
            case "==" -> x == y ? 1 : 0;
            case "!=" -> x != y ? 1 : 0;
            case "<" -> x < y ? 1 : 0;
            case "<=" -> x <= y ? 1 : 0;
            case ">" -> x > y ? 1 : 0;
            case ">=" -> x >= y ? 1 : 0;
            default -> throw new Laufzeitfehler(p, "Operator " + op + " unbekannt");
        };
    }

    // ------------------------------------------------------------------ Aufrufe

    private int aufruf(Call c, Rahmen r) {
        switch (c.name()) {
            case "printf" -> {
                return printf(c, r);
            }
            case "putchar" -> {
                if (c.args().size() != 1) {
                    throw new Laufzeitfehler(c.pos(), "putchar erwartet genau ein Argument");
                }
                int ch = wert(c.args().get(0), r);
                ausgabe.append((char) (ch & 0xFF));
                return ch;
            }
            default -> {
            }
        }
        Func f = funktionen.get(c.name());
        if (f == null) {
            throw new Laufzeitfehler(c.pos(), "Funktion '" + c.name() + "' ist nicht definiert"
                    + (Set.of("scanf", "malloc", "free", "strlen", "abs", "pow").contains(c.name())
                    ? " (Bibliotheksfunktionen ausser printf/putchar gibt es hier nicht)" : ""));
        }
        if (f.params().size() != c.args().size()) {
            throw new Laufzeitfehler(c.pos(), "'" + f.name() + "' erwartet " + f.params().size() + " Argument(e), uebergeben: "
                    + c.args().size());
        }
        if (stapel.size() > 2000) {
            throw new Laufzeitfehler(c.pos(), "Rekursion zu tief (mehr als 2000 Aufrufe) - endet die Rekursion?");
        }
        Rahmen neu = new Rahmen(f.name());
        for (int i = 0; i < f.params().size(); i++) {
            CParser.Param par = f.params().get(i);
            Expr arg = c.args().get(i);
            Slot s = new Slot();
            if (par.array()) {
                if (!(arg instanceof Var v) || slotZumLesen(v.name(), v.pos(), r).arr == null) {
                    throw new Laufzeitfehler(arg.pos(), "Parameter '" + par.name() + "' von " + f.name() + " erwartet ein Array");
                }
                s = slotZumLesen(v.name(), v.pos(), r); // Arrays werden als Referenz uebergeben
            } else {
                s.wert = wert(arg, r);
            }
            neu.bloecke.peek().put(par.name(), s);
        }
        stapel.push(neu);
        try {
            ausfuehren(f.rumpf().stmts(), neu);
            if (!f.istVoid()) {
                meldungen.add(Meldung.hinweis(f.pos().zeile(), "Funktion '" + f.name() + "' endet ohne return (Wert 0 angenommen)"));
            }
            return 0;
        } catch (ReturnSignal ret) {
            if (f.istVoid() && ret.wert != null) {
                throw new Laufzeitfehler(c.pos(), "void-Funktion '" + f.name() + "' gibt einen Wert zurueck");
            }
            if (!f.istVoid() && ret.wert == null) {
                throw new Laufzeitfehler(c.pos(), "'" + f.name() + "' muss einen Wert zurueckgeben (return ...;)");
            }
            return ret.wert == null ? 0 : ret.wert;
        } catch (BreakSignal | ContinueSignal s) {
            throw new Laufzeitfehler(f.pos(), "break/continue ausserhalb einer Schleife in '" + f.name() + "'");
        } finally {
            stapel.pop();
        }
    }

    private int printf(Call c, Rahmen r) {
        if (c.args().isEmpty() || !(c.args().get(0) instanceof Str fmt)) {
            throw new Laufzeitfehler(c.pos(), "printf erwartet als erstes Argument eine Zeichenkette \"...\"");
        }
        String f = fmt.text();
        int arg = 1;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < f.length(); i++) {
            char ch = f.charAt(i);
            if (ch != '%') {
                sb.append(ch);
                continue;
            }
            if (i + 1 >= f.length()) {
                throw new Laufzeitfehler(c.pos(), "Unvollstaendiges Format am Ende der printf-Zeichenkette");
            }
            char spec = f.charAt(++i);
            if (spec == '%') {
                sb.append('%');
                continue;
            }
            if (arg >= c.args().size()) {
                throw new Laufzeitfehler(c.pos(), "printf: zu wenige Argumente fuer %" + spec);
            }
            int v = wert(c.args().get(arg++), r);
            switch (spec) {
                case 'd', 'i' -> sb.append(v);
                case 'u' -> sb.append(Integer.toUnsignedString(v));
                case 'x' -> sb.append(Integer.toHexString(v));
                case 'X' -> sb.append(Integer.toHexString(v).toUpperCase());
                case 'c' -> sb.append((char) (v & 0xFF));
                default -> throw new Laufzeitfehler(c.pos(), "printf: Format %" + spec + " wird nicht unterstuetzt (%d %u %x %c)");
            }
        }
        if (arg < c.args().size()) {
            meldungen.add(Meldung.hinweis(c.pos().zeile(), "printf: mehr Argumente als Formatangaben"));
        }
        ausgabe.append(sb);
        return sb.length();
    }
}
