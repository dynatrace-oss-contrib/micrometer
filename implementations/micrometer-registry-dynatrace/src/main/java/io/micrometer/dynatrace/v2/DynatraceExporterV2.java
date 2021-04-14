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
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * @author Georg Pirklbauer
 */
public class DynatraceExporterV2 extends AbstractDynatraceExporter {
    private final String endpoint;
    private final MetricBuilderFactory metricBuilderFactory;
    private final Logger logger = LoggerFactory.getLogger(DynatraceExporterV2.class.getName());
    private static final Map<String, String> staticDimensions = new HashMap<String, String>() {{
        put("dt.metrics.source", "micrometer");
    }};

    private static final int METRIC_LINE_MAX_LENGTH = 2000;

    public DynatraceExporterV2(DynatraceConfig config, Clock clock, HttpSender httpClient) {
        super(config, clock, httpClient);

        this.endpoint = prepareEndpoint(config);
        logger.debug(String.format("exporting to endpoint %s", endpoint));

        MetricBuilderFactory.MetricBuilderFactoryBuilder factoryBuilder = MetricBuilderFactory
                .builder()
                .withPrefix(config.metricKeyPrefix());

        if (config.defaultDimensions() != null) {
            factoryBuilder.withDefaultDimensions(parseDefaultDimensions(config.defaultDimensions()));
        }

        if (config.enrichWithOneAgentMetadata()) {
            factoryBuilder.withOneAgentMetadata();
        }

        metricBuilderFactory = factoryBuilder.build();
    }

    private String prepareEndpoint(DynatraceConfig config) {
        String endpoint;

        if (config.uri().matches("/metrics/ingest/?$")) {
            endpoint = config.uri();
        } else {
            try {
                endpoint = new URL(new URL(config.uri()), "/api/v2/metrics/ingest").toString();
            } catch (MalformedURLException e) {
                logger.warn("could not parse endpoint url. Falling back to local OneAgent endpoint.");
                endpoint = "http://127.0.0.1:14499/metrics/ingest";
            }
        }
        return endpoint;
    }

    private DimensionList parseDefaultDimensions(Map<String, String> defaultDimensions) {
        if (defaultDimensions == null || defaultDimensions.isEmpty()) {
            return DimensionList.create(Dimension.create("dt.metrics.source", "micrometer"));
        }

        // add the dynatrace default dimension
        Stream<Map.Entry<String, String>> concatenated = Stream.concat(
                defaultDimensions.entrySet().stream(),
                staticDimensions.entrySet().stream()
        );

        return DimensionList.fromCollection(concatenated.map(
                (kv) -> Dimension.create(kv.getKey(), kv.getValue())
        ).collect(Collectors.toList()));
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

    double minFromHistogramSnapshot(HistogramSnapshot histogramSnapshot) {
        ValueAtPercentile[] valueAtPercentiles = histogramSnapshot.percentileValues();
        double min = Double.NaN;
        for (ValueAtPercentile valueAtPercentile : valueAtPercentiles) {
            if (valueAtPercentile.percentile() == 0.0) {
                min = valueAtPercentile.value();
            }
        }
        return min;
    }

    Stream<String> toTimerLine(Timer meter) {
        HistogramSnapshot histogramSnapshot = meter.takeSnapshot();
        double total = histogramSnapshot.total(getBaseTimeUnit());
        double max = histogramSnapshot.max(getBaseTimeUnit());

        return toSummaryLine(meter, histogramSnapshot, total, max);
    }

    private Stream<String> toSummaryLine(Meter meter, HistogramSnapshot histogramSnapshot, double total, double max) {
        long count = histogramSnapshot.count();

        double min;
        if (count == 1) {
            min = max;
        } else {
            min = minFromHistogramSnapshot(histogramSnapshot);
        }

        if (Double.isNaN(min)) {
            logger.warn("0% quantile disabled, could not determine minimum value.");
            return Stream.empty();
        }

        List<String> serializedLine = new ArrayList<>(1);
        try {
            throwIfValueIsInvalid(max);
            throwIfValueIsInvalid(total);

            serializedLine.add(
                    createMetricBuilder(meter, meter.getId().getName())
                            .setDoubleSummaryValue(min, max, total, count)
                            .serialize());
        } catch (MetricException e) {
            logger.warn("Could not serialize metric with name {}", meter.getId().getName());
        } catch (IllegalArgumentException iae) {
            logger.warn(String.format("Illegal value for metric with name %s: %s Dropping...", meter.getId().getName(), iae.getMessage()));
        }

        return streamOf(serializedLine);
    }

    Stream<String> toDistributionSummaryLine(DistributionSummary meter) {
        HistogramSnapshot histogramSnapshot = meter.takeSnapshot();
        double total = histogramSnapshot.total();
        double max = histogramSnapshot.max();
        return toSummaryLine(meter, histogramSnapshot, total, max);
    }

    Stream<String> toLongTaskTimerLine(LongTaskTimer meter) {
        HistogramSnapshot histogramSnapshot = meter.takeSnapshot();
        double total = histogramSnapshot.total(getBaseTimeUnit());
        double max = histogramSnapshot.max(getBaseTimeUnit());

        return toSummaryLine(meter, histogramSnapshot, total, max);
    }

    Stream<String> toTimeGaugeLine(TimeGauge meter) {
        return toGauge(meter);
    }

    Stream<String> toFunctionCounterLine(FunctionCounter meter) {
        return toCounter(meter);
    }

    Stream<String> toFunctionTimerLine(FunctionTimer meter) {
        return toGauge(meter);
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
                        String metricKey = createMetricKey(meter.getId().getName(),
                                measurement.getStatistic().getTagValueRepresentation());
                        if (metricKey == null) {
                            return null;
                        }
                        return createMetricBuilder(meter, metricKey)
                                .setDoubleGaugeValue(measurement.getValue())
                                .serialize();
                    } catch (MetricException e) {
                        logger.warn("Could not serialize metric with name {}", meter.getId().getName());
                    } catch (IllegalArgumentException iae) {
                        logger.warn(String.format("Illegal value for metric with name %s: %s Dropping...", meter.getId().getName(), iae.getMessage()));
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
                        return createMetricBuilder(meter, meter.getId().getName())
                                .setDoubleCounterValueDelta(measurement.getValue())
                                .serialize();
                    } catch (MetricException e) {
                        logger.warn(String.format("could not serialize metric with name %s", meter.getId().getName()));
                    } catch (IllegalArgumentException iae) {
                        logger.warn(String.format("Illegal value for metric with name %s: %s Dropping...", meter.getId().getName(), iae.getMessage()));
                    }
                    return null;
                })
                .filter(Objects::nonNull);
    }

    private Metric.Builder createMetricBuilder(Meter meter, String metricKey) {
        return metricBuilderFactory
                .newMetricBuilder(metricKey)
                .setDimensions(fromTags(meter.getId().getTags()))
                .setTimestamp(Instant.ofEpochMilli(clock.wallTime()));
    }

    private String createMetricKey(String name, String tagValueRepresentation) {
        String dynatraceTag;
        switch (tagValueRepresentation) {
            case "total":
                dynatraceTag = "sum";
                break;
            case "value":
                dynatraceTag = "";
                break;
            case "percentile":
                // drop lines that have a tag value representation of percentile.
                // we use the 0 percentile to determine minimum values for summaries and timers
                // so this will export 0 for every timer and summary.
                return null;
            default:
                dynatraceTag = tagValueRepresentation;
                break;
        }

        if (dynatraceTag.isEmpty()) {
            return name;
        }
        return String.format("%s.%s", name, dynatraceTag);
    }

    private DimensionList fromTags(List<Tag> tags) {
        return DimensionList.fromCollection(
                tags.stream()
                        .map(x -> Dimension.create(x.getKey(), x.getValue()))
                        .collect(Collectors.toList()));
    }

    static <T> Stream<T> streamOf(Iterable<T> iterable) {
        return StreamSupport.stream(iterable.spliterator(), false);
    }

    private void send(List<String> metricLines) {
        try {
            String body = metricLines.stream().collect(Collectors.joining(System.lineSeparator()));
            logger.debug("sending lines:\n" + body);

            httpClient.post(endpoint)
                    .withHeader("Authorization", "Api-Token " + config.apiToken())
                    .withPlainText(body)
                    .send()
                    .onSuccess((r) -> logger.info("Ingested {} metric lines into Dynatrace, response: {}", metricLines.size(), r.body()))
                    .onError((r) -> logger.error("Failed metric ingestion. code={} body={}", r.code(), r.body()));
        } catch (Throwable throwable) {
            logger.error("Failed metric ingestion: {}", throwable.getMessage());
        }
    }

    private void sendInBatches(List<String> metricLines) {
        MetricLinePartition.partition(metricLines, config.batchSize())
                .forEach(this::send);
    }

    private static boolean lineLengthBelowLimit(String line) {
        return line.length() <= METRIC_LINE_MAX_LENGTH;
    }

    static class MetricLinePartition extends AbstractPartition<String> {

        MetricLinePartition(List<String> list, int partitionSize) {
            super(list, partitionSize);
        }

        static List<List<String>> partition(List<String> list, int partitionSize) {
            return new MetricLinePartition(list, partitionSize);
        }
    }
}
