package de.ro.testat.latex;

import de.ro.testat.rv.Encoder;

import static de.ro.testat.latex.Tex.f;

/** Befehlswort gross in 4er-Gruppen, darunter je eine Klammer mit der Hex-Ziffer. */
public final class NibbleDiagram {

    private static final double PITCH = 0.42;
    private static final double GAP = 0.35;

    private NibbleDiagram() {
    }

    public static String render(long word) {
        String b = Encoder.binary(word, 32);
        String hx = f("%08X", word);
        StringBuilder L = new StringBuilder("\\begin{tikzpicture}[font=\\Large\\ttfamily]\n");
        for (int k = 0; k < 8; k++) {
            double x0 = k * (4 * PITCH + GAP);
            for (int j = 0; j < 4; j++) {
                L.append(f("\\node at (%.3f,0) {%c};\n", x0 + j * PITCH + PITCH / 2, b.charAt(4 * k + j)));
            }
            L.append(f("\\draw[thick, decorate, decoration={brace, mirror, amplitude=5pt}] "
                    + "(%.3f,-0.35) -- (%.3f,-0.35);\n", x0 + 0.03, x0 + 4 * PITCH - 0.03));
            L.append(f("\\node[font=\\Large\\ttfamily\\bfseries] at (%.3f,-0.95) {%c};\n", x0 + 2 * PITCH, hx.charAt(k)));
        }
        return L.append("\\end{tikzpicture}").toString();
    }
}
