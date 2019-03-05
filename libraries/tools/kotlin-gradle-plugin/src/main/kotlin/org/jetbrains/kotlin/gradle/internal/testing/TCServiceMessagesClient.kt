package org.jetbrains.kotlin.gradle.internal.testing

import jetbrains.buildServer.messages.serviceMessages.*
import org.gradle.api.internal.tasks.testing.*
import org.gradle.api.tasks.testing.TestOutputEvent.Destination.StdErr
import org.gradle.api.tasks.testing.TestOutputEvent.Destination.StdOut
import org.gradle.api.tasks.testing.TestResult
import org.gradle.api.tasks.testing.TestResult.ResultType.*
import org.gradle.internal.operations.OperationIdentifier
import org.jetbrains.kotlin.gradle.logging.kotlinDebug
import org.jetbrains.kotlin.gradle.targets.js.NodeJsTestFailure
import org.slf4j.Logger
import java.text.ParseException
import java.lang.System.currentTimeMillis as currentTimeMillis1

internal class TCServiceMessagesClient(
    private val results: TestResultProcessor,
    val settings: TCServiceMessagesTestExecutionSpec,
    val log: Logger
): ServiceMessageParserCallback {
    inline fun root(operation: OperationIdentifier, actions: () -> Unit) {
        RootNode(operation.id).open { root ->
            if (settings.nameOfRootSuiteToAppend != null) {
                SuiteNode(root, settings.nameOfRootSuiteToAppend).open {
                    actions()
                }
            } else {
                actions()
            }
        }
    }

    override fun parseException(e: ParseException, text: String) {
        log.error("Failed to parse test process messages: \"$text\"", e)
    }

    override fun serviceMessage(message: ServiceMessage) {
        log.kotlinDebug { "TCSM: $message" }

        when (message) {
            is TestSuiteStarted -> open(message.ts, SuiteNode(leaf, hookSuiteName(message)))
            is TestStarted -> beginTest(message.ts, message.testName)
            is TestStdOut -> results.output(requireLeafTest().descriptor, DefaultTestOutputEvent(StdOut, message.stdOut))
            is TestStdErr -> results.output(requireLeafTest().descriptor, DefaultTestOutputEvent(StdErr, message.stdErr))
            is TestFailed -> requireLeafTest().failure(message)
            is TestFinished -> endTest(message.ts, message.testName)
            is TestIgnored -> {
                if (message.attributes["suite"] == "true") {
                    // non standard property for dealing with ignored test suites without visiting all inner tests
                    SuiteNode(requireLeafGroup(), message.testName).open(message.ts) { message.ts }
                } else {
                    beginTest(message.ts, message.testName, isIgnored = true)
                    endTest(message.ts, message.testName)
                }
            }
            is TestSuiteFinished -> close(message.ts, message.suiteName)
            else -> Unit
        }
    }

    private fun hookSuiteName(message: TestSuiteStarted) =
            if (leaf?.parent == null && settings.nameOfRootSuiteToReplace != null) settings.nameOfRootSuiteToReplace
            else message.suiteName

    override fun regularText(text: String) {
        log.kotlinDebug { "TCSM stdout captured: $text" }

        results.output(requireLeafTest().descriptor, DefaultTestOutputEvent(StdOut, text))
    }

    private fun beginTest(ts: Long, testName: String, isIgnored: Boolean = false) {
        val parent = requireLeafGroup()

        val methodNameCut = testName.lastIndexOf('.')
        val className: String
        val methodName: String
        if (methodNameCut != -1) {
            className = testName.substring(0, methodNameCut)
            methodName = testName.substring(methodNameCut + 1)
        } else {
            className = parent.localId
            methodName = testName
        }

        if (settings.nameOfLeafSuiteToAppend != null) {
            val group = open(ts, SuiteNode(parent, testName))
            open(ts, TestNode(group, className, methodName, "$testName.${settings.nameOfLeafSuiteToAppend}", isIgnored))
        } else {
            open(ts, TestNode(parent, className, methodName, testName, isIgnored))
        }
    }

    private fun endTest(ts: Long, testName: String) {
        if (settings.nameOfLeafSuiteToAppend != null) {
            close(ts, settings.nameOfLeafSuiteToAppend)
        }

        close(ts, testName)
    }

    private fun Node.failure(
            message: TestFailed
    ) {
        hasFailures = true
        results.failure(descriptor.id, NodeJsTestFailure(message.messageName, message.stacktrace))
    }

    private inline fun <NodeType: Node> NodeType.open(contents: (NodeType) -> Unit) = open(System.currentTimeMillis()) {
        contents(it)
        System.currentTimeMillis()
    }


    private inline fun <NodeType: Node> NodeType.open(tsStart: Long, contents: (NodeType) -> Long) {
        val child = open(tsStart, this@open)
        val tsEnd = contents(child)
        assert(close(tsEnd, child.localId) === child)
    }

    private fun <NodeType: Node> open(ts: Long, new: NodeType): NodeType = new.also {
        log.kotlinDebug { "Test node opened: $it" }

        if (!it.isSkippedRoot) {
            results.started(it.descriptor, TestStartEvent(ts, it.reportingParent?.descriptor?.id))
        }
        push(it)
    }

    private fun close(ts: Long, name: String) = pop().also {
        check(it.localId == name)

        log.kotlinDebug { "Test node closed: $it" }

        if (!it.isSkippedRoot) {
            results.completed(it.descriptor.id, TestCompleteEvent(ts, it.resultType))
        }
    }

    /**
     * Node of tests tree
     */
    abstract inner class Node(
            var parent: Node? = null,
            val localId: String
    ) {
        val reportingParent: Node?
            get() = when {
                parent == null -> null
                parent!!.isSkippedRoot -> parent!!.reportingParent
                else -> parent
            }

        val isSkippedRoot: Boolean
            get() = settings.skipRoots && parent != null && parent!!.parent == null

        val id: String = if (parent != null) "${reportingParent?.descriptor?.id}.$localId" else localId

        abstract val descriptor: TestDescriptorInternal

        var hasFailures: Boolean = false
            set(value) {
                // traverse parents only on first failure
                if (!field) {
                    field = value
                    parent?.hasFailures = true
                }
            }

        /**
         * If all tests in group are ignored, then group marked as skipped.
         * This is workaround for absence of ignored test suite flag in TC service messages protocol.
         */
        var containsNotIgnored: Boolean = false
            set(value) {
                // traverse parents only on first test
                if (!field) {
                    field = value
                    parent?.containsNotIgnored = true
                }
            }

        val resultType: TestResult.ResultType
            get() = when {
                containsNotIgnored -> when {
                    hasFailures -> FAILURE
                    else -> SUCCESS
                }
                else -> SKIPPED
            }

        override fun toString(): String = descriptor.toString()
    }

    inner class RootNode(val ownerBuildOperationId: Any) : Node(null, settings.rootNodeName) {
        override val descriptor =
                object : DefaultTestSuiteDescriptor(settings.rootNodeName, localId) {
                    override fun getOwnerBuildOperationId(): Any? = this@RootNode.ownerBuildOperationId
                }
    }

    inner class SuiteNode(parent: Node? = null, name: String) : Node(parent, name) {
        override val descriptor = object : DefaultTestSuiteDescriptor(id, name) {
            override fun getParent(): TestDescriptorInternal? = this@SuiteNode.parent?.descriptor
        }
    }

    inner class TestNode(parent: Node, className: String, methodName: String, localId: String, ignored: Boolean = false) : Node(parent, localId) {
        override val descriptor = object : DefaultTestMethodDescriptor(id, className, methodName) {
            override fun getParent(): TestDescriptorInternal? = this@TestNode.parent?.descriptor
        }

        init {
            if (!ignored) containsNotIgnored = true
        }
    }

    private var leaf: Node? = null

    private val ServiceMessage.ts: Long
        get() = creationTimestamp?.timestamp?.time ?: System.currentTimeMillis()

    private fun push(node: Node) = node.also { leaf = node }
    private fun pop() = leaf!!.also { leaf = it.parent }

    fun closeAll() {
        val ts = System.currentTimeMillis()

        while (leaf != null) {
            close(ts, leaf!!.localId)
        }
    }

    private fun requireLeafGroup() = leaf ?: error("test out of group")
    private fun requireLeafTest() = leaf as? TestNode
            ?: error("no running test")
}