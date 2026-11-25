# Summe 1..10 in a0, Ergebnis in den Speicher schreiben
        addi t0, zero, 1
        addi t1, zero, 11
        addi a0, zero, 0
schleife:
        add  a0, a0, t0
        addi t0, t0, 1
        blt  t0, t1, schleife
        sw   a0, 0(gp)
