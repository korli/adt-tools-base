/*
 * Copyright (C) 2012 The Android Open Source Project
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
package com.android.build.gradle.tasks;

import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.ALL;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.MANIFEST;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.dsl.CoreBuildType;
import com.android.build.gradle.internal.dsl.CoreProductFlavor;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.SplitScope;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.TaskInputHelper;
import com.android.build.gradle.internal.variant.ApkVariantOutputData;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.core.VariantConfiguration;
import com.android.builder.model.ApiVersion;
import com.android.ide.common.build.ApkData;
import com.android.manifmerger.ManifestMerger2;
import com.android.manifmerger.ManifestMerger2.Invoker.Feature;
import com.android.manifmerger.ManifestProvider;
import com.android.manifmerger.MergingReport;
import com.android.manifmerger.XmlDocument;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.ParallelizableTask;
import org.gradle.internal.component.local.model.OpaqueComponentArtifactIdentifier;

/**
 * A task that processes the manifest
 */
@ParallelizableTask
public class MergeManifests extends ManifestProcessorTask {

    private Supplier<String> minSdkVersion;
    private Supplier<String> targetSdkVersion;
    private Supplier<Integer> maxSdkVersion;
    private File reportFile;
    private VariantConfiguration<CoreBuildType, CoreProductFlavor, CoreProductFlavor>
            variantConfiguration;
    private ApkVariantOutputData variantOutputData;
    private ArtifactCollection manifests;
    private FileCollection microApkManifest;
    private FileCollection compatibleScreensManifest;
    private List<Feature> optionalFeatures;
    private SplitScope splitScope;

    @Override
    protected void doFullTaskAction() throws IOException {
        // read the output of the compatible screen manifest.
        Collection<SplitScope.SplitOutput> compatibleScreenManifests =
                SplitScope.load(
                        VariantScope.TaskOutputType.COMPATIBLE_SCREEN_MANIFEST,
                        compatibleScreensManifest);
        @Nullable SplitScope.SplitOutput compatibleScreenManifestForSplit;
        // FIX ME : multi threading.
        for (ApkData apkData : splitScope.getApkDatas()) {
            compatibleScreenManifestForSplit =
                    SplitScope.getOutput(
                            compatibleScreenManifests,
                            VariantScope.TaskOutputType.COMPATIBLE_SCREEN_MANIFEST,
                            apkData);
            File manifestOutputFile =
                    FileUtils.join(
                            getManifestOutputDirectory(),
                            apkData.getDirName(),
                            SdkConstants.ANDROID_MANIFEST_XML);
            File instantRunManifestOutputFile =
                    FileUtils.join(
                            getInstantRunManifestOutputDirectory(),
                            apkData.getDirName(),
                            SdkConstants.ANDROID_MANIFEST_XML);
            MergingReport mergingReport =
                    getBuilder()
                            .mergeManifestsForApplication(
                                    getMainManifest(),
                                    getManifestOverlays(),
                                    computeFullProviderList(compatibleScreenManifestForSplit),
                                    getPackageOverride(),
                                    apkData.getVersionCode(),
                                    apkData.getVersionName(),
                                    getMinSdkVersion(),
                                    getTargetSdkVersion(),
                                    getMaxSdkVersion(),
                                    manifestOutputFile.getAbsolutePath(),
                                    // no aapt friendly merged manifest file necessary for applications.
                                    null /* aaptFriendlyManifestOutputFile */,
                                    instantRunManifestOutputFile.getAbsolutePath(),
                                    ManifestMerger2.MergeType.APPLICATION,
                                    variantConfiguration.getManifestPlaceholders(),
                                    getOptionalFeatures(),
                                    getReportFile());

            XmlDocument mergedXmlDocument =
                    mergingReport.getMergedXmlDocument(MergingReport.MergedManifestKind.MERGED);

            ImmutableMap<String, String> properties =
                    mergedXmlDocument != null
                            ? ImmutableMap.of(
                                    "packageId", mergedXmlDocument.getPackageName(),
                                    "split", mergedXmlDocument.getSplitName())
                            : ImmutableMap.of();

            splitScope.addOutputForSplit(
                    VariantScope.TaskOutputType.MERGED_MANIFESTS,
                    apkData,
                    manifestOutputFile,
                    properties);
            splitScope.addOutputForSplit(
                    VariantScope.TaskOutputType.INSTANT_RUN_MERGED_MANIFESTS,
                    apkData,
                    instantRunManifestOutputFile,
                    properties);
        }
        splitScope.save(
                ImmutableList.of(VariantScope.TaskOutputType.MERGED_MANIFESTS),
                getManifestOutputDirectory());
        splitScope.save(
                ImmutableList.of(VariantScope.TaskOutputType.INSTANT_RUN_MERGED_MANIFESTS),
                getInstantRunManifestOutputDirectory());
    }

