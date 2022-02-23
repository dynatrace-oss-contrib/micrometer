/*
 * Copyright 2022 VMware, Inc.
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

import io.micrometer.core.instrument.AbstractTimer;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import io.micrometer.core.lang.NonNull;

import java.util.concurrent.TimeUnit;

public class DynatraceTimer extends AbstractTimer implements DynatraceSummarySnapshotSupport {
    private final DynatraceSummary summary = new DynatraceSummary();

    public DynatraceTimer(Id id, Clock clock, DistributionStatisticConfig distributionStatisticConfig, PauseDetector pauseDetector, TimeUnit baseTimeUnit, boolean supportsAggregablePercentiles) {
        super(id, clock, distributionStatisticConfig, pauseDetector, baseTimeUnit, supportsAggregablePercentiles);
    }

    @Override
    public DynatraceSummarySnapshot takeSummarySnapshot() {
        return takeSummarySnapshot(baseTimeUnit());
    }

    @Override
    public DynatraceSummarySnapshot takeSummarySnapshot(TimeUnit unit) {
        return convertSnapshotToUnit(summary.takeSummarySnapshot(), baseTimeUnit(), unit);
    }


    @Override
    public DynatraceSummarySnapshot takeSummarySnapshotAndReset() {
        return takeSummarySnapshot(baseTimeUnit());
    }

    @Override
    public DynatraceSummarySnapshot takeSummarySnapshotAndReset(TimeUnit unit) {
        DynatraceSummarySnapshot snapshot;
        synchronized (this) {
            snapshot = takeSummarySnapshot(unit);
            summary.reset();
        }
        return snapshot;
    }

    @Override
    protected void recordNonNegative(long amount, @NonNull TimeUnit unit) {
        // store everything in baseTimeUnit
        long inBaseUnit = baseTimeUnit().convert(amount, unit);
        summary.recordNonNegative(inBaseUnit);
    }

    @Override
    public long count() {
        return summary.getCount();
    }

    @Override
    public double totalTime(TimeUnit unit) {
        return unit.convert((long) summary.getTotal(), baseTimeUnit());
    }

    @Override
    public double max(TimeUnit unit) {
        return unit.convert((long) summary.getMax(), baseTimeUnit());
    }

    public double min(TimeUnit unit) {
        return unit.convert((long) summary.getMin(), baseTimeUnit());
    }

    private static DynatraceSummarySnapshot convertSnapshotToUnit(DynatraceSummarySnapshot snapshot, TimeUnit sourceUnit, TimeUnit targetUnit) {
        if (targetUnit == sourceUnit) {
            return snapshot;
        }

        // convert to the requested unit
        return new DynatraceSummarySnapshot(
                targetUnit.convert((long) snapshot.getMin(), sourceUnit),
                targetUnit.convert((long) snapshot.getMax(), sourceUnit),
                targetUnit.convert((long) snapshot.getTotal(), sourceUnit),
                snapshot.getCount()
        );
    }
}
