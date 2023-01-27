package io.micrometer.dynatrace.types;

import java.util.concurrent.TimeUnit;

public interface DynatraceTimerSnapshotSupport {

    DynatraceSummarySnapshot getSnapshot(TimeUnit unit);

    DynatraceSummarySnapshot getSnapshotAndReset(TimeUnit unit);
}