    @Optional
    @InputFile
    public File getMainManifest() {
        return variantConfiguration.getMainManifest();
    }

    @InputFiles
    public List<File> getManifestOverlays() {
        return variantConfiguration.getManifestOverlays();
    }

    @Input
    @Optional
    public String getPackageOverride() {
        return variantConfiguration.getIdOverride();
    }

    @Input
    public int getVersionCode() {
        if (variantOutputData != null) {
            return variantOutputData.getVersionCode();
        }
        return variantConfiguration.getVersionCode();
    }

    @Input
    @Optional
    public String getVersionName() {
        if (variantOutputData != null) {
            return variantOutputData.getVersionName();
        }
        return variantConfiguration.getVersionName();
    }

    /**
     * Returns a serialized version of our map of key value pairs for placeholder substitution.
     *
     * This serialized form is only used by gradle to compare past and present tasks to determine
     * whether a task need to be re-run or not.
     */
    @Input
    @Optional
    public String getManifestPlaceholders() {
        return serializeMap(variantConfiguration.getManifestPlaceholders());
    }

    /**
     * Compute the final list of providers based on the manifest file collection and the other
     * providers.
     *
     * @return the list of providers.
     */
    private List<ManifestProvider> computeFullProviderList(
            @Nullable SplitScope.SplitOutput compatibleScreenManifestForSplit) {
        final Set<ResolvedArtifactResult> artifacts = manifests.getArtifacts();
        List<ManifestProvider> providers = Lists.newArrayListWithCapacity(artifacts.size() + 2);

        for (ResolvedArtifactResult artifact : artifacts) {
            providers.add(new ConfigAction.ManifestProviderImpl(
                    artifact.getFile(),
                    getArtifactName(artifact)));
        }

        if (microApkManifest != null) {
            // this is now always present if embedding is enabled, but it doesn't mean
            // anything got embedded so the file may not run (the file path exists and is
            // returned by the FC but the file doesn't exist.
            File microManifest = microApkManifest.getSingleFile();
            if (microManifest.isFile()) {
                providers.add(new ConfigAction.ManifestProviderImpl(
                        microManifest,
                        "Wear App sub-manifest"));
            }
        }

        if (compatibleScreenManifestForSplit != null) {
            providers.add(
                    new ConfigAction.ManifestProviderImpl(
                            compatibleScreenManifestForSplit.getOutputFile(),
                            "Compatible-Screens sub-manifest"));

        }

        return providers;
    }

    // TODO put somewhere else?
    @NonNull
    public static String getArtifactName(@NonNull ResolvedArtifactResult artifact) {
        ComponentIdentifier id = artifact.getId().getComponentIdentifier();
        if (id instanceof ProjectComponentIdentifier) {
            return ((ProjectComponentIdentifier) id).getProjectPath();

        } else if (id instanceof ModuleComponentIdentifier) {
            ModuleComponentIdentifier mID = (ModuleComponentIdentifier) id;
            return mID.getGroup() + ":" + mID.getModule() + ":" + mID.getVersion();

        } else if (id instanceof OpaqueComponentArtifactIdentifier) {
            // this is the case for local jars.
            // FIXME: use a non internal class.
            return id.getDisplayName();
        } else {
            throw new RuntimeException("Unsupported type of CompoenentIdentifier");
        }
    }

    @Input
    @Optional
    public String getMinSdkVersion() {
        return minSdkVersion.get();
    }

    @Input
    @Optional
    public String getTargetSdkVersion() {
        return targetSdkVersion.get();
    }

    @Input
    @Optional
    public Integer getMaxSdkVersion() {
        return maxSdkVersion.get();
    }

    @OutputFile
    @Optional
    public File getReportFile() {
        return reportFile;
    }

    public void setReportFile(File reportFile) {
        this.reportFile = reportFile;
    }

    /** Not an input, see {@link #getOptionalFeaturesString()}. */
    public List<Feature> getOptionalFeatures() {
        return optionalFeatures;
    }

    /** Synthetic input for {@link #getOptionalFeatures()} */
    @Input
    public List<String> getOptionalFeaturesString() {
        return optionalFeatures.stream().map(Enum::toString).collect(Collectors.toList());
    }

    public VariantConfiguration getVariantConfiguration() {
        return variantConfiguration;
    }

    public void setVariantConfiguration(
            VariantConfiguration<CoreBuildType, CoreProductFlavor, CoreProductFlavor> variantConfiguration) {
        this.variantConfiguration = variantConfiguration;
    }

