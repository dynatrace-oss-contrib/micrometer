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
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

public class DynatraceTimer extends AbstractTimer {
    private final LongAdder count = new LongAdder();
    private final LongAdder total = new LongAdder();
    private final AtomicLong max = new AtomicLong(0);
    private final AtomicLong min = new AtomicLong(0);

    public DynatraceTimer(Id id, Clock clock, DistributionStatisticConfig distributionStatisticConfig, PauseDetector pauseDetector, TimeUnit baseTimeUnit, boolean supportsAggregablePercentiles) {
        super(id, clock, distributionStatisticConfig, pauseDetector, baseTimeUnit, supportsAggregablePercentiles);
    }

    @Override
    protected void recordNonNegative(final long amount, @NonNull final TimeUnit unit) {
        if (amount >= 0) {
            long currCount = count.longValue();
            long amountInBaseUnit = baseTimeUnit().convert(amount, unit);

            if (currCount == 0) {
                max.set(amountInBaseUnit);
                min.set(amountInBaseUnit);
            } else {
                max.getAndUpdate(prev -> (prev < amountInBaseUnit) ? amountInBaseUnit : prev);
                min.getAndUpdate(prev -> (prev < amountInBaseUnit) ? prev : amountInBaseUnit);
            }
            count.increment();
            total.add(amountInBaseUnit);
        }
    }


    public long count() {
        return count.longValue();
    }

    @Override
    public double totalTime(final TimeUnit unit) {
        return unit.convert(total.sum(), baseTimeUnit());
    }

    @Override
    public double max(final TimeUnit unit) {
        return unit.convert(max.get(), baseTimeUnit());
    }

    public double min(final TimeUnit unit) {
        return unit.convert(min.get(), baseTimeUnit());
    }

    public synchronized void reset() {
        min.set(0);
        max.set(0);
        total.reset();
        count.reset();
    }

    public synchronized DynatraceHistogramSnapshot takeSnapshotAndReset(final TimeUnit unit) {
        DynatraceHistogramSnapshot snapshot = new DynatraceHistogramSnapshot(min(unit), max(unit), totalTime(unit), count());
        this.reset();
        return snapshot;
    }
}
