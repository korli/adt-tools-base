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

package com.android.build.gradle.internal.ide;

import static com.android.SdkConstants.EXT_AAR;
import static com.android.SdkConstants.EXT_JAR;
import static com.android.SdkConstants.FD_JARS;
import static com.google.common.base.Preconditions.checkNotNull;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.ide.level2.AndroidLibraryImpl;
import com.android.build.gradle.internal.ide.level2.GraphItemImpl;
import com.android.build.gradle.internal.ide.level2.ModuleLibraryImpl;
import com.android.build.gradle.internal.ide.level2.SimpleDependencyGraphsImpl;
import com.android.build.gradle.internal.publishing.AndroidArtifacts;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.builder.dependency.MavenCoordinatesImpl;
import com.android.builder.dependency.level2.JavaDependency;
import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.JavaLibrary;
import com.android.builder.model.MavenCoordinates;
import com.android.builder.model.level2.DependencyGraphs;
import com.android.builder.model.level2.GraphItem;
import com.android.builder.model.level2.Library;
import com.android.ide.common.caching.CreatingCache;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import java.io.File;
import java.util.List;
import java.util.Map;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.artifacts.result.ResolvedVariantResult;
import org.gradle.api.component.Artifact;

/** For creating dependency graph based on {@link ResolvedArtifactResult}. */
public class ArtifactDependencyGraph {

    private static final CreatingCache<ResolvedArtifactResult, MavenCoordinates>
            sMavenCoordinatesCache =
                    new CreatingCache<>(ArtifactDependencyGraph::computeMavenCoordinates);

    private static final CreatingCache<ResolvedArtifactResult, Library> sLibraryCache =
            new CreatingCache<>(ArtifactDependencyGraph::instantiateLibrary);

    public static void clearCaches() {
        sMavenCoordinatesCache.clear();
        sLibraryCache.clear();
    }

    private static Library instantiateLibrary(@NonNull ResolvedArtifactResult artifact) {
        ComponentIdentifier id = artifact.getId().getComponentIdentifier();
        String address = ArtifactDependencyGraph.computeAddress(artifact);
        if (id instanceof ProjectComponentIdentifier) {
            return new ModuleLibraryImpl(
                    address,
                    artifact.getFile(),
                    address,
                    null /* variant */); // FIXME: Waiting for Gradle to provide a way to get variant information.
        } else if (Files.getFileExtension(artifact.getFile().getName()).equals(EXT_JAR)) {
            return new com.android.build.gradle.internal.ide.level2.JavaLibraryImpl(
                    address, artifact.getFile());
        } else {
            return new AndroidLibraryImpl(
                    address,
                    null, /* artifactFile */
                    artifact.getFile(),
                    new File(artifact.getFile(), FD_JARS), // TODO: This should not be hard-coded.
                    ImmutableList.of()); // FIXME: get local jar override
        }
    }

    public static Map<String, Library> getGlobalLibMap() {
        List<Library> values = sLibraryCache.values();
        Map<String, Library> map = Maps.newHashMapWithExpectedSize(values.size());
        for (Library library : values) {
            map.put(library.getArtifactAddress(), library);
        }
        return map;
    }

    private static String computeAddress(ResolvedArtifactResult artifact) {
        ComponentIdentifier id = artifact.getId().getComponentIdentifier();
        if (id instanceof ProjectComponentIdentifier) {
            // FIXME: Project address needs to contain variant name.
            return ((ProjectComponentIdentifier) id).getProjectPath().intern();
        } else if (id instanceof ModuleComponentIdentifier
                || Files.getFileExtension(artifact.getFile().getName()).equals(EXT_JAR)) {
            MavenCoordinates coordinates =
                    sMavenCoordinatesCache.get(new HashableResolvedArtifactResult(artifact));
            checkNotNull(coordinates);
            return coordinates.toString().intern();
        } else {
            throw new RuntimeException(
                    "Don't know how to handle ComponentIdentifier '"
                            + id.getDisplayName()
                            + "'of type "
                            + id.getClass());
        }
    }

    private static MavenCoordinates computeMavenCoordinates(ResolvedArtifactResult artifact) {
        ComponentIdentifier id = artifact.getId().getComponentIdentifier();

        String extension = Files.getFileExtension(artifact.getFile().getName());
        if (id instanceof ModuleComponentIdentifier) {
            ModuleComponentIdentifier moduleComponentId = (ModuleComponentIdentifier) id;
            return new MavenCoordinatesImpl(
                    moduleComponentId.getGroup(),
                    moduleComponentId.getModule(),
                    moduleComponentId.getVersion(),
                    extension.isEmpty() ? EXT_AAR : extension,
                    null);
        } else if (id instanceof ProjectComponentIdentifier) {
            return new MavenCoordinatesImpl(
                    "artifacts", ((ProjectComponentIdentifier) id).getProjectPath(), "unspecified");
        } else if (extension.equals(EXT_JAR)) {
            // We have a local jar.
            return JavaDependency.getCoordForLocalJar(artifact.getFile());
        }
        throw new RuntimeException(
                "Don't know how to compute maven coordinate for artifact '"
                        + artifact.getId().getDisplayName()
                        + "' with component identifier of type '"
                        + id.getClass()
                        + "'.");
    }

