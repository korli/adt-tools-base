package com.android.build.gradle.integration.application;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.builder.core.VariantType.ANDROID_TEST;
import static com.android.builder.model.AndroidProject.ARTIFACT_ANDROID_TEST;

import com.android.build.gradle.integration.common.fixture.GetAndroidModelAction;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.ModelHelper;
import com.android.build.gradle.integration.common.utils.SourceProviderHelper;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.ProductFlavorContainer;
import com.android.builder.model.SourceProviderContainer;
import com.android.builder.model.Variant;
import com.android.ide.common.process.ProcessException;
import com.android.utils.StringHelper;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import org.junit.Rule;
import org.junit.Test;

/** Assemble tests for basicMultiFlavors */
public class BasicMultiFlavorTest {

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestProject("basicMultiFlavors").create();

    @Test
    public void checkSourceProviders() throws IOException {
        GetAndroidModelAction.ModelContainer<AndroidProject> modelContainer =
                project.model().getSingle();
        AndroidProject model = modelContainer.getOnlyModel();
        File projectDir = project.getTestDir();
        ModelHelper.testDefaultSourceSets(model, projectDir);

        // test the source provider for the flavor
        Collection<ProductFlavorContainer> productFlavors = model.getProductFlavors();
        assertThat(productFlavors).hasSize(4);

        for (ProductFlavorContainer pfContainer : productFlavors) {
            String name = pfContainer.getProductFlavor().getName();
            new SourceProviderHelper(
                            model.getName(), projectDir, name, pfContainer.getSourceProvider())
                    .test();

            // Unit tests and android tests.
            assertThat(pfContainer.getExtraSourceProviders()).hasSize(2);
            SourceProviderContainer container =
                    ModelHelper.getSourceProviderContainer(
                            pfContainer.getExtraSourceProviders(), ARTIFACT_ANDROID_TEST);

            new SourceProviderHelper(
                            model.getName(),
                            projectDir,
                            ANDROID_TEST.getPrefix() + StringHelper.capitalize(name),
                            container.getSourceProvider())
                    .test();
        }

        // test the source provider for the artifacts
        for (Variant variant : model.getVariants()) {
            AndroidArtifact artifact = variant.getMainArtifact();
            assertThat(artifact.getVariantSourceProvider()).isNotNull();
            assertThat(artifact.getMultiFlavorSourceProvider()).isNotNull();
        }

    }

    @Test
    public void checkPrecedenceForMultiFlavor() throws IOException, InterruptedException {
        project.execute("assembleFreeBetaDebug");

        // Make sure "beta" overrides "free" and "defaultConfig".
        assertThat(project.getApk(GradleTestProject.ApkType.DEBUG, "free", "beta"))
                .hasMaxSdkVersion(18);

        // Make sure the suffixes are applied in the right order.
        assertThat(project.getApk(GradleTestProject.ApkType.DEBUG, "free", "beta"))
                .hasVersionName("com.example.default.free.beta.debug");
    }

    @Test
    public void checkResValueAndManifestPlaceholders() throws IOException, InterruptedException {
        addResValuesAndPlaceholders();
        GetAndroidModelAction.ModelContainer<AndroidProject> model =
                project.executeAndReturnModel("assembleFreeBetaDebug");

        Variant variant =
                ModelHelper.findVariantByName(model.getOnlyModel().getVariants(), "freeBetaDebug");

        assertThat(variant.getMergedFlavor().getResValues().get("VALUE_DEBUG").getValue())
                .isEqualTo("10"); // Value from "beta".

        assertThat(variant.getMergedFlavor().getManifestPlaceholders().get("holder"))
                .isEqualTo("free");
    }

    @Test
    public void checkResourcesResolution()
            throws IOException, InterruptedException, ProcessException {
        project.execute("assembleFreeBetaDebug");
        assertThat(project.getApk(GradleTestProject.ApkType.DEBUG, "free", "beta"))
                .containsResource("drawable/free.png");
    }

    private void addResValuesAndPlaceholders() throws IOException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "android {\n"
                        + "    productFlavors {\n"
                        + "        free {\n"
                        + "            resValue \"string\", \"VALUE_DEBUG\",   \"10\"\n"
                        + "            manifestPlaceholders = [\"holder\":\"free\"]\n"
                        + "        }\n"
                        + "        beta {\n"
                        + "            resValue \"string\", \"VALUE_DEBUG\",   \"13\"\n"
                        + "            manifestPlaceholders = [\"holder\":\"beta\"]\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");
    }
}
