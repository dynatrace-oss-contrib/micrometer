package io.micrometer.dynatrace.types;

public interface DynatraceSnapshotSupport {

    DynatraceSummarySnapshot getSnapshot();

    DynatraceSummarySnapshot getSnapshotAndReset();

}