    public ApkVariantOutputData getVariantOutputData() {
        return variantOutputData;
    }

    public void setVariantOutputData(ApkVariantOutputData variantOutputData) {
        this.variantOutputData = variantOutputData;
    }

    @SuppressWarnings("unused")
    @InputFiles
    public FileCollection getManifests() {
        return manifests.getArtifactFiles();
    }

    @SuppressWarnings("unused")
    @InputFiles
    @Optional
    public FileCollection getMicroApkManifest() {
        return microApkManifest;
    }

    @SuppressWarnings("unused")
    @InputFiles
    @Optional
    public FileCollection getCompatibleScreensManifest() {
        return compatibleScreensManifest;
    }

    public static class ConfigAction implements TaskConfigAction<MergeManifests> {

        private final VariantScope variantScope;
        private final List<Feature> optionalFeatures;

        public ConfigAction(@NonNull VariantScope scope, @NonNull List<Feature> optionalFeatures) {
            this.variantScope = scope;
            this.optionalFeatures = optionalFeatures;
        }

        @NonNull
        @Override
        public String getName() {
            return variantScope.getTaskName("process", "Manifest");
        }

        @NonNull
        @Override
        public Class<MergeManifests> getType() {
            return MergeManifests.class;
        }

        @Override
        public void execute(@NonNull MergeManifests processManifestTask) {
            final BaseVariantData<? extends BaseVariantOutputData> variantData =
                    variantScope.getVariantData();
            final VariantConfiguration<CoreBuildType, CoreProductFlavor, CoreProductFlavor> config =
                    variantData.getVariantConfiguration();
            GlobalScope globalScope = variantScope.getGlobalScope();
            AndroidBuilder androidBuilder = globalScope.getAndroidBuilder();

            processManifestTask.setAndroidBuilder(androidBuilder);
            processManifestTask.setVariantName(config.getFullName());
            processManifestTask.splitScope = variantData.getSplitScope();

            processManifestTask.setVariantConfiguration(config);

            Project project = globalScope.getProject();

            // this includes the libraries and the atoms.
            processManifestTask.manifests = variantScope.getArtifactCollection(
                    RUNTIME_CLASSPATH, ALL, MANIFEST);

            // optional manifest files too.
            if (variantScope.getMicroApkTask() != null &&
                    config.getBuildType().isEmbedMicroApp()) {
                processManifestTask.microApkManifest = project.files(
                        variantScope.getMicroApkManifestFile());
            }
            processManifestTask.compatibleScreensManifest =
                    variantScope.getOutputs(VariantScope.TaskOutputType.COMPATIBLE_SCREEN_MANIFEST);

            processManifestTask.minSdkVersion =
                    TaskInputHelper.memoize(
                            () -> {
                                if (androidBuilder.isPreviewTarget()) {
                                    return androidBuilder.getTargetCodename();
                                }

                                ApiVersion minSdk = config.getMergedFlavor().getMinSdkVersion();
                                return minSdk == null ? null : minSdk.getApiString();
                            });

            processManifestTask.targetSdkVersion =
                    TaskInputHelper.memoize(
                            () -> {
                                if (androidBuilder.isPreviewTarget()) {
                                    return androidBuilder.getTargetCodename();
                                }
                                ApiVersion targetSdk =
                                        config.getMergedFlavor().getTargetSdkVersion();
                                return targetSdk == null ? null : targetSdk.getApiString();
                            });

            processManifestTask.maxSdkVersion =
                    TaskInputHelper.memoize(
                            () -> {
                                if (androidBuilder.isPreviewTarget()) {
                                    return null;
                                }
                                return config.getMergedFlavor().getMaxSdkVersion();
                            });

            processManifestTask.setManifestOutputDirectory(
                    variantScope.getManifestOutputDirectory());

            processManifestTask.setInstantRunManifestOutputDirectory(
                    variantScope.getInstantRunManifestOutputDirectory());

            processManifestTask.setReportFile(variantScope.getManifestReportFile());
            processManifestTask.optionalFeatures = optionalFeatures;
        }

        /**
         * Implementation of AndroidBundle that only contains a manifest.
         *
         * This is used to pass to the merger manifest snippet that needs to be added during
         * merge.
         */
        public static class ManifestProviderImpl implements ManifestProvider {

            @NonNull
            private final File manifest;

            @NonNull
            private final String name;

            public ManifestProviderImpl(@NonNull File manifest, @NonNull String name) {
                this.manifest = manifest;
                this.name = name;
            }

            @NonNull
            @Override
            public File getManifest() {
                return manifest;
            }

            @NonNull
            @Override
            public String getName() {
                return name;
            }
        }
    }
}
