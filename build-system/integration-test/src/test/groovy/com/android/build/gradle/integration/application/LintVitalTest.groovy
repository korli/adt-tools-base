/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.build.gradle.integration.application

import com.android.build.gradle.integration.common.fixture.GradleBuildResult
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.AndroidTestApp
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.truth.TruthHelper
import com.android.build.gradle.integration.common.utils.TestFileUtils
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test

import static com.google.common.truth.Truth.assertThat
/**
 * Checks if fatal lint errors stop the release build.
 */
class LintVitalTest {

    public static final AndroidTestApp helloWorldApp = HelloWorldApp.noBuildFile()

    @Rule
    public GradleTestProject project = GradleTestProject.builder()
        .fromTestApp(helloWorldApp)
        .create()

    @Before
    public void setUp() {
        Assume.assumeFalse("Disabled until lintVital supports Jack", GradleTestProject.USE_JACK)

        project.getBuildFile() << """
apply plugin: 'com.android.application'

android {
    compileSdkVersion $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
    buildToolsVersion "$GradleTestProject.DEFAULT_BUILD_TOOL_VERSION"
}
"""

        def manifest = project.file("src/main/AndroidManifest.xml")
        def manifestLines = manifest.readLines()

        int packageLineNumber = manifestLines.findIndexOf { it =~ /package="/ }
        manifestLines.add(packageLineNumber + 1, 'android:debuggable="true"')
        manifest.write(manifestLines.join(System.getProperty("line.separator")))
    }

    @Test
    public void fatalLintCheckFailsBuild() {
        GradleBuildResult result =
                project.executor().expectFailure().run("assembleRelease");
        assertThat(result.getFailureMessage()).contains("fatal errors");
        TruthHelper.assertThat(result.getTask(":lintVitalRelease")).failed();
    }

    @Test
    public void lintVitalIsNotRunForLibraries() {
        TestFileUtils.searchAndReplace(
                project.getBuildFile(),
                "com\\.android\\.application",
                "com.android.library");
        GradleBuildResult result = project.executor().run("assembleRelease");
        TruthHelper.assertThat(result.getTask(":lintVitalRelease")).wasNotExecuted();
    }


    @Test
    public void lintVitalDisabled() {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "android.lintOptions.checkReleaseBuilds = false\n");

        GradleBuildResult result = project.executor().run("assembleRelease");
        TruthHelper.assertThat(result.getTask(":lintVitalRelease")).wasNotExecuted();
    }
}
