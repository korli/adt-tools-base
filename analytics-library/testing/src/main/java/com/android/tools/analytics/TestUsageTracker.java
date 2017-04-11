package com.android.tools.analytics;

import com.android.annotations.NonNull;
import com.android.testutils.VirtualTimeDateProvider;
import com.android.testutils.VirtualTimeScheduler;
import com.android.utils.DateProvider;
import com.android.utils.FileUtils;
import com.google.common.io.Files;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.wireless.android.play.playlog.proto.ClientAnalytics;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * An implementation of {@link UsageTracker} for use in tests. Allows introspection of the logged
 * usages via {@link #getUsages()}
 */
public class TestUsageTracker extends UsageTracker {

    private final VirtualTimeScheduler scheduler;
    private final List<LoggedUsage> usages = new ArrayList<>();
    private final File androidSdkHomeEnvironment;

    public TestUsageTracker(
            @NonNull AnalyticsSettings settings, @NonNull VirtualTimeScheduler scheduler) {
        super(settings, scheduler);
        this.scheduler = scheduler;
        // in order to ensure reproducible anonymized values & timestamps are reported,
        // set a date provider based on the virtual time scheduler.
        sDateProvider = AnalyticsSettings.sDateProvider = new VirtualTimeDateProvider(scheduler);
        mStartTimeMs = sDateProvider.now().getTime();
        androidSdkHomeEnvironment = Files.createTempDir();
        EnvironmentFakes.setCustomAndroidSdkHomeEnvironment(androidSdkHomeEnvironment.getPath());
    }

    @Override
    public void logDetails(@NonNull ClientAnalytics.LogEvent.Builder logEvent) {
        try {
            usages.add(new LoggedUsage(scheduler.getCurrentTimeNanos(), logEvent.build()));
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException(
                    "Expecting a LogEvent that contains an AndroidStudioEvent proto", e);
        }
    }

    @Override
    public void close() throws Exception {
        // Clean up the virtual time data provider after the test is done.
        UsageTracker.sDateProvider = AnalyticsSettings.sDateProvider = DateProvider.SYSTEM;
        FileUtils.deleteDirectoryContents(androidSdkHomeEnvironment);
        Environment.setInstance(Environment.SYSTEM);
    }

    @NonNull
    public List<LoggedUsage> getUsages() {
        return usages;
    }
}
