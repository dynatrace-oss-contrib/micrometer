package io.micrometer.dynatrace.types;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tags;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class DynatraceDistributionSummaryTest {
    private static final Offset<Double> OFFSET = Offset.offset(0.0001);
    private static final Meter.Id ID = new Meter.Id("test.id", Tags.empty(), "1", "desc", Meter.Type.DISTRIBUTION_SUMMARY);

    @Test
    void testHasNewValues() {
        DynatraceDistributionSummary ds = new DynatraceDistributionSummary(ID);
        assertThat(ds.hasNewValues()).isFalse();
        ds.record(3.14);
        assertThat(ds.hasNewValues()).isTrue();

        // reset, hasNewValues should be initially false
        ds.takeSummarySnapshotAndReset();
        assertThat(ds.hasNewValues()).isFalse();

        // add invalid value, hasNewValue stays false
        ds.record(-1.234);
        assertThat(ds.hasNewValues()).isFalse();
    }

    @Test
    void testDynatraceDistributionSummary() {
        DynatraceDistributionSummary ds = new DynatraceDistributionSummary(ID);
        ds.record(3.14);
        ds.record(4.76);

        assertMinMaxSumCount(ds, 3.14, 4.76, 7.9, 2);
    }

    @Test
    void testRecordNegativeIgnored() {
        DynatraceDistributionSummary ds = new DynatraceDistributionSummary(ID);
        ds.record(3.14);
        ds.record(-1.234);
        ds.record(4.76);
        ds.record(-6.789);

        assertMinMaxSumCount(ds, 3.14, 4.76, 7.9, 2);
    }

    @Test
    void testMinMaxAreOverwritten() {
        DynatraceDistributionSummary ds = new DynatraceDistributionSummary(ID);
        ds.record(3.14);
        ds.record(4.76);
        ds.record(0.123);
        ds.record(8.93);

        assertMinMaxSumCount(ds, 0.123, 8.93, 16.953, 4);
    }


    @Test
    void testGetSnapshotNoReset() {
        DynatraceDistributionSummary ds = new DynatraceDistributionSummary(ID);
        ds.record(3.14);
        ds.record(4.76);

        assertMinMaxSumCount(ds.takeSummarySnapshot(), 3.14, 4.76, 7.9, 2);
        // run twice to make sure its not reset in between
        assertMinMaxSumCount(ds.takeSummarySnapshot(), 3.14, 4.76, 7.9, 2);
    }

    @Test
    void testGetSnapshotNoResetWithTimeUnitIgnored() {
        DynatraceDistributionSummary ds = new DynatraceDistributionSummary(ID);
        ds.record(3.14);
        ds.record(4.76);

        assertMinMaxSumCount(ds.takeSummarySnapshot(TimeUnit.MINUTES), 3.14, 4.76, 7.9, 2);
        // run twice to make sure its not reset in between
        assertMinMaxSumCount(ds.takeSummarySnapshot(TimeUnit.MINUTES), 3.14, 4.76, 7.9, 2);
    }


    @Test
    void testGetSnapshotAndReset() {
        DynatraceDistributionSummary ds = new DynatraceDistributionSummary(ID);
        ds.record(3.14);
        ds.record(4.76);

        assertMinMaxSumCount(ds.takeSummarySnapshotAndReset(), 3.14, 4.76, 7.9, 2);
        // run twice to make sure its not reset in between
        assertMinMaxSumCount(ds.takeSummarySnapshotAndReset(), 0d, 0d, 0d, 0);
    }

    @Test
    void testGetSnapshotAndResetWithTimeUnitIgnored() {
        DynatraceDistributionSummary ds = new DynatraceDistributionSummary(ID);
        ds.record(3.14);
        ds.record(4.76);

        assertMinMaxSumCount(ds.takeSummarySnapshotAndReset(TimeUnit.MINUTES), 3.14, 4.76, 7.9, 2);
        // run twice to make sure its not reset in between
        assertMinMaxSumCount(ds.takeSummarySnapshotAndReset(TimeUnit.MINUTES), 0d, 0d, 0d, 0);
    }

    private void assertMinMaxSumCount(DynatraceDistributionSummary ds, double expMin, double expMax, double expTotal, long expCount) {
        assertThat(ds.min()).isCloseTo(expMin, OFFSET);
        assertThat(ds.max()).isCloseTo(expMax, OFFSET);
        assertThat(ds.totalAmount()).isCloseTo(expTotal, OFFSET);
        assertThat(ds.count()).isEqualTo(expCount);
    }

    private void assertMinMaxSumCount(DynatraceSummarySnapshot snapshot, double expMin, double expMax, double expTotal, long expCount) {
        assertThat(snapshot.getMin()).isCloseTo(expMin, OFFSET);
        assertThat(snapshot.getMax()).isCloseTo(expMax, OFFSET);
        assertThat(snapshot.getTotal()).isCloseTo(expTotal, OFFSET);
        assertThat(snapshot.getCount()).isEqualTo(expCount);
    }
}
