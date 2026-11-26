# Verstoesse gegen die Aufrufkonvention (fuer den strengen Modus)
main:
    addi s0, zero, 7
    addi t0, zero, 100      # Zwischenwert in t0
    addi a0, zero, 3
    call f
    add  a0, a0, t0         # t0 nach dem Aufruf verwendet
    add  a0, a0, s0
    li   a7, 10
    ecall
f:
    add  t1, a0, t0         # liest t0 des Aufrufers
    addi s0, a0, 1          # s0 nicht gesichert
    addi sp, sp, -8         # Stack nicht freigegeben
    add  a0, s0, t1
    ret
