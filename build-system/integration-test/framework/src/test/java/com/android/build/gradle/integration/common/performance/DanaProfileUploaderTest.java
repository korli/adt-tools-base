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

package com.android.build.gradle.integration.common.performance;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.*;

import com.android.annotations.Nullable;
import com.android.build.gradle.integration.common.fixture.RandomGradleBenchmark;
import com.android.build.gradle.integration.performance.DanaProfileUploader;
import com.android.build.gradle.integration.performance.DanaProfileUploader.BuildbotResponse;
import com.android.build.gradle.integration.performance.DanaProfileUploader.Infos;
import com.android.build.gradle.integration.performance.DanaProfileUploader.SampleRequest;
import com.android.build.gradle.integration.performance.DanaProfileUploader.SerieRequest;
import com.google.wireless.android.sdk.gradlelogging.proto.Logging.GradleBenchmarkResult;
import com.google.wireless.android.sdk.stats.GradleBuildProfileSpan;
import java.io.IOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;

public class DanaProfileUploaderTest {
    private final String danaBaseUrl = "http://example.com";
    private final String danaProjectId = "test";
    private final String buildbotMasterUrl = "http://example.com";
    private final String buildbotBuilderName = "fred";

    private DanaProfileUploader uploader;

    @Before
    public void setUp() throws IOException {
        BuildbotResponse buildInfo = new BuildbotResponse();
        buildInfo.sourceStamp.changes =
                new DanaProfileUploader.Change[] {new DanaProfileUploader.Change()};
        buildInfo.sourceStamp.changes[0].comments = "comments";
        buildInfo.sourceStamp.changes[0].rev = "00000000000000000000000000000000000000";
        buildInfo.sourceStamp.changes[0].revlink = "http://example.com";
        buildInfo.sourceStamp.changes[0].who = "you@google.com";

        uploader =
                spy(
                        DanaProfileUploader.create(
                                danaBaseUrl,
                                danaProjectId,
                                buildbotMasterUrl,
                                buildbotBuilderName,
                                DanaProfileUploader.Mode.NORMAL));

        doReturn(buildInfo).when(uploader).jsonGet(anyString(), eq(BuildbotResponse.class));
        DanaProfileUploader.clearCache();
    }

    private static void assertValidInfos(@Nullable Infos infos) {
        assertThat(infos).isNotNull();

        assertThat(infos.abbrevHash).isNotEmpty();
        assertThat(infos.abbrevHash).doesNotContain("\n");
        assertThat(infos.abbrevHash).doesNotContain(" ");
        assertThat(infos.abbrevHash.length()).isLessThan(infos.hash.length());

        assertThat(infos.hash).isNotEmpty();
        assertThat(infos.hash).doesNotContain("\n");
        assertThat(infos.hash).doesNotContain(" ");

        assertThat(infos.authorEmail).isNotEmpty();
        assertThat(infos.authorEmail).doesNotContain("\n");
        assertThat(infos.authorEmail).contains("@google.com");

        assertThat(infos.authorName).isNotEmpty();
        assertThat(infos.authorName).doesNotContain("\n");

        assertThat(infos.subject).isNotEmpty();
    }

    @Test
    public void infos() throws ExecutionException {
        assertValidInfos(uploader.infos(1));
    }

    @Test
    public void infosManualTrigger() throws IOException, ExecutionException {
        doReturn(new BuildbotResponse())
                .when(uploader)
                .jsonGet(anyString(), eq(BuildbotResponse.class));
        assertValidInfos(uploader.infos(1));
    }

    @Test
    public void infosTransientNetworkProblem() throws IOException {
        /*
         * Note that doing the throw on jsonGet bypasses the retry mechanisms. This is a bit of a
         * trade-off: we don't want the tests to go through the steps of sleeping and retrying
         * for multiple minutes, but we do want to make sure that these errors propagate.
         */
        doThrow(SocketException.class)
                .when(uploader)
                .jsonGet(anyString(), eq(BuildbotResponse.class));
        try {
            uploader.infos(1);
            fail("uploader.infos(1) should have thrown an exception");
        } catch (ExecutionException e) {
            assertThat(e.getCause()).isInstanceOf(SocketException.class);
        }

        doThrow(SocketTimeoutException.class)
                .when(uploader)
                .jsonGet(anyString(), eq(BuildbotResponse.class));
        try {
            uploader.infos(1);
            fail("uploader.infos(1) should have thrown an exception");
        } catch (ExecutionException e) {
            assertThat(e.getCause()).isInstanceOf(SocketTimeoutException.class);
        }
    }

