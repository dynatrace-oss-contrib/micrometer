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

import io.micrometer.core.instrument.AbstractDistributionSummary;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public class DynatraceDistributionSummary extends AbstractDistributionSummary implements DynatraceSummarySnapshotSupport {
    private final DynatraceSummary summary = new DynatraceSummary();
    private static final Logger LOGGER = LoggerFactory.getLogger(DynatraceDistributionSummary.class.getName());

    public DynatraceDistributionSummary(Id id, Clock clock, DistributionStatisticConfig distributionStatisticConfig, double scale, boolean supportsAggregablePercentiles) {
        super(id, clock, distributionStatisticConfig, scale, supportsAggregablePercentiles);
    }

    @Override
    protected void recordNonNegative(double amount) {
        summary.recordNonNegative(amount);
    }

    @Override
    public long count() {
        return summary.getCount();
    }

    @Override
    public double totalAmount() {
        return summary.getTotal();
    }

    @Override
    public double max() {
        return summary.getMax();
    }

    public double min() {
        return summary.getMin();
    }

    @Override
    public DynatraceSummarySnapshot takeSummarySnapshot() {
        return summary.takeSummarySnapshot();
    }

    @Override
    public DynatraceSummarySnapshot takeSummarySnapshot(TimeUnit timeUnit) {
        LOGGER.debug("Called takeSummarySnapshot with a TimeUnit on a DistributionSummary. Ignoring TimeUnit.");
        return takeSummarySnapshot();
    }

    @Override
    public DynatraceSummarySnapshot takeSummarySnapshotAndReset() {
        return summary.takeSummarySnapshotAndReset();
    }

    @Override
    public DynatraceSummarySnapshot takeSummarySnapshotAndReset(TimeUnit unit) {
        LOGGER.debug("Called takeSummarySnapshot with a TimeUnit on a DistributionSummary. Ignoring TimeUnit.");
        return takeSummarySnapshotAndReset();
    }
}
