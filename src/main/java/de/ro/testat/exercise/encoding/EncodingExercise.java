package de.ro.testat.exercise.encoding;

import de.ro.testat.exercise.Exercise;
import de.ro.testat.rv.Encoder;
import de.ro.testat.rv.Instance;
import de.ro.testat.rv.InstanceGenerator;
import de.ro.testat.rv.Mnemonic;

import java.util.List;
import java.util.Random;

/** Gemeinsame Logik fuer Assembler -> Maschinencode und Maschinencode -> Assembler. */
abstract class EncodingExercise implements Exercise {

    private final Direction direction;
    private final String auswahl;
    private final List<Mnemonic> pool;

    /**
     * @param auswahl Befehle, aus denen gezogen wird: "zufall", ein Format ("B"), eine Gruppe
     *                ("Load", "Shift"), ein Befehl ("beq") oder eine Liste ("B,J,S")
     */
    EncodingExercise(Direction direction, String auswahl) {
        this.direction = direction;
        this.auswahl = auswahl == null || auswahl.isBlank() ? "zufall" : auswahl.trim();
        this.pool = Mnemonic.resolve(this.auswahl)
                .orElseThrow(() -> new IllegalArgumentException("Unbekannte Befehlsauswahl: " + auswahl));
    }

    @Override
    public String describe() {
        return id() + "(" + auswahl + ")";
    }

    /** TikZ fuer die Schemata und die Farben (gleiche Farbe = gleiche Herkunft). */
    private static final List<String> PREAMBLE = List.of(
            "\\usepackage{tikz}",
            "\\usetikzlibrary{decorations.pathreplacing}",
            """
            % Farben der Kodier-Schemata: gleiche Farbe = gleiche Herkunft
            \\definecolor{cMn}{HTML}{4B5563}    % aus dem Mnemonic: opcode, funct3, funct7
            \\definecolor{cRd}{HTML}{D97706}    % rd
            \\definecolor{cRsA}{HTML}{2563EB}   % rs1
            \\definecolor{cRsB}{HTML}{16A34A}   % rs2
            \\definecolor{cSh}{HTML}{0891B2}    % shamt
            \\definecolor{cImmA}{HTML}{DC2626}  % Immediate-Gruppen
            \\definecolor{cImmB}{HTML}{9333EA}
            \\definecolor{cImmC}{HTML}{0D9488}
            \\definecolor{cImmD}{HTML}{B45309}
            \\definecolor{cImmGen}{HTML}{7C3AED}""");

    @Override
    public List<String> preamble() {
        return PREAMBLE;
    }

    @Override
    public Part generate(Random rng) {
        Mnemonic mn = pool.get(rng.nextInt(pool.size()));
        Instance in = InstanceGenerator.generate(mn, rng);
        Encoder.Encoded enc = Encoder.encode(in);

        String frage = direction == Direction.ASM2BIN ? in.asm(false) : enc.hex();
        String antwort = in.asm(true) + " = " + enc.hex() + " = " + enc.binary();
        if (in.format().isPcRelative()) {
            frage += String.format(" (PC=0x%08X)", in.pc());
            antwort += String.format(" (ziel=0x%08X)", in.target());
        }
        return new Part(EncodingTex.titel(direction), EncodingTex.aufgabe(in, enc, direction),
                EncodingTex.loesung(in, enc, direction), frage, antwort);
    }
}
