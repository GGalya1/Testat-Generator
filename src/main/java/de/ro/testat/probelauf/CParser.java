package de.ro.testat.probelauf;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Liest eine Teilmenge von C: int-Variablen und -Arrays, Funktionen (auch rekursiv),
 * if / else if / else, while, do-while, for, break, continue, return, printf/putchar und
 * alle ueblichen Operatoren. Anweisungen ohne main sind erlaubt (wie in den Testat-Aufgaben).
 * Bei einem Syntaxfehler wird abgebrochen und genau dieser Fehler mit Zeile/Spalte gemeldet.
 */
public final class CParser {

    // ------------------------------------------------------------------ AST

    record Pos(int zeile, int spalte) {
    }

    sealed interface Expr permits Num, Str, Var, Index, Assign, Unary, IncDec, Binary, Ternary, Call {
        Pos pos();
    }

    record Num(int wert, Pos pos) implements Expr {
    }

    record Str(String text, Pos pos) implements Expr {
    }

    record Var(String name, Pos pos) implements Expr {
    }

    record Index(String name, Expr index, Pos pos) implements Expr {
    }

    record Assign(Expr ziel, String op, Expr wert, Pos pos) implements Expr {
    }

    record Unary(String op, Expr e, Pos pos) implements Expr {
    }

    record IncDec(Expr ziel, boolean prefix, boolean plus, Pos pos) implements Expr {
    }

    record Binary(String op, Expr l, Expr r, Pos pos) implements Expr {
    }

    record Ternary(Expr bed, Expr ja, Expr nein, Pos pos) implements Expr {
    }

    record Call(String name, List<Expr> args, Pos pos) implements Expr {
    }

    sealed interface Stmt permits Decl, ExprStmt, If, While, DoWhile, For, Block, Return, Break, Continue, Empty {
        Pos pos();
    }

    /** Eine Variable in einer Deklaration; groesse != null bei Arrays. */
    record DeclItem(String name, Expr groesse, Expr init, List<Expr> arrayInit, Pos pos) {
    }

    record Decl(List<DeclItem> items, Pos pos) implements Stmt {
    }

    record ExprStmt(Expr e, Pos pos) implements Stmt {
    }

    record If(Expr bed, Stmt dann, Stmt sonst, Pos pos) implements Stmt {
    }

    record While(Expr bed, Stmt rumpf, Pos pos) implements Stmt {
    }

    record DoWhile(Stmt rumpf, Expr bed, Pos pos) implements Stmt {
    }

    record For(Stmt init, Expr bed, Expr schritt, Stmt rumpf, Pos pos) implements Stmt {
    }

    record Block(List<Stmt> stmts, Pos pos) implements Stmt {
    }

    record Return(Expr wert, Pos pos) implements Stmt {
    }

    record Break(Pos pos) implements Stmt {
    }

    record Continue(Pos pos) implements Stmt {
    }

    record Empty(Pos pos) implements Stmt {
    }

    record Param(String name, boolean array) {
    }

    record Func(String name, boolean istVoid, List<Param> params, Block rumpf, Pos pos) {
    }

    /** Programm: Funktionen und Anweisungen/Deklarationen auf oberster Ebene. */
    public record Programm(List<Func> funktionen, List<Stmt> oben) {
    }

    /** Syntaxfehler mit Stelle. */
    public static final class SyntaxFehler extends RuntimeException {
        final int zeile;
        final int spalte;

        SyntaxFehler(int zeile, int spalte, String text) {
            super(text);
            this.zeile = zeile;
            this.spalte = spalte;
        }

        public Meldung meldung() {
            return Meldung.fehler(zeile, spalte, getMessage());
        }
    }

    // ------------------------------------------------------------------ Lexer

    enum T { NUM, ID, STR, OP, EOF }

    record Tok(T typ, String text, int wert, int zeile, int spalte, int endSpalte) {
    }

