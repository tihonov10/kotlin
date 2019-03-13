/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.codegen

import com.intellij.psi.tree.IElementType
import org.jetbrains.annotations.Contract
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.codegen.AsmUtil.*
import org.jetbrains.kotlin.codegen.binding.CodegenBinding
import org.jetbrains.kotlin.codegen.binding.CodegenBinding.CLASS_FOR_CALLABLE
import org.jetbrains.kotlin.codegen.binding.CodegenBinding.RECURSIVE_SUSPEND_CALLABLE_REFERENCE
import org.jetbrains.kotlin.codegen.coroutines.unwrapInitialDescriptorForSuspendFunction
import org.jetbrains.kotlin.codegen.intrinsics.IntrinsicMethods
import org.jetbrains.kotlin.codegen.pseudoInsns.storeNotNull
import org.jetbrains.kotlin.codegen.state.KotlinTypeMapper
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.impl.SyntheticFieldDescriptor
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.ImportedFromObjectCallableDescriptor
import org.jetbrains.kotlin.resolve.calls.model.DefaultValueArgument
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.resolve.calls.model.ResolvedValueArgument
import org.jetbrains.kotlin.resolve.isInlineClassType
import org.jetbrains.kotlin.resolve.jvm.AsmTypes
import org.jetbrains.kotlin.resolve.jvm.AsmTypes.*
import org.jetbrains.kotlin.resolve.jvm.jvmSignature.JvmMethodParameterKind
import org.jetbrains.kotlin.resolve.scopes.receivers.ReceiverValue
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.TypeUtils
import org.jetbrains.org.objectweb.asm.Label
import org.jetbrains.org.objectweb.asm.Opcodes
import org.jetbrains.org.objectweb.asm.Opcodes.*
import org.jetbrains.org.objectweb.asm.Type
import org.jetbrains.org.objectweb.asm.commons.InstructionAdapter
import java.util.function.Consumer
import kotlin.collections.*

