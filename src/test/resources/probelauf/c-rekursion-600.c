int tief(int n) {
    if (n == 0) return 0;
    return 1 + tief(n - 1);
}
int main() {
    int r = tief(600);
    printf("%d\n", r);
    return 0;
}
