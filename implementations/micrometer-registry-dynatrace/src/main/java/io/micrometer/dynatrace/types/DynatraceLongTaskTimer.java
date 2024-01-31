/*
 * Copyright 2023 VMware, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.dynatrace.types;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.internal.DefaultLongTaskTimer;

import java.util.concurrent.TimeUnit;

/**
 * Implementation of the LongTaskTimer that ensures produced data is consistent for
 * exporting to Dynatrace.
 *
 * @author Georg Pirklbauer
 * @since 1.12.2
 */
public class DynatraceLongTaskTimer extends DefaultLongTaskTimer implements DynatraceSummarySnapshotSupport {

    public DynatraceLongTaskTimer(Id id, Clock clock, TimeUnit baseTimeUnit,
            DistributionStatisticConfig distributionStatisticConfig, boolean supportsAggregablePercentiles) {
        super(id, clock, baseTimeUnit, distributionStatisticConfig, supportsAggregablePercentiles);
    }

    /**
     * @deprecated see {@link DynatraceSummarySnapshotSupport#hasValues()}.
     */
    @Override
    @Deprecated
    public boolean hasValues() {
        return activeTasks() > 0;
    }

    @Override
    public DynatraceSummarySnapshot takeSummarySnapshot() {
        return takeSummarySnapshot(baseTimeUnit());
    }

    @Override
    public DynatraceSummarySnapshot takeSummarySnapshot(TimeUnit unit) {
        if (activeTasks() < 1) {
            return DynatraceSummarySnapshot.NO_RECORDED_VALUES;
        }

        DynatraceSummary summary = new DynatraceSummary();

        // iterate active samples and create a Dynatrace summary.
        super.forEachActive(sample -> {
            // sample.duration will return -1 if the task is already finished (only
            // currently active tasks are measured).
            // -1 will be ignored in recordNonNegative.
            summary.recordNonNegative(sample.duration(unit));
        });

        return summary.takeSummarySnapshot();
    }

    @Override
    public DynatraceSummarySnapshot takeSummarySnapshotAndReset() {
        return takeSummarySnapshotAndReset(baseTimeUnit());
    }

    @Override
    public DynatraceSummarySnapshot takeSummarySnapshotAndReset(TimeUnit unit) {
        // LongTaskTimers record a snapshot of in-flight operations, e.g., the number of
        // active requests.
        // Therefore, the Snapshot needs to be created from scratch during the export.
        // In takeSummarySnapshot() above, the Summary object is deleted at the end of the
        // method, therefore effectively resetting the snapshot.
        return takeSummarySnapshot(unit);
    }

}
