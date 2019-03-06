package org.jetbrains.kotlin.gradle.targets.js.nodejs

import org.gradle.api.Plugin
import org.gradle.api.Project

open class NodeJsPlugin : Plugin<Project> {
    override fun apply(project: Project): Unit = project.run {
        NodeJsExtension.create(this)

        tasks.create(NodeJsSetupTask.NAME, NodeJsSetupTask::class.java)
    }

    companion object {
        const val PLUGIN_ID = "org.jetbrains.kotlin.nodejs"

        fun ensureAppliedInHierarchy(myProject: Project): Project {
            var project : Project? = myProject
            while (project != null) {
                if (myProject.pluginManager.findPlugin(PLUGIN_ID) != null) return project
                project = project.parent
            }

            myProject.pluginManager.apply(NodeJsPlugin::class.java)
            return myProject
        }
    }
}
