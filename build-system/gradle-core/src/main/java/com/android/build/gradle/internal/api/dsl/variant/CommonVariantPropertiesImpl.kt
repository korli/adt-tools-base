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

package com.android.build.gradle.internal.api.dsl.variant

import com.android.build.api.dsl.variant.CommonVariantProperties
import com.android.build.api.sourcesets.AndroidSourceSet
import com.android.build.gradle.internal.api.dsl.sealing.SealableObject
import com.android.builder.errors.EvalIssueReporter
import com.google.common.collect.ImmutableList

/** propertie for variants that are not coming from the user model */
class CommonVariantPropertiesImpl(
        override val name: String,
        override val buildTypeName: String,
        flavorNames: List<String>,
        sourceSets: List<AndroidSourceSet>,
        override val variantSourceSet: AndroidSourceSet,
        override val multiFlavorSourceSet: AndroidSourceSet?,
        issueReporter: EvalIssueReporter
        ) : SealableObject(issueReporter), CommonVariantProperties {

    override val flavorNames: List<String> = ImmutableList.copyOf(flavorNames)
    override val baseSourceSets: List<AndroidSourceSet> = ImmutableList.copyOf(sourceSets)

    override fun seal() {
        super.seal()

        // FIXME seal source sets

    }
}