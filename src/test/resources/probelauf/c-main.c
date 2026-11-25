// Fakultaet rekursiv
int fak(int n) {
    if (n <= 1) return 1;
    return n * fak(n - 1);
}

int main() {
    int a[5];
    int i;
    for (i = 0; i < 5; i++) {
        a[i] = fak(i + 1);
    }
    printf("fak(5) = %d, hex %x\n", a[4], a[4]);
    putchar('O'); putchar('K'); putchar('\n');
    return a[2];
}
