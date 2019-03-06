/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.js.test.interop

import com.eclipsesource.v8.V8
import com.eclipsesource.v8.V8Array
import com.eclipsesource.v8.V8Object
import com.eclipsesource.v8.utils.V8ObjectUtils
import java.io.File
import java.lang.StringBuilder

class ScriptEngineV8 : ScriptEngine {
    override fun <T> releaseObject(t: T) {
        (t as? V8Object)?.release()
    }

    inner class V8RuntimeContext(contextKeys: List<String>, contextValues: List<Any?>) : RuntimeContext {
        private val map = contextKeys.zip(contextValues).toMap()

        override val keys = map.keys
        override val values = map.values

        override operator fun get(k: String) = map[k]
        override operator fun set(k: String, v: Any?) {
            evalVoid("this['$k'] = ${v?.toString() ?: "void 0"}")
        }

        override fun toMap() = map
    }

    override fun restoreState(originalContext: RuntimeContext) {
        val scriptBuilder = StringBuilder()

        val globalState = getGlobalPropertyNames()
        for (key in globalState) {
            if (key !in originalContext) {
                scriptBuilder.append("this['$key'] = void 0;\n")
            }
        }
        evalVoid(scriptBuilder.toString())
    }

    private fun getGlobalPropertyNames(): List<String> {
        val v8Array = eval<V8Array>("Object.getOwnPropertyNames(this)")
        val javaArray = V8ObjectUtils.toList(v8Array) as List<String>
        v8Array.release()
        return javaArray
    }

    override fun getGlobalContext(): V8RuntimeContext {
        val v8ArrayKeys = eval<V8Array>("Object.getOwnPropertyNames(this)")
        val javaArrayKeys = V8ObjectUtils.toList(v8ArrayKeys).also { v8ArrayKeys.release() } as List<String>
        val javaArrayValues = arrayOfNulls<Any?>(javaArrayKeys.size)
        return V8RuntimeContext(javaArrayKeys, javaArrayValues.toList())
    }

    private val myRuntime: V8 = V8.createV8Runtime("global")

    @Suppress("UNCHECKED_CAST")
    override fun <T> eval(script: String): T {
        return myRuntime.executeScript(script) as T
    }

    override fun evalVoid(script: String) {
        return myRuntime.executeVoidScript(script)
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> callMethod(obj: Any, name: String, vararg args: Any?): T {
        if (obj !is V8Object) {
            throw Exception("InteropV8 can deal only with V8Object")
        }

        val runtimeArray = V8Array(myRuntime)
        val result = obj.executeFunction(name, runtimeArray) as T
        runtimeArray.release()
        return result
    }

    override fun loadFile(path: String) {
        evalVoid(File(path).bufferedReader().use { it.readText() })
    }

    override fun release() {
        myRuntime.release()
    }
}

class ScriptEngineV8Lazy: ScriptEngine {
    override fun <T> eval(script: String) = engine.eval<T>(script)

    override fun getGlobalContext() = engine.getGlobalContext()

    override fun evalVoid(script: String) = engine.evalVoid(script)

    override fun <T> callMethod(obj: Any, name: String, vararg args: Any?) = engine.callMethod<T>(obj, name, args)

    override fun loadFile(path: String) = engine.loadFile(path)

    override fun release() = engine.release()

    override fun <T> releaseObject(t: T) = engine.releaseObject(t)

    override fun restoreState(originalContext: RuntimeContext) = engine.restoreState(originalContext)

    private val engine by lazy { ScriptEngineV8() }
}