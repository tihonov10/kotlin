package org.jetbrains.kotlin.gradle.tasks.js

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.*
import org.jetbrains.kotlin.gradle.tasks.Kotlin2JsCompile
import java.io.File

open class KotlinJsNodeModulesTask : DefaultTask() {
    @OutputDirectory
    @SkipWhenEmpty
    public lateinit var nodeModulesDir: File

    @Input
    @SkipWhenEmpty
    public lateinit var compile: Kotlin2JsCompile

    @TaskAction
    fun copyFromRuntimeClasspath() {
        project.sync { copy ->
            copy.includeEmptyDirs = false

            compile.jsRuntimeClasspath
                .forEach {
                    if (it.isZip) copy.from(project.zipTree(it))
                    else copy.from(it)
                }

            copy.include { fileTreeElement ->
                isKotlinJsRuntimeFile(fileTreeElement.file)
            }

            copy.into(nodeModulesDir)
        }
    }
}

val File.isZip
    get() = isFile && name.endsWith(".jar")

val Kotlin2JsCompile.jsRuntimeClasspath: List<File>
    get() = classpath + destinationDir

fun isKotlinJsRuntimeFile(file: File): Boolean {
    if (!file.isFile) return false
    val name = file.name
    return (name.endsWith(".js") && !name.endsWith(".meta.js"))
            || name.endsWith(".js.map")
}