/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.resolve

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.FirTypeParameter
import org.jetbrains.kotlin.fir.resolve.transformers.firUnsafe
import org.jetbrains.kotlin.fir.scopes.FirScope
import org.jetbrains.kotlin.fir.scopes.impl.FirClassDeclaredMemberScope
import org.jetbrains.kotlin.fir.scopes.impl.FirClassSubstitutionScope
import org.jetbrains.kotlin.fir.scopes.impl.FirClassUseSiteScope
import org.jetbrains.kotlin.fir.scopes.impl.FirCompositeScope
import org.jetbrains.kotlin.fir.scopes.lookupSuperTypes
import org.jetbrains.kotlin.fir.symbols.ConeTypeParameterSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.types.*

fun ConeKotlinType.scope(useSiteSession: FirSession): FirScope? {
    return when (this) {
        is ConeKotlinErrorType -> null
        is ConeClassErrorType -> null
        is ConeAbbreviatedType -> directExpansion.scope(useSiteSession)
        is ConeClassLikeType -> {
            val fir = this.lookupTag.toSymbol(useSiteSession)?.firUnsafe<FirRegularClass>() ?: return null
            fir.buildUseSiteScope(useSiteSession)
        }
        is ConeTypeParameterType -> {
            val fir = this.lookupTag.toSymbol(useSiteSession)?.firUnsafe<FirTypeParameter>() ?: return null
            FirCompositeScope(fir.bounds.mapNotNullTo(mutableListOf()) { it.coneTypeUnsafe().scope(useSiteSession) })
        }
        else -> error("Failed type ${this}")
    }
}




private fun ConeClassLikeType.buildSubstitutionScope(
    useSiteSession: FirSession,
    unsubstituted: FirScope,
    regularClass: FirRegularClass
): FirClassSubstitutionScope? {
    if (this.typeArguments.isEmpty()) return null

    val substitution = regularClass.typeParameters.zip(this.typeArguments) { typeParameter, typeArgument ->
        typeParameter.symbol to (typeArgument as? ConeTypedProjection)?.type
    }.filter { (_, type) -> type != null }.toMap() as Map<ConeTypeParameterSymbol, ConeKotlinType>

    return FirClassSubstitutionScope(useSiteSession, unsubstituted, substitution, true)
}

private fun FirRegularClass.buildUseSiteScope(useSiteSession: FirSession = session): FirClassUseSiteScope {
    val superTypeScope = FirCompositeScope(mutableListOf())
    val declaredScope = FirClassDeclaredMemberScope(this, useSiteSession)
    lookupSuperTypes(this, lookupInterfaces = true, deep = false, useSiteSession = useSiteSession)
        .mapNotNullTo(superTypeScope.scopes) { useSiteSuperType ->
            if (useSiteSuperType is ConeClassErrorType) return@mapNotNullTo null
            val symbol = useSiteSuperType.lookupTag.toSymbol(useSiteSession)
            if (symbol is FirClassSymbol) {
                val scope = symbol.fir.buildUseSiteScope(useSiteSession)
                useSiteSuperType.buildSubstitutionScope(useSiteSession, scope, symbol.fir) ?: scope
            } else {
                null
            }
        }
    return FirClassUseSiteScope(useSiteSession, superTypeScope, declaredScope, true)
}
