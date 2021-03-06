/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.build.gradle.internal.res.namespaced

import com.android.SdkConstants
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.ExistingBuildElements
import com.android.build.gradle.internal.scope.TaskConfigAction
import com.android.build.gradle.internal.scope.TaskOutputHolder.TaskOutputType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.AndroidBuilderTask
import com.android.builder.core.VariantType
import com.android.builder.internal.aapt.AaptOptions
import com.android.builder.internal.aapt.AaptPackageConfig
import com.android.sdklib.IAndroidTarget
import com.android.utils.FileUtils
import com.google.common.base.Suppliers
import com.google.common.collect.ImmutableList
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.workers.IsolationMode
import org.gradle.workers.WorkerExecutor
import java.io.File
import java.util.function.Supplier
import javax.inject.Inject

/**
 * Task to link the resources in a library project into an AAPT2 static library.
 */
@CacheableTask
open class LinkLibraryAndroidResourcesTask @Inject constructor(private val workerExecutor: WorkerExecutor) :
        AndroidBuilderTask() {

    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE) lateinit var manifestFileDirectory: FileCollection private set
    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE) lateinit var inputResourcesDirectories: FileCollection private set
    @get:InputFiles @get:PathSensitive(PathSensitivity.NONE) lateinit var libraryDependencies: FileCollection private set
    @get:InputFiles @get:PathSensitive(PathSensitivity.NONE) lateinit var sharedLibraryDependencies: FileCollection private set
    @get:InputFiles @get:PathSensitive(PathSensitivity.NONE) @get:Optional var featureDependencies: FileCollection? = null; private set
    @get:InputFiles @get:PathSensitive(PathSensitivity.NONE) @get:Optional var tested: FileCollection? = null; private set

    @get:Internal lateinit var packageForRSupplier: Supplier<String> private set
    @get:Input private val packageForR get() = packageForRSupplier.get()

    @get:OutputDirectory lateinit var aaptIntermediateDir: File private set
    @get:Optional var rClassSource: File? = null; private set
    @get:OutputFile lateinit var rDotTxt: File private set
    @get:OutputFile lateinit var staticLibApk: File private set

    @TaskAction
    fun taskAction() {

        val imports = ImmutableList.builder<File>()
        // Link against library dependencies
        imports.addAll(libraryDependencies.files)
        imports.addAll(sharedLibraryDependencies.files)

        // Link against features
        featureDependencies?.let {
            imports.addAll(
                    it.files
                            .map { ExistingBuildElements.from(TaskOutputType.PROCESSED_RES, it) }
                            .filterNot { it.isEmpty() }
                            .map { splitOutputs -> splitOutputs.single().outputFile })
        }

        val request = AaptPackageConfig(
                androidJarPath = builder.target.getPath(IAndroidTarget.ANDROID_JAR),
                manifestFile = File(manifestFileDirectory.singleFile,
                        SdkConstants.ANDROID_MANIFEST_XML),
                options = AaptOptions(null, false, null),
                resourceDirs = ImmutableList.copyOf(inputResourcesDirectories.asIterable()),
                staticLibrary = true,
                imports = imports.build(),
                // TODO: Remove generating R.java once b/69956357 is fixed.
                sourceOutputDir = rClassSource,
                resourceOutputApk = staticLibApk,
                variantType = VariantType.LIBRARY,
                customPackageForR = packageForR,
                symbolOutputDir = rDotTxt.parentFile,
                intermediateDir = aaptIntermediateDir)

        workerExecutor.submit(Aapt2LinkRunnable::class.java) {
            it.isolationMode = IsolationMode.NONE
            it.setParams(Aapt2LinkRunnable.Params(buildTools.revision, request))
        }
    }

    class ConfigAction(
            private val scope: VariantScope,
            private val rClassSource: File?,
            private val staticLibApk: File,
            private val rDotTxt: File) : TaskConfigAction<LinkLibraryAndroidResourcesTask> {

        override fun getName() = scope.getTaskName("link", "Resources")

        override fun getType() = LinkLibraryAndroidResourcesTask::class.java

        override fun execute(task: LinkLibraryAndroidResourcesTask) {
            task.variantName = scope.fullVariantName
            task.manifestFileDirectory =
                    if (scope.hasOutput(TaskOutputType.AAPT_FRIENDLY_MERGED_MANIFESTS)) {
                        scope.getOutput(TaskOutputType.AAPT_FRIENDLY_MERGED_MANIFESTS)
                    } else {
                        scope.getOutput(TaskOutputType.MERGED_MANIFESTS)
                    }
            task.inputResourcesDirectories = scope.getOutput(TaskOutputType.RES_COMPILED_FLAT_FILES)
            task.libraryDependencies =
                    scope.getArtifactFileCollection(
                            AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH,
                            AndroidArtifacts.ArtifactScope.ALL,
                            AndroidArtifacts.ArtifactType.RES_STATIC_LIBRARY)
            task.sharedLibraryDependencies =
                    scope.getArtifactFileCollection(
                            AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH,
                            AndroidArtifacts.ArtifactScope.ALL,
                            AndroidArtifacts.ArtifactType.RES_SHARED_STATIC_LIBRARY)

            if (scope.variantData.type == VariantType.FEATURE && !scope.isBaseFeature) {
                task.featureDependencies =
                        scope.getArtifactFileCollection(
                                AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH,
                                AndroidArtifacts.ArtifactScope.MODULE,
                                AndroidArtifacts.ArtifactType.FEATURE_RESOURCE_PKG)
            }

            val testedScope = scope.testedVariantData?.scope
            if (testedScope != null) {
                task.tested = testedScope.getOutput(TaskOutputType.RES_STATIC_LIBRARY)
            }

            task.aaptIntermediateDir =
                    FileUtils.join(
                            scope.globalScope.intermediatesDir, "res-link-intermediate", scope.variantConfiguration.dirName)
            task.rClassSource = rClassSource
            task.staticLibApk = staticLibApk
            task.setAndroidBuilder(scope.globalScope.androidBuilder)
            task.packageForRSupplier = Suppliers.memoize(scope.variantConfiguration::getOriginalApplicationId)
            task.rDotTxt = rDotTxt
        }
    }

}
