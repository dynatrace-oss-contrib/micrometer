/*
 * Copyright 2024 VMware, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.dynatrace.v2;

import io.micrometer.common.util.internal.logging.WarnThenDebugLogger;

class WarnThenDebugLoggers {

    static class StackTraceLogger extends WarnThenDebugLogger {

        public StackTraceLogger() {
            super(StackTraceLogger.class);
        }

    }

    static class NanGaugeLogger extends WarnThenDebugLogger {

        public NanGaugeLogger() {
            super(NanGaugeLogger.class);
        }

    }

    static class MetadataDiscrepancyLogger extends WarnThenDebugLogger {

        public MetadataDiscrepancyLogger() {
            super(MetadataDiscrepancyLogger.class);
        }

    }

}
