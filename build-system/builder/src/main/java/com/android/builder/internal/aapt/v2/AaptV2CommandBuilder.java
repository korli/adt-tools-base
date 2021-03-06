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

package com.android.builder.internal.aapt.v2;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.builder.core.VariantType;
import com.android.builder.internal.aapt.AaptException;
import com.android.builder.internal.aapt.AaptPackageConfig;
import com.android.builder.internal.aapt.AaptUtils;
import com.android.builder.internal.aapt.BlockingResourceLinker;
import com.android.ide.common.res2.CompileResourceRequest;
import com.android.utils.FileUtils;
import com.android.utils.ILogger;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Builds the command lines for use with {@code aapt2}.
 */
public final class AaptV2CommandBuilder {

    /**
     * Utility class, no constructor.
     */
    private AaptV2CommandBuilder() {}

    /**
     * Creates the command line used to compile a resource. See {@link
     * com.android.builder.internal.aapt.Aapt#compile(CompileResourceRequest)}.
     *
     * @return the command line arguments
     */
    public static ImmutableList<String> makeCompile(@NonNull CompileResourceRequest request) {
        ImmutableList.Builder<String> parameters = new ImmutableList.Builder<>();

        if (request.isPseudoLocalize()) {
            parameters.add("--pseudo-localize");
        }

        if (!request.isPngCrunching()) {
            // Only pass --no-crunch for png files and not for 9-patch files as that breaks them.
            String lowerName = request.getInputFile().getPath().toLowerCase(Locale.US);
            if (lowerName.endsWith(SdkConstants.DOT_PNG)
                    && !lowerName.endsWith(SdkConstants.DOT_9PNG)) {
                parameters.add("--no-crunch");
            }
        }

        parameters.add("--legacy");
        parameters.add("-o", request.getOutputDirectory().getAbsolutePath());
        parameters.add(request.getInputFile().getAbsolutePath());

        return parameters.build();
    }

