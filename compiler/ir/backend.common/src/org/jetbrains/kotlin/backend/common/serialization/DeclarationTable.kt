/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.backend.common.serialization

import org.jetbrains.kotlin.backend.common.serialization.KotlinMangler
import org.jetbrains.kotlin.backend.common.serialization.UniqId
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.declarations.impl.IrAnonymousInitializerImpl
import org.jetbrains.kotlin.ir.descriptors.IrBuiltIns

class DescriptorTable {
    private val descriptors = mutableMapOf<DeclarationDescriptor, Long>()
    fun put(descriptor: DeclarationDescriptor, uniqId: UniqId) {
        descriptors.getOrPut(descriptor) { uniqId.index }
    }
    fun get(descriptor: DeclarationDescriptor) = descriptors[descriptor]
}

// TODO: We don't manage id clashes anyhow now.
open class DeclarationTable(val builtIns: IrBuiltIns, val descriptorTable: DescriptorTable, mangler: KotlinMangler): KotlinMangler by mangler {

    private val table = mutableMapOf<IrDeclaration, UniqId>()
    val debugIndex = mutableMapOf<UniqId, String>()
    val descriptors = descriptorTable
    open protected var currentIndex = 0L

    init {
        loadKnownBuiltins()
    }

    open protected fun loadKnownBuiltins() {
        builtIns.knownBuiltins.forEach {
            table.put(it, UniqId(currentIndex++, false))
        }
    }

    fun uniqIdByDeclaration(value: IrDeclaration) = table.getOrPut(value) {
        computeUniqIdByDeclaration(value)
    }

    open protected fun computeUniqIdByDeclaration(value: IrDeclaration): UniqId {
        val index = if (value.origin == IrDeclarationOrigin.FAKE_OVERRIDE ||
            !value.isExported()
            || value is IrVariable
            || value is IrTypeParameter
            || value is IrValueParameter
            || value is IrAnonymousInitializerImpl
        ) {
            UniqId(currentIndex++, true)
        } else {
            UniqId(value.hashedMangle, false)
        }

        return index
    }
}

// This is what we pre-populate tables with
val IrBuiltIns.knownBuiltins
    get() = irBuiltInsExternalPackageFragment.declarations
