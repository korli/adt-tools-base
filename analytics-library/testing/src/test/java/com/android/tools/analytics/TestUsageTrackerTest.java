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
package com.android.tools.analytics;

import static org.junit.Assert.*;

import com.android.testutils.VirtualTimeScheduler;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

/** Tests for {@link TestUsageTracker}. */
public class TestUsageTrackerTest {
    @Test
    public void testUsageTrackerTest() {
        // first ensure our default is the NullUsageTracker.
        UsageTracker tracker = UsageTracker.getInstance();
        assertEquals(NullUsageTracker.class, tracker.getClass());

        // create settings & scheduler
        AnalyticsSettings settings = new AnalyticsSettings();
        VirtualTimeScheduler scheduler = new VirtualTimeScheduler();

        // advance time to ensure we have a uptime different from reorted time.
        scheduler.advanceBy(1, TimeUnit.MILLISECONDS);

        // create the test usage tracker and set the global instance.
        TestUsageTracker testUsageTracker = new TestUsageTracker(settings, scheduler);
        UsageTracker.setInstanceForTest(testUsageTracker);

        // ensure the global instance is the one we just set.
        tracker = UsageTracker.getInstance();
        assertEquals(testUsageTracker, tracker);

        // move time forward to ensure the report time is different from start time.
        scheduler.advanceBy(1, TimeUnit.MILLISECONDS);

        // log an event
        tracker.log(
                AndroidStudioEvent.newBuilder().setKind(AndroidStudioEvent.EventKind.META_METRICS));

        // ensure that that event is what our test usage tracker rports.
        assertEquals(1, testUsageTracker.getUsages().size());
        LoggedUsage usage = testUsageTracker.getUsages().get(0);
        assertEquals(AndroidStudioEvent.EventKind.META_METRICS, usage.getStudioEvent().getKind());

        // ensure that virtual time has moved as we instructed.
        assertEquals(TimeUnit.MILLISECONDS.toNanos(2), usage.getTimestamp());
        assertEquals(2, usage.getLogEvent().getEventTimeMs());
        assertEquals(1, usage.getLogEvent().getEventUptimeMs());

        // ensure that cleaning the instance puts us back in the initial state.
        UsageTracker.cleanAfterTesting();
        tracker = UsageTracker.getInstance();
        assertEquals(NullUsageTracker.class, tracker.getClass());
    }
}
