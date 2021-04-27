/**
 * Copyright 2020 VMware, Inc.
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
package io.micrometer.dynatrace;

import io.micrometer.core.instrument.config.validate.InvalidReason;
import io.micrometer.core.instrument.config.validate.Validated;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DynatraceConfigTest {
    private final Map<String, String> props = new HashMap<>();
    private final DynatraceConfig config = props::get;

    @Test
    void invalid() {
        List<Validated.Invalid<?>> failures = config.validate().failures();
        assertThat(failures.size()).isEqualTo(3);
        assertThat(failures.stream().map(Validated::toString)).containsExactlyInAnyOrder(
                "Invalid{property='dynatrace.apiToken', value='null', message='is required'}",
                "Invalid{property='dynatrace.uri', value='null', message='is required'}",
                "Invalid{property='dynatrace.deviceId', value='null', message='is required'}"
        );
    }

    @Test
    void invalidOverrideTechnologyType() {
        Validated<?> validate = new DynatraceConfig() {
            @Override
            public String technologyType() {
                return "";
            }

            @Override
            public String get(String key) {
                return null;
            }
        }.validate();

        assertThat(validate.failures().size()).isEqualTo(4);
        assertThat(validate.failures().stream().map(Validated::toString)).containsExactlyInAnyOrder(
                "Invalid{property='dynatrace.apiToken', value='null', message='is required'}",
                "Invalid{property='dynatrace.uri', value='null', message='is required'}",
                "Invalid{property='dynatrace.deviceId', value='null', message='is required'}",
                "Invalid{property='dynatrace.technologyType', value='', message='cannot be blank'}"
        );
    }

    @Test
    void invalidVersion() {
        Map<String, String> properties = new HashMap<String, String>() {{
            put("dynatrace.apiToken", "secret");
            put("dynatrace.uri", "https://uri.dynatrace.com");
            put("dynatrace.deviceId", "device");
            put("dynatrace.apiVersion", "v-INVALID");
        }};
        DynatraceConfig config = properties::get;

        List<Validated.Invalid<?>> failures = config.validate().failures();
        assertThat(failures).hasSize(1);
        Validated.Invalid<?> failure = failures.get(0);
        assertThat(failure.getProperty()).isEqualTo("dynatrace.apiVersion");
        assertThat(failure.getValue()).isEqualTo("v-INVALID");
        assertThat(failure.getMessage()).startsWith("should be one of ");
        assertThat(failure.getReason()).isSameAs(InvalidReason.MALFORMED);
        assertThat(failure.getException()).isNull();
    }

    @Test
    void valid() {
        props.put("dynatrace.apiToken", "secret");
        props.put("dynatrace.uri", "https://uri.dynatrace.com");
        props.put("dynatrace.deviceId", "device");

        assertThat(config.validate().isValid()).isTrue();
    }

    @Test
    void testFallbackToV1() {
        Map<String, String> properties = new HashMap<String, String>() {{
            put("dynatrace.apiToken", "secret");
            put("dynatrace.uri", "https://uri.dynatrace.com");
            put("dynatrace.deviceId", "device");
        }};

        DynatraceConfig config = properties::get;

        assertThat(config.validate().isValid()).isTrue();
        assertThat(config.apiVersion()).isSameAs(DynatraceApiVersion.V1);
    }

    @Test
    void testDefaultOneAgentEndpoint() {
        Map<String, String> properties = new HashMap<String, String>() {{
            put("dynatrace.apiVersion", "v2");
        }};

        DynatraceConfig config = properties::get;
        assertThat(config.apiToken()).isNull();
        assertThat(config.uri()).isEqualTo("http://localhost:14499");
        assertThat(config.apiVersion()).isEqualTo(DynatraceApiVersion.V2);

        Validated<?> validated = config.validate();
        assertThat(validated.isValid()).isTrue();
    }

    @Test
    void testOneAgentEndpointWithDifferentPort() {
        Map<String, String> properties = new HashMap<String, String>() {{
            put("dynatrace.apiVersion", "v2");
            put("dynatrace.uri", "http://localhost:13333");
        }};

        DynatraceConfig config = properties::get;
        assertThat(config.apiToken()).isNull();
        assertThat(config.uri()).isEqualTo("http://localhost:13333");
        assertThat(config.apiVersion()).isEqualTo(DynatraceApiVersion.V2);

        Validated<?> validated = config.validate();
        assertThat(validated.isValid()).isTrue();
    }

    @Test
    void testV2requiredProperties() {
        Map<String, String> properties = new HashMap<String, String>() {{
            put("dynatrace.apiVersion", "v2");
            put("dynatrace.uri", "https://uri.dynatrace.com");
            put("dynatrace.apiToken", "secret");
        }};

        DynatraceConfig config = properties::get;
        assertThat(config.apiToken()).isEqualTo("secret");
        assertThat(config.uri()).isEqualTo("https://uri.dynatrace.com");
        assertThat(config.apiVersion()).isEqualTo(DynatraceApiVersion.V2);

        Validated<?> validated = config.validate();
        assertThat(validated.isValid()).isTrue();
    }
}
