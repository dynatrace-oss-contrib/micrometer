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

public class DynatraceSummary {
    private long count = 0;
    private double total = 0.;
    private double min = 0.;
    private double max = 0.;

    protected synchronized void recordNonNegative(double amount) {
        if (amount >= 0) {
            if (count == 0) {
                min = amount;
                max = amount;
            } else {
                min = Math.min(min, amount);
                max = Math.max(max, amount);
            }
            count++;
            total += amount;
        }
    }

    public long getCount() {
        return count;
    }

    public double getTotal() {
        return total;
    }

    public double getMin() {
        return min;
    }

    public double getMax() {
        return max;
    }

    synchronized void reset() {
        min = 0.;
        max = 0.;
        total = 0.;
        count = 0;
    }

    public synchronized DynatraceSummarySnapshot takeSummarySnapshot() {
        return new DynatraceSummarySnapshot(min, max, total, count);
    }

    public synchronized DynatraceSummarySnapshot takeSummarySnapshotAndReset() {
        DynatraceSummarySnapshot snapshot = takeSummarySnapshot();
        this.reset();
        return snapshot;
    }
}
