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
package com.android.projectmodel

import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import com.google.common.truth.Truth.assertThat
import org.junit.Ignore
import org.junit.Test
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Paths

class PathStringTest {
    @Test
    fun testParent() {
        val path = PathString("/var/log")
        assertThat(path.parent).isEqualTo(PathString("/var"))
        assertThat(path.parent!!.parent).isEqualTo(PathString("/"))
        assertThat(path.parent!!.parent!!.parent).isNull()
        assertThat(PathString("").parent).isNull()
    }

    @Test
    fun testRoot() {
        assertThat(PathString("/").root).isEqualTo(PathString("/"))
        assertThat(PathString("foo").root).isNull()
        assertThat(PathString("C:").root).isEqualTo(PathString("C:"))
        assertThat(PathString("C:\\").root).isEqualTo(PathString("C:\\"))
        assertThat(PathString("C:/").root).isEqualTo(PathString("C:/"))
        assertThat(PathString("////stuff").root).isEqualTo(PathString("////"))
        assertThat(PathString("/foo/bar/").root).isEqualTo(PathString("/"))
        assertThat(PathString("foo/bar/").root).isNull()
        assertThat(PathString("C:\\Program Files\\My App").root).isEqualTo(PathString("C:\\"))
        assertThat(PathString("").root).isNull()
    }

    @Test
    fun testDefaultFilesystem() {
        val defaultFilesystemUri = PathString("").filesystemUri
        val fileSystem = Paths.get(defaultFilesystemUri).fileSystem
        assertThat(fileSystem).isEqualTo(FileSystems.getDefault())
    }

    @Test
    fun testToPath() {
        assertThat(PathString("/foo").toPath()).isEqualTo(Paths.get("/foo"))
    }

    @Test
    fun testCustomUnixFilesystem() {
        // Set up a PathString to something in an in-memory nio filesystem that can't exist on disk
        val fs = Jimfs.newFileSystem(Configuration.unix())
        val somePath = fs.getPath("tempFile/foo")
        val aPath = PathString(somePath)

        assertThat(aPath.toPath()).isEqualTo(somePath)
        fs.close()
    }

    @Test
    fun testNameCount() {
        assertThat(PathString("C:\\").nameCount).isEqualTo(0)
        assertThat(PathString("C:").nameCount).isEqualTo(0)
        assertThat(PathString("/foo/bar").nameCount).isEqualTo(2)
        assertThat(PathString("/foo/").nameCount).isEqualTo(1)
        assertThat(PathString("/").nameCount).isEqualTo(0)
        assertThat(PathString("").nameCount).isEqualTo(0)
    }

    @Test
    fun testFileName() {
        assertThat(PathString("foo").fileName.rawPath).isEqualTo("foo")
        assertThat(PathString("").fileName.rawPath).isEqualTo("")
        assertThat(PathString("/").fileName.rawPath).isEqualTo("")
        assertThat(PathString("foo/").fileName.rawPath).isEqualTo("foo")
        assertThat(PathString("foo/bar").fileName.rawPath).isEqualTo("bar")
        assertThat(PathString("C:\\").fileName.rawPath).isEqualTo("")
        assertThat(PathString("\\\\server").fileName.rawPath).isEqualTo("server")
    }

    @Test
    fun testUnchangedByNormalize() {
        listOf(
                "",
                "C:",
                "C:\\",
                "\\\\Server",
                "\\\\",
                "\\",
                "D:\\Program Files",
                "D:myfile.txt",
                "My Documents\\Project",
                "/",
                "/bin/bash",
                ".bashrc",
                "~",
                "misc/some_file"
        )
                .map { PathString(it) }
                .forEach {
                    assertThat(it.normalize()).isEqualTo(it)
                }
    }

    @Test
    fun testLeadingParentSegments() {
        assertThat(PathString("foo/../../bar").normalize()).isEqualTo(PathString("../bar"))
        assertThat(PathString("/foo/../../bar").normalize()).isEqualTo(PathString("/bar"))
    }

    @Test
    fun testStripSelfSegments() {
        assertThat(PathString("foo/././bar").normalize()).isEqualTo(PathString("foo/bar"))
    }

    @Test
    fun testNameOperator() {
        val testPath = PathString("C:\\zero\\one\\two")

        assertThat(testPath[0]).isEqualTo(PathString("zero"))
        assertThat(testPath[1]).isEqualTo(PathString("one"))
        assertThat(testPath[2]).isEqualTo(PathString("two"))
    }

    @Test
    fun testRangeOperator() {
        val testPath = PathString("C:\\zero\\one\\two")

        assertThat(testPath[0.until(0)].isEmptyPath).isTrue()
        assertThat(testPath[0..0]).isEqualTo(PathString("zero"))
        assertThat(testPath[0..1]).isEqualTo(PathString("zero\\one"))
        assertThat(testPath[0..2]).isEqualTo(PathString("zero\\one\\two"))
        assertThat(testPath[1..0]).isEqualTo(PathString(""))
        assertThat(testPath[1..1]).isEqualTo(PathString("one"))
        assertThat(testPath[1..2]).isEqualTo(PathString("one\\two"))
        assertThat(testPath[2..1]).isEqualTo(PathString(""))
        assertThat(testPath[2..2]).isEqualTo(PathString("two"))
        assertThat(testPath[3..2]).isEqualTo(PathString(""))
    }

    @Test
    fun testEmptyRange() {
        val emptyPath = PathString("")
        val rootOnlyPath = PathString( "/")
        assertThat(emptyPath[0.until(0)]).isEqualTo(emptyPath)
        assertThat(rootOnlyPath[0.until(0)]).isEqualTo(emptyPath)
    }

    @Test
    fun testTrailingSeparator() {
        assertThat(PathString("").hasTrailingSeparator).isFalse()
        assertThat(PathString("/").hasTrailingSeparator).isFalse()
        assertThat(PathString("/foo").hasTrailingSeparator).isFalse()
        assertThat(PathString("/foo/").hasTrailingSeparator).isTrue()
    }

