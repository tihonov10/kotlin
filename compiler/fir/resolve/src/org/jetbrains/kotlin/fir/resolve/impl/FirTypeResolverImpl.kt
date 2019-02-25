/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.resolve.impl

import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.expandedConeType
import org.jetbrains.kotlin.fir.render
import org.jetbrains.kotlin.fir.resolve.FirQualifierResolver
import org.jetbrains.kotlin.fir.resolve.FirSymbolProvider
import org.jetbrains.kotlin.fir.resolve.FirTypeResolver
import org.jetbrains.kotlin.fir.scopes.FirPosition
import org.jetbrains.kotlin.fir.scopes.FirScope
import org.jetbrains.kotlin.fir.symbols.ConeClassLikeSymbol
import org.jetbrains.kotlin.fir.symbols.ConeClassSymbol
import org.jetbrains.kotlin.fir.symbols.ConeClassifierSymbol
import org.jetbrains.kotlin.fir.symbols.ConeTypeParameterSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirTypeAliasSymbol
import org.jetbrains.kotlin.fir.types.*
import org.jetbrains.kotlin.fir.types.impl.*
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.types.Variance

class FirTypeResolverImpl(firstSession: FirSession) : FirTypeResolver {

    private val symbolProvider by lazy {
        firstSession.getService(FirSymbolProvider::class)
    }

    private fun List<FirQualifierPart>.toTypeProjections() = flatMap {
        it.typeArguments.map {
            when (it) {
                is FirStarProjection -> StarProjection
                is FirTypeProjectionWithVariance -> {
                    val type = (it.typeRef as FirResolvedTypeRef).type
                    when (it.variance) {
                        Variance.INVARIANT -> type
                        Variance.IN_VARIANCE -> ConeKotlinTypeProjectionIn(type)
                        Variance.OUT_VARIANCE -> ConeKotlinTypeProjectionOut(type)
                    }
                }
                else -> error("!")
            }
        }
    }.toTypedArray()

    private fun ConeClassifierSymbol.toConeKotlinType(parts: List<FirQualifierPart>, isNullable: Boolean): ConeKotlinType? {
        return when (this) {
            is ConeTypeParameterSymbol -> {
                ConeTypeParameterTypeImpl(this, isNullable)
            }
            is ConeClassSymbol -> {
                ConeClassTypeImpl(this.toLookupTag(), parts.toTypeProjections(), isNullable)
            }
            is FirTypeAliasSymbol -> {
                ConeAbbreviatedTypeImpl(
                    abbreviationLookupTag = this.toLookupTag(),
                    typeArguments = parts.toTypeProjections(),
                    directExpansion = fir.expandedConeType ?: ConeClassErrorType("Unresolved expansion"),
                    isNullable = isNullable
                )
            }
            else -> error("!")
        }
    }

    private data class ClassIdInSession(val session: FirSession, val id: ClassId)

    private val implicitBuiltinTypeSymbols = mutableMapOf<ClassIdInSession, ConeClassLikeSymbol>()


    private fun resolveBuiltInQualified(id: ClassId, session: FirSession): ConeClassLikeSymbol {
        val nameInSession = ClassIdInSession(session, id)
        return implicitBuiltinTypeSymbols.getOrPut(nameInSession) {
            symbolProvider.getClassLikeSymbolByFqName(id) as ConeClassLikeSymbol
        }
    }

    override fun resolveToSymbol(
        typeRef: FirTypeRef,
        scope: FirScope,
        position: FirPosition
    ): ConeClassifierSymbol? {
        return when (typeRef) {
            is FirResolvedTypeRef -> typeRef.coneTypeSafe<ConeLookupTagBasedType>()?.lookupTag?.let(symbolProvider::getSymbolByLookupTag)
            is FirUserTypeRef -> {

                val qualifierResolver = FirQualifierResolver.getInstance(typeRef.session)

                var resolvedSymbol: ConeClassifierSymbol? = null
                scope.processClassifiersByName(typeRef.qualifier.first().name, position) { symbol ->
                    resolvedSymbol = when (symbol) {
                        is ConeClassLikeSymbol -> {
                            if (typeRef.qualifier.size == 1) {
                                symbol
                            } else {
                                qualifierResolver.resolveSymbolWithPrefix(typeRef.qualifier, symbol.classId)
                            }
                        }
                        is ConeTypeParameterSymbol -> {
                            assert(typeRef.qualifier.size == 1)
                            symbol
                        }
                        else -> error("!")
                    }
                    resolvedSymbol == null
                }

                // TODO: Imports
                resolvedSymbol ?: qualifierResolver.resolveSymbol(typeRef.qualifier)
            }
            is FirImplicitBuiltinTypeRef -> {
                resolveBuiltInQualified(typeRef.id, typeRef.session)
            }
            else -> null
        }
    }

    override fun resolveUserType(typeRef: FirUserTypeRef, symbol: ConeClassifierSymbol?, scope: FirScope): ConeKotlinType {
        symbol ?: return ConeKotlinErrorType("Symbol not found, for `${typeRef.render()}`")
        return symbol.toConeKotlinType(typeRef.qualifier, typeRef.isMarkedNullable)
            ?: ConeKotlinErrorType("Failed to resolve qualified type")
    }

    override fun resolveType(
        typeRef: FirTypeRef,
        scope: FirScope,
        position: FirPosition
    ): ConeKotlinType {
        return when (typeRef) {
            is FirResolvedTypeRef -> typeRef.type
            is FirUserTypeRef -> {
                resolveUserType(typeRef, resolveToSymbol(typeRef, scope, position), scope)
            }
            is FirErrorTypeRef -> {
                ConeKotlinErrorType(typeRef.reason)
            }
            is FirFunctionTypeRef -> {
                ConeFunctionTypeImpl(
                    (typeRef.receiverTypeRef as FirResolvedTypeRef?)?.type,
                    typeRef.valueParameters.map { it.returnTypeRef.coneTypeUnsafe() },
                    typeRef.returnTypeRef.coneTypeUnsafe(),
                    resolveBuiltInQualified(KotlinBuiltIns.getFunctionClassId(typeRef.parametersCount), typeRef.session).toLookupTag(),
                    typeRef.isMarkedNullable
                )
            }
            is FirImplicitBuiltinTypeRef -> {
                resolveToSymbol(typeRef, scope, position)!!.toConeKotlinType(emptyList(), isNullable = false)!!
            }
            is FirDynamicTypeRef, is FirImplicitTypeRef, is FirDelegatedTypeRef -> {
                ConeKotlinErrorType("Not supported: ${typeRef::class.simpleName}")
            }
            else -> error("!")
        }
    }
}
