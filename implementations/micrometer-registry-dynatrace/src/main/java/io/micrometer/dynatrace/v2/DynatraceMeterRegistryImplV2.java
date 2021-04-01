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
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.util.AbstractPartition;
import io.micrometer.core.ipc.http.HttpSender;
import io.micrometer.dynatrace.DynatraceConfig;
import io.micrometer.dynatrace.DynatraceMeterRegistryImplBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadFactory;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class DynatraceMeterRegistryImplV2 extends DynatraceMeterRegistryImplBase {
    private final String endpoint;
    private final MetricBuilderFactory metricBuilderFactory;
    private final Logger logger = LoggerFactory.getLogger(DynatraceMeterRegistryImplV2.class.getName());

    private static final int METRIC_LINE_MAX_LENGTH = 2000;

    public DynatraceMeterRegistryImplV2(DynatraceConfig config, Clock clock, ThreadFactory threadFactory, HttpSender httpClient) {
        super(config, clock, threadFactory, httpClient);

        this.config = config;
        this.httpClient = httpClient;
        this.endpoint = config.uri() + "/api/v2/metrics/ingest";

        // todo default dimensions? OneAgent Metadata?
        DimensionList defaultDimensions = parseDefaultDimensions(config.defaultDimensions());

        MetricBuilderFactory.MetricBuilderFactoryBuilder factoryBuilder = MetricBuilderFactory
                .builder()
                .withPrefix(config.prefix())
                .withDefaultDimensions(defaultDimensions);

        if (config.enrichWithOneAgentMetadata()) {
            factoryBuilder.withOneAgentMetadata();
        }

        metricBuilderFactory = factoryBuilder.build();
    }

    private DimensionList parseDefaultDimensions(String defaultDimensions) {
        // assume a semicolon separated list of key=value pairs
        // todo see if its possible to serialize that from the YAML
        List<Dimension> dimensions = new ArrayList<>();
        for (String dimension : defaultDimensions.split(";")) {
            String[] split = dimension.split("=", 2);
            if (split.length == 2) {
                dimensions.add(Dimension.create(split[0], split[1]));
            }
        }
        return DimensionList.fromCollection(dimensions);
    }

    @Override
    public void publish(MeterRegistry registry) {
        // split the lines by whether or not they exceed the line length limit.
        Map<Boolean, List<String>> metricLines =
                registry.getMeters().stream()
                        .flatMap(this::toMetricLines)
                        .collect(Collectors.partitioningBy(DynatraceMeterRegistryImplV2::lineLengthBelowLimit));

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

    private Stream<String> toGaugeLine(Gauge meter) {
        return toGauge(meter);
    }

    private Stream<String> toCounterLine(Counter meter) {
        return toCounter(meter);
    }

    private Stream<String> toTimerLine(Timer meter) {
        return toGauge(meter);
    }

    private Stream<String> toDistributionSummaryLine(DistributionSummary meter) {
        // todo check if this gives min/max/sum/count values.
        // e. g check for each value to see its type
        // meter.measure().forEach(x->System.out.println(x.getStatistic().getTagValueRepresentation() ==
        // Statistic.MAX));
        return toGauge(meter);
    }

    private Stream<String> toLongTaskTimerLine(LongTaskTimer meter) {
        return toGauge(meter);
    }

    private Stream<String> toTimeGaugeLine(TimeGauge meter) {
        return toGauge(meter);
    }

    private Stream<String> toFunctionCounterLine(FunctionCounter meter) {
        return toCounter(meter);
    }

    private Stream<String> toFunctionTimerLine(FunctionTimer meter) {
        return toGauge(meter);
    }

    private Stream<String> toMeterLine(Meter meter) {
        return toGauge(meter);
    }

    private Stream<String> toGauge(Meter meter) {
        return streamOf(meter.measure()).map(
                measurement -> {
                    try {
                        return createMetricBuilder(meter)
                                .setDoubleGaugeValue(measurement.getValue())
                                .serialize();
                    } catch (MetricException e) {
                        logger.warn("Could not serialize metric with name {}", meter.getId().getName());
                    }
                    return null;
                }
        ).filter(Objects::nonNull);
    }

    private Stream<String> toCounter(Meter meter) {
        return streamOf(meter.measure()).map(
                measurement -> {
                    try {
                        return createMetricBuilder(meter)
                                .setDoubleCounterValueDelta(measurement.getValue())
                                .serialize();
                    } catch (MetricException e) {
                        logger.warn(String.format("could not serialize metric with name %s", meter.getId().getName()));
                    }
                    return null;
                })
                .filter(Objects::nonNull);
    }

    private Metric.Builder createMetricBuilder(Meter meter) {
        return metricBuilderFactory
                .newMetricBuilder(meter.getId().getName())
                .setDimensions(fromTags(meter.getId().getTags()))
                .setTimestamp(Instant.ofEpochSecond(clock.wallTime()));
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
                    .onSuccess((r) -> {
                        logger.debug("Ingested {} metric lines into Dynatrace, response: {}", metricLines.size(), r.body());
                    })
                    .onError((r) -> logger.error("Failed metric ingestion. code={} body={}", r.code(), r.body()));
        } catch (Throwable throwable) {
            logger.error("Failed metric ingestion", throwable);
        }
    }

    static class MetricLinePartition extends AbstractPartition<String> {

        MetricLinePartition(List<String> list, int partitionSize) {
            super(list, partitionSize);
        }

        static List<List<String>> partition(List<String> list, int partitionSize) {
            return new MetricLinePartition(list, partitionSize);
        }
    }

    void sendInBatches(List<String> metricLines) {
        MetricLinePartition.partition(metricLines, config.batchSize())
                .forEach(this::send);
    }

    private static boolean lineLengthBelowLimit(String line) {
        return line.length() <= METRIC_LINE_MAX_LENGTH;
    }
}
