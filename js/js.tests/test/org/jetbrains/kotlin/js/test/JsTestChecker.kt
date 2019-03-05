/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.js.test

import org.jetbrains.kotlin.js.test.interop.RuntimeContext
import org.jetbrains.kotlin.js.test.interop.ScriptEngine
import org.jetbrains.kotlin.js.test.interop.ScriptEngineNashorn
import org.jetbrains.kotlin.js.test.interop.ScriptEngineV8
import org.junit.Assert

fun createScriptEngine(): ScriptEngine {
    return ScriptEngineNashorn()
}

fun ScriptEngine.overrideAsserter() {
    evalVoid("this['kotlin-test'].kotlin.test.overrideAsserter_wbnzx$(this['kotlin-test'].kotlin.test.DefaultAsserter);")
}

fun ScriptEngine.runTestFunction(
    testModuleName: String?,
    testPackageName: String?,
    testFunctionName: String,
    withModuleSystem: Boolean
): String? {
    var script = when {
        withModuleSystem -> BasicBoxTest.KOTLIN_TEST_INTERNAL + ".require('" + testModuleName!! + "')"
        testModuleName === null -> "this"
        else -> testModuleName
    }

    if (testPackageName !== null) {
        script += ".$testPackageName"
    }

    val testPackage = eval<Any>(script)
    return callMethod<String?>(testPackage, testFunctionName).also {
        releaseObject(testPackage)
    }
}

abstract class AbstractJsTestChecker {
    fun check(
        files: List<String>,
        testModuleName: String?,
        testPackageName: String?,
        testFunctionName: String,
        expectedResult: String,
        withModuleSystem: Boolean
    ) {
        val actualResult = run(files, testModuleName, testPackageName, testFunctionName, withModuleSystem)
        Assert.assertEquals(expectedResult, actualResult)
    }

    private fun run(
        files: List<String>,
        testModuleName: String?,
        testPackageName: String?,
        testFunctionName: String,
        withModuleSystem: Boolean
    ) = run(files) {
        runTestFunction(testModuleName, testPackageName, testFunctionName, withModuleSystem)
    }


    fun run(files: List<String>) {
        run(files) { null }
    }

    protected abstract fun run(files: List<String>, f: ScriptEngine.() -> Any?): Any?
}

fun ScriptEngine.runAndRestoreContext(originalContext: RuntimeContext = getGlobalContext(), f: ScriptEngine.() -> Any?): Any? {
    return try {
        f()
    } finally {
        restoreState(originalContext)
    }
}

abstract class AbstractNashornJsTestChecker : AbstractJsTestChecker() {

    private var engineUsageCnt = 0

    private var engineCache: ScriptEngineNashorn? = null
    private lateinit var globalObject: RuntimeContext
    private lateinit var originalState: RuntimeContext

    protected val engine: ScriptEngineNashorn
        get() = engineCache ?: createScriptEngineForTest().also {
            engineCache = it
            globalObject = it.getGlobalContext()
            originalState = ScriptEngineNashorn.NashornRuntimeContext(globalObject.toMap())
        }

    protected open fun beforeRun() {}

    override fun run(files: List<String>, f: ScriptEngine.() -> Any?): Any? {
        // Recreate the engine once in a while
        if (engineUsageCnt++ > 100) {
            engineUsageCnt = 0
            engineCache = null
        }

        beforeRun()

        return engine.runAndRestoreContext(originalState) {
            files.forEach { loadFile(it) }
            f()
        }
    }

    protected abstract val preloadedScripts: List<String>

    protected open fun createScriptEngineForTest(): ScriptEngineNashorn {
        val engine = ScriptEngineNashorn()

        preloadedScripts.forEach { engine.loadFile(it) }

        return engine
    }
}

const val SETUP_KOTLIN_OUTPUT = "kotlin.kotlin.io.output = new kotlin.kotlin.io.BufferedOutput();"
const val GET_KOTLIN_OUTPUT = "kotlin.kotlin.io.output.buffer;"

object NashornJsTestChecker : AbstractNashornJsTestChecker() {

    override fun beforeRun() {
        engine.evalVoid(SETUP_KOTLIN_OUTPUT)
    }

    override val preloadedScripts = listOf(
        BasicBoxTest.TEST_DATA_DIR_PATH + "nashorn-polyfills.js",
        BasicBoxTest.DIST_DIR_JS_PATH + "kotlin.js",
        BasicBoxTest.DIST_DIR_JS_PATH + "kotlin-test.js"
    )

    fun checkStdout(files: List<String>, expectedResult: String) {
        run(files)
        val actualResult = engine.eval<String>(GET_KOTLIN_OUTPUT)
        Assert.assertEquals(expectedResult, actualResult)
    }

    override fun createScriptEngineForTest(): ScriptEngineNashorn {
        val engine = super.createScriptEngineForTest()

        engine.overrideAsserter()

        return engine
    }
}

class NashornIrJsTestChecker : AbstractNashornJsTestChecker() {
    override val preloadedScripts = listOf(
        BasicBoxTest.TEST_DATA_DIR_PATH + "nashorn-polyfills.js",
        "libraries/stdlib/js/src/js/polyfills.js"
    )
}

abstract class AbstractV8JsTestChecker : AbstractJsTestChecker() {
    protected abstract val engine: ScriptEngineV8
}

object V8JsTestChecker : AbstractV8JsTestChecker() {
    override val engine by lazy { createV8Engine() }
    private lateinit var originalState: ScriptEngineV8.V8RuntimeContext

    private fun createV8Engine(): ScriptEngineV8 {
        val v8 = ScriptEngineV8()

        listOf(
            BasicBoxTest.DIST_DIR_JS_PATH + "kotlin.js",
            BasicBoxTest.DIST_DIR_JS_PATH + "kotlin-test.js"
        ).forEach { v8.loadFile(it) }

        v8.overrideAsserter()
        originalState = v8.getGlobalContext()

        return v8
    }


    fun checkStdout(files: List<String>, expectedResult: String) {
        run(files) {
            val actualResult = engine.eval<String>(GET_KOTLIN_OUTPUT)
            Assert.assertEquals(expectedResult, actualResult)
        }
    }

    override fun run(files: List<String>, f: ScriptEngine.() -> Any?): Any? {
        engine.evalVoid(SETUP_KOTLIN_OUTPUT)
        return engine.runAndRestoreContext(originalState) {
            files.forEach { loadFile(it) }
            f()
        }
    }
}

object V8IrJsTestChecker : AbstractV8JsTestChecker() {
    override val engine get() = ScriptEngineV8()

    override fun run(files: List<String>, f: ScriptEngine.() -> Any?): Any? {
        val v8 = engine
        return try {
            files.forEach { v8.loadFile(it) }
            v8.f()
        } finally {
            v8.release()
        }
    }
}