    @Test
    fun testIsEmpty() {
        assertThat(PathString("").isEmptyPath).isTrue()
        assertThat(PathString("/").isEmptyPath).isFalse()
        assertThat(PathString("foo").isEmptyPath).isFalse()
        assertThat(PathString("C:").isEmptyPath).isFalse()
        assertThat(PathString("C:\\").isEmptyPath).isFalse()
    }

    @Test
    fun testCompareTo() {
        assertThat(PathString("A:").compareTo(PathString("A:\\"))).isLessThan(0)
        assertThat(PathString("A:\\").compareTo(PathString("A:"))).isGreaterThan(0)
        assertThat(PathString("A:").compareTo(PathString("A:"))).isEqualTo(0)
        assertThat(PathString("A:").compareTo(PathString("B:"))).isLessThan(0)
        assertThat(PathString("B:").compareTo(PathString("A:"))).isGreaterThan(0)
        assertThat(PathString("a").compareTo(PathString("ab"))).isLessThan(0)
        assertThat(PathString("A:\\b").compareTo(PathString("A:\\c"))).isLessThan(0)
        assertThat(PathString("A:\\b").compareTo(PathString("A:\\b\\"))).isLessThan(0)
    }

    private fun assertRelativize(relativeTo: String, fullPath: String, relPath: String) {
        val p1 = PathString(relativeTo)
        val p2 = PathString(fullPath)
        val rel = p1.relativize(p2)

        assertThat(rel).isEqualTo(PathString(relPath))
    }

