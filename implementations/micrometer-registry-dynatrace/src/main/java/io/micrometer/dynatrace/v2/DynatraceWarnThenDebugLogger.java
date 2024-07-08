package io.micrometer.dynatrace.v2;

import io.micrometer.common.util.internal.logging.InternalLoggerFactory;
import io.micrometer.common.util.internal.logging.WarnThenDebugLogger;
import io.micrometer.dynatrace.WarnErrLoggerFilter;

public class DynatraceWarnThenDebugLogger extends WarnThenDebugLogger {
    public DynatraceWarnThenDebugLogger(String name, boolean logWarningsAtInfo) {
        super(new WarnErrLoggerFilter(InternalLoggerFactory.getInstance(name), logWarningsAtInfo, true));
    }
}
