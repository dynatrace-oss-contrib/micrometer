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

import io.micrometer.core.instrument.AbstractMeter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.concurrent.TimeUnit;

public class DynatraceDistributionSummary extends AbstractMeter implements DistributionSummary, DynatraceSummarySnapshotSupport {
    private final DynatraceSummary summary = new DynatraceSummary();
    private static final Logger LOGGER = LoggerFactory.getLogger(DynatraceDistributionSummary.class.getName());

    public DynatraceDistributionSummary(Id id) {
        super(id);
    }

    @Override
    public void record(double amount) {
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

    @Override
    public HistogramSnapshot takeSnapshot() {
        LOGGER.warn("Called takeSnapshot on a Dynatrace Distribution Summary, no percentiles will be exported.");
        DynatraceSummarySnapshot dtSnapshot = takeSummarySnapshot();
        return HistogramSnapshot.empty(dtSnapshot.getCount(), dtSnapshot.getTotal(), dtSnapshot.getMax());
    }
}
