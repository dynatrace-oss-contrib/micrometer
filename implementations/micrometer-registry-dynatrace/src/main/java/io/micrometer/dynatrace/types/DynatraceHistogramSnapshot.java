package io.micrometer.dynatrace.types;

public class DynatraceHistogramSnapshot {
    double min;
    double max;
    double total;
    long count;

    public DynatraceHistogramSnapshot(double min, double max, double total, long count) {
        this.min = min;
        this.max = max;
        this.total = total;
        this.count = count;
    }

    public double getMin() {
        return min;
    }

    public double getMax() {
        return max;
    }

    public double getTotal() {
        return total;
    }

    public long getCount() {
        return count;
    }
}

