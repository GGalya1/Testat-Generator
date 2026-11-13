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

    @Override
    public Part generate(Random rng, int nr) {
        Mnemonic mn = pool.get(rng.nextInt(pool.size()));
        Instance in = InstanceGenerator.generate(mn, rng);
        Encoder.Encoded enc = Encoder.encode(in);

        String frage = direction == Direction.ASM2BIN ? in.asm(false) : enc.hex();
        String antwort = in.asm(true) + " = " + enc.hex() + " = " + enc.binary();
        if (in.format().isPcRelative()) {
            frage += String.format(" (PC=0x%08X)", in.pc());
            antwort += String.format(" (ziel=0x%08X)", in.target());
        }
        return new Part(EncodingTex.aufgabe(in, enc, direction, nr),
                EncodingTex.loesung(in, enc, direction, nr), frage, antwort);
    }
}
