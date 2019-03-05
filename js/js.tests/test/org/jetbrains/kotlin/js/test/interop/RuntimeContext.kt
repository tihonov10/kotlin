/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.js.test.interop

interface RuntimeContext {
    val keys: Collection<String>
    val values: Collection<Any?>
    operator fun get(k: String): Any?
    operator fun set(k: String, v: Any?)

    operator fun contains(k: String) = k in keys

    fun toMap(): Map<String, Any?>
}