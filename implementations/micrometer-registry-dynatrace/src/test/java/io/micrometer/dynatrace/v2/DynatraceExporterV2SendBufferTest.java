package io.micrometer.dynatrace.v2;

import com.dynatrace.metric.util.MetricException;
import com.dynatrace.metric.util.MetricLineBuilder;
import io.micrometer.core.ipc.http.HttpSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.*;

class DynatraceExporterV2SendBufferTest {

    private final HttpSender mockSender = spy(HttpSender.class);
    private static final String testEndpoint = "http://localhost:14499";
    private static final String testToken = "testToken";
    private static final Instant testTime = Instant.ofEpochMilli(1730415600000L);

    @BeforeEach
    void setUp() {
        reset(mockSender);
    }

    @Test
    void testAddLines_willSendAtBatchSize() {
        DynatraceExporterV2SendBuffer sb = new DynatraceExporterV2SendBuffer(testEndpoint, testToken, mockSender, 3, 1048576);

        // send 7 lines, send buffer only holds 3 per request
        provideMetricLines(7, 3).forEach(sb::putMetricLine);

        // first two exports, triggered by the batch size being exceeded.
        verify(mockSender, times(2)).post(testEndpoint);

        // after flushing the remaining lines, the total number of exports is 3
        sb.flush();
        verify(mockSender, times(3)).post(testEndpoint);
    }


    private static List<String> provideMetricLines(int numberOfLines, int numberOfDimensions) {
        List<String> metricLines = new ArrayList<>();
        try {
            for (int i = 0; i < numberOfLines; i++) {
                MetricLineBuilder.TypeStep lb = MetricLineBuilder.create().metricKey("metric.key." + i);
                for (int j = 0; j < numberOfDimensions; j++) {
                    lb.dimension("key" + j, "value" + j);
                }
                metricLines.add(lb.gauge().value(i).timestamp(testTime).build());
            }
        } catch (MetricException e) {
            fail("failed to build metric line");
        }
        return metricLines;
    }

}
