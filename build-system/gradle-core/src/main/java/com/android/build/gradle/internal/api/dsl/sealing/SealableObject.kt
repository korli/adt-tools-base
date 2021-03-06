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

package com.android.build.gradle.internal.api.dsl.sealing

import com.android.builder.errors.EvalIssueReporter
import com.android.builder.model.SyncIssue
import org.gradle.api.Named

abstract class SealableObject(
            protected val issueReporter: EvalIssueReporter,
            private val name: String?)
        : Sealable {

    constructor(issueReporter: EvalIssueReporter): this(issueReporter, null)

    private var sealed: Boolean = false

    override fun seal() {
        if (sealed) {
            // get the name of the class
            val className = this.javaClass.name
            val itemNameStr = computeName()
            issueReporter.reportError(
                    EvalIssueReporter.Type.GENERIC,
                    "Attempting to seal object$itemNameStr of type $className after it's been sealed.")
        }

        sealed = true
    }

    fun isSealed() = sealed

    fun checkSeal(): Boolean {
        if (sealed) {
            // get the name of the class
            val className = this.javaClass.name
            val itemNameStr = computeName()
            // FIXME better error message and custom TYPE.
            issueReporter.reportError(
                    EvalIssueReporter.Type.GENERIC,
                    "Attempting to modify object$itemNameStr of type $className after it's been sealed.",
                    className)

            return false
        }

        return true
    }

    private fun computeName(): String {
        val itemName = name ?: if (this is Named) this.name else null
        return if (itemName != null) " '$itemName'" else ""
    }
}