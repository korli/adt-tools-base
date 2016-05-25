/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.builder.model;

/**
 * enum describing possible optional compilation steps. This can be used to turn on java byte code
 * manipulation in order to support instant reloading, or profiling, or anything related to
 * transforming java compiler .class files before they are processed into .dex files.
 */
public enum OptionalCompilationStep {

    /**
     * presence will turn on the InstantRun feature.
     */
    INSTANT_DEV,
    /**
     * presence will disable all tasks before javac.
     */
    LOCAL_JAVA_ONLY,
    /**
     * presence will disable all tasks before resource merger.
     */
    LOCAL_RES_ONLY,
    /**
     * presence will force production of all the necessary artifacts to do an application restart.
     */
    RESTART_ONLY,
}
