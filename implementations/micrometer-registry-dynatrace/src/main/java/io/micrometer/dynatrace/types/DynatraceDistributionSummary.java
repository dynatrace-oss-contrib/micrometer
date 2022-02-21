package io.micrometer.dynatrace.types;

import io.micrometer.core.instrument.AbstractDistributionSummary;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.LongAdder;

public class DynatraceDistributionSummary extends AbstractDistributionSummary {
    private final LongAdder count = new LongAdder();
    private final DoubleAdder total = new DoubleAdder();
    private final AtomicLong min = new AtomicLong(0);
    private final AtomicLong max = new AtomicLong(0);

    public DynatraceDistributionSummary(Id id, Clock clock, DistributionStatisticConfig distributionStatisticConfig, double scale, boolean supportsAggregablePercentiles) {
        super(id, clock, distributionStatisticConfig, scale, supportsAggregablePercentiles);
    }

    @Override
    protected void recordNonNegative(double amount) {
        synchronized (this) {
            if (amount >= 0) {
                long currCount = count.longValue();
                long longBits = Double.doubleToLongBits(amount);
                if (currCount == 0) {
                    // if the count in the current time interval is 0, this value is the first recorded value in the
                    // interval. Therefore, set the value as min and max always.
                    max.set(longBits);
                    min.set(longBits);
                } else {
                    // otherwise, check if the values are bigger or smaller than the ones set already and update
                    // accordingly.
                    //                    equivalent to Math.max
                    max.getAndUpdate(prev -> (prev < longBits) ? longBits : prev);
                    //                    equivalent to Math.min
                    min.getAndUpdate(prev -> (prev < longBits) ? prev : longBits);
                }
                count.increment();
                total.add(amount);
            }
        }
    }

    public long count() {
        return count.longValue();
    }

    public double totalAmount() {
        return total.doubleValue();
    }

    public double max() {
        return Double.longBitsToDouble(max.get());
    }

    public double min() {
        return Double.longBitsToDouble(min.get());
    }

    public synchronized void reset() {
        min.set(0);
        max.set(0);
        total.reset();
        count.reset();
    }

    public synchronized DynatraceHistogramSnapshot takeSnapshotAndReset() {
        DynatraceHistogramSnapshot snapshot = new DynatraceHistogramSnapshot(min(), max(), totalAmount(), count());
        this.reset();
        return snapshot;
    }
}
