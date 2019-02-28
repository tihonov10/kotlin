/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

@file:Suppress("PackageDirectoryMismatch") // Old package for compatibility
package org.jetbrains.kotlin.gradle.plugin.mpp

import org.jetbrains.kotlin.gradle.dsl.KotlinJvmOptions
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

class KotlinJvmAndroidCompilation(
    target: KotlinAndroidTarget,
    name: String
) : AbstractKotlinCompilationToRunnableFiles<KotlinJvmOptions>(target, name) {
   override val compileKotlinTask: KotlinCompile
       get() = super.compileKotlinTask as KotlinCompile

   override val relatedConfigurationNames: List<String>
       get() = super.relatedConfigurationNames + listOf("${name}ApiElements", "${name}RuntimeElements")
}