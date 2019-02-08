// IGNORE_BACKEND: JVM_IR
fun foo() {
    if (0 < 1) {
        System.out?.println()
    }
}
// 1 LINENUMBER 3