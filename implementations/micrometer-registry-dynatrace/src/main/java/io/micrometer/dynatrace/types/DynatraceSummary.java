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

/**
 * Internal class for resettable summary statistics.
 *
 * @author Georg Pirklbauer
 */
final class DynatraceSummary {

    private long count;

    private double total;

    private double min;

    private double max;

    void recordNonNegative(double amount) {
        if (amount < 0) {
            return;
        }

        synchronized (this) {
            max = Math.max(max, amount);
            min = count > 0 ? Math.min(min, amount) : amount;
            total += amount;
            count++;
        }
    }

    long getCount() {
        return count;
    }

    double getTotal() {
        return total;
    }

    double getMin() {
        return min;
    }

    double getMax() {
        return max;
    }

    DynatraceSummarySnapshot takeSummarySnapshot() {
        synchronized (this) {
            return new DynatraceSummarySnapshot(getMin(), getMax(), getTotal(), getCount());
        }
    }

    DynatraceSummarySnapshot takeSummarySnapshotAndReset() {
        synchronized (this) {
            DynatraceSummarySnapshot snapshot = takeSummarySnapshot();
            reset();
            return snapshot;
        }
    }

    void reset() {
        synchronized (this) {
            min = 0.;
            max = 0.;
            total = 0.;
            count = 0;
        }
    }

}