    private static final String[] OPS = {"<<=", ">>=", "++", "--", "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=",
            "<<", ">>", "<=", ">=", "==", "!=", "&&", "||", "+", "-", "*", "/", "%", "<", ">", "=", "!", "~",
            "&", "|", "^", "?", ":", "(", ")", "{", "}", "[", "]", ";", ","};

    static final Set<String> NICHT_UNTERSTUETZT = Set.of("long", "short", "char", "float", "double", "unsigned",
            "signed", "bool", "_Bool", "string", "String", "struct", "auto", "var", "const", "static");

    static List<Tok> lex(String q) {
        List<Tok> out = new ArrayList<>();
        int i = 0, zeile = 1, sp = 1;
        int n = q.length();
        while (i < n) {
            char c = q.charAt(i);
            if (c == '\n') {
                i++;
                zeile++;
                sp = 1;
                continue;
            }
            if (Character.isWhitespace(c)) {
                i++;
                sp++;
                continue;
            }
            if (c == '#' && (out.isEmpty() || out.get(out.size() - 1).zeile() < zeile)) { // #include ...
                while (i < n && q.charAt(i) != '\n') {
                    i++;
                }
                continue;
            }
            if (c == '/' && i + 1 < n && q.charAt(i + 1) == '/') {
                while (i < n && q.charAt(i) != '\n') {
                    i++;
                }
                continue;
            }
            if (c == '/' && i + 1 < n && q.charAt(i + 1) == '*') {
                int z0 = zeile, s0 = sp;
                i += 2;
                sp += 2;
                while (i + 1 < n && !(q.charAt(i) == '*' && q.charAt(i + 1) == '/')) {
                    if (q.charAt(i) == '\n') {
                        zeile++;
                        sp = 1;
                    } else {
                        sp++;
                    }
                    i++;
                }
                if (i + 1 >= n) {
                    throw new SyntaxFehler(z0, s0, "Kommentar /* wird nicht mit */ geschlossen");
                }
                i += 2;
                sp += 2;
                continue;
            }
            int s0 = sp;
            if (Character.isDigit(c)) {
                int j = i;
                if (c == '0' && j + 1 < n && (q.charAt(j + 1) == 'x' || q.charAt(j + 1) == 'X')) {
                    j += 2;
                    while (j < n && Character.digit(q.charAt(j), 16) >= 0) {
                        j++;
                    }
                } else if (c == '0' && j + 1 < n && (q.charAt(j + 1) == 'b' || q.charAt(j + 1) == 'B')) {
                    j += 2;
                    while (j < n && (q.charAt(j) == '0' || q.charAt(j) == '1')) {
                        j++;
                    }
                } else {
                    while (j < n && Character.isDigit(q.charAt(j))) {
                        j++;
                    }
                }
                if (j < n && (Character.isLetter(q.charAt(j)) || q.charAt(j) == '.')) {
                    throw new SyntaxFehler(zeile, s0, "Ungueltige Zahl '" + q.substring(i, Math.min(n, j + 1))
                            + "' (nur ganze Zahlen vom Typ int)");
                }
                String t = q.substring(i, j);
                long v;
                try {
                    v = t.startsWith("0x") || t.startsWith("0X") ? Long.parseLong(t.substring(2), 16)
                            : t.startsWith("0b") || t.startsWith("0B") ? Long.parseLong(t.substring(2), 2)
                            : t.length() > 1 && t.charAt(0) == '0' ? Long.parseLong(t.substring(1), 8) // wie in C: 010 == 8
                            : Long.parseLong(t);
                } catch (NumberFormatException e) {
                    boolean oktal = t.length() > 1 && t.charAt(0) == '0' && Character.isDigit(t.charAt(1));
                    throw new SyntaxFehler(zeile, s0, "Ungueltige Zahl '" + t + "'"
                            + (oktal ? " (eine fuehrende 0 bedeutet in C eine Oktalzahl, nur Ziffern 0-7)" : ""));
                }
                if (v > 0xFFFF_FFFFL) {
                    throw new SyntaxFehler(zeile, s0, "Zahl " + t + " passt nicht in 32 Bit");
                }
                out.add(new Tok(T.NUM, t, (int) v, zeile, s0, s0 + (j - i)));
                sp += j - i;
                i = j;
                continue;
            }
            if (Character.isLetter(c) || c == '_') {
                int j = i;
                while (j < n && (Character.isLetterOrDigit(q.charAt(j)) || q.charAt(j) == '_')) {
                    j++;
                }
                out.add(new Tok(T.ID, q.substring(i, j), 0, zeile, s0, s0 + (j - i)));
                sp += j - i;
                i = j;
                continue;
            }
            if (c == '"') {
                StringBuilder sb = new StringBuilder();
                int j = i + 1;
                while (j < n && q.charAt(j) != '"' && q.charAt(j) != '\n') {
                    char d = q.charAt(j);
                    if (d == '\\' && j + 1 < n) {
                        char e = q.charAt(++j);
                        sb.append(switch (e) {
                            case 'n' -> '\n';
                            case 't' -> '\t';
                            case '0' -> '\0';
                            default -> e;
                        });
                    } else {
                        sb.append(d);
                    }
                    j++;
                }
                if (j >= n || q.charAt(j) != '"') {
                    throw new SyntaxFehler(zeile, s0, "Zeichenkette wird nicht mit \" geschlossen");
                }
                out.add(new Tok(T.STR, sb.toString(), 0, zeile, s0, s0 + (j + 1 - i)));
                sp += j + 1 - i;
                i = j + 1;
                continue;
            }
            if (c == '\'') {
                int j = i + 1;
                int v;
                if (j < n && q.charAt(j) == '\\' && j + 1 < n) {
                    char e = q.charAt(j + 1);
                    v = switch (e) {
                        case 'n' -> '\n';
                        case 't' -> '\t';
                        case '0' -> 0;
                        default -> e;
                    };
                    j += 2;
                } else if (j < n) {
                    v = q.charAt(j);
                    j++;
                } else {
                    v = 0;
                }
                if (j >= n || q.charAt(j) != '\'') {
                    throw new SyntaxFehler(zeile, s0, "Zeichenkonstante wird nicht mit ' geschlossen");
                }
                out.add(new Tok(T.NUM, q.substring(i, j + 1), v, zeile, s0, s0 + (j + 1 - i)));
                sp += j + 1 - i;
                i = j + 1;
                continue;
            }
            String op = null;
            for (String o : OPS) {
                if (q.startsWith(o, i)) {
                    op = o;
                    break;
                }
            }
            if (op == null) {
                throw new SyntaxFehler(zeile, s0, "Unerwartetes Zeichen '" + c + "'");
            }
            out.add(new Tok(T.OP, op, 0, zeile, s0, s0 + op.length()));
            sp += op.length();
            i += op.length();
        }
        out.add(new Tok(T.EOF, "Dateiende", 0, zeile, sp, sp));
        return out;
    }

