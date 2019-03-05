/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.js.test.interop

import jdk.nashorn.api.scripting.NashornScriptEngineFactory
import jdk.nashorn.internal.runtime.ScriptRuntime
import javax.script.Invocable

class ScriptEngineNashorn : ScriptEngine {


    class NashornRuntimeContext(private val map: Map<String, Any?>) : RuntimeContext {
        override val keys = map.keys
        override val values = map.values

        override operator fun get(k: String) = map[k]
        override operator fun set(k: String, v: Any?) {
            (map as? MutableMap<String, Any?>)?.let { it[k] = v }
        }

        override fun toMap() = map.toMap()
    }

    // TODO use "-strict"
    private val myEngine = NashornScriptEngineFactory().getScriptEngine("--language=es5", "--no-java", "--no-syntax-extensions")

    @Suppress("UNCHECKED_CAST")
    override fun <T> eval(script: String): T {
        return myEngine.eval(script) as T
    }

    override fun evalVoid(script: String) {
        myEngine.eval(script)
    }

    override fun getGlobalContext(): NashornRuntimeContext {
        return NashornRuntimeContext(eval("this"))
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> callMethod(obj: Any, name: String, vararg args: Any?): T {
        return (myEngine as Invocable).invokeMethod(obj, name, *args) as T
    }

    override fun loadFile(path: String) {
        evalVoid("load('${path.replace('\\', '/')}');")
    }

    override fun release() {}
    override fun <T> releaseObject(t: T) {}
    override fun restoreState(originalContext: RuntimeContext) {
        val globalContext = getGlobalContext()
        for (key in globalContext.keys) {
            globalContext[key] = originalContext[key] ?: ScriptRuntime.UNDEFINED
        }
    }
}