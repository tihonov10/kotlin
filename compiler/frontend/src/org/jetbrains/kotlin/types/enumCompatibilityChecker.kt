/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.types

import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.types.expressions.ExpressionTypingContext
import org.jetbrains.kotlin.types.typeUtil.isEnum
import org.jetbrains.kotlin.types.typeUtil.isSubtypeOf
import org.jetbrains.kotlin.types.typeUtil.makeNotNullable

fun checkEnumsForCompatibility(context: ExpressionTypingContext, reportOn: KtElement, typeA: KotlinType, typeB: KotlinType) {
    if (isIncompatibleEnums(typeA, typeB)) {
        val diagnostic = if (context.languageVersionSettings.supportsFeature(LanguageFeature.ProhibitComparisonOfIncompatibleEnums)) {
            Errors.INCOMPATIBLE_ENUM_COMPARISON_ERROR
        } else {
            Errors.INCOMPATIBLE_ENUM_COMPARISON
        }

        context.trace.report(diagnostic.on(reportOn, typeA, typeB))
    }
}

private fun isIncompatibleEnums(typeA: KotlinType, typeB: KotlinType): Boolean {
    if (!typeA.isEnum() && !typeB.isEnum()) return false
    if (TypeUtils.isNullableType(typeA) && TypeUtils.isNullableType(typeB)) return false

    val notNullTypeA = typeA.makeNotNullable()
    val notNullTypeB = typeB.makeNotNullable()

    return !notNullTypeA.isSubtypeOf(notNullTypeB) && !notNullTypeB.isSubtypeOf(notNullTypeA)
}
