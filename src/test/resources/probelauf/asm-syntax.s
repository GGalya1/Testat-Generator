main:
    addi t0, zero, 5000
    add  t1, t0
    lw   t2, 3(xx)
    mul  a0, t0, t1
    jal  ra, nirgendwo
	foo  a0, a1, a2
main:
    beq  t0, t1, main
