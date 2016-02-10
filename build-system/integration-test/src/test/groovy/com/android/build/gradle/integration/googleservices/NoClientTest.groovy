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

package com.android.build.gradle.integration.googleservices
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.AndroidTestApp
import com.android.build.gradle.integration.common.fixture.app.EmptyAndroidTestApp
import com.android.build.gradle.integration.common.fixture.app.TestSourceFile
import com.google.common.io.Files
import com.google.common.truth.Truth
import groovy.json.internal.Charsets
import groovy.transform.CompileStatic
import org.gradle.api.GradleException;
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException

/**
 * Test with a mismatch json file vs the app package name.
 */
@CompileStatic
class NoClientTest {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    public static final AndroidTestApp helloWorldApp = new EmptyAndroidTestApp("com.example.app.typo")

    static {
        File source = new File(new File(GradleTestProject.TEST_RES_DIR, "basic"), "example.json")
        helloWorldApp.addFile(new TestSourceFile(
                "",
                TestHelper.JSON_FILE_NAME,
                Files.toString(source, Charsets.UTF_8)))
    }

    @ClassRule
    public static GradleTestProject project = GradleTestProject.builder()
        .fromTestApp(helloWorldApp)
        .create()

    @BeforeClass
    public static void setUp() {

    }

    @AfterClass
    public static void cleanUp() {
        project = null
    }

    @Test
    public void "test exception is thrown"() {

        project.getBuildFile() << """
apply plugin: 'com.android.application'
apply plugin: 'com.google.gms.google-services'

android {
    compileSdkVersion $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
    buildToolsVersion "$GradleTestProject.DEFAULT_BUILD_TOOL_VERSION"

    defaultConfig {
        minSdkVersion 15
        targetSdkVersion $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
    }
}
"""
        thrown.expect(org.gradle.tooling.BuildException.class)
        project.execute("clean", "assembleDebug")
    }
}
