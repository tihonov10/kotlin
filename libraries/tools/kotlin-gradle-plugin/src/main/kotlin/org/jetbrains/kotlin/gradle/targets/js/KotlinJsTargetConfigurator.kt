/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.targets.js

import org.gradle.api.Project
import org.gradle.api.internal.plugins.DslObject
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.tasks.Delete
import org.gradle.testing.base.plugins.TestingBasePlugin
import org.jetbrains.kotlin.gradle.plugin.*
import org.jetbrains.kotlin.gradle.plugin.Kotlin2JsSourceSetProcessor
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetProcessor
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinJsCompilation
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinOnlyTarget
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsExtension
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsPlugin
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsSetupTask
import org.jetbrains.kotlin.gradle.targets.js.tasks.KotlinJsNodeModulesTask
import org.jetbrains.kotlin.gradle.targets.js.tasks.KotlinNodeJsTestRuntimeToNodeModulesTask
import org.jetbrains.kotlin.gradle.targets.js.tasks.KotlinNodeJsTestTask
import org.jetbrains.kotlin.gradle.tasks.Kotlin2JsCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinTasksProvider
import java.io.File

class KotlinJsTargetConfigurator(kotlinPluginVersion: String) :
    KotlinTargetConfigurator<KotlinJsCompilation>(true, true, kotlinPluginVersion) {

    override fun buildCompilationProcessor(compilation: KotlinJsCompilation): KotlinSourceSetProcessor<*> {
        val tasksProvider = KotlinTasksProvider(compilation.target.targetName)
        return Kotlin2JsSourceSetProcessor(compilation.target.project, tasksProvider, compilation, kotlinPluginVersion)
    }

    override fun configureTest(target: KotlinOnlyTarget<KotlinJsCompilation>) {
        target.compilations.all {
            it.compileKotlinTask.kotlinOptions.moduleKind = "umd"

            if (it.name == KotlinCompilation.TEST_COMPILATION_NAME) {
                configureTest(it)
            }
        }
    }

    private fun configureTest(compilation: KotlinCompilationToRunnableFiles<*>) {
        val target = compilation.target
        val project = target.project
        val compileTestKotlin2Js = compilation.compileKotlinTask as Kotlin2JsCompile
        val isDefaultTarget = compilation.name.isBlank()

        val projectWithNodeJsPlugin = NodeJsPlugin.ensureAppliedInHierarchy(project)

        fun camelCaseTargetName(prefix: String): String {
            return if (isDefaultTarget) prefix
            else target.name + prefix.capitalize()
        }

        fun underscoredCompilationName(prefix: String): String {
            return if (isDefaultTarget) prefix
            else "${target.name}_${compilation.name}_$prefix"
        }

        val nodeModulesDir = project.buildDir.resolve(underscoredCompilationName("node_modules"))
        val nodeModulesTask = project.tasks.create(
            camelCaseTargetName("kotlinJsNodeModules"),
            KotlinJsNodeModulesTask::class.java
        ) {
            it.dependsOn(compileTestKotlin2Js)

            it.nodeModulesDir = nodeModulesDir
            it.compileTaskName = compileTestKotlin2Js.name
        }

        val nodeModulesTestRuntimeTask = project.tasks.create(
            camelCaseTargetName("kotlinJsNodeModulesTestRuntime"),
            KotlinNodeJsTestRuntimeToNodeModulesTask::class.java
        ) {
            it.nodeModulesDir = nodeModulesDir
        }

        val testJs: KotlinNodeJsTestTask =
            if (isDefaultTarget) project.tasks.create("testJs", KotlinNodeJsTestTask::class.java)
            else project.tasks.replace(camelCaseTargetName("test"), KotlinNodeJsTestTask::class.java)

        testJs.also { testJs ->
            testJs.group = "verification"

            val nodeJsSetupTask = projectWithNodeJsPlugin.tasks.findByName(NodeJsSetupTask.NAME)

            testJs.dependsOn(
                    nodeJsSetupTask,
                    nodeModulesTask,
                    nodeModulesTestRuntimeTask
            )

            if (!isDefaultTarget) {
                testJs.targetName = target.name
            }

            project.afterEvaluate {
                // defer nodeJs executable setup, as it nodejs project settings may change during configuration
                if (testJs.nodeJsProcessOptions.executable == null) {
                    testJs.nodeJsProcessOptions.executable = NodeJsExtension[projectWithNodeJsPlugin].buildEnv().nodeExec
                }
            }
            testJs.nodeJsProcessOptions.workingDir = project.projectDir

            testJs.nodeModulesDir = nodeModulesDir
            testJs.nodeModulesToLoad = setOf(compileTestKotlin2Js.outputFile.name)

            val htmlReport = DslObject(testJs.reports.html)
            val xmlReport = DslObject(testJs.reports.junitXml)

            xmlReport.conventionMapping.map("destination") { project.testResults.resolve(testJs.name) }
            htmlReport.conventionMapping.map("destination") { project.testReports.resolve(testJs.name) }
            testJs.conventionMapping.map("binResultsDir") { project.testResults.resolve(testJs.name + "/binary") }
        }

        val cleanTestJs = project.tasks.register("clean" + testJs.name.capitalize(), Delete::class.java) {
            @Suppress("UnstableApiUsage")
            it.delete(testJs.binResultsDir)
        }

        project.afterEvaluate {
            project.tasks.findByName(JavaBasePlugin.CHECK_TASK_NAME)?.dependsOn(testJs)
        }
    }

    @Suppress("UnstableApiUsage")
    val Project.testResults: File
        get() = project.buildDir.resolve(TestingBasePlugin.TEST_RESULTS_DIR_NAME)

    @Suppress("UnstableApiUsage")
    val Project.testReports: File
        get() = project.buildDir.resolve(TestingBasePlugin.TESTS_DIR_NAME)
}