abstract class StackValue @JvmOverloads protected constructor(
    @JvmField val type: Type,
    @JvmField val kotlinType: KotlinType? = null,
    private val canHaveSideEffects: Boolean = true
) {

    protected constructor(type: Type, canHaveSideEffects: Boolean) : this(type, null, canHaveSideEffects) {}

    /**
     * This method is called to put the value on the top of the JVM stack if `depth` other values have been put on the
     * JVM stack after this value was generated.
     *
     * @param type  the type as which the value should be put
     * @param v     the visitor used to genClassOrObject the instructions
     * @param depth the number of new values put onto the stack
     */
    open fun moveToTopOfStack(type: Type, kotlinType: KotlinType?, v: InstructionAdapter, depth: Int) {
        put(type, kotlinType, v)
    }

    fun put(v: InstructionAdapter) {
        put(type, null, v, false)
    }

    fun put(type: Type, v: InstructionAdapter) {
        put(type, null, v, false)
    }

    @JvmOverloads
    fun put(type: Type, kotlinType: KotlinType?, v: InstructionAdapter, skipReceiver: Boolean = false) {
        if (!skipReceiver) {
            putReceiver(v, true)
        }
        putSelector(type, kotlinType, v)
    }

    abstract fun putSelector(type: Type, kotlinType: KotlinType?, v: InstructionAdapter)

    open fun isNonStaticAccess(isRead: Boolean): Boolean {
        return false
    }

    open fun putReceiver(v: InstructionAdapter, isRead: Boolean) {
        //by default there is no receiver
        //if you have it inherit StackValueWithSimpleReceiver
    }

    open fun dup(v: InstructionAdapter, withReceiver: Boolean) {
        if (Type.VOID_TYPE != type) {
            AsmUtil.dup(v, type)
        }
    }

    fun store(value: StackValue, v: InstructionAdapter) {
        store(value, v, false)
    }

    fun canHaveSideEffects(): Boolean {
        return canHaveSideEffects
    }

    open fun store(rightSide: StackValue, v: InstructionAdapter, skipReceiver: Boolean) {
        if (!skipReceiver) {
            putReceiver(v, false)
        }
        rightSide.put(rightSide.type, rightSide.kotlinType, v)
        storeSelector(rightSide.type, rightSide.kotlinType, v)
    }

    open fun storeSelector(topOfStackType: Type, topOfStackKotlinType: KotlinType?, v: InstructionAdapter) {
        throw UnsupportedOperationException("Cannot store to value $this")
    }

    fun coerceTo(toType: Type, toKotlinType: KotlinType?, v: InstructionAdapter) {
        coerce(this.type, this.kotlinType, toType, toKotlinType, v)
    }

    protected fun coerceFrom(topOfStackType: Type, topOfStackKotlinType: KotlinType?, v: InstructionAdapter) {
        coerce(topOfStackType, topOfStackKotlinType, this.type, this.kotlinType, v)
    }

    private class None private constructor() : StackValue(Type.VOID_TYPE, false) {

        override fun putSelector(type: Type, kotlinType: KotlinType?, v: InstructionAdapter) {
            coerceTo(type, kotlinType, v)
        }

        companion object {
            val INSTANCE = None()
        }
    }

    class Local constructor(val index: Int, type: Type, kotlinType: KotlinType? = null) : StackValue(type, kotlinType, false) {

        init {

            if (index < 0) {
                throw IllegalStateException("local variable index must be non-negative")
            }
        }

        override fun putSelector(type: Type, kotlinType: KotlinType?, v: InstructionAdapter) {
            v.load(index, this.type)
            coerceTo(type, kotlinType, v)
        }

        override fun storeSelector(topOfStackType: Type, topOfStackKotlinType: KotlinType?, v: InstructionAdapter) {
            coerceFrom(topOfStackType, topOfStackKotlinType, v)
            v.store(index, this.type)
        }
    }

    class LateinitLocal constructor(val index: Int, type: Type, kotlinType: KotlinType, private val name: Name) :
        StackValue(type, kotlinType, false) {

        init {

            if (index < 0) {
                throw IllegalStateException("local variable index must be non-negative")
            }

            if (name == null) {
                throw IllegalArgumentException("Lateinit local variable should have name: #" + index + " " + type.descriptor)
            }
        }

        override fun putSelector(type: Type, kotlinType: KotlinType?, v: InstructionAdapter) {
            v.load(index, this.type)
            genNonNullAssertForLateinit(v, name.asString())
            coerceTo(type, kotlinType, v)
        }

        override fun storeSelector(topOfStackType: Type, topOfStackKotlinType: KotlinType?, v: InstructionAdapter) {
            coerceFrom(topOfStackType, topOfStackKotlinType, v)
            v.store(index, this.type)
            v.storeNotNull()
        }
    }

    class Delegate constructor(
        type: Type,
        private val delegateValue: StackValue,
        private val metadataValue: StackValue,
        internal val variableDescriptor: VariableDescriptorWithAccessors,
        private val codegen: ExpressionCodegen
    ) : StackValue(type) {


        private fun getResolvedCall(isGetter: Boolean): ResolvedCall<FunctionDescriptor> {
            val bindingContext = codegen.state.bindingContext
            val accessor = (if (isGetter) variableDescriptor.getter else variableDescriptor.setter)
                ?: error("Accessor descriptor for delegated local property should be present $variableDescriptor")
            return bindingContext.get(BindingContext.DELEGATED_PROPERTY_RESOLVED_CALL, accessor)
                ?: error("Resolve call should be recorded for delegate call $variableDescriptor")
        }

        override fun putSelector(type: Type, kotlinType: KotlinType?, v: InstructionAdapter) {
            val resolvedCall = getResolvedCall(true)
            val arguments = resolvedCall.call.valueArguments
            assert(arguments.size == 2) {
                "Resolved call for 'getValue' should have 2 arguments, but was " +
                        arguments.size + ": " + resolvedCall
            }

            codegen.tempVariables[arguments[0].asElement()] = StackValue.constant(null, OBJECT_TYPE)
            codegen.tempVariables[arguments[1].asElement()] = metadataValue
            val lastValue = codegen.invokeFunction(resolvedCall, delegateValue)
            lastValue.put(type, kotlinType, v)

            codegen.tempVariables.remove(arguments[0].asElement())
            codegen.tempVariables.remove(arguments[1].asElement())
        }

        override fun store(rightSide: StackValue, v: InstructionAdapter, skipReceiver: Boolean) {
            val resolvedCall = getResolvedCall(false)
            val arguments = resolvedCall.call.valueArguments
            assert(arguments.size == 3) {
                "Resolved call for 'setValue' should have 3 arguments, but was " +
                        arguments.size + ": " + resolvedCall
            }

            codegen.tempVariables[arguments[0].asElement()] = StackValue.constant(null, OBJECT_TYPE)
            codegen.tempVariables[arguments[1].asElement()] = metadataValue
            codegen.tempVariables[arguments[2].asElement()] = rightSide
            val lastValue = codegen.invokeFunction(resolvedCall, delegateValue)
            lastValue.put(Type.VOID_TYPE, null, v)

            codegen.tempVariables.remove(arguments[0].asElement())
            codegen.tempVariables.remove(arguments[1].asElement())
            codegen.tempVariables.remove(arguments[2].asElement())
        }
    }

    class OnStack @JvmOverloads constructor(type: Type, kotlinType: KotlinType? = null) : StackValue(type, kotlinType) {

        override fun putSelector(type: Type, kotlinType: KotlinType?, v: InstructionAdapter) {
            coerceTo(type, kotlinType, v)
        }

        override fun moveToTopOfStack(type: Type, kotlinType: KotlinType?, v: InstructionAdapter, depth: Int) {
            if (depth == 0) {
                put(type, kotlinType, v)
            } else if (depth == 1) {
                val size = this.type.size
                if (size == 1) {
                    v.swap()
                } else if (size == 2) {
                    v.dupX2()
                    v.pop()
                } else {
                    throw UnsupportedOperationException("don't know how to move type $type to top of stack")
                }

                coerceTo(type, kotlinType, v)
            } else if (depth == 2) {
                val size = this.type.size
                if (size == 1) {
                    v.dup2X1()
                    v.pop2()
                } else if (size == 2) {
                    v.dup2X2()
                    v.pop2()
                } else {
                    throw UnsupportedOperationException("don't know how to move type $type to top of stack")
                }

                coerceTo(type, kotlinType, v)
            } else {
                throw UnsupportedOperationException("unsupported move-to-top depth $depth")
            }
        }
    }

    class Constant(val value: Any?, type: Type, kotlinType: KotlinType?) : StackValue(type, kotlinType, false) {

        init {
            assert(Type.BOOLEAN_TYPE != type) { "Boolean constants should be created via 'StackValue.constant'" }
        }

        override fun putSelector(type: Type, kotlinType: KotlinType?, v: InstructionAdapter) {
            if (value is Int || value is Byte || value is Short) {
                v.iconst((value as Number).toInt())
            } else if (value is Char) {
                v.iconst(value.toInt())
            } else if (value is Long) {
                v.lconst((value as Long?)!!)
            } else if (value is Float) {
                v.fconst((value as Float?)!!)
            } else if (value is Double) {
                v.dconst((value as Double?)!!)
            } else {
                v.aconst(value)
            }

            if (value != null || AsmUtil.isPrimitive(type)) {
                coerceTo(type, kotlinType, v)
            }
        }
    }

    private class ArrayElement(type: Type, kotlinType: KotlinType?, array: StackValue, index: StackValue) :
        StackValueWithSimpleReceiver(type, kotlinType, false, false, Receiver(Type.LONG_TYPE, array, index), true) {

        override fun storeSelector(topOfStackType: Type, topOfStackKotlinType: KotlinType?, v: InstructionAdapter) {
            coerceFrom(topOfStackType, topOfStackKotlinType, v)
            v.astore(this.type)
        }

        override fun receiverSize(): Int {
            return 2
        }

        override fun putSelector(
            type: Type, kotlinType: KotlinType?, v: InstructionAdapter
        ) {
            v.aload(this.type)    // assumes array and index are on the stack
            coerceTo(type, kotlinType, v)
        }
    }

    class UnderlyingValueOfInlineClass(
        type: Type,
        kotlinType: KotlinType?,
        receiver: StackValue
    ) : StackValueWithSimpleReceiver(type, kotlinType, false, false, receiver, true) {

        override fun putSelector(
            type: Type, kotlinType: KotlinType?, v: InstructionAdapter
        ) {
            coerceTo(type, kotlinType, v)
        }
    }

    class CollectionElementReceiver(
        private val callable: Callable,
        private val receiver: StackValue,
        private val resolvedGetCall: ResolvedCall<FunctionDescriptor>?,
        private val resolvedSetCall: ResolvedCall<FunctionDescriptor>?,
        internal val isGetter: Boolean,
        private val codegen: ExpressionCodegen,
        internal val valueArguments: List<ResolvedValueArgument>
    ) : StackValue(OBJECT_TYPE) {
        private val frame: FrameMap
        internal var defaultArgs: DefaultCallArgs? = null
        internal var callGenerator: CallGenerator? = null
        internal var isComplexOperationWithDup: Boolean = false

        init {
            this.frame = codegen.myFrameMap
        }

        override fun putSelector(type: Type, kotlinType: KotlinType?, v: InstructionAdapter) {
            val call = if (isGetter) resolvedGetCall else resolvedSetCall
            val newReceiver = StackValue.receiver(call!!, receiver, codegen, callable)
            val generator = createArgumentGenerator()
            newReceiver.put(newReceiver.type, newReceiver.kotlinType, v)
            callGenerator!!.processAndPutHiddenParameters(false)

            defaultArgs = generator.generate(valueArguments, valueArguments, call.resultingDescriptor)
        }

        private fun createArgumentGenerator(): ArgumentGenerator {
            assert(callGenerator == null) { "'putSelector' and 'createArgumentGenerator' methods should be called once for CollectionElementReceiver: $callable" }
            val resolvedCall = (if (isGetter) resolvedGetCall else resolvedSetCall) ?: error("Resolved call should be non-null: $callable")
            callGenerator = if (!isComplexOperationWithDup) codegen.getOrCreateCallGenerator(resolvedCall) else codegen.defaultCallGenerator
            return CallBasedArgumentGenerator(
                codegen,
                callGenerator!!,
                resolvedCall.resultingDescriptor.valueParameters, callable.valueParameterTypes
            )
        }

        override fun dup(v: InstructionAdapter, withReceiver: Boolean) {
            dupReceiver(v)
        }

        fun dupReceiver(v: InstructionAdapter) {
            if (CollectionElement.isStandardStack(
                    codegen.typeMapper,
                    resolvedGetCall,
                    1
                ) && CollectionElement.isStandardStack(codegen.typeMapper, resolvedSetCall, 2)
            ) {
                v.dup2()   // collection and index
                return
            }

            val mark = frame.mark()

            // indexes
            val valueParameters = resolvedGetCall!!.resultingDescriptor.valueParameters
            var firstParamIndex = -1
            for (i in valueParameters.indices.reversed()) {
                val type = codegen.typeMapper.mapType(valueParameters[i].type)
                firstParamIndex = frame.enterTemp(type)
                v.store(firstParamIndex, type)
            }

            val receiverParameter = resolvedGetCall!!.extensionReceiver
            var receiverIndex = -1
            if (receiverParameter != null) {
                val type = codegen.typeMapper.mapType(receiverParameter.type)
                receiverIndex = frame.enterTemp(type)
                v.store(receiverIndex, type)
            }

            val dispatchReceiver = resolvedGetCall.dispatchReceiver
            var thisIndex = -1
            if (dispatchReceiver != null) {
                thisIndex = frame.enterTemp(OBJECT_TYPE)
                v.store(thisIndex, OBJECT_TYPE)
            }

            // for setter

            val realReceiverIndex: Int
            val realReceiverType: Type
            if (receiverIndex != -1) {
                realReceiverType = codegen.typeMapper.mapType(receiverParameter!!.type)
                realReceiverIndex = receiverIndex
            } else if (thisIndex != -1) {
                realReceiverType = OBJECT_TYPE
                realReceiverIndex = thisIndex
            } else {
                throw UnsupportedOperationException()
            }

            if (resolvedSetCall!!.dispatchReceiver != null) {
                if (resolvedSetCall.extensionReceiver != null) {
                    codegen.generateReceiverValue(resolvedSetCall.dispatchReceiver, false).put(OBJECT_TYPE, null, v)
                }
                v.load(realReceiverIndex, realReceiverType)
            } else {
                if (resolvedSetCall.extensionReceiver != null) {
                    v.load(realReceiverIndex, realReceiverType)
                } else {
                    throw UnsupportedOperationException()
                }
            }

            var index = firstParamIndex
            for (valueParameter in valueParameters) {
                val type = codegen.typeMapper.mapType(valueParameter.type)
                v.load(index, type)
                index -= type.size
            }

            // restoring original
            if (thisIndex != -1) {
                v.load(thisIndex, OBJECT_TYPE)
            }

            if (receiverIndex != -1) {
                v.load(receiverIndex, realReceiverType)
            }

            index = firstParamIndex
            for (valueParameter in valueParameters) {
                val type = codegen.typeMapper.mapType(valueParameter.type)
                v.load(index, type)
                index -= type.size
            }

            mark.dropTo()
        }
    }

    class CollectionElement(
        collectionElementReceiver: CollectionElementReceiver,
        type: Type,
        kotlinType: KotlinType?,
        private val resolvedGetCall: ResolvedCall<FunctionDescriptor>?,
        private val resolvedSetCall: ResolvedCall<FunctionDescriptor>?,
        private val codegen: ExpressionCodegen
    ) : StackValueWithSimpleReceiver(type, kotlinType, false, false, collectionElementReceiver, true) {
        private val getter: Callable?
        private val setter: Callable?

        private val callGenerator: CallGenerator
            get() = (receiver as CollectionElementReceiver).callGenerator ?: error(
                "CollectionElementReceiver should be putted on stack before CollectionElement:" +
                        " getCall = " + resolvedGetCall + ",  setCall = " + resolvedSetCall
            )

        init {
            this.setter = if (resolvedSetCall == null)
                null
            else
                codegen.resolveToCallable(codegen.accessibleFunctionDescriptor(resolvedSetCall), false, resolvedSetCall)
            this.getter = if (resolvedGetCall == null)
                null
            else
                codegen.resolveToCallable(codegen.accessibleFunctionDescriptor(resolvedGetCall), false, resolvedGetCall)
        }

        override fun putSelector(type: Type, kotlinType: KotlinType?, v: InstructionAdapter) {
            if (getter == null) {
                throw UnsupportedOperationException("no getter specified")
            }
            val callGenerator = callGenerator
            callGenerator.genCall(getter, resolvedGetCall, genDefaultMaskIfPresent(callGenerator), codegen)
            coerceTo(type, kotlinType, v)
        }

        private fun genDefaultMaskIfPresent(callGenerator: CallGenerator): Boolean {
            val defaultArgs = (receiver as CollectionElementReceiver).defaultArgs
            return defaultArgs!!.generateOnStackIfNeeded(callGenerator, true)
        }

        override fun receiverSize(): Int {
            return if (isStandardStack(codegen.typeMapper, resolvedGetCall, 1) && isStandardStack(codegen.typeMapper, resolvedSetCall, 2)) {
                2
            } else {
                -1
            }
        }

        override fun storeSelector(topOfStackType: Type, topOfStackKotlinType: KotlinType?, v: InstructionAdapter) {
            if (setter == null) {
                throw UnsupportedOperationException("no setter specified")
            }

            val lastParameterType = setter.parameterTypes.last()
            val lastParameterKotlinType = resolvedSetCall!!.resultingDescriptor.original.valueParameters.last().type

            coerce(topOfStackType, topOfStackKotlinType, lastParameterType, lastParameterKotlinType, v)

            callGenerator.putValueIfNeeded(
                JvmKotlinType(lastParameterType, lastParameterKotlinType),
                StackValue.onStack(lastParameterType, lastParameterKotlinType)
            )

            //Convention setter couldn't have default parameters, just getter can have it at last positions
            //We should remove default parameters of getter from stack*/
            //Note that it works only for non-inline case
            val collectionElementReceiver = receiver as CollectionElementReceiver
            if (collectionElementReceiver.isGetter) {
                val arguments = collectionElementReceiver.valueArguments
                val types = getter!!.valueParameterTypes
                for (i in arguments.indices.reversed()) {
                    val argument = arguments[i]
                    if (argument is DefaultValueArgument) {
                        val defaultType = types[i]
                        AsmUtil.swap(v, lastParameterType, defaultType)
                        AsmUtil.pop(v, defaultType)
                    }
                }
            }

            callGenerator.genCall(setter, resolvedSetCall, false, codegen)
            val returnType = setter.returnType
            if (returnType !== Type.VOID_TYPE) {
                pop(v, returnType)
            }
        }

        companion object {

            fun isStandardStack(typeMapper: KotlinTypeMapper, call: ResolvedCall<*>?, valueParamsSize: Int): Boolean {
                if (call == null) {
                    return true
                }

                val valueParameters = call.resultingDescriptor.valueParameters
                if (valueParameters.size != valueParamsSize) {
                    return false
                }

                for (valueParameter in valueParameters) {
                    if (typeMapper.mapType(valueParameter.type).size != 1) {
                        return false
                    }
                }

                if (call.dispatchReceiver != null) {
                    if (call.extensionReceiver != null) {
                        return false
                    }
                } else {

                    if (typeMapper.mapType(call.resultingDescriptor.extensionReceiverParameter!!.type).size != 1) {
                        return false
                    }
                }

                return true
            }
        }
    }

    class Field(
        type: Type,
        kotlinType: KotlinType?,
        @JvmField val owner: Type,
        @JvmField val name: String,
        isStatic: Boolean,
        receiver: StackValue,
        @JvmField val descriptor: DeclarationDescriptor?
    ) : StackValueWithSimpleReceiver(type, kotlinType, isStatic, isStatic, receiver, receiver.canHaveSideEffects()) {

        override fun putSelector(type: Type, kotlinType: KotlinType?, v: InstructionAdapter) {
            v.visitFieldInsn(if (isStaticPut) GETSTATIC else GETFIELD, owner.internalName, name, this.type.descriptor)
            coerceTo(type, kotlinType, v)
        }

        override fun storeSelector(topOfStackType: Type, topOfStackKotlinType: KotlinType?, v: InstructionAdapter) {
            coerceFrom(topOfStackType, topOfStackKotlinType, v)
            v.visitFieldInsn(if (isStaticStore) PUTSTATIC else PUTFIELD, owner.internalName, name, this.type.descriptor)
        }

        override fun changeReceiver(newReceiver: StackValue): StackValueWithSimpleReceiver {
            return field(this, newReceiver)
        }
    }

    class Property : StackValueWithSimpleReceiver {
        private val getter: CallableMethod?
        private val setter: CallableMethod?
        private val backingFieldOwner: Type?
        private val descriptor: PropertyDescriptor
        private val fieldName: String?
        private val codegen: ExpressionCodegen
        private val resolvedCall: ResolvedCall<*>?
        private val skipLateinitAssertion: Boolean
        private val delegateKotlinType: KotlinType?

        val delegateOrNull: Property?
            get() = if (delegateKotlinType == null) null else Property(this, DelegatePropertyConstructorMarker)

        constructor(
            descriptor: PropertyDescriptor, backingFieldOwner: Type?, getter: CallableMethod?,
            setter: CallableMethod?, isStaticBackingField: Boolean, fieldName: String?, type: Type,
            receiver: StackValue, codegen: ExpressionCodegen, resolvedCall: ResolvedCall<*>?,
            skipLateinitAssertion: Boolean, delegateKotlinType: KotlinType?
        ) : super(type, descriptor.type, isStatic(isStaticBackingField, getter), isStatic(isStaticBackingField, setter), receiver, true) {
            this.backingFieldOwner = backingFieldOwner
            this.getter = getter
            this.setter = setter
            this.descriptor = descriptor
            this.fieldName = fieldName
            this.codegen = codegen
            this.resolvedCall = resolvedCall
            this.skipLateinitAssertion = skipLateinitAssertion
            this.delegateKotlinType = delegateKotlinType
        }

        private object DelegatePropertyConstructorMarker {

        }

        /**
         * Given a delegating property, create a "property" corresponding to the underlying delegate itself.
         * This will take care of backing fields, accessors, and other such stuff.
         * Note that we just replace `kotlinType` with the `delegateKotlinType`
         * (so that type coercion will work properly),
         * and `delegateKotlinType` with `null`
         * (so that the resulting property has no underlying delegate of its own).
         *
         * @param delegating    delegating property
         * @param marker        intent marker
         */
        private constructor(delegating: Property, marker: DelegatePropertyConstructorMarker) : super(
            delegating.type, delegating.delegateKotlinType,
            delegating.isStaticPut, delegating.isStaticStore, delegating.receiver, true
        ) {

            this.backingFieldOwner = delegating.backingFieldOwner
            this.getter = delegating.getter
            this.setter = delegating.setter
            this.descriptor = delegating.descriptor
            this.fieldName = delegating.fieldName
            this.codegen = delegating.codegen
            this.resolvedCall = delegating.resolvedCall
            this.skipLateinitAssertion = delegating.skipLateinitAssertion
            this.delegateKotlinType = null
        }

        override fun putSelector(type: Type, kotlinType: KotlinType?, v: InstructionAdapter) {
            if (getter == null) {
                assert(fieldName != null) { "Property should have either a getter or a field name: $descriptor" }
                assert(backingFieldOwner != null) { "Property should have either a getter or a backingFieldOwner: $descriptor" }
                if (inlineConstantIfNeeded(type, kotlinType, v)) return

                v.visitFieldInsn(
                    if (isStaticPut) GETSTATIC else GETFIELD,
                    backingFieldOwner!!.internalName, fieldName, this.type.descriptor
                )
                if (!skipLateinitAssertion && descriptor.isLateInit) {
                    val contextDescriptor = codegen.context.contextDescriptor
                    val isCompanionAccessor =
                        contextDescriptor is AccessorForPropertyBackingField && contextDescriptor.accessorKind === AccessorKind.IN_CLASS_COMPANION
                    if (!isCompanionAccessor) {
                        genNonNullAssertForLateinit(v, this.descriptor.name.asString())
                    }
                }
                coerceTo(type, kotlinType, v)
            } else {
                val getterDescriptor = descriptor.getter ?: error("Getter descriptor should be not null for $descriptor")
                if (resolvedCall != null && getterDescriptor!!.isInline) {
                    val callGenerator = codegen.getOrCreateCallGenerator(resolvedCall, getterDescriptor!!)
                    callGenerator.processAndPutHiddenParameters(false)
                    callGenerator.genCall(getter, resolvedCall, false, codegen)
                } else {
                    getter.genInvokeInstruction(v)
                }

                var typeOfValueOnStack = getter.returnType
                var kotlinTypeOfValueOnStack = getterDescriptor!!.returnType
                if (DescriptorUtils.isAnnotationClass(descriptor.containingDeclaration)) {
                    if (this.type == K_CLASS_TYPE) {
                        wrapJavaClassIntoKClass(v)
                        typeOfValueOnStack = K_CLASS_TYPE
                        kotlinTypeOfValueOnStack = null
                    } else if (this.type == K_CLASS_ARRAY_TYPE) {
                        wrapJavaClassesIntoKClasses(v)
                        typeOfValueOnStack = K_CLASS_ARRAY_TYPE
                        kotlinTypeOfValueOnStack = null
                    }
                }

                coerce(typeOfValueOnStack, kotlinTypeOfValueOnStack, type, kotlinType, v)

                // For non-private lateinit properties in companion object, the assertion is generated in the public getFoo method
                // in the companion and _not_ in the synthetic accessor access$getFoo$cp in the outer class. The reason is that this way,
                // the synthetic accessor can be reused for isInitialized checks, which require there to be no assertion.
                // For lateinit properties that are accessed via the backing field directly (or via the synthetic accessor, if the access
                // is from a different context), the assertion will be generated on each access, see KT-28331.
                if (descriptor is AccessorForPropertyBackingField) {
                    val property = descriptor.calleeDescriptor
                    if (!skipLateinitAssertion && property.isLateInit && JvmAbi.isPropertyWithBackingFieldInOuterClass(property) &&
                        !JvmCodegenUtil.couldUseDirectAccessToProperty(property, true, false, codegen.context, false)
                    ) {
                        genNonNullAssertForLateinit(v, property.name.asString())
                    }
                }

                val returnType = descriptor.returnType
                if (returnType != null && KotlinBuiltIns.isNothing(returnType)) {
                    v.aconst(null)
                    v.athrow()
                }
            }
        }

        private fun inlineConstantIfNeeded(type: Type, kotlinType: KotlinType?, v: InstructionAdapter): Boolean {
            if (JvmCodegenUtil.isInlinedJavaConstProperty(descriptor)) {
                return inlineConstant(type, kotlinType, v)
            }

            return if (descriptor.isConst && codegen.state.shouldInlineConstVals) {
                inlineConstant(type, kotlinType, v)
            } else false

        }

        private fun inlineConstant(type: Type, kotlinType: KotlinType?, v: InstructionAdapter): Boolean {
            assert(AsmUtil.isPrimitive(this.type) || AsmTypes.JAVA_STRING_TYPE == this.type) { "Const property should have primitive or string type: $descriptor" }
            assert(isStaticPut) { "Const property should be static$descriptor" }

            val constantValue = descriptor.compileTimeInitializer ?: return false

            var value = constantValue.value
            if (this.type === Type.FLOAT_TYPE && value is Double) {
                value = value.toFloat()
            }

            StackValue.constant(value, this.type, this.kotlinType).putSelector(type, kotlinType, v)

            return true
        }

        override fun store(rightSide: StackValue, v: InstructionAdapter, skipReceiver: Boolean) {
            val setterDescriptor = descriptor.setter
            if (resolvedCall != null && setterDescriptor != null && setterDescriptor!!.isInline) {
                assert(setter != null) { "Setter should be not null for $descriptor" }
                val callGenerator = codegen.getOrCreateCallGenerator(resolvedCall, setterDescriptor!!)
                if (!skipReceiver) {
                    putReceiver(v, false)
                }
                callGenerator.processAndPutHiddenParameters(true)
                callGenerator.putValueIfNeeded(
                    JvmKotlinType(
                        setter!!.getValueParameters().last().asmType,
                        setterDescriptor!!.valueParameters.last().getType()
                    ),
                    rightSide
                )
                callGenerator.putHiddenParamsIntoLocals()
                callGenerator.genCall(setter, resolvedCall, false, codegen)
            } else {
                super.store(rightSide, v, skipReceiver)
            }
        }

        override fun storeSelector(topOfStackType: Type, topOfStackKotlinType: KotlinType?, v: InstructionAdapter) {
            if (setter == null) {
                coerceFrom(topOfStackType, topOfStackKotlinType, v)
                assert(fieldName != null) { "Property should have either a setter or a field name: $descriptor" }
                assert(backingFieldOwner != null) { "Property should have either a setter or a backingFieldOwner: $descriptor" }
                v.visitFieldInsn(
                    if (isStaticStore) PUTSTATIC else PUTFIELD,
                    backingFieldOwner!!.internalName,
                    fieldName,
                    this.type.descriptor
                )
            } else {
                val setterDescriptor = descriptor.setter
                val setterLastParameterType =
                    if (setterDescriptor != null) setterDescriptor!!.valueParameters.last().getReturnType() else null

                coerce(topOfStackType, topOfStackKotlinType, this.setter.parameterTypes.last(), setterLastParameterType, v)
                setter.genInvokeInstruction(v)

                val returnType = this.setter.returnType
                if (returnType !== Type.VOID_TYPE) {
                    pop(v, returnType)
                }
            }
        }

        companion object {
            private fun isStatic(isStaticBackingField: Boolean, callable: CallableMethod?): Boolean {
                if (isStaticBackingField && callable == null) {
                    return true
                }

                if (callable != null && callable.isStaticCall()) {
                    val parameters = callable.getValueParameters()
                    for (parameter in parameters) {
                        val kind = parameter.kind
                        if (kind == JvmMethodParameterKind.VALUE) {
                            break
                        }
                        if (kind == JvmMethodParameterKind.RECEIVER || kind == JvmMethodParameterKind.THIS) {
                            return false
                        }
                    }
                    return true
                }

                return false
            }
        }
    }

    private class Expression(type: Type, private val expression: KtExpression, private val generator: ExpressionCodegen) :
        StackValue(type, generator.kotlinType(expression)) {

        override fun putSelector(type: Type, kotlinType: KotlinType?, v: InstructionAdapter) {
            generator.gen(expression, type, kotlinType)
        }
    }

    class Shared @JvmOverloads constructor(
        val index: Int,
        type: Type,
        kotlinType: KotlinType? = null,
        private val isLateinit: Boolean = false,
        private val name: Name? = null
    ) : StackValueWithSimpleReceiver(type, kotlinType, false, false, local(index, OBJECT_TYPE), false) {

        init {
            if (isLateinit && name == null) {
                throw IllegalArgumentException("Lateinit shared local variable should have name: #" + index + " " + type.descriptor)
            }
        }

        override fun putSelector(type: Type, kotlinType: KotlinType?, v: InstructionAdapter) {
            val refType = refType(this.type)
            val sharedType = sharedTypeForType(this.type)
            v.visitFieldInsn(GETFIELD, sharedType.internalName, "element", refType.descriptor)
            if (isLateinit) {
                StackValue.genNonNullAssertForLateinit(v, name!!.asString())
            }
            coerceFrom(refType, null, v)
            coerceTo(type, kotlinType, v)
        }

        override fun storeSelector(topOfStackType: Type, topOfStackKotlinType: KotlinType?, v: InstructionAdapter) {
            coerceFrom(topOfStackType, topOfStackKotlinType, v)
            val refType = refType(this.type)
            val sharedType = sharedTypeForType(this.type)
            v.visitFieldInsn(PUTFIELD, sharedType.internalName, "element", refType.descriptor)
        }
    }

    class FieldForSharedVar(
        type: Type, kotlinType: KotlinType?,
        internal val owner: Type, internal val name: String, receiver: StackValue.Field,
        internal val isLateinit: Boolean, internal val variableName: Name
    ) : StackValueWithSimpleReceiver(type, kotlinType, false, false, receiver, receiver.canHaveSideEffects()) {

        init {

            if (isLateinit && variableName == null) {
                throw IllegalArgumentException("variableName should be non-null for captured lateinit variable $name")
            }
        }

        override fun putSelector(type: Type, kotlinType: KotlinType?, v: InstructionAdapter) {
            val sharedType = sharedTypeForType(this.type)
            val refType = refType(this.type)
            v.visitFieldInsn(GETFIELD, sharedType.internalName, "element", refType.descriptor)
            if (isLateinit) {
                StackValue.genNonNullAssertForLateinit(v, variableName.asString())
            }
            coerceFrom(refType, null, v)
            coerceTo(type, kotlinType, v)
        }

        override fun storeSelector(topOfStackType: Type, topOfStackKotlinType: KotlinType?, v: InstructionAdapter) {
            coerceFrom(topOfStackType, topOfStackKotlinType, v)
            v.visitFieldInsn(PUTFIELD, sharedTypeForType(type).internalName, "element", refType(type).descriptor)
        }

        override fun changeReceiver(newReceiver: StackValue): StackValueWithSimpleReceiver {
            return fieldForSharedVar(this, newReceiver)
        }
    }

    private class ThisOuter(
        private val codegen: ExpressionCodegen,
        private val descriptor: ClassDescriptor,
        private val isSuper: Boolean,
        private val coerceType: Boolean
    ) : StackValue(OBJECT_TYPE, false) {

        override fun putSelector(type: Type, kotlinType: KotlinType?, v: InstructionAdapter) {
            val stackValue = codegen.generateThisOrOuter(descriptor, isSuper)
            stackValue.put(
                if (coerceType) type else stackValue.type,
                if (coerceType) kotlinType else stackValue.kotlinType,
                v
            )
        }
    }

    private class PostIncrement(private val index: Int, private val increment: Int) : StackValue(Type.INT_TYPE) {

        override fun putSelector(type: Type, kotlinType: KotlinType?, v: InstructionAdapter) {
            if (type != Type.VOID_TYPE) {
                v.load(index, Type.INT_TYPE)
                coerceTo(type, kotlinType, v)
            }
            v.iinc(index, increment)
        }
    }

    private class PreIncrementForLocalVar(private val index: Int, private val increment: Int, kotlinType: KotlinType?) :
        StackValue(Type.INT_TYPE, kotlinType) {

        override fun putSelector(type: Type, kotlinType: KotlinType?, v: InstructionAdapter) {
            v.iinc(index, increment)
            if (type != Type.VOID_TYPE) {
                v.load(index, Type.INT_TYPE)
                coerceTo(type, kotlinType, v)
            }
        }
    }

    private class PrefixIncrement(
        type: Type,
        private var value: StackValue,
        private val resolvedCall: ResolvedCall<*>,
        private val codegen: ExpressionCodegen
    ) : StackValue(type, value.kotlinType) {

        override fun putSelector(type: Type, kotlinType: KotlinType?, v: InstructionAdapter) {
            value = StackValue.complexReceiver(value, true, false, true)
            value!!.put(this.type, this.kotlinType, v)

            value!!.store(codegen.invokeFunction(resolvedCall, StackValue.onStack(this.type, this.kotlinType)), v, true)

            value!!.put(this.type, this.kotlinType, v, true)
            coerceTo(type, kotlinType, v)
        }
    }

    abstract class StackValueWithSimpleReceiver(
        type: Type,
        kotlinType: KotlinType?,
        @JvmField val isStaticPut: Boolean,
        val isStaticStore: Boolean,
        @JvmField val receiver: StackValue,
        canHaveSideEffects: Boolean
    ) : StackValue(type, kotlinType, canHaveSideEffects) {

        override fun putReceiver(v: InstructionAdapter, isRead: Boolean) {
            val hasReceiver = isNonStaticAccess(isRead)
            if (hasReceiver || receiver.canHaveSideEffects()) {
                receiver.put(
                    if (hasReceiver) receiver.type else Type.VOID_TYPE,
                    if (hasReceiver) receiver.kotlinType else null,
                    v
                )
            }
        }

        override fun isNonStaticAccess(isRead: Boolean): Boolean {
            return if (isRead) !isStaticPut else !isStaticStore
        }

        open fun receiverSize(): Int {
            return receiver.type.size
        }

        override fun dup(v: InstructionAdapter, withWriteReceiver: Boolean) {
            if (!withWriteReceiver) {
                super.dup(v, false)
            } else {
                val receiverSize = if (isNonStaticAccess(false)) receiverSize() else 0
                when (receiverSize) {
                    0 -> AsmUtil.dup(v, type)

                    1 -> if (type.size == 2) {
                        v.dup2X1()
                    } else {
                        v.dupX1()
                    }

                    2 -> if (type.size == 2) {
                        v.dup2X2()
                    } else {
                        v.dupX2()
                    }

                    -1 -> throw UnsupportedOperationException()
                }
            }
        }

        override fun store(
            rightSide: StackValue, v: InstructionAdapter, skipReceiver: Boolean
        ) {
            if (!skipReceiver) {
                putReceiver(v, false)
            }
            rightSide.put(rightSide.type, rightSide.kotlinType, v)
            storeSelector(rightSide.type, rightSide.kotlinType, v)
        }

        open fun changeReceiver(newReceiver: StackValue): StackValueWithSimpleReceiver {
            return this
        }
    }

    private class ComplexReceiver(
        private val originalValueWithReceiver: StackValueWithSimpleReceiver,
        private val isReadOperations: BooleanArray
    ) : StackValue(originalValueWithReceiver.type, originalValueWithReceiver.receiver.canHaveSideEffects()) {

        init {
            if (originalValueWithReceiver is CollectionElement) {
                if (originalValueWithReceiver.receiver is CollectionElementReceiver) {
                    originalValueWithReceiver.receiver.isComplexOperationWithDup = true
                }
            }
        }

        override fun putSelector(
            type: Type, kotlinType: KotlinType?, v: InstructionAdapter
        ) {
            var wasPut = false
            val receiver = originalValueWithReceiver.receiver
            for (operation in isReadOperations) {
                if (originalValueWithReceiver.isNonStaticAccess(operation)) {
                    if (!wasPut) {
                        receiver.put(receiver.type, receiver.kotlinType, v)
                        wasPut = true
                    } else {
                        receiver.dup(v, false)
                    }
                }
            }

            if (!wasPut && receiver.canHaveSideEffects()) {
                receiver.put(Type.VOID_TYPE, null, v)
            }
        }
    }

    class Receiver(type: Type, vararg receiverInstructions: StackValue) : StackValue(type) {

        private val instructions = receiverInstructions

        override fun putSelector(
            type: Type, kotlinType: KotlinType?, v: InstructionAdapter
        ) {
            for (instruction in instructions) {
                instruction.put(instruction.type, instruction.kotlinType, v)
            }
        }
    }

    private class DelegatedForComplexReceiver(
        type: Type,
        val originalValue: StackValueWithSimpleReceiver,
        receiver: ComplexReceiver
    ) : StackValueWithSimpleReceiver(
        type,
        null,
        bothReceiverStatic(originalValue),
        bothReceiverStatic(originalValue),
        receiver,
        originalValue.canHaveSideEffects()
    ) {

        companion object {
            private fun bothReceiverStatic(originalValue: StackValueWithSimpleReceiver): Boolean {
                return !(originalValue.isNonStaticAccess(true) || originalValue.isNonStaticAccess(false))
            }
        }

        override fun putSelector(
            type: Type, kotlinType: KotlinType?, v: InstructionAdapter
        ) {
            originalValue.putSelector(type, kotlinType, v)
        }

        override fun store(rightSide: StackValue, v: InstructionAdapter, skipReceiver: Boolean) {
            if (!skipReceiver) {
                putReceiver(v, false)
            }
            originalValue.store(rightSide, v, true)
        }

        override fun storeSelector(
            topOfStackType: Type, kotlinType: KotlinType?, v: InstructionAdapter
        ) {
            originalValue.storeSelector(topOfStackType, kotlinType, v)
        }

        override fun dup(v: InstructionAdapter, withWriteReceiver: Boolean) {
            originalValue.dup(v, withWriteReceiver)
        }
    }

    internal class SafeCall(type: Type, kotlinType: KotlinType?, private val receiver: StackValue, private val ifNull: Label?) :
        StackValue(type, kotlinType) {

        override fun putSelector(type: Type, kotlinType: KotlinType?, v: InstructionAdapter) {
            receiver.put(this.type, this.kotlinType, v)
            if (ifNull != null) {
                //not a primitive
                v.dup()
                v.ifnull(ifNull)
            }
            coerceTo(type, kotlinType, v)
        }
    }

    internal class SafeFallback(type: Type, private val ifNull: Label?, receiver: StackValue) :
        StackValueWithSimpleReceiver(type, null, false, false, receiver, true) {

        override fun putSelector(type: Type, kotlinType: KotlinType?, v: InstructionAdapter) {
            val end = Label()

            v.goTo(end)
            v.mark(ifNull)
            v.pop()
            if (this.type != Type.VOID_TYPE) {
                v.aconst(null)
            }
            v.mark(end)

            coerceTo(type, kotlinType, v)
        }

        override fun store(
            rightSide: StackValue, v: InstructionAdapter, skipReceiver: Boolean
        ) {
            receiver.store(rightSide, v, skipReceiver)

            val end = Label()
            v.goTo(end)
            v.mark(ifNull)
            v.pop()
            v.mark(end)
        }
    }

    companion object {

        private const val NULLABLE_BYTE_TYPE_NAME = "java/lang/Byte"
        private const val NULLABLE_SHORT_TYPE_NAME = "java/lang/Short"
        private const val NULLABLE_LONG_TYPE_NAME = "java/lang/Long"

        @JvmField val LOCAL_0: Local = local(0, OBJECT_TYPE)

        private val UNIT = operation(UNIT_TYPE) { v ->
            v.visitFieldInsn(GETSTATIC, UNIT_TYPE.internalName, JvmAbi.INSTANCE_FIELD, UNIT_TYPE.descriptor)
            null
        }

        @JvmStatic
        fun local(index: Int, type: Type): Local {
            return Local(index, type)
        }

        @JvmStatic
        fun local(index: Int, type: Type, kotlinType: KotlinType?): Local {
            return Local(index, type, kotlinType)
        }

        @JvmStatic
        @JvmOverloads
        fun local(index: Int, type: Type, descriptor: VariableDescriptor, delegateKotlinType: KotlinType? = null): StackValue {
            if (descriptor.isLateInit) {
                assert(delegateKotlinType == null) { "Delegated property can't be lateinit: $descriptor, delegate type: $delegateKotlinType" }
                return LateinitLocal(index, type, descriptor.type, descriptor.name)
            } else {
                return Local(
                    index, type,
                    delegateKotlinType ?: descriptor.type
                )
            }
        }

        @JvmStatic
        fun localDelegate(
            type: Type,
            delegateValue: StackValue,
            metadataValue: StackValue,
            variableDescriptor: VariableDescriptorWithAccessors,
            codegen: ExpressionCodegen
        ): Delegate {
            return Delegate(type, delegateValue, metadataValue, variableDescriptor, codegen)
        }

        @JvmStatic
        fun shared(index: Int, type: Type): StackValue {
            return Shared(index, type)
        }

        @JvmStatic
        @JvmOverloads
        fun shared(index: Int, type: Type, descriptor: VariableDescriptor, delegateKotlinType: KotlinType? = null): StackValue {
            return Shared(
                index, type,
                delegateKotlinType ?: descriptor.type,
                descriptor.isLateInit, descriptor.name
            )
        }

        @JvmOverloads
        @JvmStatic
        fun onStack(type: Type, kotlinType: KotlinType? = null): StackValue {
            return if (type === Type.VOID_TYPE) none() else OnStack(type, kotlinType)
        }

        fun integerConstant(value: Int, type: Type): StackValue {
            return if (type === Type.LONG_TYPE) {
                constant(java.lang.Long.valueOf(value.toLong()), type)
            } else if (type === Type.BYTE_TYPE || type === Type.SHORT_TYPE || type === Type.INT_TYPE) {
                constant(Integer.valueOf(value), type)
            } else if (type === Type.CHAR_TYPE) {
                constant(Character.valueOf(value.toChar()), type)
            } else {
                throw AssertionError("Unexpected integer type: $type")
            }
        }

        @JvmStatic
        fun constant(value: Int): StackValue {
            return constant(value, Type.INT_TYPE)
        }

        @JvmStatic
        @JvmOverloads
        fun constant(value: Any?, type: Type, kotlinType: KotlinType? = null): StackValue {
            if (type === Type.BOOLEAN_TYPE) {
                assert(value is Boolean) { "Value for boolean constant should have boolean type: " + value!! }
                return BranchedValue.booleanConstant((value as Boolean?)!!)
            } else {
                return Constant(value, type, kotlinType)
            }
        }

        fun createDefaultValue(type: Type): StackValue {
            return if (type.sort == Type.OBJECT || type.sort == Type.ARRAY) {
                constant(null, type)
            } else {
                createDefaultPrimitiveValue(type)
            }
        }

        private fun createDefaultPrimitiveValue(type: Type): StackValue {
            assert(Type.BOOLEAN <= type.sort && type.sort <= Type.DOUBLE) { "'createDefaultPrimitiveValue' method should be called only for primitive types, but $type" }
            var value: Any = 0
            if (type.sort == Type.BOOLEAN) {
                value = java.lang.Boolean.FALSE
            } else if (type.sort == Type.FLOAT) {
                value = 0.0f
            } else if (type.sort == Type.DOUBLE) {
                value = 0.0
            } else if (type.sort == Type.LONG) {
                value = 0L
            }

            return constant(value, type)
        }

        @JvmStatic
        fun cmp(opToken: IElementType, type: Type, left: StackValue, right: StackValue): StackValue {
            return BranchedValue.cmp(opToken, type, left, right)
        }

        @JvmStatic
        fun not(stackValue: StackValue): StackValue {
            return BranchedValue.createInvertValue(stackValue)
        }

        @JvmStatic
        fun or(left: StackValue, right: StackValue): StackValue {
            return Or(left, right)
        }

        @JvmStatic
        fun and(left: StackValue, right: StackValue): StackValue {
            return And(left, right)
        }

        @JvmStatic
        fun compareIntWithZero(argument: StackValue, operation: Int): StackValue {
            return BranchedValue(argument, null, Type.INT_TYPE, operation)
        }

        @JvmStatic
        fun compareWithNull(argument: StackValue, operation: Int): StackValue {
            return BranchedValue(argument, null, AsmTypes.OBJECT_TYPE, operation)
        }

        @JvmStatic
        fun arrayElement(type: Type, kotlinType: KotlinType?, array: StackValue, index: StackValue): StackValue {
            return ArrayElement(type, kotlinType, array, index)
        }

        @JvmStatic
        fun collectionElement(
            collectionElementReceiver: CollectionElementReceiver,
            type: Type,
            kotlinType: KotlinType,
            getter: ResolvedCall<FunctionDescriptor>?,
            setter: ResolvedCall<FunctionDescriptor>?,
            codegen: ExpressionCodegen
        ): StackValue {
            return CollectionElement(collectionElementReceiver, type, kotlinType, getter, setter, codegen)
        }

        @JvmStatic
        fun underlyingValueOfInlineClass(
            type: Type,
            kotlinType: KotlinType?,
            receiver: StackValue
        ): UnderlyingValueOfInlineClass {
            return UnderlyingValueOfInlineClass(type, kotlinType, receiver)
        }

        @JvmStatic
        fun field(type: Type, owner: Type, name: String, isStatic: Boolean, receiver: StackValue): Field {
            return field(type, null, owner, name, isStatic, receiver)
        }

        @JvmOverloads
        @JvmStatic
        fun field(
            type: Type,
            kotlinType: KotlinType?,
            owner: Type,
            name: String,
            isStatic: Boolean,
            receiver: StackValue,
            descriptor: DeclarationDescriptor? = null
        ): Field {
            return Field(type, kotlinType, owner, name, isStatic, receiver, descriptor)
        }

        @JvmStatic
        fun field(field: StackValue.Field, newReceiver: StackValue): Field {
            return field(field.type, field.kotlinType, field.owner, field.name, field.isStaticPut, newReceiver, field.descriptor)
        }

        @JvmStatic
        fun field(info: FieldInfo, receiver: StackValue): Field {
            return field(
                info.fieldType,
                info.fieldKotlinType,
                Type.getObjectType(info.ownerInternalName),
                info.fieldName,
                info.isStatic,
                receiver
            )
        }

        @JvmStatic
        fun changeReceiverForFieldAndSharedVar(stackValue: StackValueWithSimpleReceiver, newReceiver: StackValue?): StackValue {
            //TODO static check
            return if (newReceiver == null || stackValue.isStaticPut) stackValue else stackValue.changeReceiver(newReceiver)
        }

        @JvmStatic
        fun property(
            descriptor: PropertyDescriptor,
            backingFieldOwner: Type?,
            type: Type,
            isStaticBackingField: Boolean,
            fieldName: String?,
            getter: CallableMethod?,
            setter: CallableMethod?,
            receiver: StackValue,
            codegen: ExpressionCodegen,
            resolvedCall: ResolvedCall<*>?,
            skipLateinitAssertion: Boolean,
            delegateKotlinType: KotlinType?
        ): Property {
            return Property(
                descriptor, backingFieldOwner, getter, setter, isStaticBackingField, fieldName, type, receiver, codegen,
                resolvedCall, skipLateinitAssertion, delegateKotlinType
            )
        }

        @JvmStatic
        fun expression(type: Type, expression: KtExpression, generator: ExpressionCodegen): StackValue {
            return Expression(type, expression, generator)
        }

        private fun box(type: Type, toType: Type, v: InstructionAdapter) {
            var type = type
            if (type === Type.INT_TYPE) {
                if (toType.internalName == NULLABLE_BYTE_TYPE_NAME) {
                    type = Type.BYTE_TYPE
                } else if (toType.internalName == NULLABLE_SHORT_TYPE_NAME) {
                    type = Type.SHORT_TYPE
                } else if (toType.internalName == NULLABLE_LONG_TYPE_NAME) {
                    type = Type.LONG_TYPE
                }
                v.cast(Type.INT_TYPE, type)
            }

            val boxedType = AsmUtil.boxType(type)
            if (boxedType === type) return

            v.invokestatic(boxedType.internalName, "valueOf", Type.getMethodDescriptor(boxedType, type), false)
            coerce(boxedType, toType, v)
        }

        private fun unbox(methodOwner: Type, type: Type, v: InstructionAdapter) {
            assert(isPrimitive(type)) { "Unboxing should be performed to primitive type, but " + type.className }
            v.invokevirtual(methodOwner.internalName, type.className + "Value", "()" + type.descriptor, false)
        }

        private fun boxInlineClass(kotlinType: KotlinType, v: InstructionAdapter) {
            val boxedType = KotlinTypeMapper.mapInlineClassTypeAsDeclaration(kotlinType)
            val underlyingType = KotlinTypeMapper.mapUnderlyingTypeOfInlineClassType(kotlinType)

            if (TypeUtils.isNullableType(kotlinType) && !isPrimitive(underlyingType)) {
                boxOrUnboxWithNullCheck(v, { vv -> invokeBoxMethod(vv, boxedType, underlyingType) })
            } else {
                invokeBoxMethod(v, boxedType, underlyingType)
            }
        }

        private fun invokeBoxMethod(
            v: InstructionAdapter,
            boxedType: Type,
            underlyingType: Type
        ) {
            v.invokestatic(
                boxedType.internalName,
                KotlinTypeMapper.BOX_JVM_METHOD_NAME,
                Type.getMethodDescriptor(boxedType, underlyingType),
                false
            )
        }

        @JvmStatic
        fun unboxInlineClass(type: Type, targetInlineClassType: KotlinType, v: InstructionAdapter) {
            val owner = KotlinTypeMapper.mapInlineClassTypeAsDeclaration(targetInlineClassType)

            coerce(type, owner, v)

            val resultType = KotlinTypeMapper.mapUnderlyingTypeOfInlineClassType(targetInlineClassType)

            if (TypeUtils.isNullableType(targetInlineClassType) && !isPrimitive(resultType)) {
                boxOrUnboxWithNullCheck(v, { vv -> invokeUnboxMethod(vv, owner, resultType) })
            } else {
                invokeUnboxMethod(v, owner, resultType)
            }
        }

        private fun invokeUnboxMethod(
            v: InstructionAdapter,
            owner: Type,
            resultType: Type
        ) {
            v.invokevirtual(
                owner.internalName,
                KotlinTypeMapper.UNBOX_JVM_METHOD_NAME,
                "()" + resultType.descriptor,
                false
            )
        }

        private fun boxOrUnboxWithNullCheck(v: InstructionAdapter, body: Consumer<InstructionAdapter>) {
            val lNull = Label()
            val lDone = Label()
            // NB The following piece of code looks sub-optimal (we have a 'null' value on stack and could just keep it there),
            // but it is required, because bytecode verifier doesn't take into account null checks,
            // and sees null-checked value on the top of the stack as a value of the source type (e.g., Ljava/lang/String;),
            // which is not assignable to the expected type (destination type, e.g., LStr;).
            v.dup()
            v.ifnull(lNull)
            body.accept(v)
            v.goTo(lDone)
            v.mark(lNull)
            v.pop()
            v.aconst(null)
            v.mark(lDone)
        }

        @JvmStatic
        fun coerce(
            fromType: Type,
            fromKotlinType: KotlinType?,
            toType: Type,
            toKotlinType: KotlinType?,
            v: InstructionAdapter
        ) {
            if (coerceInlineClasses(fromType, fromKotlinType, toType, toKotlinType, v)) return
            coerce(fromType, toType, v)
        }

        @JvmStatic
        fun requiresInlineClassBoxingOrUnboxing(
            fromType: Type,
            fromKotlinType: KotlinType?,
            toType: Type,
            toKotlinType: KotlinType?
        ): Boolean {
            // NB see also coerceInlineClasses below

            if (fromKotlinType == null || toKotlinType == null) return false

            val isFromTypeInlineClass = fromKotlinType.isInlineClassType()
            val isToTypeInlineClass = toKotlinType.isInlineClassType()

            if (!isFromTypeInlineClass && !isToTypeInlineClass) return false

            val isFromTypeUnboxed = isFromTypeInlineClass && isUnboxedInlineClass(fromKotlinType, fromType)
            val isToTypeUnboxed = isToTypeInlineClass && isUnboxedInlineClass(toKotlinType, toType)

            return if (isFromTypeInlineClass && isToTypeInlineClass) {
                isFromTypeUnboxed != isToTypeUnboxed
            } else {
                isFromTypeInlineClass /* && !isToTypeInlineClass */ && isFromTypeUnboxed || isToTypeInlineClass /* && !isFromTypeInlineClass */ && isToTypeUnboxed
            }
        }

        private fun coerceInlineClasses(
            fromType: Type,
            fromKotlinType: KotlinType?,
            toType: Type,
            toKotlinType: KotlinType?,
            v: InstructionAdapter
        ): Boolean {
            // NB see also requiresInlineClassBoxingOrUnboxing above

            if (fromKotlinType == null || toKotlinType == null) return false

            val isFromTypeInlineClass = fromKotlinType.isInlineClassType()
            val isToTypeInlineClass = toKotlinType.isInlineClassType()

            if (!isFromTypeInlineClass && !isToTypeInlineClass) return false

            if (fromKotlinType == toKotlinType && fromType == toType) return true

            /*
        * Preconditions: one of the types is definitely inline class type and types are not equal
        * Consider the following situations:
        *  - both types are inline class types: we do box/unbox only if they are not both boxed or unboxed
        *  - from type is inline class type: we should do box, because target type can be only "subtype" of inline class type (like Any)
        *  - target type is inline class type: we should do unbox, because from type can come from some 'is' check for object type
        *
        *  "return true" means that types were coerced successfully and usual coercion shouldn't be evaluated
        * */

            if (isFromTypeInlineClass && isToTypeInlineClass) {
                val isFromTypeUnboxed = isUnboxedInlineClass(fromKotlinType, fromType)
                val isToTypeUnboxed = isUnboxedInlineClass(toKotlinType, toType)
                if (isFromTypeUnboxed && !isToTypeUnboxed) {
                    boxInlineClass(fromKotlinType, v)
                    return true
                } else if (!isFromTypeUnboxed && isToTypeUnboxed) {
                    unboxInlineClass(fromType, toKotlinType, v)
                    return true
                }
            } else if (isFromTypeInlineClass) {
                if (isUnboxedInlineClass(fromKotlinType, fromType)) {
                    boxInlineClass(fromKotlinType, v)
                    return true
                }
            } else { // isToTypeInlineClass is `true`
                if (isUnboxedInlineClass(toKotlinType, toType)) {
                    unboxInlineClass(fromType, toKotlinType, v)
                    return true
                }
            }

            return false
        }

        private fun isUnboxedInlineClass(kotlinType: KotlinType, actualType: Type): Boolean {
            return KotlinTypeMapper.mapUnderlyingTypeOfInlineClassType(kotlinType) == actualType
        }

        @JvmStatic
        fun coerce(fromType: Type, toType: Type, v: InstructionAdapter) {
            if (toType == fromType) return

            if (toType.sort == Type.VOID) {
                pop(v, fromType)
            } else if (fromType.sort == Type.VOID) {
                if (toType == UNIT_TYPE || toType == OBJECT_TYPE) {
                    putUnitInstance(v)
                } else if (toType.sort == Type.OBJECT || toType.sort == Type.ARRAY) {
                    v.aconst(null)
                } else {
                    pushDefaultPrimitiveValueOnStack(toType, v)
                }
            } else if (toType == UNIT_TYPE) {
                if (fromType == getType(Any::class.java)) {
                    v.checkcast(UNIT_TYPE)
                } else if (fromType != getType(Void::class.java)) {
                    pop(v, fromType)
                    putUnitInstance(v)
                }
            } else if (toType.sort == Type.ARRAY) {
                if (fromType.sort != Type.ARRAY) {
                    v.checkcast(toType)
                } else if (toType.dimensions != fromType.dimensions) {
                    v.checkcast(toType)
                } else if (toType.elementType != OBJECT_TYPE) {
                    v.checkcast(toType)
                }
            } else if (toType.sort == Type.OBJECT) {
                if (fromType.sort == Type.OBJECT || fromType.sort == Type.ARRAY) {
                    if (toType != OBJECT_TYPE) {
                        v.checkcast(toType)
                    }
                } else {
                    box(fromType, toType, v)
                }
            } else if (fromType.sort == Type.OBJECT) {
                //toType is primitive here
                val unboxedType = unboxPrimitiveTypeOrNull(fromType)
                if (unboxedType != null) {
                    unbox(fromType, unboxedType, v)
                    coerce(unboxedType, toType, v)
                } else if (toType.sort == Type.BOOLEAN) {
                    coerce(fromType, BOOLEAN_WRAPPER_TYPE, v)
                    unbox(BOOLEAN_WRAPPER_TYPE, Type.BOOLEAN_TYPE, v)
                } else if (toType.sort == Type.CHAR) {
                    if (fromType == NUMBER_TYPE) {
                        unbox(NUMBER_TYPE, Type.INT_TYPE, v)
                        v.visitInsn(Opcodes.I2C)
                    } else {
                        coerce(fromType, CHARACTER_WRAPPER_TYPE, v)
                        unbox(CHARACTER_WRAPPER_TYPE, Type.CHAR_TYPE, v)
                    }
                } else {
                    coerce(fromType, NUMBER_TYPE, v)
                    unbox(NUMBER_TYPE, toType, v)
                }
            } else {
                v.cast(fromType, toType)
            }
        }

        @JvmStatic
        fun putUnitInstance(v: InstructionAdapter) {
            unit().put(UNIT_TYPE, null, v)
        }

        @JvmStatic
        fun unit(): StackValue {
            return UNIT
        }

        @JvmStatic
        fun none(): StackValue {
            return None.INSTANCE
        }

        @JvmStatic
        fun receiverWithRefWrapper(
            localType: Type,
            classType: Type,
            fieldName: String,
            receiver: StackValue,
            descriptor: DeclarationDescriptor?
        ): Field {
            return field(sharedTypeForType(localType), null, classType, fieldName, false, receiver, descriptor)
        }

        @JvmStatic
        fun fieldForSharedVar(
            localType: Type,
            classType: Type,
            fieldName: String,
            refWrapper: Field,
            variableDescriptor: VariableDescriptor
        ): FieldForSharedVar {
            return FieldForSharedVar(
                localType, variableDescriptor.type, classType, fieldName, refWrapper,
                variableDescriptor.isLateInit, variableDescriptor.name
            )
        }

        @JvmStatic
        fun fieldForSharedVar(field: FieldForSharedVar, newReceiver: StackValue): FieldForSharedVar {
            val oldReceiver = field.receiver as Field
            val newSharedVarReceiver = field(oldReceiver, newReceiver)
            return FieldForSharedVar(
                field.type, field.kotlinType,
                field.owner, field.name, newSharedVarReceiver, field.isLateinit, field.variableName
            )
        }

        @JvmStatic
        fun coercion(value: StackValue, castType: Type, castKotlinType: KotlinType?): StackValue {
            return coercionValueForArgumentOfInlineClassConstructor(value, castType, castKotlinType, null)
        }

        @JvmStatic
        fun coercionValueForArgumentOfInlineClassConstructor(
            value: StackValue,
            castType: Type,
            castKotlinType: KotlinType?,
            underlyingKotlinType: KotlinType?
        ): StackValue {
            val kotlinTypesAreEqual =
                value.kotlinType == null && castKotlinType == null || value.kotlinType != null && castKotlinType != null && castKotlinType == value.kotlinType
            return if (value.type == castType && kotlinTypesAreEqual) {
                value
            } else CoercionValue(value, castType, castKotlinType, underlyingKotlinType)
        }

        @JvmStatic
        fun thisOrOuter(
            codegen: ExpressionCodegen,
            descriptor: ClassDescriptor,
            isSuper: Boolean,
            castReceiver: Boolean
        ): StackValue {
            // Coerce 'this' for the case when it is smart cast.
            // Do not coerce for other cases due to the 'protected' access issues (JVMS 7, 4.9.2 Structural Constraints).
            val coerceType = descriptor.kind == ClassKind.INTERFACE || descriptor.isInline || castReceiver && !isSuper
            return ThisOuter(codegen, descriptor, isSuper, coerceType)
        }

        @JvmStatic
        fun postIncrement(index: Int, increment: Int): StackValue {
            return PostIncrement(index, increment)
        }

        @JvmStatic
        fun preIncrementForLocalVar(index: Int, increment: Int, kotlinType: KotlinType?): StackValue {
            return PreIncrementForLocalVar(index, increment, kotlinType)
        }

        @JvmStatic
        fun preIncrement(
            type: Type,
            stackValue: StackValue,
            delta: Int,
            resolvedCall: ResolvedCall<*>,
            codegen: ExpressionCodegen
        ): StackValue {
            return if (stackValue is StackValue.Local && Type.INT_TYPE === stackValue.type) {
                preIncrementForLocalVar(stackValue.index, delta, stackValue.kotlinType)
            } else PrefixIncrement(type, stackValue, resolvedCall, codegen)
        }

        @JvmStatic
        fun receiver(
            resolvedCall: ResolvedCall<*>,
            receiver: StackValue,
            codegen: ExpressionCodegen,
            callableMethod: Callable?
        ): StackValue {
            var callDispatchReceiver = resolvedCall.dispatchReceiver
            var descriptor: CallableDescriptor = resolvedCall.resultingDescriptor
            if (descriptor is SyntheticFieldDescriptor) {
                callDispatchReceiver = descriptor.getDispatchReceiverForBackend()
            }

            val callExtensionReceiver = resolvedCall.extensionReceiver

            var isImportedObjectMember = false
            if (descriptor is ImportedFromObjectCallableDescriptor<*>) {
                isImportedObjectMember = true
                descriptor = descriptor.callableFromObject
            }

            if (callDispatchReceiver != null || callExtensionReceiver != null
                || isLocalFunCall(callableMethod) || isImportedObjectMember
            ) {
                var dispatchReceiverParameter = descriptor.dispatchReceiverParameter
                val extensionReceiverParameter = descriptor.extensionReceiverParameter

                if (descriptor is SyntheticFieldDescriptor) {
                    dispatchReceiverParameter = descriptor.getDispatchReceiverParameterForBackend()
                }

                val hasExtensionReceiver = callExtensionReceiver != null
                val dispatchReceiver = platformStaticCallIfPresent(
                    genReceiver(
                        if (hasExtensionReceiver) none() else receiver,
                        codegen,
                        descriptor,
                        callableMethod,
                        callDispatchReceiver,
                        false
                    )!!,
                    descriptor
                )
                val extensionReceiver = genReceiver(receiver, codegen, descriptor, callableMethod, callExtensionReceiver, true)
                return CallReceiver.generateCallReceiver(
                    resolvedCall, codegen, callableMethod,
                    dispatchReceiverParameter, dispatchReceiver,
                    extensionReceiverParameter, extensionReceiver!!
                )
            }
            return receiver
        }

        private fun genReceiver(
            receiver: StackValue,
            codegen: ExpressionCodegen,
            descriptor: CallableDescriptor,
            callableMethod: Callable?,
            receiverValue: ReceiverValue?,
            isExtension: Boolean
        ): StackValue? {
            val containingDeclaration = descriptor.containingDeclaration
            if (receiver === none()) {
                if (receiverValue != null) {
                    return codegen.generateReceiverValue(receiverValue, false)
                } else if (isLocalFunCall(callableMethod) && !isExtension) {
                    if (descriptor is SimpleFunctionDescriptor) {
                        val initial = descriptor.unwrapInitialDescriptorForSuspendFunction()
                        if (initial != null && initial.isSuspend) {
                            return putLocalSuspendFunctionOnStack(codegen, initial.original)
                        }
                    }
                    return codegen.findLocalOrCapturedValue(descriptor.original)
                        ?: error("Local fun should be found in locals or in captured params: $descriptor")
                } else if (!isExtension && DescriptorUtils.isObject(containingDeclaration)) {
                    // Object member could be imported by name, in which case it has no explicit dispatch receiver
                    return singleton(containingDeclaration as ClassDescriptor, codegen.typeMapper)
                }
            } else if (receiverValue != null) {
                return receiver
            }
            return none()
        }

        private fun putLocalSuspendFunctionOnStack(
            codegen: ExpressionCodegen,
            callee: SimpleFunctionDescriptor
        ): StackValue? {
            // There can be three types of suspend local function calls:
            // 1) normal call: we first define it as a closure and then call it
            // 2) call using callable reference: in this case it is not local, but rather captured value
            // 3) recursive call: we are in the middle of defining it, but, thankfully, we can simply call `this.invoke` to
            // create new coroutine
            // 4) Normal call, but the value is captured

            // First, check whether this is a normal call
            val index = codegen.lookupLocalIndex(callee)
            if (index >= 0) {
                // This is a normal local call
                return local(index, OBJECT_TYPE)
            }

            // Then check for call inside a callable reference
            val bindingContext = codegen.bindingContext
            val calleeType = CodegenBinding.asmTypeForAnonymousClass(bindingContext, callee)
            if (codegen.context.hasThisDescriptor()) {
                val thisDescriptor = codegen.context.thisDescriptor
                val classDescriptor = bindingContext.get(CLASS_FOR_CALLABLE, callee)
                if (thisDescriptor is SyntheticClassDescriptorForLambda && thisDescriptor.isCallableReference()) {
                    // Call is inside a callable reference
                    // if it is call to recursive local, just return this$0
                    val isRecursive = bindingContext.get(RECURSIVE_SUSPEND_CALLABLE_REFERENCE, thisDescriptor)
                    if (isRecursive != null && isRecursive) {
                        assert(classDescriptor != null) { "No CLASS_FOR_CALLABLE$callee" }
                        return thisOrOuter(codegen, classDescriptor!!, false, false)
                    }
                    // Otherwise, just call constructor of the closure
                    return codegen.findCapturedValue(callee)
                }
                if (classDescriptor === thisDescriptor) {
                    // Recursive suspend local function, just call invoke on this, it will create new coroutine automatically
                    codegen.v.visitVarInsn(ALOAD, 0)
                    return onStack(calleeType)
                }
            }
            // Otherwise, this is captured value
            return codegen.findCapturedValue(callee)
        }

        private fun platformStaticCallIfPresent(resultReceiver: StackValue, descriptor: CallableDescriptor): StackValue {
            return if (descriptor.isJvmStaticInObjectOrClassOrInterface()) {
                if (resultReceiver.canHaveSideEffects()) {
                    coercion(resultReceiver, Type.VOID_TYPE, null)
                } else {
                    none()
                }
            } else resultReceiver
        }

        @JvmStatic
        @Contract("null -> false")
        fun isLocalFunCall(callableMethod: Callable?): Boolean {
            return callableMethod != null && callableMethod.generateCalleeType != null
        }

        @JvmStatic
        fun receiverWithoutReceiverArgument(receiverWithParameter: StackValue): StackValue {
            return if (receiverWithParameter is CallReceiver) {
                receiverWithParameter.withoutReceiverArgument()
            } else receiverWithParameter
        }

        @JvmStatic
        fun enumEntry(descriptor: ClassDescriptor, typeMapper: KotlinTypeMapper): Field {
            val enumClass = descriptor.containingDeclaration
            assert(DescriptorUtils.isEnumClass(enumClass)) { "Enum entry should be declared in enum class: $descriptor" }
            val enumType = (enumClass as ClassDescriptor).defaultType
            val type = typeMapper.mapType(enumType)
            return field(type, enumType, type, descriptor.name.asString(), true, none(), descriptor)
        }

        @JvmStatic
        fun singleton(classDescriptor: ClassDescriptor, typeMapper: KotlinTypeMapper): Field {
            return field(FieldInfo.createForSingleton(classDescriptor, typeMapper), none())
        }

        @JvmStatic
        fun createSingletonViaInstance(classDescriptor: ClassDescriptor, typeMapper: KotlinTypeMapper, name: String): Field {
            return field(FieldInfo.createSingletonViaInstance(classDescriptor, typeMapper, name), none())
        }

        @JvmStatic
        fun operation(type: Type, lambda: Function1<InstructionAdapter, Unit>): StackValue {
            return operation(type, null, lambda)
        }

        @JvmStatic
        fun operation(type: Type, kotlinType: KotlinType?, lambda: Function1<InstructionAdapter, Unit>): StackValue {
            return OperationStackValue(type, kotlinType, lambda)
        }

        @JvmStatic
        fun functionCall(type: Type, kotlinType: KotlinType?, lambda: Function1<InstructionAdapter, Unit>): StackValue {
            return FunctionCallStackValue(type, kotlinType, lambda)
        }

        @JvmStatic
        fun couldSkipReceiverOnStaticCall(value: StackValue): Boolean {
            return value is Local || value is Constant
        }

        private fun genNonNullAssertForLateinit(v: InstructionAdapter, name: String) {
            v.dup()
            val ok = Label()
            v.ifnonnull(ok)
            v.visitLdcInsn(name)
            v.invokestatic(
                IntrinsicMethods.INTRINSICS_CLASS_NAME,
                "throwUninitializedPropertyAccessException",
                "(Ljava/lang/String;)V",
                false
            )
            v.mark(ok)
        }

        @JvmStatic
        fun sharedTypeForType(type: Type): Type {
            when (type.sort) {
                Type.OBJECT, Type.ARRAY -> return OBJECT_REF_TYPE
                else -> {
                    val primitiveType = AsmUtil.asmPrimitiveTypeToLangPrimitiveType(type) ?: throw UnsupportedOperationException()
                    return sharedTypeForPrimitive(primitiveType)
                }
            }
        }

        @JvmStatic
        fun refType(type: Type): Type {
            return if (type.sort == Type.OBJECT || type.sort == Type.ARRAY) {
                OBJECT_TYPE
            } else type

        }

        @JvmStatic
        fun complexWriteReadReceiver(stackValue: StackValue): StackValue {
            return complexReceiver(stackValue, false, true)
        }

        private fun complexReceiver(stackValue: StackValue, vararg isReadOperations: Boolean): StackValue {
            if (stackValue is Delegate) {
                //TODO need to support
                throwUnsupportedComplexOperation(stackValue.variableDescriptor)
            }

            return if (stackValue is StackValueWithSimpleReceiver) {
                DelegatedForComplexReceiver(
                    stackValue.type, stackValue,
                    ComplexReceiver(stackValue, isReadOperations)
                )
            } else {
                stackValue
            }
        }

        private fun throwUnsupportedComplexOperation(
            descriptor: CallableDescriptor
        ) {
            throw RuntimeException(
                "Augmented assignment and increment are not supported for local delegated properties and inline properties: $descriptor"
            )
        }
    }
}