    // ------------------------------------------------------------------ Parser

    private final List<Tok> toks;
    private int k;

    private CParser(List<Tok> toks) {
        this.toks = toks;
    }

    public static Programm parse(String quelltext) {
        CParser p = new CParser(lex(quelltext.replace("\r", "")));
        return p.programm();
    }

    private Tok cur() {
        return toks.get(k);
    }

    private Tok prev() {
        return toks.get(Math.max(0, k - 1));
    }

    private Tok peek(int d) {
        return toks.get(Math.min(toks.size() - 1, k + d));
    }

    private boolean is(String s) {
        Tok t = cur();
        return (t.typ() == T.OP || t.typ() == T.ID) && t.text().equals(s);
    }

    private boolean accept(String s) {
        if (is(s)) {
            k++;
            return true;
        }
        return false;
    }

    private Tok expect(String s, String wofuer) {
        if (is(s)) {
            return toks.get(k++);
        }
        if (s.equals(";") || s.equals(")") || s.equals("]")) {
            Tok p = prev();
            throw new SyntaxFehler(p.zeile(), p.endSpalte(), "'" + s + "' fehlt" + (wofuer.isEmpty() ? "" : " " + wofuer)
                    + (cur().typ() == T.EOF ? "" : " (danach steht '" + cur().text() + "')"));
        }
        throw fehler("'" + s + "' erwartet" + (wofuer.isEmpty() ? "" : " " + wofuer));
    }

