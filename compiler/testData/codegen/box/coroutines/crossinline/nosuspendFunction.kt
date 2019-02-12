// IGNORE_BACKEND: JVM_IR
// WITH_RUNTIME
// WITH_COROUTINES
// COMMON_COROUTINES_TEST
// CHECK_BYTECODE_LISTING
import helpers.*
import COROUTINES_PACKAGE.*


interface Factory {
    fun create(): suspend () -> Unit
}

interface Factory1 {
    fun create(): Factory
}

inline fun inlineMe(crossinline c: suspend () -> Unit) = object: Factory {
    override fun create() = suspend { c() }
}

inline fun inlineMe1(crossinline c: suspend () -> Unit) = object: Factory1 {
    override fun create() = object: Factory {
        override fun create() = suspend { c() }
    }
}

fun builder(c: suspend () -> Unit) {
    c.startCoroutine(EmptyContinuation)
}

fun box(): String {
    var res = "FAIL 0"
    builder {
        val lambda = suspend {
            res = "OK"
        }
        inlineMe(lambda).create()()
    }
    if (res != "OK") return res
    res = "FAIL 1"
    builder {
        val lambda = suspend {
            res = "OK"
        }
        inlineMe1(lambda).create().create()()
    }
    return res
}