    /** Return an Iterable of all artifact a variant depends on. */
    private static Iterable<ResolvedArtifactResult> getAllArtifacts(VariantScope variantScope) {
        // FIXME: This is returning all aar artifacts first and then jar artifacts instead of the
        // order that the variant would actually see.
        ArtifactCollection aarArtifacts =
                variantScope.getArtifactCollection(
                        AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH,
                        AndroidArtifacts.ArtifactScope.ALL,
                        AndroidArtifacts.ArtifactType.EXPLODED_AAR);
        ArtifactCollection jarArtifacts =
                variantScope.getArtifactCollection(
                        AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH,
                        AndroidArtifacts.ArtifactScope.ALL,
                        AndroidArtifacts.ArtifactType.JAR);
        return Iterables.concat(aarArtifacts, jarArtifacts);
    }

    /** Create a level 2 dependency graph. */
    public static DependencyGraphs createLevel2DependencyGraph(VariantScope variantScope) {
        List<GraphItem> compileItems = Lists.newArrayList();

        for (ResolvedArtifactResult artifact : getAllArtifacts(variantScope)) {
            compileItems.add(new GraphItemImpl(computeAddress(artifact), ImmutableList.of()));
            sLibraryCache.get(new HashableResolvedArtifactResult(artifact));
        }

        return new SimpleDependencyGraphsImpl(compileItems);
    }

    /** Create a level 1 dependency list. */
    public static DependenciesConverter.DependenciesImpl createDependencies(
            VariantScope variantScope) {
        ImmutableList.Builder<String> projects = ImmutableList.builder();
        ImmutableList.Builder<AndroidLibrary> androidLibraries = ImmutableList.builder();
        ImmutableList.Builder<JavaLibrary> javaLibrary = ImmutableList.builder();

        for (ResolvedArtifactResult artifact : getAllArtifacts(variantScope)) {
            ComponentIdentifier id = artifact.getId().getComponentIdentifier();

            boolean isSubproject = id instanceof ProjectComponentIdentifier;
            String projectPath =
                    isSubproject ? ((ProjectComponentIdentifier) id).getProjectPath() : null;

            if (Files.getFileExtension(artifact.getFile().getName()).equals(EXT_JAR)) {
                if (projectPath != null) {
                    projects.add(projectPath);
                    continue;
                }
                // FIXME: Dependencies information is not set correctly.
                javaLibrary.add(
                        new JavaLibraryImpl(
                                artifact.getFile(),
                                null,
                                ImmutableList.of(), /* dependencies */
                                null, /* requestedCoordinates */
                                checkNotNull(
                                        sMavenCoordinatesCache.get(
                                                new HashableResolvedArtifactResult(artifact))),
                                false, /* isSkipped */
                                false)); /* isProvided */
            } else {
                androidLibraries.add(
                        new com.android.build.gradle.internal.ide.AndroidLibraryImpl(
                                // FIXME: Dependencies information is not set correctly.
                                checkNotNull(
                                        sMavenCoordinatesCache.get(
                                                new HashableResolvedArtifactResult(artifact))),
                                projectPath,
                                artifact.getFile(),
                                new File(
                                        artifact.getFile(),
                                        FD_JARS), // TODO: This should not be hard-coded.
                                null, //@Nullable String variant,   FIXME: Waiting for Gradle to provide a way to get variant information.
                                false, /* dependencyItem.isProvided() */
                                false, /* dependencyItem.isSkipped() */
                                ImmutableList.of(), /* androidLibraries */
                                ImmutableList.of(), /* javaLibraries */
                                ImmutableList.of())); /*localJarOverride */
            }
        }

        // FIXME: Get atom libraries.
        return new DependenciesConverter.DependenciesImpl(
                ImmutableList.of(), /* atoms */
                androidLibraries.build(),
                javaLibrary.build(),
                projects.build(),
                null); /* baseAtom */
    }

    private static class HashableResolvedArtifactResult implements ResolvedArtifactResult {
        @NonNull private ResolvedArtifactResult delegate;

        public HashableResolvedArtifactResult(@NonNull ResolvedArtifactResult delegate) {
            this.delegate = delegate;
        }

        @Override
        public File getFile() {
            return delegate.getFile();
        }

        @Override
        public ResolvedVariantResult getVariant() {
            return delegate.getVariant();
        }

        @Override
        public ComponentArtifactIdentifier getId() {
            return delegate.getId();
        }

        @Override
        public Class<? extends Artifact> getType() {
            return delegate.getType();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            HashableResolvedArtifactResult that = (HashableResolvedArtifactResult) o;
            return Objects.equal(getFile(), that.getFile())
                    && Objects.equal(getId(), that.getId())
                    && Objects.equal(getType(), that.getType());
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(getFile(), getId(), getType());
        }
    }
}
