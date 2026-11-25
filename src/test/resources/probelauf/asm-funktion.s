# quadrat(5) mit Aufruf und Stack
main:
    addi a0, zero, 5
    jal  ra, quadrat
    addi s0, a0, 0
    j    ende
quadrat:
    addi sp, sp, -4
    sw   ra, 0(sp)
    mul  a0, a0, a0
    lw   ra, 0(sp)
    addi sp, sp, 4
    jalr zero, 0(ra)
ende:
