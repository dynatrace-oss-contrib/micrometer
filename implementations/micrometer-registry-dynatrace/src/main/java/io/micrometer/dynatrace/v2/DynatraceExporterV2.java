/**
 * Copyright 2017 VMware, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.micrometer.dynatrace.v2;

import com.dynatrace.metric.util.*;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.micrometer.core.instrument.distribution.ValueAtPercentile;
import io.micrometer.core.instrument.util.AbstractPartition;
import io.micrometer.core.ipc.http.HttpSender;
import io.micrometer.dynatrace.AbstractDynatraceExporter;
import io.micrometer.dynatrace.DynatraceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * @author Georg Pirklbauer
 */
public final class DynatraceExporterV2 extends AbstractDynatraceExporter {
    private static final String DEFAULT_ONEAGENT_ENDPOINT = "http://127.0.0.1:14499/metrics/ingest";
    private static final int MAX_BATCH_SIZE = 1000;

    private static final String metricExceptionFormatter = "Could not serialize metric with name %s: %s";
    private static final String illegalArgumentExceptionFormatter = "Illegal value for metric with name %s: %s Dropping...";

    private final String endpoint;
    private final MetricBuilderFactory metricBuilderFactory;

    private static final Logger logger = LoggerFactory.getLogger(DynatraceExporterV2.class.getName());
    private static final Map<String, String> staticDimensions = new HashMap<String, String>() {{
        put("dt.metrics.source", "micrometer");
    }};

    private static final int METRIC_LINE_MAX_LENGTH = 2000;

    public DynatraceExporterV2(DynatraceConfig config, Clock clock, HttpSender httpClient) {
        super(config, clock, httpClient);

        this.endpoint = prepareEndpoint(config.uri());

        MetricBuilderFactory.MetricBuilderFactoryBuilder factoryBuilder = MetricBuilderFactory
                .builder()
                .withPrefix(config.metricKeyPrefix())
                .withDefaultDimensions(parseDefaultDimensions(config.defaultDimensions()));

        if (config.enrichWithOneAgentMetadata()) {
            factoryBuilder.withOneAgentMetadata();
        }

        metricBuilderFactory = factoryBuilder.build();
    }

    static String prepareEndpoint(String uri) {
        String endpoint = DEFAULT_ONEAGENT_ENDPOINT;

        if (!uri.isEmpty()) {
            // ends with "/metrics/ingest" or "/metrics/ingest/"
            if (uri.matches(".+/metrics/ingest/?$")) {
                endpoint = uri;
            } else {
                try {
                    if (uri.contains("localhost") || uri.contains("127.0.0.1")) {
                        // append /metrics/ingest for local endpoints if not already there.
                        endpoint = new URL(new URL(uri), "/metrics/ingest").toString();
                    } else {
                        // append /api/v2/metrics/ingest to the uri if its not already there.
                        endpoint = new URL(new URL(uri), "/api/v2/metrics/ingest").toString();
                    }
                } catch (MalformedURLException e) {
                    logger.warn("Could not parse endpoint url ({}). The export might fail.", uri);
                    endpoint = uri;
                }
            }
        }

        logger.info(String.format("exporting to endpoint %s", endpoint));
        return endpoint;
    }

    private static DimensionList parseDefaultDimensions(Map<String, String> defaultDimensions) {
        Stream<Map.Entry<String, String>> defaultDimensionStream = Stream.empty();

        if (defaultDimensions != null) {
            defaultDimensionStream = defaultDimensions.entrySet().stream();
        }

        // combine static dimensions (for this implementation) and default dimensions passed
        // via the configuration into one stream.
        Stream<Map.Entry<String, String>> concatenated = Stream.concat(
                defaultDimensionStream,
                staticDimensions.entrySet().stream()
        );

        // create dimensions from the combined stream elements.
        List<Dimension> dimensions = concatenated.map(
                (kv) -> Dimension.create(kv.getKey(), kv.getValue())
        ).collect(Collectors.toList());
        // construct a dimensionlist from the combined diemensions.
        return DimensionList.fromCollection(dimensions);
    }

    @Override
    public void export(MeterRegistry registry) {
        // split the lines by whether or not they exceed the line length limit.
        Map<Boolean, List<String>> metricLines =
                registry.getMeters().stream()
                        .flatMap(this::toMetricLines)
                        .collect(Collectors.partitioningBy(DynatraceExporterV2::lineLengthBelowLimit));

        // both keys will be present, even if empty.
        if (!metricLines.get(false).isEmpty()) {
            logger.info("dropping {} lines that exceed the line length limit of {}", metricLines.get(false).size(), METRIC_LINE_MAX_LENGTH);
        }

        sendInBatches(metricLines.get(true));
    }

    private Stream<String> toMetricLines(Meter meter) {
        return meter.match(
                this::toGaugeLine,
                this::toCounterLine,
                this::toTimerLine,
                this::toDistributionSummaryLine,
                this::toLongTaskTimerLine,
                this::toTimeGaugeLine,
                this::toFunctionCounterLine,
                this::toFunctionTimerLine,
                this::toMeterLine
        );
    }

    Stream<String> toGaugeLine(Gauge meter) {
        return toGauge(meter);
    }

    Stream<String> toCounterLine(Counter meter) {
        return toCounter(meter);
    }

    static double minFromHistogramSnapshot(HistogramSnapshot histogramSnapshot, TimeUnit timeUnit) {
        ValueAtPercentile[] valuesAtPercentiles = histogramSnapshot.percentileValues();
        double min = Double.NaN;

        for (ValueAtPercentile valueAtPercentile : valuesAtPercentiles) {
            if (valueAtPercentile.percentile() == 0.0) {
                if (timeUnit == null) {
                    // not a timer value, probably a DistributionSummary
                    min = valueAtPercentile.value();
                } else {
                    min = valueAtPercentile.value(timeUnit);
                }
                break;
            }
        }

        return min;
    }

    Stream<String> toTimerLine(Timer meter) {
        HistogramSnapshot histogramSnapshot = meter.takeSnapshot();
        double total = histogramSnapshot.total(getBaseTimeUnit());
        double max = histogramSnapshot.max(getBaseTimeUnit());

        return toSummaryLine(meter, histogramSnapshot, total, max, true);
    }

    private Stream<String> makeSummaryLine(Meter meter, double min, double max, double total, long count) {
        List<String> serializedLine = new ArrayList<>(1);
        try {
            throwIfValueIsInvalid(max);
            throwIfValueIsInvalid(total);

            serializedLine.add(
                    createMetricBuilder(meter)
                            .setDoubleSummaryValue(min, max, total, count)
                            .serialize());
        } catch (MetricException e) {
            logger.warn(String.format(metricExceptionFormatter, meter.getId().getName(), e.getMessage()));
        } catch (IllegalArgumentException iae) {
            // drop lines containing NaN or Infinity silently.
            logger.debug(String.format(illegalArgumentExceptionFormatter, meter.getId().getName(), iae.getMessage()));
        }

        return streamOf(serializedLine);
    }

    private Stream<String> toSummaryLine(Meter meter, HistogramSnapshot histogramSnapshot, double total, double max, boolean isTimer) {
        long count = histogramSnapshot.count();

        double min;
        if (count == 1) {
            min = max;
        } else {
            if (isTimer) {
                min = minFromHistogramSnapshot(histogramSnapshot, getBaseTimeUnit());
            } else {
                min = minFromHistogramSnapshot(histogramSnapshot, null);
            }
        }

        return makeSummaryLine(meter, min, max, total, count);
    }

    Stream<String> toDistributionSummaryLine(DistributionSummary meter) {
        HistogramSnapshot histogramSnapshot = meter.takeSnapshot();
        double total = histogramSnapshot.total();
        double max = histogramSnapshot.max();

        return toSummaryLine(meter, histogramSnapshot, total, max, false);
    }

    Stream<String> toLongTaskTimerLine(LongTaskTimer meter) {
        HistogramSnapshot histogramSnapshot = meter.takeSnapshot();
        double total = histogramSnapshot.total(getBaseTimeUnit());
        double max = histogramSnapshot.max(getBaseTimeUnit());

        return toSummaryLine(meter, histogramSnapshot, total, max, true);
    }

    Stream<String> toTimeGaugeLine(TimeGauge meter) {
        return toGauge(meter);
    }

    Stream<String> toFunctionCounterLine(FunctionCounter meter) {
        return toCounter(meter);
    }

    Stream<String> toFunctionTimerLine(FunctionTimer meter) {
        double total = meter.totalTime(getBaseTimeUnit());
        double average = meter.mean(getBaseTimeUnit());
        long longCount = Double.valueOf(meter.count()).longValue();

        return makeSummaryLine(meter, average, average, total, longCount);
    }

    Stream<String> toMeterLine(Meter meter) {
        return toGauge(meter);
    }

    private void throwIfValueIsInvalid(Double value) {
        if (value.isNaN()) {
            throw new IllegalArgumentException("Value cannot be NaN.");
        }
        if (value.isInfinite()) {
            throw new IllegalArgumentException("Value cannot be infinite.");
        }
    }

    Stream<String> toGauge(Meter meter) {
        return streamOf(meter.measure()).map(
                measurement -> {
                    try {
                        throwIfValueIsInvalid(measurement.getValue());
                        return createMetricBuilder(meter)
                                .setDoubleGaugeValue(measurement.getValue())
                                .serialize();
                    } catch (MetricException e) {
                        logger.warn(String.format(metricExceptionFormatter, meter.getId().getName(), e.getMessage()));
                    } catch (IllegalArgumentException iae) {
                        // drop lines containing NaN or Infinity silently.
                        logger.debug(String.format(illegalArgumentExceptionFormatter, meter.getId().getName(), iae.getMessage()));
                    }
                    return null;
                })
                .filter(Objects::nonNull);
    }

    Stream<String> toCounter(Meter meter) {
        return streamOf(meter.measure()).map(
                measurement -> {
                    try {
                        throwIfValueIsInvalid(measurement.getValue());
                        return createMetricBuilder(meter)
                                .setDoubleCounterValueDelta(measurement.getValue())
                                .serialize();
                    } catch (MetricException e) {
                        logger.warn(String.format(metricExceptionFormatter, meter.getId().getName(), e.getMessage()));
                    } catch (IllegalArgumentException iae) {
                        // drop lines containing NaN or Infinity silently.
                        logger.debug(String.format(illegalArgumentExceptionFormatter, meter.getId().getName(), iae.getMessage()));
                    }
                    return null;
                })
                .filter(Objects::nonNull);
    }

    private Metric.Builder createMetricBuilder(Meter meter) {
        return metricBuilderFactory
                .newMetricBuilder(meter.getId().getName())
                .setDimensions(fromTags(meter.getId().getTags()))
                .setTimestamp(Instant.ofEpochMilli(clock.wallTime()));
    }

    private static DimensionList fromTags(List<Tag> tags) {
        return DimensionList.fromCollection(
                tags.stream()
                        .map(tag -> Dimension.create(tag.getKey(), tag.getValue()))
                        .collect(Collectors.toList()));
    }

    static <T> Stream<T> streamOf(Iterable<T> iterable) {
        return StreamSupport.stream(iterable.spliterator(), false);
    }

    private void send(List<String> metricLines) {
        try {
            String body = String.join("\n", metricLines);
            if (logger.isDebugEnabled()) {
                logger.debug("sending lines:\n" + body);
            }

            httpClient.post(endpoint)
                    .withHeader("Authorization", "Api-Token " + config.apiToken())
                    .withHeader("Content-Type", "text/plain")
                    .withHeader("User-Agent", "micrometer")
                    .withPlainText(body)
                    .send()
                    .onSuccess((r) -> logger.info("Ingested {} metric lines into Dynatrace, response: {}", metricLines.size(), r.body()))
                    .onError((r) -> logger.error("Failed metric ingestion. code={} response.body={}", r.code(), r.body()));
        } catch (Throwable throwable) {
            logger.error("Failed metric ingestion: {}", throwable.getMessage());
        }
    }

    private void sendInBatches(List<String> metricLines) {
        MetricLinePartition.partition(metricLines)
                .forEach(this::send);
    }

    private static boolean lineLengthBelowLimit(String line) {
        return line.length() <= METRIC_LINE_MAX_LENGTH;
    }

    static class MetricLinePartition extends AbstractPartition<String> {

        MetricLinePartition(List<String> list, int partitionSize) {
            super(list, partitionSize);
        }

        static List<List<String>> partition(List<String> list) {
            return new MetricLinePartition(list, MAX_BATCH_SIZE);
        }
    }
}
