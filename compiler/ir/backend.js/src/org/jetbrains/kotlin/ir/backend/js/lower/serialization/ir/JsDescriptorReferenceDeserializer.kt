package org.jetbrains.kotlin.ir.backend.js.lower.serialization.ir

import org.jetbrains.kotlin.backend.common.serialization.DescriptorReferenceDeserializer
import org.jetbrains.kotlin.backend.common.serialization.DescriptorUniqIdAware
import org.jetbrains.kotlin.backend.common.serialization.UniqId
import org.jetbrains.kotlin.backend.common.serialization.UniqIdKey
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.ir.descriptors.IrBuiltIns
import org.jetbrains.kotlin.name.FqName


class JsDescriptorReferenceDeserializer(
    currentModule: ModuleDescriptor,
    val builtIns: IrBuiltIns,
    val FUNCTION_INDEX_START: Long,
    val jsDescriptorUniqIdAware: DescriptorUniqIdAware) :
        DescriptorReferenceDeserializer(currentModule, mutableMapOf<UniqIdKey, UniqIdKey>()),
        DescriptorUniqIdAware by jsDescriptorUniqIdAware {

    val knownBuiltInsDescriptors = mutableMapOf<DeclarationDescriptor, UniqId>()

    override fun resolveSpecialDescriptor(fqn: FqName) = builtIns.builtIns.getBuiltInClassByFqName(fqn)

    override fun checkIfSpecialDescriptorId(id: Long) =
        (FUNCTION_INDEX_START + BUILT_IN_UNIQ_ID_CLASS_OFFSET) <= id && id < (FUNCTION_INDEX_START + BUILT_IN_UNIQ_ID_GAP)

    override fun getDescriptorIdOrNull(descriptor: DeclarationDescriptor) =
        knownBuiltInsDescriptors[descriptor]?.index ?: if (isBuiltInFunction(descriptor))
            FUNCTION_INDEX_START + builtInFunctionId(descriptor)
        else null

}

