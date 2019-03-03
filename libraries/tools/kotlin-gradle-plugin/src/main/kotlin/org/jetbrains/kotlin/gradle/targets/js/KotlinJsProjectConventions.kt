/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.targets.js

import org.gradle.api.Project
import org.gradle.api.internal.plugins.DslObject
import org.gradle.testing.base.plugins.TestingBasePlugin
import org.jetbrains.kotlin.gradle.targets.js.tasks.KotlinNodeJsTestTask

class KotlinJsProjectConventions {
    class Flag

    fun ensureConfigured(project: Project) {
        if (project.extensions.findByType(Flag::class.java) == null) {
            project.extensions.add(Flag::class.java, "kotlinJsConventions", Flag())

            configureConventions(project)
        }
    }

    fun configureConventions(project: Project) {
        @Suppress("UnstableApiUsage")
        project.tasks.withType(KotlinNodeJsTestTask::class.java).configureEach {
            configureTestDefaults(it, project)
        }
    }

    @Suppress("UnstableApiUsage")
    private fun configureTestDefaults(
        test: KotlinNodeJsTestTask,
        project: Project
    ) {
        val htmlReport = DslObject(test.reports.html)
        val xmlReport = DslObject(test.reports.junitXml)

        val testResults = project.buildDir.resolve(TestingBasePlugin.TEST_RESULTS_DIR_NAME)
        val testReports = project.buildDir.resolve(TestingBasePlugin.TESTS_DIR_NAME)

        xmlReport.conventionMapping.map("destination") { testResults.resolve(test.name) }
        htmlReport.conventionMapping.map("destination") { testReports.resolve(test.name) }
        test.conventionMapping.map("binResultsDir") { testResults.resolve(test.name + "/binary") }

        test.workingDir(project.projectDir)
    }
}