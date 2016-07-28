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
package com.android.assetstudiolib;

import static com.google.common.truth.Truth.assertThat;

import com.android.assetstudiolib.AssetStudio.Generator;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Matchers;
import org.mockito.Mockito;

import java.util.Collections;

@RunWith(JUnit4.class)
public final class AssetStudioTest {

    @Rule public final ExpectedException thrown = ExpectedException.none();

    private Generator mGenerator;

    @Before
    public void mockGenerator() {
        mGenerator = Mockito.mock(Generator.class);

        Mockito.when(mGenerator.getResourceNames(Matchers.any()))
                .thenReturn(Collections.emptyIterator());

        Mockito.when(mGenerator.getResourceNames("images/material_design_icons/action/"))
                .thenReturn(Collections.singletonList("ic_search_black_24dp.xml").iterator());
    }

    @Test
    public void getBasenameToPathMap() {
        Object expected = Collections.singletonMap(
                "ic_search_black_24dp",
                "images/material_design_icons/action/ic_search_black_24dp.xml");

        assertThat(AssetStudio.getBasenameToPathMap(mGenerator)).isEqualTo(expected);
    }

    @Test
    public void getBasenameToPathMapThrowsIllegalArgumentException() {
        Mockito.when(mGenerator.getResourceNames("images/material_design_icons/device/"))
                .thenReturn(Collections.singletonList("ic_search_black_24dp.xml").iterator());

        thrown.expect(IllegalArgumentException.class);
        AssetStudio.getBasenameToPathMap(mGenerator);
    }
}
