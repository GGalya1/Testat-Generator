.data
zahlen: .word 7, -3, 0x10
text: .asciz "Hallo, Welt!\n"
.text
main:
    la   a0, text
    li   a7, 4
    ecall
    la   t0, zahlen
    lw   a0, 4(t0)
    li   a7, 1
    ecall
    li   a0, 10
    li   a7, 11
    ecall
    li   a0, 255
    li   a7, 34
    ecall
    li   a0, 10
    li   a7, 11
    ecall
    li   a0, -6
    li   a7, 35
    ecall
    li   a7, 10
    ecall
