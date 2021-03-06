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

package com.android.build.gradle.tasks

import com.android.build.api.artifact.ArtifactType
import com.android.build.api.artifact.BuildArtifactType
import com.android.build.api.artifact.BuildableArtifact
import com.android.build.gradle.internal.scope.BuildArtifactHolder
import com.android.build.gradle.internal.scope.TaskConfigAction
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.AndroidBuilderTask
import com.android.build.gradle.options.StringOption
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import org.gradle.api.Task
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.io.FileReader
import java.io.FileWriter

/**
 * Task to report information about build artifacts transformations.
 */
private typealias Report = Map<ArtifactType, List<BuildArtifactReportTask.BuildableArtifactData>>

open class BuildArtifactReportTask : AndroidBuilderTask() {
    private lateinit var buildArtifactHolder : BuildArtifactHolder

    @get:Input
    private lateinit var types : Collection<ArtifactType>

    /**
     * Output file is optional.  If one is specified, the task will output to the file in JSON
     * format.  Otherwise, it will output to stdout in a more human-readable format.
     */
    @get:Optional
    @get:OutputFile
    private var outputFile : File? = null

    //FIXME: VisibleForTesting.  Make this internal when bazel supports it for tests (b/71602857)
    fun init(
            buildArtifactHolder : BuildArtifactHolder,
            types : Collection<ArtifactType>,
            outputFile : File? = null) {
        this.buildArtifactHolder = buildArtifactHolder
        this.types = types
        this.outputFile = outputFile
    }

    @TaskAction
    fun report() {
        val reports : Report =
                types.associate {
                    it to buildArtifactHolder.getHistory(it).map(this::newArtifact) }

        if (outputFile != null) {
            val gson = GsonBuilder().setPrettyPrinting().create()
            FileWriter(outputFile).use { writer ->
                val reportType = object : TypeToken<Report>() {}.type
                gson.toJson(reports, reportType, writer)
            }
        }
        for ((type, report) in reports.entries) {
            println(type.name())
            println("-".repeat(type.name().length))
            for ((index, artifact) in report.withIndex()) {
                println("BuildableArtifact $index")
                println("files: ${artifact.files}")
                println("builtBy: ${artifact.builtBy}")
                println("")
            }
        }
    }

    class ConfigAction(val scope : VariantScope) : TaskConfigAction<BuildArtifactReportTask> {
        override fun getName() = scope.getTaskName("reportBuildArtifacts")

        override fun getType() = BuildArtifactReportTask::class.java

        override fun execute(task: BuildArtifactReportTask) {
            task.variantName = scope.fullVariantName
            val outputFileName =
                    scope.globalScope.projectOptions.get(StringOption.BUILD_ARTIFACT_REPORT_FILE)
            val outputFile : File? =
                    if (outputFileName == null) null
                    else scope.globalScope.project.file(outputFileName)

             // TODO: populate 'types' with all ArtifactType in buildArtifactHolder.
            task.init(
                    buildArtifactHolder = scope.buildArtifactHolder,
                    types = listOf(),
                    outputFile = outputFile)
        }
    }

    data class BuildableArtifactData(
            @SerializedName("files") var files : Collection<File>,
            @SerializedName("builtBy") var builtBy : List<String>)

    /** Create [BuildableArtifactData] from [BuildableArtifact]. */
    private fun newArtifact(artifact : BuildableArtifact) =
            // getDependencies accepts null.
            @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
            BuildableArtifactData(
                    artifact.files,
                    artifact.buildDependencies.getDependencies(null).map(Task::getPath))


    companion object {
        fun parseReport(file : File) : Report {
            val result = mutableMapOf<ArtifactType, List<BuildableArtifactData>>()
            val parser = JsonParser()
            FileReader(file).use { reader ->
                for ((key, value) in parser.parse(reader).asJsonObject.entrySet()) {
                    val history =
                            value.asJsonArray.map {
                                val obj = it.asJsonObject
                                BuildableArtifactData(
                                        obj.getAsJsonArray("files").map {
                                            File(it.asJsonObject.get("path").asString)
                                        },
                                        obj.getAsJsonArray("builtBy").map(JsonElement::getAsString))
                            }
                    result.put(BuildArtifactType.valueOf(key), history)
                }
            }
            return result
        }
    }
}