    @Test
    fun testRelavitize() {
        assertRelativize("", "", "")
        assertRelativize("", "C:", "C:")
        assertRelativize("", "C:\\", "C:\\")
        assertRelativize("", "\\\\Server", "\\\\Server")
        assertRelativize("", "\\\\", "\\\\")
        assertRelativize("", "\\", "\\")
        assertRelativize("", "D:\\Program Files", "D:\\Program Files")
        assertRelativize("", "D:myfile.txt", "D:myfile.txt")
        assertRelativize("", "D:", "D:")
        assertRelativize("", "My Documents\\Project", "My Documents\\Project")
        assertRelativize("", "/", "/")
        assertRelativize("", "/bin/bash", "/bin/bash")
        assertRelativize("", "/bin/bash/boom/flash/", "/bin/bash/boom/flash/")
        assertRelativize("", ".bashrc", ".bashrc")
        assertRelativize("", "~", "~")
        assertRelativize("", "misc/some_file", "misc/some_file")
        assertRelativize("C:", "", "")
        assertRelativize("C:", "C:", "")
        assertRelativize("C:", "C:\\", "\\")
        assertRelativize("C:", "\\\\Server", "\\\\Server")
        assertRelativize("C:", "\\\\", "\\\\")
        assertRelativize("C:", "\\", "\\")
        assertRelativize("C:", "D:\\Program Files", "D:\\Program Files")
        assertRelativize("C:", "D:myfile.txt", "D:myfile.txt")
        assertRelativize("C:", "D:", "D:")
        assertRelativize("C:", "My Documents\\Project", "My Documents\\Project")
        assertRelativize("C:", "/", "/")
        assertRelativize("C:", "/bin/bash", "/bin/bash")
        assertRelativize("C:", "/bin/bash/boom/flash/", "/bin/bash/boom/flash/")
        assertRelativize("C:", ".bashrc", ".bashrc")
        assertRelativize("C:", "~", "~")
        assertRelativize("C:", "misc/some_file", "misc/some_file")
        assertRelativize("C:\\", "", "")
        assertRelativize("C:\\", "C:", "")
        assertRelativize("C:\\", "C:\\", "")
        assertRelativize("C:\\", "\\\\Server", "\\\\Server")
        assertRelativize("C:\\", "\\\\", "\\\\")
        assertRelativize("C:\\", "\\", "\\")
        assertRelativize("C:\\", "D:\\Program Files", "D:\\Program Files")
        assertRelativize("C:\\", "D:myfile.txt", "D:myfile.txt")
        assertRelativize("C:\\", "D:", "D:")
        assertRelativize("C:\\", "My Documents\\Project", "My Documents\\Project")
        assertRelativize("C:\\", "/", "/")
        assertRelativize("C:\\", "/bin/bash", "/bin/bash")
        assertRelativize("C:\\", "/bin/bash/boom/flash/", "/bin/bash/boom/flash/")
        assertRelativize("C:\\", ".bashrc", ".bashrc")
        assertRelativize("C:\\", "~", "~")
        assertRelativize("C:\\", "misc/some_file", "misc/some_file")
        assertRelativize("\\\\Server", "", "")
        assertRelativize("\\\\Server", "C:", "C:")
        assertRelativize("\\\\Server", "C:\\", "C:\\")
        assertRelativize("\\\\Server", "\\\\Server", "")
        assertRelativize("\\\\Server", "\\\\", "..")
        assertRelativize("\\\\Server", "\\", "\\")
        assertRelativize("\\\\Server", "D:\\Program Files", "D:\\Program Files")
        assertRelativize("\\\\Server", "D:myfile.txt", "D:myfile.txt")
        assertRelativize("\\\\Server", "D:", "D:")
        assertRelativize("\\\\Server", "My Documents\\Project", "My Documents\\Project")
        assertRelativize("\\\\Server", "/", "/")
        assertRelativize("\\\\Server", "/bin/bash", "/bin/bash")
        assertRelativize("\\\\Server", "/bin/bash/boom/flash/", "/bin/bash/boom/flash/")
        assertRelativize("\\\\Server", ".bashrc", ".bashrc")
        assertRelativize("\\\\Server", "~", "~")
        assertRelativize("\\\\Server", "misc/some_file", "misc/some_file")
        assertRelativize("\\\\", "", "")
        assertRelativize("\\\\", "C:", "C:")
        assertRelativize("\\\\", "C:\\", "C:\\")
        assertRelativize("\\\\", "\\\\Server", "Server")
        assertRelativize("\\\\", "\\\\", "")
        assertRelativize("\\\\", "\\", "\\")
        assertRelativize("\\\\", "D:\\Program Files", "D:\\Program Files")
        assertRelativize("\\\\", "D:myfile.txt", "D:myfile.txt")
        assertRelativize("\\\\", "D:", "D:")
        assertRelativize("\\\\", "My Documents\\Project", "My Documents\\Project")
        assertRelativize("\\\\", "/", "/")
        assertRelativize("\\\\", "/bin/bash", "/bin/bash")
        assertRelativize("\\\\", "/bin/bash/boom/flash/", "/bin/bash/boom/flash/")
        assertRelativize("\\\\", ".bashrc", ".bashrc")
        assertRelativize("\\\\", "~", "~")
        assertRelativize("\\\\", "misc/some_file", "misc/some_file")
        assertRelativize("\\", "", "")
        assertRelativize("\\", "C:", "C:")
        assertRelativize("\\", "C:\\", "C:\\")
        assertRelativize("\\", "\\\\Server", "\\\\Server")
        assertRelativize("\\", "\\\\", "\\\\")
        assertRelativize("\\", "\\", "")
        assertRelativize("\\", "D:\\Program Files", "D:\\Program Files")
        assertRelativize("\\", "D:myfile.txt", "D:myfile.txt")
        assertRelativize("\\", "D:", "D:")
        assertRelativize("\\", "My Documents\\Project", "My Documents\\Project")
        assertRelativize("\\", "/", "")
        assertRelativize("\\", "/bin/bash", "bin/bash")
        assertRelativize("\\", "/bin/bash/boom/flash/", "bin/bash/boom/flash/")
        assertRelativize("\\", ".bashrc", ".bashrc")
        assertRelativize("\\", "~", "~")
        assertRelativize("\\", "misc/some_file", "misc/some_file")
        assertRelativize("D:\\Program Files", "", "")
        assertRelativize("D:\\Program Files", "C:", "C:")
        assertRelativize("D:\\Program Files", "C:\\", "C:\\")
        assertRelativize("D:\\Program Files", "\\\\Server", "\\\\Server")
        assertRelativize("D:\\Program Files", "\\\\", "\\\\")
        assertRelativize("D:\\Program Files", "\\", "\\")
        assertRelativize("D:\\Program Files", "D:\\Program Files", "")
        assertRelativize("D:\\Program Files", "D:myfile.txt", "myfile.txt")
        assertRelativize("D:\\Program Files", "D:", "")
        assertRelativize("D:\\Program Files", "My Documents\\Project", "My Documents\\Project")
        assertRelativize("D:\\Program Files", "/", "/")
        assertRelativize("D:\\Program Files", "/bin/bash", "/bin/bash")
        assertRelativize("D:\\Program Files", "/bin/bash/boom/flash/", "/bin/bash/boom/flash/")
        assertRelativize("D:\\Program Files", ".bashrc", ".bashrc")
        assertRelativize("D:\\Program Files", "~", "~")
        assertRelativize("D:\\Program Files", "misc/some_file", "misc/some_file")
        assertRelativize("D:myfile.txt", "", "")
        assertRelativize("D:myfile.txt", "C:", "C:")
        assertRelativize("D:myfile.txt", "C:\\", "C:\\")
        assertRelativize("D:myfile.txt", "\\\\Server", "\\\\Server")
        assertRelativize("D:myfile.txt", "\\\\", "\\\\")
        assertRelativize("D:myfile.txt", "\\", "\\")
        assertRelativize("D:myfile.txt", "D:\\Program Files", "\\Program Files")
        assertRelativize("D:myfile.txt", "D:myfile.txt", "")
        assertRelativize("D:myfile.txt", "D:", "..")
        assertRelativize("D:myfile.txt", "My Documents\\Project", "My Documents\\Project")
        assertRelativize("D:myfile.txt", "/", "/")
        assertRelativize("D:myfile.txt", "/bin/bash", "/bin/bash")
        assertRelativize("D:myfile.txt", "/bin/bash/boom/flash/", "/bin/bash/boom/flash/")
        assertRelativize("D:myfile.txt", ".bashrc", ".bashrc")
        assertRelativize("D:myfile.txt", "~", "~")
        assertRelativize("D:myfile.txt", "misc/some_file", "misc/some_file")
        assertRelativize("D:", "", "")
        assertRelativize("D:", "C:", "C:")
        assertRelativize("D:", "C:\\", "C:\\")
        assertRelativize("D:", "\\\\Server", "\\\\Server")
        assertRelativize("D:", "\\\\", "\\\\")
        assertRelativize("D:", "\\", "\\")
        assertRelativize("D:", "D:\\Program Files", "\\Program Files")
        assertRelativize("D:", "D:myfile.txt", "myfile.txt")
        assertRelativize("D:", "D:", "")
        assertRelativize("D:", "My Documents\\Project", "My Documents\\Project")
        assertRelativize("D:", "/", "/")
        assertRelativize("D:", "/bin/bash", "/bin/bash")
        assertRelativize("D:", "/bin/bash/boom/flash/", "/bin/bash/boom/flash/")
        assertRelativize("D:", ".bashrc", ".bashrc")
        assertRelativize("D:", "~", "~")
        assertRelativize("D:", "misc/some_file", "misc/some_file")
        assertRelativize("My Documents\\Project", "", "../..")
        assertRelativize("My Documents\\Project", "C:", "C:")
        assertRelativize("My Documents\\Project", "C:\\", "C:\\")
        assertRelativize("My Documents\\Project", "\\\\Server", "\\\\Server")
        assertRelativize("My Documents\\Project", "\\\\", "\\\\")
        assertRelativize("My Documents\\Project", "\\", "\\")
        assertRelativize("My Documents\\Project", "D:\\Program Files", "D:\\Program Files")
        assertRelativize("My Documents\\Project", "D:myfile.txt", "D:myfile.txt")
        assertRelativize("My Documents\\Project", "D:", "D:")
        assertRelativize("My Documents\\Project", "My Documents\\Project", "")
        assertRelativize("My Documents\\Project", "/", "/")
        assertRelativize("My Documents\\Project", "/bin/bash", "/bin/bash")
        assertRelativize("My Documents\\Project", "/bin/bash/boom/flash/", "/bin/bash/boom/flash/")
        assertRelativize("My Documents\\Project", ".bashrc", "../../.bashrc")
        assertRelativize("My Documents\\Project", "~", "../../~")
        assertRelativize("My Documents\\Project", "misc/some_file", "../../misc/some_file")
        assertRelativize("/", "", "")
        assertRelativize("/", "C:", "C:")
        assertRelativize("/", "C:\\", "C:\\")
        assertRelativize("/", "\\\\Server", "\\\\Server")
        assertRelativize("/", "\\\\", "\\\\")
        assertRelativize("/", "\\", "")
        assertRelativize("/", "D:\\Program Files", "D:\\Program Files")
        assertRelativize("/", "D:myfile.txt", "D:myfile.txt")
        assertRelativize("/", "D:", "D:")
        assertRelativize("/", "My Documents\\Project", "My Documents\\Project")
        assertRelativize("/", "/", "")
        assertRelativize("/", "/bin/bash", "bin/bash")
        assertRelativize("/", "/bin/bash/boom/flash/", "bin/bash/boom/flash/")
        assertRelativize("/", ".bashrc", ".bashrc")
        assertRelativize("/", "~", "~")
        assertRelativize("/", "misc/some_file", "misc/some_file")
        assertRelativize("/bin/bash", "", "")
        assertRelativize("/bin/bash", "C:", "C:")
        assertRelativize("/bin/bash", "C:\\", "C:\\")
        assertRelativize("/bin/bash", "\\\\Server", "\\\\Server")
        assertRelativize("/bin/bash", "\\\\", "\\\\")
        assertRelativize("/bin/bash", "\\", "..\\..")
        assertRelativize("/bin/bash", "D:\\Program Files", "D:\\Program Files")
        assertRelativize("/bin/bash", "D:myfile.txt", "D:myfile.txt")
        assertRelativize("/bin/bash", "D:", "D:")
        assertRelativize("/bin/bash", "My Documents\\Project", "My Documents\\Project")
        assertRelativize("/bin/bash", "/", "../..")
        assertRelativize("/bin/bash", "/bin/bash", "")
        assertRelativize("/bin/bash", "/bin/bash/boom/flash/", "boom/flash/")
        assertRelativize("/bin/bash", ".bashrc", ".bashrc")
        assertRelativize("/bin/bash", "~", "~")
        assertRelativize("/bin/bash", "misc/some_file", "misc/some_file")
        assertRelativize("/bin/bash/boom/flash/", "", "")
        assertRelativize("/bin/bash/boom/flash/", "C:", "C:")
        assertRelativize("/bin/bash/boom/flash/", "C:\\", "C:\\")
        assertRelativize("/bin/bash/boom/flash/", "\\\\Server", "\\\\Server")
        assertRelativize("/bin/bash/boom/flash/", "\\\\", "\\\\")
        assertRelativize("/bin/bash/boom/flash/", "\\", "..\\..\\..\\..")
        assertRelativize("/bin/bash/boom/flash/", "D:\\Program Files", "D:\\Program Files")
        assertRelativize("/bin/bash/boom/flash/", "D:myfile.txt", "D:myfile.txt")
        assertRelativize("/bin/bash/boom/flash/", "D:", "D:")
        assertRelativize("/bin/bash/boom/flash/", "My Documents\\Project", "My Documents\\Project")
        assertRelativize("/bin/bash/boom/flash/", "/", "../../../..")
        assertRelativize("/bin/bash/boom/flash/", "/bin/bash", "../..")
        assertRelativize("/bin/bash/boom/flash/", "/bin/bash/boom/flash/", "")
        assertRelativize("/bin/bash/boom/flash/", ".bashrc", ".bashrc")
        assertRelativize("/bin/bash/boom/flash/", "~", "~")
        assertRelativize("/bin/bash/boom/flash/", "misc/some_file", "misc/some_file")
        assertRelativize(".bashrc", "", "..")
        assertRelativize(".bashrc", "C:", "C:")
        assertRelativize(".bashrc", "C:\\", "C:\\")
        assertRelativize(".bashrc", "\\\\Server", "\\\\Server")
        assertRelativize(".bashrc", "\\\\", "\\\\")
        assertRelativize(".bashrc", "\\", "\\")
        assertRelativize(".bashrc", "D:\\Program Files", "D:\\Program Files")
        assertRelativize(".bashrc", "D:myfile.txt", "D:myfile.txt")
        assertRelativize(".bashrc", "D:", "D:")
        assertRelativize(".bashrc", "My Documents\\Project", "..\\My Documents\\Project")
        assertRelativize(".bashrc", "/", "/")
        assertRelativize(".bashrc", "/bin/bash", "/bin/bash")
        assertRelativize(".bashrc", "/bin/bash/boom/flash/", "/bin/bash/boom/flash/")
        assertRelativize(".bashrc", ".bashrc", "")
        assertRelativize(".bashrc", "~", "../~")
        assertRelativize(".bashrc", "misc/some_file", "../misc/some_file")
        assertRelativize("~", "", "..")
        assertRelativize("~", "C:", "C:")
        assertRelativize("~", "C:\\", "C:\\")
        assertRelativize("~", "\\\\Server", "\\\\Server")
        assertRelativize("~", "\\\\", "\\\\")
        assertRelativize("~", "\\", "\\")
        assertRelativize("~", "D:\\Program Files", "D:\\Program Files")
        assertRelativize("~", "D:myfile.txt", "D:myfile.txt")
        assertRelativize("~", "D:", "D:")
        assertRelativize("~", "My Documents\\Project", "..\\My Documents\\Project")
        assertRelativize("~", "/", "/")
        assertRelativize("~", "/bin/bash", "/bin/bash")
        assertRelativize("~", "/bin/bash/boom/flash/", "/bin/bash/boom/flash/")
        assertRelativize("~", ".bashrc", "../.bashrc")
        assertRelativize("~", "~", "")
        assertRelativize("~", "misc/some_file", "../misc/some_file")
        assertRelativize("misc/some_file", "", "../..")
        assertRelativize("misc/some_file", "C:", "C:")
        assertRelativize("misc/some_file", "C:\\", "C:\\")
        assertRelativize("misc/some_file", "\\\\Server", "\\\\Server")
        assertRelativize("misc/some_file", "\\\\", "\\\\")
        assertRelativize("misc/some_file", "\\", "\\")
        assertRelativize("misc/some_file", "D:\\Program Files", "D:\\Program Files")
        assertRelativize("misc/some_file", "D:myfile.txt", "D:myfile.txt")
        assertRelativize("misc/some_file", "D:", "D:")
        assertRelativize("misc/some_file", "My Documents\\Project", "..\\..\\My Documents\\Project")
        assertRelativize("misc/some_file", "/", "/")
        assertRelativize("misc/some_file", "/bin/bash", "/bin/bash")
        assertRelativize("misc/some_file", "/bin/bash/boom/flash/", "/bin/bash/boom/flash/")
        assertRelativize("misc/some_file", ".bashrc", "../../.bashrc")
        assertRelativize("misc/some_file", "~", "../../~")
        assertRelativize("misc/some_file", "misc/some_file", "")
        assertRelativize("C:", "C:My Documents\\Project", "My Documents\\Project")
        assertRelativize("C:", "C:\\bin\\bash", "\\bin\\bash")
        assertRelativize("C:", "C:\\bin\\bash\\boom\\flash\\", "\\bin\\bash\\boom\\flash\\")
        assertRelativize("C:", "C:.bashrc", ".bashrc")
        assertRelativize("C:", "C:~", "~")
        assertRelativize("C:", "C:misc\\some_file", "misc\\some_file")
        assertRelativize("C:\\", "C:\\My Documents\\Project", "My Documents\\Project")
        assertRelativize("C:\\", "C:\\bin\\bash", "bin\\bash")
        assertRelativize("C:\\", "C:\\bin\\bash\\boom\\flash\\", "bin\\bash\\boom\\flash\\")
        assertRelativize("C:\\", "C:\\.bashrc", ".bashrc")
        assertRelativize("C:\\", "C:\\~", "~")
        assertRelativize("C:\\", "C:\\misc\\some_file", "misc\\some_file")
        assertRelativize("\\\\Server", "\\\\Server\\My Documents\\Project", "My Documents\\Project")
        assertRelativize("\\\\Server", "\\\\Server\\.bashrc", ".bashrc")
        assertRelativize("\\\\Server", "\\\\Server\\~", "~")
        assertRelativize("\\\\Server", "\\\\Server\\misc\\some_file", "misc\\some_file")
        assertRelativize("\\\\", "\\\\My Documents\\Project", "My Documents\\Project")
        assertRelativize("\\\\", "\\\\.bashrc", ".bashrc")
        assertRelativize("\\\\", "\\\\~", "~")
        assertRelativize("\\\\", "\\\\misc\\some_file", "misc\\some_file")
        assertRelativize("\\", "\\My Documents\\Project", "My Documents\\Project")
        assertRelativize("\\", "\\.bashrc", ".bashrc")
        assertRelativize("\\", "\\~", "~")
        assertRelativize("\\", "\\misc\\some_file", "misc\\some_file")
        assertRelativize("D:\\Program Files", "D:\\", "..")
        assertRelativize("D:\\Program Files", "D:\\Program Files\\myfile.txt", "myfile.txt")
        assertRelativize("D:\\Program Files",
                "D:\\Program Files\\My Documents\\Project",
                "My Documents\\Project")
        assertRelativize("D:\\Program Files", "D:\\bin\\bash", "..\\bin\\bash")
        assertRelativize("D:\\Program Files",
                "D:\\bin\\bash\\boom\\flash\\",
                "..\\bin\\bash\\boom\\flash\\")
        assertRelativize("D:\\Program Files", "D:\\Program Files\\.bashrc", ".bashrc")
        assertRelativize("D:\\Program Files", "D:\\Program Files\\~", "~")
        assertRelativize("D:\\Program Files",
                "D:\\Program Files\\misc\\some_file",
                "misc\\some_file")
        assertRelativize("D:myfile.txt", "D:\\", "\\")
        assertRelativize("D:myfile.txt", "D:myfile.txt\\myfile.txt", "myfile.txt")
        assertRelativize("D:myfile.txt",
                "D:myfile.txt\\My Documents\\Project",
                "My Documents\\Project")
        assertRelativize("D:myfile.txt", "D:\\bin\\bash", "\\bin\\bash")
        assertRelativize("D:myfile.txt",
                "D:\\bin\\bash\\boom\\flash\\",
                "\\bin\\bash\\boom\\flash\\")
        assertRelativize("D:myfile.txt", "D:myfile.txt\\.bashrc", ".bashrc")
        assertRelativize("D:myfile.txt", "D:myfile.txt\\~", "~")
        assertRelativize("D:myfile.txt", "D:myfile.txt\\misc\\some_file", "misc\\some_file")
        assertRelativize("D:", "D:\\", "\\")
        assertRelativize("D:", "D:My Documents\\Project", "My Documents\\Project")
        assertRelativize("D:", "D:\\bin\\bash", "\\bin\\bash")
        assertRelativize("D:", "D:\\bin\\bash\\boom\\flash\\", "\\bin\\bash\\boom\\flash\\")
        assertRelativize("D:", "D:.bashrc", ".bashrc")
        assertRelativize("D:", "D:~", "~")
        assertRelativize("D:", "D:misc\\some_file", "misc\\some_file")
        assertRelativize("My Documents\\Project",
                "My Documents\\Project\\My Documents\\Project",
                "My Documents\\Project")
        assertRelativize("My Documents\\Project", "My Documents\\Project\\.bashrc", ".bashrc")
        assertRelativize("My Documents\\Project", "My Documents\\Project\\~", "~")
        assertRelativize("My Documents\\Project",
                "My Documents\\Project\\misc\\some_file",
                "misc\\some_file")
        assertRelativize("/", "/My Documents/Project", "My Documents/Project")
        assertRelativize("/", "/.bashrc", ".bashrc")
        assertRelativize("/", "/~", "~")
        assertRelativize("/", "/misc/some_file", "misc/some_file")
        assertRelativize("/bin/bash", "/bin/bash/My Documents/Project", "My Documents/Project")
        assertRelativize("/bin/bash", "/bin/bash/.bashrc", ".bashrc")
        assertRelativize("/bin/bash", "/bin/bash/~", "~")
        assertRelativize("/bin/bash", "/bin/bash/misc/some_file", "misc/some_file")
        assertRelativize("/bin/bash/boom/flash/",
                "/bin/bash/boom/flash/My Documents/Project",
                "My Documents/Project")
        assertRelativize("/bin/bash/boom/flash/", "/bin/bash/boom/flash/.bashrc", ".bashrc")
        assertRelativize("/bin/bash/boom/flash/", "/bin/bash/boom/flash/~", "~")
        assertRelativize("/bin/bash/boom/flash/",
                "/bin/bash/boom/flash/misc/some_file",
                "misc/some_file")
        assertRelativize(".bashrc", ".bashrc/My Documents/Project", "My Documents/Project")
        assertRelativize(".bashrc", ".bashrc/.bashrc", ".bashrc")
        assertRelativize(".bashrc", ".bashrc/~", "~")
        assertRelativize(".bashrc", ".bashrc/misc/some_file", "misc/some_file")
        assertRelativize("~", "~/My Documents/Project", "My Documents/Project")
        assertRelativize("~", "~/.bashrc", ".bashrc")
        assertRelativize("~", "~/~", "~")
        assertRelativize("~", "~/misc/some_file", "misc/some_file")
        assertRelativize("misc/some_file",
                "misc/some_file/My Documents/Project",
                "My Documents/Project")
        assertRelativize("misc/some_file", "misc/some_file/.bashrc", ".bashrc")
        assertRelativize("misc/some_file", "misc/some_file/~", "~")
        assertRelativize("misc/some_file", "misc/some_file/misc/some_file", "misc/some_file")
    }

