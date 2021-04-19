/**
 * Copyright 2021 VMware, Inc.
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

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.step.StepTimer;
import io.micrometer.core.ipc.http.HttpSender;
import io.micrometer.dynatrace.DynatraceApiVersion;
import io.micrometer.dynatrace.DynatraceConfig;
import io.micrometer.dynatrace.DynatraceMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DynatraceExporterV2Test {
    private final DynatraceMeterRegistry meterRegistry = createMeterRegistry();
    private final DynatraceExporterV2 exporter = createExporter();

    private DynatraceConfig createDynatraceConfig() {
        return new DynatraceConfig() {
            @Override
            public String get(String key) {
                return null;
            }

            @Override
            public String uri() {
                return "http://localhost";
            }

            @Override
            public String apiToken() {
                return "apiToken";
            }

            @Override
            public DynatraceApiVersion apiVersion() {
                return DynatraceApiVersion.V2;
            }

            @Override
            public Duration step() {
                return Duration.ofMillis(100);
            }
        };
    }

    private DynatraceMeterRegistry createMeterRegistry() {
        DynatraceConfig config = createDynatraceConfig();

        return DynatraceMeterRegistry.builder(config)
                .httpClient(request -> new HttpSender.Response(200, null))
                .build();
    }

    private DynatraceExporterV2 createExporter() {
        DynatraceConfig config = createDynatraceConfig();

        return new DynatraceExporterV2(config, Clock.SYSTEM,
                request -> new HttpSender.Response(200, null));
    }

    @Test
    void testToDistributionSummaryLine() {
        DistributionSummary summary = DistributionSummary.builder("my.summary").register(meterRegistry);
        summary.record(3.1);
        summary.record(2.3);
        summary.record(5.4);
        summary.record(.1);

        List<String> actual = exporter.toDistributionSummaryLine(summary).collect(Collectors.toList());
        assertThat(actual).hasSize(1);
        assertThat(actual.get(0)).startsWith("my.summary,dt.metrics.source=micrometer gauge");
        assertThat(actual.get(0)).contains("max=").contains("min=").contains("sum=").contains("count=");
    }

    @Test
    void toGaugeLine() {
        meterRegistry.gauge("my.gauge", 1.23);
        Gauge myGauge = meterRegistry.find("my.gauge").gauge();

        List<String> actual = exporter.toGaugeLine(myGauge).collect(Collectors.toList());
        assertThat(actual).hasSize(1);
        assertThat(actual.get(0)).startsWith("my.gauge,dt.metrics.source=micrometer gauge,1.23 ");
        String expectedDummy = "my.gauge,dt.metrics.source=micrometer gauge,1.23 1617714022879";
        assertThat(actual.get(0)).hasSize(expectedDummy.length());
    }


    @Test
    void toMeterLine() {
        meterRegistry.gauge("my.meter", 1.23);
        Gauge gauge = meterRegistry.find("my.meter").gauge();
        assertNotNull(gauge);

        List<String> actual = exporter.toMeterLine(gauge).collect(Collectors.toList());
        assertThat(actual).hasSize(1);
        assertThat(actual.get(0)).startsWith("my.meter,dt.metrics.source=micrometer gauge,1.23");
        String expectedDummy = "my.meter,dt.metrics.source=micrometer gauge,1.23 1617714022879";
        assertThat(actual.get(0)).hasSize(expectedDummy.length());
    }

    @Test
    void toCounterLine() {
        meterRegistry.counter("my.counter");
        Counter counter = meterRegistry.find("my.counter").counter();
        assertNotNull(counter);
        counter.increment();
        counter.increment();
        counter.increment();

        String expected = String.format("my.counter,dt.metrics.source=micrometer count,delta=%s ", String.valueOf(counter.count()));
        List<String> actual = exporter.toCounterLine(counter).collect(Collectors.toList());
        assertThat(actual).hasSize(1);
        String actualLine = actual.get(0);
        String expectedDummy = "my.counter,dt.metrics.source=micrometer count,delta=0.0 1617714022879";
        assertThat(actualLine).hasSize(expectedDummy.length());
        assertThat(actualLine).startsWith(expected);
    }

    @Test
    void toTimerLine() {
        meterRegistry.timer("my.timer");
        Timer timer = meterRegistry.find("my.timer").timer();
        assertNotNull(timer);

        timer.record(() -> {
            try {
                Thread.sleep(10);
            } catch (InterruptedException ignored) {
            }
        });

        try {
            Thread.sleep(100);
        } catch (InterruptedException ignored) {
        }

        List<String> actual = exporter.toTimerLine(timer).collect(Collectors.toList());
        assertThat(actual).hasSize(1);
        assertThat(actual.get(0)).startsWith("my.timer,dt.metrics.source=micrometer gauge,");
        assertThat(actual.get(0)).contains("min=");
        assertThat(actual.get(0)).contains("max=");
        assertThat(actual.get(0)).contains("sum=");
        assertThat(actual.get(0)).contains("count=");
    }

    @Test
    void toLongTaskTimerLine() {
        LongTaskTimer.builder("my.long.task.timer").register(meterRegistry);
        LongTaskTimer longTaskTimer = meterRegistry.find("my.long.task.timer").longTaskTimer();
        assertNotNull(longTaskTimer);

        // needs to be run in the background, otherwise record will wait until the task is finished.
        Runnable r = new Runnable() {
            @Override
            public void run() {
                longTaskTimer.record(() -> {
                    try {
                        Thread.sleep(300);
                    } catch (InterruptedException ignored) {
                    }
                });
            }
        };
        new Thread(r).start();

        // wait for the first scrape to be recorded.
        try {
            Thread.sleep(100);
        } catch (InterruptedException ignored) {
        }

        List<String> actual = exporter.toLongTaskTimerLine(longTaskTimer).collect(Collectors.toList());
        assertThat(actual).hasSize(1);
        assertThat(actual.get(0)).startsWith("my.long.task.timer,dt.metrics.source=micrometer gauge,");
        assertThat(actual.get(0)).contains("max=").contains("min=").contains("sum=").contains("count=");
    }

    @Test
    void toTimeGaugeLine() {
        TimeGauge.builder("my.time.gauge", () -> 2.3, TimeUnit.MILLISECONDS).register(meterRegistry);
        TimeGauge timeGauge = meterRegistry.find("my.time.gauge").timeGauge();
        assertNotNull(timeGauge);

        List<String> actual = exporter.toTimeGaugeLine(timeGauge).collect(Collectors.toList());
        assertThat(actual).hasSize(1);
        assertThat(actual.get(0)).hasSize("my.time.gauge,dt.metrics.source=micrometer gauge,2.3 1617776498381".length());
        assertThat(actual.get(0)).startsWith("my.time.gauge,dt.metrics.source=micrometer gauge,2.3 ");
    }

    @Test
    void toFunctionCounterLine() {
        class TestClass {
            double count() {
                return 2.3;
            }
        }
        TestClass tester = new TestClass();
        FunctionCounter.builder("my.function.counter", tester, TestClass::count).register(meterRegistry);
        FunctionCounter functionCounter = meterRegistry.find("my.function.counter").functionCounter();
        assertNotNull(functionCounter);

        tester.count();

        List<String> actual = exporter.toFunctionCounterLine(functionCounter).collect(Collectors.toList());
        assertThat(actual).hasSize(1);
        assertThat(actual.get(0)).hasSize("my.function.counter,dt.metrics.source=micrometer count,delta=0.0 1617776498381".length());
        assertThat(actual.get(0)).startsWith("my.function.counter,dt.metrics.source=micrometer count,delta=");
    }

    @Test
    void toFunctionTimerLine() {
        class FunctionTimerDummy implements FunctionTimer {
            @Override
            public double count() {
                return 500.5;
            }

            @Override
            public double totalTime(TimeUnit unit) {
                return 5005;
            }

            @Override
            public TimeUnit baseTimeUnit() {
                return TimeUnit.MILLISECONDS;
            }

            @Override
            public Id getId() {
                return  new Id("my.function.timer", Tags.empty(), null, null, Type.TIMER);
            }
        }
        
        FunctionTimerDummy cut = new FunctionTimerDummy();
        
        List<String> actual = exporter.toFunctionTimerLine(cut).collect(Collectors.toList());
        assertThat(actual).hasSize(1);
        assertThat(actual.get(0)).startsWith("my.function.timer,dt.metrics.source=micrometer gauge,min=10.0,max=10.0,sum=5005.0,count=500 ");
        assertThat(actual.get(0)).hasSize("my.function.timer,dt.metrics.source=micrometer gauge,min=10.0,max=10.0,sum=5005.0,count=500 1234567890123".length());
    }

    @Test
    void toGaugeInvalidName() {
        // invalid name.
        meterRegistry.gauge("~~~", 1.23);
        Gauge gauge = meterRegistry.find("~~~").gauge();
        assertNotNull(gauge);

        List<String> actual = exporter.toGauge(gauge).collect(Collectors.toList());
        assertThat(actual).isEmpty();
    }

    @Test
    void toGaugeInvalidCases() {
        meterRegistry.gauge("my.gauge1", Double.NaN);
        Gauge gauge1 = meterRegistry.find("my.gauge1").gauge();
        List<String> actual1 = exporter.toGauge(gauge1).collect(Collectors.toList());
        assertNotNull(gauge1);
        assertThat(actual1).isEmpty();

        meterRegistry.gauge("my.gauge2", Double.NEGATIVE_INFINITY);
        Gauge gauge2 = meterRegistry.find("my.gauge2").gauge();
        assertNotNull(gauge2);
        List<String> actual2 = exporter.toGauge(gauge2).collect(Collectors.toList());
        assertThat(actual2).isEmpty();

        meterRegistry.gauge("my.gauge3", Double.POSITIVE_INFINITY);
        Gauge gauge3 = meterRegistry.find("my.gauge3").gauge();
        assertNotNull(gauge3);
        List<String> actual3 = exporter.toGauge(gauge3).collect(Collectors.toList());
        assertThat(actual3).isEmpty();
    }

    @Test
    void toGaugeTags() {
        // invalid name.
        Gauge.builder("my.gauge", () -> 1.23).tags(Tags.of("tag1", "value1", "tag2", "value2")).register(meterRegistry);
        Gauge gauge = meterRegistry.find("my.gauge").gauge();
        assertNotNull(gauge);

        List<String> actual = exporter.toGauge(gauge).collect(Collectors.toList());
        assertThat(actual).hasSize(1);
        assertThat(actual.get(0)).contains("tag1=value1").contains("tag2=value2").contains("dt.metrics.source=micrometer");
        assertThat(actual.get(0)).startsWith("my.gauge,");
        assertThat(actual.get(0)).hasSize("my.gauge,dt.metrics.source=micrometer,tag1=value1,tag2=value2 gauge,1.23 1617776498381".length());
    }

    @Test
    void toCounterInvalidName() {
        // invalid name.
        meterRegistry.counter("~~~");
        Counter counter = meterRegistry.find("~~~").counter();
        assertNotNull(counter);

        List<String> actual = exporter.toCounter(counter).collect(Collectors.toList());
        assertThat(actual).isEmpty();
    }

    @Test
    void toCounterInvalidCases() {
        meterRegistry.counter("my.counter1");
        Counter counter = meterRegistry.find("my.counter1").counter();
        assertNotNull(counter);

        counter.increment(Double.NaN);
        List<String> actual1 = exporter.toCounter(counter).collect(Collectors.toList());
        counter.increment(Double.POSITIVE_INFINITY);
        List<String> actual2 = exporter.toCounter(counter).collect(Collectors.toList());
        counter.increment(Double.NEGATIVE_INFINITY);
        List<String> actual3 = exporter.toCounter(counter).collect(Collectors.toList());
        assertThat(actual1).hasSize(1);
        assertThat(actual2).hasSize(1);
        assertThat(actual3).hasSize(1);

        assertThat(actual1.get(0)).contains("count,delta=0");
        assertThat(actual2.get(0)).contains("count,delta=0");
        assertThat(actual3.get(0)).contains("count,delta=0");
    }

    @Test
    void toCounterTags() {
        // invalid name.
        Counter.builder("my.counter").tags(Tags.of("tag1", "value1", "tag2", "value2")).register(meterRegistry);
        Counter counter = meterRegistry.find("my.counter").counter();
        assertNotNull(counter);

        List<String> actual = exporter.toCounter(counter).collect(Collectors.toList());
        assertThat(actual).hasSize(1);
        assertThat(actual.get(0)).contains("tag1=value1").contains("tag2=value2").contains("dt.metrics.source=micrometer");
        assertThat(actual.get(0)).startsWith("my.counter,");
        assertThat(actual.get(0)).hasSize("my.counter,tag1=value1,dt.metrics.source=micrometer,tag2=value2 count,delta=0.0 1617796526714".length());
    }

    @Test
    void prepareEndpoint() {
        assertThat(exporter.prepareEndpoint("")).isEqualTo("http://127.0.0.1:14499/metrics/ingest");

        assertThat(exporter.prepareEndpoint("http://127.0.0.1:14499")).isEqualTo("http://127.0.0.1:14499/metrics/ingest");
        assertThat(exporter.prepareEndpoint("http://127.0.0.1:14499/")).isEqualTo("http://127.0.0.1:14499/metrics/ingest");

        assertThat(exporter.prepareEndpoint("http://localhost:13333")).isEqualTo("http://localhost:13333/metrics/ingest");
        assertThat(exporter.prepareEndpoint("http://127.0.0.1:13333")).isEqualTo("http://127.0.0.1:13333/metrics/ingest");

        assertThat(exporter.prepareEndpoint("http://localhost:13333/metrics/ingest")).isEqualTo("http://localhost:13333/metrics/ingest");

        assertThat(exporter.prepareEndpoint("https://XXXXXXX.live.dynatrace.com")).isEqualTo("https://XXXXXXX.live.dynatrace.com/api/v2/metrics/ingest");
        assertThat(exporter.prepareEndpoint("https://XXXXXXX.live.dynatrace.com/")).isEqualTo("https://XXXXXXX.live.dynatrace.com/api/v2/metrics/ingest");

        assertThat(exporter.prepareEndpoint("https://XXXXXXX.live.dynatrace.com/api/v2/metrics/ingest")).isEqualTo("https://XXXXXXX.live.dynatrace.com/api/v2/metrics/ingest");
    }
}