    private SyntaxFehler fehler(String text) {
        Tok t = cur();
        return new SyntaxFehler(t.zeile(), t.spalte(), text + (t.typ() == T.EOF ? ", aber die Datei ist zu Ende"
                : ", gefunden '" + t.text() + "'"));
    }

    private Pos pos() {
        return new Pos(cur().zeile(), cur().spalte());
    }

    private String ident(String wofuer) {
        Tok t = cur();
        if (t.typ() != T.ID) {
            throw fehler("Name erwartet " + wofuer);
        }
        if (Set.of("int", "void", "if", "else", "while", "do", "for", "return", "break", "continue").contains(t.text())) {
            throw fehler("Name erwartet " + wofuer + " ('" + t.text() + "' ist ein Schluesselwort)");
        }
        k++;
        return t.text();
    }

    private Programm programm() {
        List<Func> funcs = new ArrayList<>();
        List<Stmt> oben = new ArrayList<>();
        while (cur().typ() != T.EOF) {
            if ((is("int") || is("void")) && peek(1).typ() == T.ID && peek(2).text().equals("(")) {
                Func f = funktion();
                if (f != null) {
                    if (funcs.stream().anyMatch(g -> g.name().equals(f.name()))) {
                        throw new SyntaxFehler(f.pos().zeile(), f.pos().spalte(), "Funktion '" + f.name() + "' ist doppelt definiert");
                    }
                    funcs.add(f);
                }
            } else {
                oben.add(stmt());
            }
        }
        return new Programm(funcs, oben);
    }

    /** Funktionsdefinition; null bei reiner Deklaration (Prototyp). */
    private Func funktion() {
        Pos p = pos();
        boolean istVoid = cur().text().equals("void");
        k++;
        String name = ident("fuer die Funktion");
        expect("(", "");
        List<Param> params = new ArrayList<>();
        if (!(is("void") && peek(1).text().equals(")")) && !is(")")) {
            do {
                typ("fuer den Parameter");
                String pn = ident("fuer den Parameter");
                boolean arr = false;
                if (accept("[")) {
                    expect("]", "beim Array-Parameter");
                    arr = true;
                }
                params.add(new Param(pn, arr));
            } while (accept(","));
        } else {
            accept("void");
        }
        expect(")", "nach den Parametern");
        if (accept(";")) {
            return null;
        }
        if (!is("{")) {
            throw fehler("'{' fuer den Funktionsrumpf erwartet");
        }
        return new Func(name, istVoid, params, block(), p);
    }

    private void typ(String wofuer) {
        if (accept("int")) {
            return;
        }
        if (cur().typ() == T.ID && NICHT_UNTERSTUETZT.contains(cur().text())) {
            throw fehler("Typ '" + cur().text() + "' wird nicht unterstuetzt (nur int)");
        }
        throw fehler("Typ int erwartet " + wofuer);
    }

    private Block block() {
        Pos p = pos();
        expect("{", "");
        List<Stmt> l = new ArrayList<>();
        while (!is("}")) {
            if (cur().typ() == T.EOF) {
                throw new SyntaxFehler(p.zeile(), p.spalte(), "Dieser Block '{' wird nicht mit '}' geschlossen");
            }
            l.add(stmt());
        }
        k++;
        return new Block(l, p);
    }