    /**
     * Creates the command line used to link the package.
     *
     * <p>See {@link BlockingResourceLinker#link(AaptPackageConfig, ILogger)}.
     *
     * @param config see above
     * @return the command line arguments
     * @throws AaptException failed to build the command line
     */
    public static ImmutableList<String> makeLink(@NonNull AaptPackageConfig config)
            throws AaptException {
        ImmutableList.Builder<String> builder = ImmutableList.builder();

        if (config.getVerbose()) {
            builder.add("-v");
        }

        if (config.getGenerateProtos()) {
            builder.add("--proto-format");
        }

        // inputs
        builder.add("-I", Preconditions.checkNotNull(config.getAndroidJarPath()));

        config.getImports().forEach(file -> builder.add("-I", file.getAbsolutePath()));

        File manifestFile = config.getManifestFile();
        Preconditions.checkNotNull(manifestFile);
        builder.add("--manifest", manifestFile.getAbsolutePath());

        File resourceOutputApk;
        if (config.getResourceOutputApk() != null) {
            resourceOutputApk = config.getResourceOutputApk();
        } else {
            // FIXME: Fix when aapt 2 support not providing -o (http://b.android.com/210026)
            try {
                File tmpOutput = File.createTempFile("aapt-", "-out");
                tmpOutput.deleteOnExit();
                resourceOutputApk = tmpOutput;
            } catch (IOException e) {
                throw new AaptException("No output apk defined and failed to create tmp file", e);
            }
        }
        builder.add("-o", resourceOutputApk.getAbsolutePath());

        if (!config.getResourceDirs().isEmpty()) {
            try {
                if (config.isListResourceFiles()) {
                    // AAPT2 only accepts individual files passed to the -R flag. In order to not
                    // pass every single resource file, instead create a temporary file containing a
                    // list of resource files and pass it as the only -R argument.
                    File resourceListFile =
                            new File(
                                    Preconditions.checkNotNull(
                                            config.getIntermediateDir(),
                                            "Intermediate directory must be supplied."),
                                    "resources-list-for-" + resourceOutputApk.getName() + ".txt");

                    // Resources list could have changed since last run.
                    FileUtils.deleteIfExists(resourceListFile);
                    for (File dir : config.getResourceDirs()) {
                        try (FileOutputStream fos = new FileOutputStream(resourceListFile);
                                PrintWriter pw = new PrintWriter(fos)) {

                            Files.walk(dir.toPath())
                                    .filter(Files::isRegularFile)
                                    .forEach((p) -> pw.print(p.toString() + " "));
                        }
                    }
                    builder.add("-R", "@" + resourceListFile.getAbsolutePath());
                } else {
                    for (File dir : config.getResourceDirs()) {
                        Files.walk(dir.toPath())
                                .filter(Files::isRegularFile)
                                .forEach((p) -> builder.add("-R", p.toString()));
                    }
                }
            } catch (IOException e) {
                throw new AaptException(
                        "Failed to walk paths "
                                + Joiner.on(File.pathSeparatorChar).join(config.getResourceDirs()),
                        e);
            }
        }

        builder.add("--auto-add-overlay");

        // outputs
        if (config.getSourceOutputDir() != null) {
            builder.add("--java", config.getSourceOutputDir().getAbsolutePath());
        }

        if (config.getProguardOutputFile() != null) {
            builder.add("--proguard", config.getProguardOutputFile().getAbsolutePath());
        }

        if (config.getMainDexListProguardOutputFile() != null) {
            builder.add(
                    "--proguard-main-dex",
                    config.getMainDexListProguardOutputFile().getAbsolutePath());
        }

        if (config.getSplits() != null) {
            for (String split : config.getSplits()) {
                String splitter = File.pathSeparator;
                builder.add("--split", resourceOutputApk + "_" + split + splitter + split);
            }
        }

        // options controlled by build variants
        if (config.getVariantType() != VariantType.ANDROID_TEST
                && config.getCustomPackageForR() != null) {
            builder.add("--custom-package", config.getCustomPackageForR());
        }

        // bundle specific options
        boolean generateFinalIds = true;
        if (config.getVariantType() == VariantType.LIBRARY) {
            generateFinalIds = false;
        }
        if (!generateFinalIds) {
            builder.add("--non-final-ids");
        }

        /*
         * Never compress apks.
         */
        builder.add("-0", "apk");

        /*
         * Add custom no-compress extensions.
         */
        Collection<String> noCompressList =
                Objects.requireNonNull(config.getOptions()).getNoCompress();
        if (noCompressList != null) {
            for (String noCompress : noCompressList) {
                builder.add("-0", noCompress);
            }
        }
        List<String> additionalParameters = config.getOptions().getAdditionalParameters();
        if (additionalParameters != null) {
            builder.addAll(additionalParameters);
        }

        List<String> resourceConfigs = new ArrayList<>(config.getResourceConfigs());

        /*
         * Split the density and language resource configs, since starting in 21, the
         * density resource configs should be passed with --preferred-density to ensure packaging
         * of scalable resources when no resource for the preferred density is present.
         */
        Collection<String> densityResourceConfigs = Lists.newArrayList(
                AaptUtils.getDensityResConfigs(resourceConfigs));
        Collection<String> otherResourceConfigs =
                Lists.newArrayList(AaptUtils.getNonDensityResConfigs(resourceConfigs));
        String preferredDensity = config.getPreferredDensity();


        Iterable<String> densityResSplits =
                config.getSplits() != null
                        ? AaptUtils.getDensityResConfigs(config.getSplits())
                        : ImmutableList.of();

        if ((preferredDensity != null || densityResSplits.iterator().hasNext())
                && !densityResourceConfigs.isEmpty()) {
            throw new AaptException(
                    String.format("When using splits in tools 21 and above, "
                                    + "resConfigs should not contain any densities. Right now, it "
                                    + "contains \"%1$s\"\nSuggestion: remove these from resConfigs "
                                    + "from build.gradle",
                            Joiner.on("\",\"").join(densityResourceConfigs)));
        }

        if (densityResourceConfigs.size() > 1) {
            throw new AaptException("Cannot filter assets for multiple densities using "
                    + "SDK build tools 21 or later. Consider using apk splits instead.");
        }

        // if we are in split mode and resConfigs has been specified, we need to add all the
        // non density based splits back to the resConfigs otherwise they will be filtered out.
        if (!otherResourceConfigs.isEmpty() && config.getSplits() != null) {
            Iterable<String> nonDensitySplits =
                    AaptUtils.getNonDensityResConfigs(config.getSplits());
            otherResourceConfigs.addAll(Lists.newArrayList(nonDensitySplits));
        }

        if (preferredDensity == null && densityResourceConfigs.size() == 1) {
            preferredDensity = Iterables.getOnlyElement(densityResourceConfigs);
        }

        if (!otherResourceConfigs.isEmpty()) {
            Joiner joiner = Joiner.on(',');
            builder.add("-c", joiner.join(otherResourceConfigs));
        }

        if (preferredDensity != null) {
            builder.add("--preferred-density", preferredDensity);
        }

        if (config.getSymbolOutputDir() != null
                && (config.getVariantType() == VariantType.LIBRARY
                        || !config.getLibrarySymbolTableFiles().isEmpty())) {
            File rDotTxt = new File(config.getSymbolOutputDir(), "R.txt");
            builder.add("--output-text-symbols", rDotTxt.getAbsolutePath());
        }

        if (config.getPackageId() != null) {
            builder.add("--package-id", "0x" + Integer.toHexString(config.getPackageId()));
            for (File dependentFeature : config.getDependentFeatures()) {
                builder.add("-I", dependentFeature.getAbsolutePath());
            }
        } else if (!config.getDependentFeatures().isEmpty()) {
            throw new AaptException("Dependent features configured but no package ID was set.");
        }

        builder.add("--no-version-vectors");

        if (config.isStaticLibrary()) {
            builder.add("--static-lib");
            if (!config.getStaticLibraryDependencies().isEmpty()) {
                throw new AaptException(
                        "Static libraries to link against should be passed as imports");
            }
        } else {
            for (File file : config.getStaticLibraryDependencies()) {
                builder.add(file.getAbsolutePath());
            }
        }

        return builder.build();
    }
}