    @Test
    fun testFilesystemUri() {
        val path = PathString("/")
        assertThat(path.filesystemUri).isEqualTo(File(File.separator).toURI())
    }

    @Test
    fun testResolve() {
        assertResolves("", "", "")
        assertResolves("", "C:", "C:")
        assertResolves("", "C:\\", "C:\\")
        assertResolves("", "\\\\Server", "\\\\Server")
        assertResolves("", "\\\\", "\\\\")
        assertResolves("", "\\", "\\")
        assertResolves("", "D:\\Program Files", "D:\\Program Files")
        assertResolves("", "D:myfile.txt", "D:myfile.txt")
        assertResolves("", "My Documents\\Project", "My Documents\\Project")
        assertResolves("", "/", "/")
        assertResolves("", "/bin/bash", "/bin/bash")
        assertResolves("", ".bashrc", ".bashrc")
        assertResolves("", "~", "~")
        assertResolves("", "misc/some_file", "misc/some_file")
        assertResolves("C:", "", "C:")
        assertResolves("C:", "C:", "C:")
        assertResolves("C:", "C:\\", "C:\\")
        assertResolves("C:", "\\\\Server", "\\\\Server")
        assertResolves("C:", "\\\\", "\\\\")
        assertResolves("C:", "\\", "C:\\")
        assertResolves("C:", "D:\\Program Files", "D:\\Program Files")
        assertResolves("C:", "D:myfile.txt", "D:myfile.txt")
        assertResolves("C:", "My Documents\\Project", "C:My Documents\\Project")
        assertResolves("C:", "/", "C:\\")
        assertResolves("C:", "/bin/bash", "C:\\bin\\bash")
        assertResolves("C:", ".bashrc", "C:.bashrc")
        assertResolves("C:", "~", "C:~")
        assertResolves("C:", "misc/some_file", "C:misc\\some_file")
        assertResolves("C:\\", "", "C:\\")
        assertResolves("C:\\", "C:", "C:\\")
        assertResolves("C:\\", "C:\\", "C:\\")
        assertResolves("C:\\", "\\\\Server", "\\\\Server")
        assertResolves("C:\\", "\\\\", "\\\\")
        assertResolves("C:\\", "\\", "C:\\")
        assertResolves("C:\\", "D:\\Program Files", "D:\\Program Files")
        assertResolves("C:\\", "D:myfile.txt", "D:myfile.txt")
        assertResolves("C:\\", "My Documents\\Project", "C:\\My Documents\\Project")
        assertResolves("C:\\", "C:\\", "C:\\")
        assertResolves("C:\\", "/bin/bash", "C:\\bin\\bash")
        assertResolves("C:\\", ".bashrc", "C:\\.bashrc")
        // Currently no special handling for ~
        assertResolves("C:\\", "~", "C:\\~")
        assertResolves("C:\\", "misc/some_file", "C:\\misc\\some_file")
        assertResolves("\\\\Server", "", "\\\\Server")
        assertResolves("\\\\Server", "C:", "C:")
        assertResolves("\\\\Server", "C:\\", "C:\\")
        assertResolves("\\\\Server", "\\\\Server", "\\\\Server")
        assertResolves("\\\\Server", "\\\\", "\\\\")
        assertResolves("\\\\Server", "\\", "\\")
        assertResolves("\\\\Server", "D:\\Program Files", "D:\\Program Files")
        assertResolves("\\\\Server", "D:myfile.txt", "D:myfile.txt")
        assertResolves("\\\\Server", "My Documents\\Project", "\\\\Server\\My Documents\\Project")
        assertResolves("\\\\Server", "/", "/")
        assertResolves("\\\\Server", "/bin/bash", "/bin/bash")
        assertResolves("\\\\Server", ".bashrc", "\\\\Server\\.bashrc")
        assertResolves("\\\\Server", "~", "\\\\Server\\~")
        assertResolves("\\\\Server", "misc/some_file", "\\\\Server\\misc\\some_file")
        assertResolves("\\\\", "", "\\\\")
        assertResolves("\\\\", "C:", "C:")
        assertResolves("\\\\", "C:\\", "C:\\")
        assertResolves("\\\\", "\\\\Server", "\\\\Server")
        assertResolves("\\\\", "\\\\", "\\\\")
        assertResolves("\\\\", "\\", "\\")
        assertResolves("\\\\", "D:\\Program Files", "D:\\Program Files")
        assertResolves("\\\\", "D:myfile.txt", "D:myfile.txt")
        assertResolves("\\\\", "My Documents\\Project", "\\\\My Documents\\Project")
        assertResolves("\\\\", "/", "/")
        assertResolves("\\\\", "/bin/bash", "/bin/bash")
        assertResolves("\\\\", ".bashrc", "\\\\.bashrc")
        assertResolves("\\\\", "~", "\\\\~")
        assertResolves("\\\\", "misc/some_file", "\\\\misc\\some_file")
        assertResolves("\\", "", "\\")
        assertResolves("\\", "C:", "C:")
        assertResolves("\\", "C:\\", "C:\\")
        assertResolves("\\", "\\\\Server", "\\\\Server")
        assertResolves("\\", "\\\\", "\\\\")
        assertResolves("\\", "\\", "\\")
        assertResolves("\\", "D:\\Program Files", "D:\\Program Files")
        assertResolves("\\", "D:myfile.txt", "D:myfile.txt")
        assertResolves("\\", "My Documents\\Project", "\\My Documents\\Project")
        assertResolves("\\", "/", "/")
        assertResolves("\\", "/bin/bash", "/bin/bash")
        assertResolves("\\", ".bashrc", "\\.bashrc")
        assertResolves("\\", "~", "\\~")
        assertResolves("\\", "misc/some_file", "\\misc\\some_file")
        assertResolves("D:\\Program Files", "", "D:\\Program Files")
        assertResolves("D:\\Program Files", "C:", "C:")
        assertResolves("D:\\Program Files", "C:\\", "C:\\")
        assertResolves("D:\\Program Files", "\\\\Server", "\\\\Server")
        assertResolves("D:\\Program Files", "\\\\", "\\\\")
        assertResolves("D:\\Program Files", "\\", "D:\\")
        assertResolves("D:\\Program Files", "D:\\Program Files", "D:\\Program Files")
        assertResolves("D:\\Program Files", "D:myfile.txt", "D:\\Program Files\\myfile.txt")
        assertResolves("D:\\Program Files",
                "My Documents\\Project",
                "D:\\Program Files\\My Documents\\Project")
        assertResolves("D:\\Program Files", "/", "D:\\")
        assertResolves("D:\\Program Files", "/bin/bash", "D:\\bin\\bash")
        assertResolves("D:\\Program Files", ".bashrc", "D:\\Program Files\\.bashrc")
        assertResolves("D:\\Program Files", "~", "D:\\Program Files\\~")
        assertResolves("D:\\Program Files", "misc/some_file", "D:\\Program Files\\misc\\some_file")
        assertResolves("D:myfile.txt", "", "D:myfile.txt")
        assertResolves("D:myfile.txt", "C:", "C:")
        assertResolves("D:myfile.txt", "C:\\", "C:\\")
        assertResolves("D:myfile.txt", "\\\\Server", "\\\\Server")
        assertResolves("D:myfile.txt", "\\\\", "\\\\")
        assertResolves("D:myfile.txt", "\\", "D:\\")
        assertResolves("D:myfile.txt", "D:\\Program Files", "D:\\Program Files")
        assertResolves("D:myfile.txt", "D:myfile.txt", "D:myfile.txt\\myfile.txt")
        assertResolves("D:myfile.txt",
                "My Documents\\Project",
                "D:myfile.txt\\My Documents\\Project")
        assertResolves("D:myfile.txt", "/", "D:\\")
        assertResolves("D:myfile.txt", "/bin/bash", "D:\\bin\\bash")
        assertResolves("D:myfile.txt", ".bashrc", "D:myfile.txt\\.bashrc")
        assertResolves("D:myfile.txt", "~", "D:myfile.txt\\~")
        assertResolves("D:myfile.txt", "misc/some_file", "D:myfile.txt\\misc\\some_file")
        assertResolves("My Documents\\Project", "", "My Documents\\Project")
        assertResolves("My Documents\\Project", "C:", "C:")
        assertResolves("My Documents\\Project", "C:\\", "C:\\")
        assertResolves("My Documents\\Project", "\\\\Server", "\\\\Server")
        assertResolves("My Documents\\Project", "\\\\", "\\\\")
        assertResolves("My Documents\\Project", "\\", "\\")
        assertResolves("My Documents\\Project", "D:\\Program Files", "D:\\Program Files")
        assertResolves("My Documents\\Project", "D:myfile.txt", "D:myfile.txt")
        assertResolves("My Documents\\Project",
                "My Documents\\Project",
                "My Documents\\Project\\My Documents\\Project")
        assertResolves("My Documents\\Project", "/", "/")
        assertResolves("My Documents\\Project", "/bin/bash", "/bin/bash")
        assertResolves("My Documents\\Project", ".bashrc", "My Documents\\Project\\.bashrc")
        assertResolves("My Documents\\Project", "~", "My Documents\\Project\\~")
        assertResolves("My Documents\\Project",
                "misc/some_file",
                "My Documents\\Project\\misc\\some_file")
        assertResolves("/", "", "/")
        assertResolves("/", "C:", "C:")
        assertResolves("/", "C:\\", "C:\\")
        assertResolves("/", "\\\\Server", "\\\\Server")
        assertResolves("/", "\\\\", "\\\\")
        assertResolves("/", "\\", "\\")
        assertResolves("/", "D:\\Program Files", "D:\\Program Files")
        assertResolves("/", "D:myfile.txt", "D:myfile.txt")
        assertResolves("/", "My Documents\\Project", "/My Documents/Project")
        assertResolves("/", "/", "/")
        assertResolves("/", "/bin/bash", "/bin/bash")
        assertResolves("/", ".bashrc", "/.bashrc")
        assertResolves("/", "~", "/~")
        assertResolves("/", "misc/some_file", "/misc/some_file")
        assertResolves("/bin/bash", "", "/bin/bash")
        assertResolves("/bin/bash", "C:", "C:")
        assertResolves("/bin/bash", "C:\\", "C:\\")
        assertResolves("/bin/bash", "\\\\Server", "\\\\Server")
        assertResolves("/bin/bash", "\\\\", "\\\\")
        assertResolves("/bin/bash", "\\", "\\")
        assertResolves("/bin/bash", "D:\\Program Files", "D:\\Program Files")
        assertResolves("/bin/bash", "D:myfile.txt", "D:myfile.txt")
        assertResolves("/bin/bash", "My Documents\\Project", "/bin/bash/My Documents/Project")
        assertResolves("/bin/bash", "/", "/")
        assertResolves("/bin/bash", "/bin/bash", "/bin/bash")
        assertResolves("/bin/bash", ".bashrc", "/bin/bash/.bashrc")
        assertResolves("/bin/bash", "~", "/bin/bash/~")
        assertResolves("/bin/bash", "misc/some_file", "/bin/bash/misc/some_file")
        assertResolves(".bashrc", "", ".bashrc")
        assertResolves(".bashrc", "C:", "C:")
        assertResolves(".bashrc", "C:\\", "C:\\")
        assertResolves(".bashrc", "\\\\Server", "\\\\Server")
        assertResolves(".bashrc", "\\\\", "\\\\")
        assertResolves(".bashrc", "\\", "\\")
        assertResolves(".bashrc", "D:\\Program Files", "D:\\Program Files")
        assertResolves(".bashrc", "D:myfile.txt", "D:myfile.txt")
        assertResolves(".bashrc", "My Documents\\Project", ".bashrc/My Documents/Project")
        assertResolves(".bashrc", "/", "/")
        assertResolves(".bashrc", "/bin/bash", "/bin/bash")
        assertResolves(".bashrc", ".bashrc", ".bashrc/.bashrc")
        assertResolves(".bashrc", "~", ".bashrc/~")
        assertResolves(".bashrc", "misc/some_file", ".bashrc/misc/some_file")
        assertResolves("~", "", "~")
        assertResolves("~", "C:", "C:")
        assertResolves("~", "C:\\", "C:\\")
        assertResolves("~", "\\\\Server", "\\\\Server")
        assertResolves("~", "\\\\", "\\\\")
        assertResolves("~", "\\", "\\")
        assertResolves("~", "D:\\Program Files", "D:\\Program Files")
        assertResolves("~", "D:myfile.txt", "D:myfile.txt")
        assertResolves("~", "My Documents\\Project", "~/My Documents/Project")
        assertResolves("~", "/", "/")
        assertResolves("~", "/bin/bash", "/bin/bash")
        assertResolves("~", ".bashrc", "~/.bashrc")
        assertResolves("~", "~", "~/~")
        assertResolves("~", "misc/some_file", "~/misc/some_file")
        assertResolves("misc/some_file", "", "misc/some_file")
        assertResolves("misc/some_file", "C:", "C:")
        assertResolves("misc/some_file", "C:\\", "C:\\")
        assertResolves("misc/some_file", "\\\\Server", "\\\\Server")
        assertResolves("misc/some_file", "\\\\", "\\\\")
        assertResolves("misc/some_file", "\\", "\\")
        assertResolves("misc/some_file", "D:\\Program Files", "D:\\Program Files")
        assertResolves("misc/some_file", "D:myfile.txt", "D:myfile.txt")
        assertResolves("misc/some_file",
                "My Documents\\Project",
                "misc/some_file/My Documents/Project")
        assertResolves("misc/some_file", "/", "/")
        assertResolves("misc/some_file", "/bin/bash", "/bin/bash")
        assertResolves("misc/some_file", ".bashrc", "misc/some_file/.bashrc")
        assertResolves("misc/some_file", "~", "misc/some_file/~")
        assertResolves("misc/some_file", "misc/some_file", "misc/some_file/misc/some_file")
    }

    private fun assertResolves(path1: String, path2: String, resolved: String) {
        val p1 = PathString(path1)
        val p2 = PathString(path2)
        val res = p1.resolve(p2)

        assertThat(res).isEqualTo(PathString(resolved))
    }
}
