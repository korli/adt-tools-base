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

package com.android.build.gradle.managed;

import com.android.annotations.Nullable;
import org.gradle.model.Managed;

@Managed
public interface DataBindingOptions {

    /**
     * The version of data binding to use.
     */
    @Nullable
    String getVersion();
    void setVersion(String version);

    /**
     * Whether to enable data binding.
     */
    boolean getEnabled();
    void setEnabled(boolean enabled);

    /**
     * Whether to add the default data binding adapters.
     */
    @Nullable
    Boolean getAddDefaultAdapters();
    void setAddDefaultAdapters(Boolean addDefaultAdapters);
    /**
     * Whether we want tests to be able to use data binding as well.
     * <p>
     * Data Binding classes generated from the application can always be accessed in the test code
     * but test itself cannot introduce new Data Binding layouts, bindables etc unless this flag
     * is turned on.
     * <p>
     * This settings help with an issue in older devices where class verifier throws an exception
     * when the application class is overwritten by the test class. It also makes it easier to run
     * proguarded tests.
     */
    @Nullable
    Boolean getEnabledForTests();
    void setEnabledForTests(Boolean enabledForTests);
}