    private Stmt stmt() {
        Pos p = pos();
        Tok t = cur();
        if (t.typ() == T.ID && NICHT_UNTERSTUETZT.contains(t.text()) && peek(1).typ() == T.ID) {
            throw fehler("Typ '" + t.text() + "' wird nicht unterstuetzt (nur int)");
        }
        if (is("{")) {
            return block();
        }
        if (is("int")) {
            Decl d = decl();
            expect(";", "nach der Deklaration");
            return d;
        }
        if (accept(";")) {
            return new Empty(p);
        }
        if (accept("if")) {
            expect("(", "nach if");
            Expr c = expr();
            expect(")", "nach der Bedingung");
            Stmt dann = stmt();
            Stmt sonst = accept("else") ? stmt() : null;
            return new If(c, dann, sonst, p);
        }
        if (accept("while")) {
            expect("(", "nach while");
            Expr c = expr();
            expect(")", "nach der Bedingung");
            return new While(c, stmt(), p);
        }
        if (accept("do")) {
            Stmt r = stmt();
            if (!is("while")) {
                throw fehler("'while' nach dem do-Rumpf erwartet");
            }
            k++;
            expect("(", "nach while");
            Expr c = expr();
            expect(")", "nach der Bedingung");
            expect(";", "nach do-while");
            return new DoWhile(r, c, p);
        }
        if (accept("for")) {
            expect("(", "nach for");
            Stmt init;
            if (is(";")) {
                init = new Empty(pos());
            } else if (is("int")) {
                init = decl();
            } else {
                Pos ip = pos();
                init = new ExprStmt(expr(), ip);
            }
            expect(";", "nach der Initialisierung der for-Schleife");
            Expr c = is(";") ? null : expr();
            expect(";", "nach der Bedingung der for-Schleife");
            Expr s = is(")") ? null : expr();
            expect(")", "am Ende des for-Kopfes");
            return new For(init, c, s, stmt(), p);
        }
        if (accept("return")) {
            Expr e = is(";") ? null : expr();
            expect(";", "nach return");
            return new Return(e, p);
        }
        if (accept("break")) {
            expect(";", "nach break");
            return new Break(p);
        }
        if (accept("continue")) {
            expect(";", "nach continue");
            return new Continue(p);
        }
        if (is("else")) {
            throw fehler("'else' ohne passendes if (fehlt eine Klammer oder steht ein ';' hinter der if-Bedingung?)");
        }
        if (is("}")) {
            throw fehler("'}' ohne passende '{'");
        }
        Expr e = expr();
        expect(";", "am Ende der Anweisung");
        return new ExprStmt(e, p);
    }

    private Decl decl() {
        Pos p = pos();
        expect("int", "");
        List<DeclItem> items = new ArrayList<>();
        do {
            Pos ip = pos();
            String name = ident("fuer die Variable");
            Expr groesse = null;
            Expr init = null;
            List<Expr> arrInit = null;
            if (accept("[")) {
                groesse = is("]") ? null : expr();
                expect("]", "bei der Array-Groesse");
                if (groesse == null && !is("=")) {
                    throw fehler("Array ohne Groesse braucht eine Initialisierung {...}");
                }
                if (groesse == null) {
                    groesse = new Num(-1, ip); // Groesse aus der Initialisierung
                }
            }
            if (accept("=")) {
                if (groesse != null) {
                    expect("{", "fuer die Array-Initialisierung");
                    arrInit = new ArrayList<>();
                    if (!is("}")) {
                        do {
                            if (is("}")) {
                                break;
                            }
                            arrInit.add(assign());
                        } while (accept(","));
                    }
                    expect("}", "am Ende der Array-Initialisierung");
                } else {
                    init = assign();
                }
            }
            items.add(new DeclItem(name, groesse, init, arrInit, ip));
        } while (accept(","));
        return new Decl(items, p);
    }

    // Ausdruecke nach C-Vorrang

    private Expr expr() {
        return assign();
    }

    private static final Set<String> ZUWEISUNG = Set.of("=", "+=", "-=", "*=", "/=", "%=", "<<=", ">>=", "&=", "|=", "^=");

