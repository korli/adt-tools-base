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

package com.android.build.gradle.options;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.ExtraModelInfo;
import com.android.builder.core.ErrorReporter;
import com.android.builder.model.AndroidProject;

public final class SyncOptions {

    private SyncOptions() {}

    public static ErrorReporter.EvaluationMode getModelQueryMode(@NonNull ProjectOptions options) {
        if (options.get(BooleanOption.IDE_BUILD_MODEL_ONLY_ADVANCED)) {
            return ErrorReporter.EvaluationMode.IDE;
        }

        if (options.get(BooleanOption.IDE_BUILD_MODEL_ONLY)) {
            return ErrorReporter.EvaluationMode.IDE_LEGACY;
        }

        return ErrorReporter.EvaluationMode.STANDARD;
    }

    public static ExtraModelInfo.ErrorFormatMode getErrorFormatMode(
            @NonNull ProjectOptions options) {
        if (options.get(BooleanOption.IDE_INVOKED_FROM_IDE)) {
            return ExtraModelInfo.ErrorFormatMode.MACHINE_PARSABLE;
        } else {
            return ExtraModelInfo.ErrorFormatMode.HUMAN_READABLE;
        }
    }

    /**
     * Returns the level of model-only mode.
     *
     * <p>The model-only mode is triggered when the IDE does a sync, and therefore we do things a
     * bit differently (don't throw exceptions for instance). Things evolved a bit over versions and
     * the behavior changes. This reflects the mode to use.
     *
     * @param options the project options
     * @return an integer or null if we are not in model-only mode.
     * @see AndroidProject#MODEL_LEVEL_0_ORIGINAL
     * @see AndroidProject#MODEL_LEVEL_1_SYNC_ISSUE
     * @see AndroidProject#MODEL_LEVEL_3_VARIANT_OUTPUT_POST_BUILD
     * @see AndroidProject#MODEL_LEVEL_4_NEW_DEP_MODEL
     */
    @Nullable
    public static Integer buildModelOnlyVersion(@NonNull ProjectOptions options) {
        Integer revision = options.get(IntegerOption.IDE_BUILD_MODEL_ONLY_VERSION);
        if (revision != null) {
            return revision;
        }

        if (options.get(BooleanOption.IDE_BUILD_MODEL_ONLY_ADVANCED)) {
            return AndroidProject.MODEL_LEVEL_1_SYNC_ISSUE;
        }

        if (options.get(BooleanOption.IDE_BUILD_MODEL_ONLY)) {
            return AndroidProject.MODEL_LEVEL_0_ORIGINAL;
        }

        return null;
    }
}