    @Test
    public void flags() {
        GradleBenchmarkResult gbr = RandomGradleBenchmark.randomBenchmarkResult();

        // Make sure that we get the same result for the same object passed in multiple times.
        assertThat(DanaProfileUploader.flags(gbr)).isEqualTo(DanaProfileUploader.flags(gbr));
    }

    @Test
    public void seriesId() {
        GradleBenchmarkResult gbr = RandomGradleBenchmark.randomBenchmarkResult();

        assertThat(gbr.getProfile()).isNotNull();
        assertThat(gbr.getProfile().getSpanList()).isNotEmpty();

        for (GradleBuildProfileSpan span : gbr.getProfile().getSpanList()) {
            assertThat(uploader.seriesId(gbr, span)).isNotEmpty();

            // Make sure that we get the same result for the same object passed in multiple times.
            assertThat(uploader.seriesId(gbr, span)).isEqualTo(uploader.seriesId(gbr, span));
        }
    }

    @Test
    public void description() {
        GradleBenchmarkResult gbr = RandomGradleBenchmark.randomBenchmarkResult();

        assertThat(gbr.getProfile()).isNotNull();
        assertThat(gbr.getProfile().getSpanList()).isNotEmpty();

        for (GradleBuildProfileSpan span : gbr.getProfile().getSpanList()) {
            assertThat(DanaProfileUploader.description(gbr, span)).isNotNull();
        }
    }

    @Test
    public void sampleRequests() {
        Collection<SampleRequest> reqs =
                uploader.sampleRequests(RandomGradleBenchmark.randomBenchmarkResults());
        assertThat(reqs).isNotEmpty();

        for (DanaProfileUploader.SampleRequest req : reqs) {
            // make sure the various IDs make sense
            assertThat(req.projectId).isNotEmpty();

            for (DanaProfileUploader.Sample sample : req.samples) {
                // act-d baulks on 0 values, so we should not return them from sampleRequests()
                assertThat(sample.value).isGreaterThan(0L);

                assertThat(sample.buildId).isGreaterThan(0L);
                assertThat(sample.url).isNotNull();
            }
        }
    }

    @Test
    public void sampleRequestsEmpty() {
        Collection<SampleRequest> reqs = uploader.sampleRequests(Arrays.asList());
        assertThat(reqs).isEmpty();
    }

    @Test
    public void buildRequest() throws ExecutionException {
        DanaProfileUploader.BuildRequest buildRequest = uploader.buildRequest(1);

        assertThat(buildRequest.build).isNotNull();
        assertThat(buildRequest.build.buildId).isGreaterThan(0L);
        assertThat(buildRequest.projectId).isNotEmpty();

        assertValidInfos(buildRequest.build.infos);
    }

    @Test
    public void serieRequests() {
        Collection<SampleRequest> sampleRequests =
                uploader.sampleRequests(RandomGradleBenchmark.randomBenchmarkResults());
        assertThat(sampleRequests).isNotEmpty();

        Collection<SerieRequest> serieRequests = uploader.serieRequests(sampleRequests);
        assertThat(serieRequests).isNotEmpty();

        for (SerieRequest serieRequest : serieRequests) {
            assertThat(serieRequest.projectId).isNotEmpty();
            assertThat(serieRequest.serieId).isNotEmpty();
        }

        long numIds =
                serieRequests
                        .stream()
                        .map(s -> s.serieId)
                        .distinct()
                        .collect(Collectors.counting());
        assertThat(numIds).isEqualTo(serieRequests.size()); // all series should have a unique id
    }
}
