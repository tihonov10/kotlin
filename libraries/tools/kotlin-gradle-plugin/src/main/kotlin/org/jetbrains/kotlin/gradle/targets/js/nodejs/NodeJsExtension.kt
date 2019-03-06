package org.jetbrains.kotlin.gradle.targets.js.nodejs

import org.gradle.api.*
import org.jetbrains.kotlin.gradle.logging.kotlinInfo
import java.io.*

open class NodeJsExtension(project: Project) {
    private val cacheDir = project.gradle.gradleUserHomeDir.also {
        project.logger.kotlinInfo("Storing cached files in $it")
    }

    var installationDir = cacheDir.resolve("nodejs")

    var nodeModulesContainer = project.buildDir
    val node_modules get() = nodeModulesContainer.resolve("node_modules")

    var distBaseUrl = "https://nodejs.org/dist"
    var version = "10.15.3"
    var npmVersion = ""

    var nodeCommand = "node"
    var npmCommand = "npm"

    var download = true

    internal fun buildEnv(): NodeJsEnv {
        val platform = NodeJsPlatform.name
        val architecture = NodeJsPlatform.architecture

        val nodeDir = installationDir.resolve("node-v${version}-$platform-$architecture")
        val isWindows = NodeJsPlatform.name == "win"

        fun executable(command: String, value: String, windowsExtension: String): String =
            if (isWindows && command == value) "$value.$windowsExtension" else value

        fun String.downloaded(bin: File) =
                if (download) File(bin, this).absolutePath else this

        val nodeBinDir = if (isWindows) nodeDir else nodeDir.resolve("bin")

        fun dependency(osName: String, osArch: String, type: String): String =
                "org.nodejs:node:$version:$osName-$osArch@$type"

        return NodeJsEnv(
            nodeDir = nodeDir,
            nodeBinDir = nodeBinDir,
            nodeExec = executable("node", nodeCommand, "exe").downloaded(nodeBinDir),
            npmExec = executable("npm", npmCommand, "cmd").downloaded(nodeBinDir),
            windows = isWindows,
            dependency = dependency(platform, architecture, if (isWindows) "zip" else "tar.gz")
        )
    }

    companion object {
        const val NODE_JS: String = "nodeJs"

        operator fun get(project: Project): NodeJsExtension {
            val extension = project.extensions.findByType(NodeJsExtension::class.java)
            if (extension != null)
                return extension
            
            val parentProject = project.parent
            if (parentProject != null)
                return get(parentProject)
            
            throw GradleException("NodeJsExtension is not installed")
        }
        
        fun create(project: Project): NodeJsExtension {
            return project.extensions.create(NODE_JS, NodeJsExtension::class.java, project)
        }
    }
}