    private Expr assign() {
        Expr l = ternary();
        if (cur().typ() == T.OP && ZUWEISUNG.contains(cur().text())) {
            Tok op = toks.get(k++);
            if (!(l instanceof Var) && !(l instanceof Index)) {
                throw new SyntaxFehler(op.zeile(), op.spalte(), "Links von '" + op.text() + "' muss eine Variable stehen"
                        + (op.text().equals("=") ? " (Vergleich wird mit == geschrieben)" : ""));
            }
            return new Assign(l, op.text(), assign(), new Pos(op.zeile(), op.spalte()));
        }
        return l;
    }

    private Expr ternary() {
        Expr c = binary(0);
        if (is("?")) {
            Pos p = pos();
            k++;
            Expr a = assign();
            expect(":", "im Ausdruck ? :");
            Expr b = ternary();
            return new Ternary(c, a, b, p);
        }
        return c;
    }

    private static final String[][] STUFEN = {{"||"}, {"&&"}, {"|"}, {"^"}, {"&"}, {"==", "!="},
            {"<", "<=", ">", ">="}, {"<<", ">>"}, {"+", "-"}, {"*", "/", "%"}};

    private Expr binary(int stufe) {
        if (stufe == STUFEN.length) {
            return unary();
        }
        Expr l = binary(stufe + 1);
        while (true) {
            String op = null;
            for (String o : STUFEN[stufe]) {
                if (cur().typ() == T.OP && cur().text().equals(o)) {
                    op = o;
                }
            }
            if (op == null) {
                return l;
            }
            Tok t = toks.get(k++);
            l = new Binary(op, l, binary(stufe + 1), new Pos(t.zeile(), t.spalte()));
        }
    }

    private Expr unary() {
        Pos p = pos();
        if (accept("-")) {
            return new Unary("-", unary(), p);
        }
        if (accept("+")) {
            return unary();
        }
        if (accept("!")) {
            return new Unary("!", unary(), p);
        }
        if (accept("~")) {
            return new Unary("~", unary(), p);
        }
        if (is("++") || is("--")) {
            boolean plus = cur().text().equals("++");
            k++;
            Expr z = unary();
            if (!(z instanceof Var) && !(z instanceof Index)) {
                throw new SyntaxFehler(p.zeile(), p.spalte(), (plus ? "++" : "--") + " braucht eine Variable");
            }
            return new IncDec(z, true, plus, p);
        }
        if (is("&") || is("*")) {
            throw fehler("Zeiger werden nicht unterstuetzt");
        }
        return postfix();
    }

    private Expr postfix() {
        Expr e = primary();
        while (is("++") || is("--")) {
            Pos p = pos();
            boolean plus = cur().text().equals("++");
            k++;
            if (!(e instanceof Var) && !(e instanceof Index)) {
                throw new SyntaxFehler(p.zeile(), p.spalte(), (plus ? "++" : "--") + " braucht eine Variable");
            }
            e = new IncDec(e, false, plus, p);
        }
        return e;
    }

    private Expr primary() {
        Tok t = cur();
        Pos p = new Pos(t.zeile(), t.spalte());
        if (t.typ() == T.NUM) {
            k++;
            return new Num(t.wert(), p);
        }
        if (t.typ() == T.STR) {
            k++;
            return new Str(t.text(), p);
        }
        if (accept("(")) {
            if (is("int") || (cur().typ() == T.ID && NICHT_UNTERSTUETZT.contains(cur().text()))) {
                throw fehler("Typumwandlungen (Casts) werden nicht unterstuetzt");
            }
            Expr e = expr();
            expect(")", "");
            return e;
        }
        if (t.typ() == T.ID) {
            String name = ident("");
            if (accept("(")) {
                List<Expr> args = new ArrayList<>();
                if (!is(")")) {
                    do {
                        args.add(assign());
                    } while (accept(","));
                }
                expect(")", "nach den Argumenten von " + name);
                return new Call(name, args, p);
            }
            if (accept("[")) {
                Expr i = expr();
                expect("]", "nach dem Index");
                if (is("[")) {
                    throw fehler("Mehrdimensionale Arrays werden nicht unterstuetzt");
                }
                return new Index(name, i, p);
            }
            return new Var(name, p);
        }
        throw fehler("Ausdruck erwartet");
    }
}
