package org.jetbrains.kotlin.gradle.targets.js.nodejs

import java.io.*

internal data class NodeJsEnv(
    val nodeDir: File,
    val nodeBinDir: File,
    val nodeExec: String,
    val npmExec: String,
    val windows: Boolean,
    val dependency: String
)
