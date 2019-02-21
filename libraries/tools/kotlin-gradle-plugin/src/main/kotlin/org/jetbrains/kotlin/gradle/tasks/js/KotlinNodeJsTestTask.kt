/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.tasks.js

import com.moowork.gradle.node.NodeExtension
import com.moowork.gradle.node.NodePlugin
import com.moowork.gradle.node.variant.VariantBuilder
import org.gradle.api.Project
import org.gradle.api.internal.tasks.testing.filter.DefaultTestFilter
import org.gradle.api.tasks.*
import org.gradle.process.internal.DefaultProcessForkOptions
import org.gradle.process.internal.ExecHandleFactory
import org.jetbrains.kotlin.gradle.AbstractTestProcessForkTask
import org.jetbrains.kotlin.gradle.tasks.testing.teamcity.TCServiceMessagesTestExecutionSpec
import org.jetbrains.kotlin.gradle.tasks.testing.teamcity.TCServiceMessagesTestExecutor
import java.io.File
import javax.inject.Inject

open class KotlinNodeJsTestTask : AbstractTestProcessForkTask() {
    @Input
    var ignoredTestSuites: IgnoredTestSuites =
        IgnoredTestSuites.showWithContents

    @Input
    var excludes = mutableSetOf<String>()

    @InputDirectory
    var nodeModulesDir: File? = null

    @Input
    @SkipWhenEmpty
    var nodeModulesToLoad: Set<String> = setOf()

    @InputFile
    @Optional
    var testRuntimeNodeModule: File? = null

    @Suppress("UnstableApiUsage")
    private val filterExt: DefaultTestFilter
        get() = filter as DefaultTestFilter

    init {
        filterExt.isFailOnNoMatchingTests = false
    }

    override fun createTestExecutionSpec(): TCServiceMessagesTestExecutionSpec = doCreateSpec(project)

    @get:Inject
    open val execHandleFactory: ExecHandleFactory
        get() = error("should be injected by gradle")

    private val finalTestRuntimeNodeModule: File
        get() = testRuntimeNodeModule
            ?: nodeModulesDir!!.resolve(".bin").resolve(kotlinNodeJsTestRuntimeBin)

    override fun createTestExecuter() = TCServiceMessagesTestExecutor(
        execHandleFactory,
        buildOperationExecutor
    )

    fun doCreateSpec(project: Project): TCServiceMessagesTestExecutionSpec {
        val extendedForkOptions = DefaultProcessForkOptions(getFileResolver())
        getForkOptions().copyTo(extendedForkOptions)

        if (extendedForkOptions.executable == null) {
            extendedForkOptions.executable = getNodeJsFromMooworkPlugin(project)
        }

        extendedForkOptions.environment.addPath("NODE_PATH", nodeModulesDir!!.canonicalPath)

        val cliArgs = KotlinNodeJsTestRunnerCliArgs(
            nodeModulesToLoad.toList(),
            filterExt.includePatterns + filterExt.commandLineIncludePatterns,
            excludes,
            ignoredTestSuites.cli
        )

        return TCServiceMessagesTestExecutionSpec(
            name,
            extendedForkOptions,
            listOf(finalTestRuntimeNodeModule.absolutePath) + cliArgs.toList()
        )
    }

    fun getNodeJsFromMooworkPlugin(project: Project): String? {
        project.pluginManager.apply(NodePlugin::class.java)
        val nodeJsSettings = NodeExtension.get(project)
        return VariantBuilder(nodeJsSettings).build().nodeExec
    }
}

@Suppress("EnumEntryName")
enum class IgnoredTestSuites(val cli: KotlinNodeJsTestRunnerCliArgs.IgnoredTestSuitesReporting) {
    hide(KotlinNodeJsTestRunnerCliArgs.IgnoredTestSuitesReporting.skip),
    showWithContents(KotlinNodeJsTestRunnerCliArgs.IgnoredTestSuitesReporting.reportAllInnerTestsAsIgnored),
    showWithoutContents(KotlinNodeJsTestRunnerCliArgs.IgnoredTestSuitesReporting.reportAsIgnoredTest)
}

private fun MutableMap<String, Any>.addPath(key: String, path: String) {
    val prev = get(key)
    if (prev == null) set(key, path)
    else set(key, prev as String + File.pathSeparator + path)
}