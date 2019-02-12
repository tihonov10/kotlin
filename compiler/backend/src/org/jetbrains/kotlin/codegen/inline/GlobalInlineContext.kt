/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.codegen.inline

import org.jetbrains.kotlin.codegen.InlineCycleReporter
import org.jetbrains.kotlin.diagnostics.DiagnosticSink
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import java.util.*

class GlobalInlineContext(diagnostics: DiagnosticSink) {

    private val inlineCycleReporter: InlineCycleReporter = InlineCycleReporter(diagnostics)

    private val typesUsedInInlineFunctions = LinkedList<MutableSet<String>>()

    private val crossinlineSuspendParameters = Stack<BitSet>()

    fun enterTransformation() {
        crossinlineSuspendParameters.push(BitSet())
    }

    fun exitTransformation() {
        crossinlineSuspendParameters.pop()
    }

    fun enterIntoInlining(call: ResolvedCall<*>?): Boolean {
        if (call != null) {
            val bitSet = BitSet(call.resultingDescriptor.valueParameters.size + 2)
            var index = 0
            if (call.resultingDescriptor.dispatchReceiverParameter != null) {
                bitSet[index++] = false
            }
            if (call.resultingDescriptor.extensionReceiverParameter != null) {
                bitSet[index++] = false
            }
            for (param in call.resultingDescriptor.valueParameters) {
                bitSet[index++] = param.isCrossinline
            }
            crossinlineSuspendParameters.push(bitSet)
        } else {
            crossinlineSuspendParameters.push(BitSet())
        }
        return inlineCycleReporter.enterIntoInlining(call).also {
            if (it) typesUsedInInlineFunctions.push(hashSetOf())
        }
    }

    fun exitFromInliningOf(call: ResolvedCall<*>?) {
        crossinlineSuspendParameters.pop()
        inlineCycleReporter.exitFromInliningOf(call)
        val pop = typesUsedInInlineFunctions.pop()
        typesUsedInInlineFunctions.peek()?.addAll(pop)
    }

    fun recordTypeFromInlineFunction(type: String) = typesUsedInInlineFunctions.peek().add(type)

    fun isTypeFromInlineFunction(type: String) = typesUsedInInlineFunctions.peek().contains(type)

    fun isCrossinlineParameter(i: Int): Boolean = crossinlineSuspendParameters.isNotEmpty() && crossinlineSuspendParameters.peek()[i]
}