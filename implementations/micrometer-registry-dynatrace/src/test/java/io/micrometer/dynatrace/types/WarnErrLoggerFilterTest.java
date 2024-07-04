package io.micrometer.dynatrace.types;

import io.micrometer.common.util.internal.logging.InternalLogLevel;
import io.micrometer.common.util.internal.logging.InternalLogger;
import io.micrometer.common.util.internal.logging.MockLoggerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;
import static org.mockito.internal.verification.VerificationModeFactory.times;

class WarnErrLoggerFilterTest {

    private InternalLogger loggerMock;
    private Object[] argsArray;
    private Throwable throwable;

    @BeforeEach
    void setUp() {
        loggerMock = spy(MockLoggerFactory.getInstance(WarnErrLoggerFilterTest.class.getName()));

        List<Object> args = new ArrayList<>();
        args.add("test");
        args.add(1.);
        argsArray = args.toArray();
        throwable = new Throwable();
    }

    @AfterEach
    void tearDown() {
        reset(loggerMock);
    }

    @Test
    void testWarn_whenShouldLogWarnIsTrue_shouldLogWarningAtWarn() {
        WarnErrLoggerFilter wrapped = new WarnErrLoggerFilter(loggerMock, true, true);

        useAllWarnMethods(wrapped);

        verify(loggerMock, times(1)).warn("test");
        verify(loggerMock, times(1)).warn("te%s", "st");
        verify(loggerMock, times(1)).warn("te%s%d", "st", 1);
        verify(loggerMock, times(1)).warn("te%s%d", argsArray);
        verify(loggerMock, times(1)).warn("test", throwable);
        verify(loggerMock, times(1)).warn(throwable);
        verify(loggerMock, never()).info(anyString());
    }


    @Test
    void testWarn_whenShouldLogWarnIsFalse_shouldLogWarningAtInfo() {
        WarnErrLoggerFilter wrapped = new WarnErrLoggerFilter(loggerMock, false, false);

        useAllWarnMethods(wrapped);

        verify(loggerMock, times(1)).info("test");
        verify(loggerMock, times(1)).info("te%s", "st");
        verify(loggerMock, times(1)).info("te%s%d", "st", 1);
        verify(loggerMock, times(1)).info("te%s%d", argsArray);
        verify(loggerMock, times(1)).info("test", throwable);
        verify(loggerMock, times(1)).info(throwable);
        verify(loggerMock, never()).warn(anyString());
    }

    @Test
    void testError_whenShouldLogErrorIsTrue_shouldLogErrorAtError() {
        WarnErrLoggerFilter wrapped = new WarnErrLoggerFilter(loggerMock, true, true);

        useAllErrorMethods(wrapped);

        verify(loggerMock, times(1)).error("test");
        verify(loggerMock, times(1)).error("te%s", "st");
        verify(loggerMock, times(1)).error("te%s%d", "st", 1);
        verify(loggerMock, times(1)).error("te%s%d", argsArray);
        verify(loggerMock, times(1)).error("test", throwable);
        verify(loggerMock, times(1)).error(throwable);
        verify(loggerMock, never()).info(anyString());
    }

    @Test
    void testError_whenShouldLogErrorIsFalse_shouldLogErrorAtInfo() {
        WarnErrLoggerFilter wrapped = new WarnErrLoggerFilter(loggerMock, false, false);

        useAllErrorMethods(wrapped);

        verify(loggerMock, times(1)).info("test");
        verify(loggerMock, times(1)).info("te%s", "st");
        verify(loggerMock, times(1)).info("te%s%d", "st", 1);
        verify(loggerMock, times(1)).info("te%s%d", argsArray);
        verify(loggerMock, times(1)).info("test", throwable);
        verify(loggerMock, times(1)).info(throwable);
        verify(loggerMock, never()).error(anyString());
    }

    @Test
    void testLog_whenShouldLogErrorIsTrue_shouldLogErrorAtError() {
        WarnErrLoggerFilter wrapped = new WarnErrLoggerFilter(loggerMock, true, true);

        useAllLogMethods(wrapped, InternalLogLevel.ERROR);

        verify(loggerMock, times(1)).error("test");
        verify(loggerMock, times(1)).error("te%s", "st");
        verify(loggerMock, times(1)).error("te%s%d", "st", 1);
        verify(loggerMock, times(1)).error("te%s%d", argsArray);
        verify(loggerMock, times(1)).error("test", throwable);
        verify(loggerMock, times(1)).error(throwable);
        verify(loggerMock, never()).info(anyString());
    }

    @Test
    void testLog_whenShouldLogErrorIsFalse_shouldLogErrorAtInfo() {
        WarnErrLoggerFilter wrapped = new WarnErrLoggerFilter(loggerMock, false, false);

        useAllLogMethods(wrapped, InternalLogLevel.ERROR);

        verify(loggerMock, times(1)).info("test");
        verify(loggerMock, times(1)).info("te%s", "st");
        verify(loggerMock, times(1)).info("te%s%d", "st", 1);
        verify(loggerMock, times(1)).info("te%s%d", argsArray);
        verify(loggerMock, times(1)).info("test", throwable);
        verify(loggerMock, times(1)).info(throwable);
        verify(loggerMock, never()).error(anyString());
    }

    @Test
    void testLog_whenShouldLogWarnIsTrue_shouldLogWarnAtWarn() {
        WarnErrLoggerFilter wrapped = new WarnErrLoggerFilter(loggerMock, true, true);

        useAllLogMethods(wrapped, InternalLogLevel.WARN);

        verify(loggerMock, times(1)).warn("test");
        verify(loggerMock, times(1)).warn("te%s", "st");
        verify(loggerMock, times(1)).warn("te%s%d", "st", 1);
        verify(loggerMock, times(1)).warn("te%s%d", argsArray);
        verify(loggerMock, times(1)).warn("test", throwable);
        verify(loggerMock, times(1)).warn(throwable);
        verify(loggerMock, never()).info(anyString());
    }

    @Test
    void testLog_whenShouldLogWarnIsFalse_shouldLogWarnAtInfo() {
        WarnErrLoggerFilter wrapped = new WarnErrLoggerFilter(loggerMock, false, false);

        useAllLogMethods(wrapped, InternalLogLevel.WARN);

        verify(loggerMock, times(1)).info("test");
        verify(loggerMock, times(1)).info("te%s", "st");
        verify(loggerMock, times(1)).info("te%s%d", "st", 1);
        verify(loggerMock, times(1)).info("te%s%d", argsArray);
        verify(loggerMock, times(1)).info("test", throwable);
        verify(loggerMock, times(1)).info(throwable);
        verify(loggerMock, never()).warn(anyString());
    }

    @Test
    void test_nonErrorOrWarnMethods_passThroughToDelegate() {
        WarnErrLoggerFilter wrapped = new WarnErrLoggerFilter(loggerMock, false, false);

        wrapped.trace("test");
        wrapped.trace("te%s", "st");
        wrapped.trace("te%s%d", "st", 1);
        wrapped.trace("te%s%d", argsArray);
        wrapped.trace("test", throwable);
        wrapped.trace(throwable);

        wrapped.debug("test");
        wrapped.debug("te%s", "st");
        wrapped.debug("te%s%d", "st", 1);
        wrapped.debug("te%s%d", argsArray);
        wrapped.debug("test", throwable);
        wrapped.debug(throwable);

        wrapped.info("test");
        wrapped.info("te%s", "st");
        wrapped.info("te%s%d", "st", 1);
        wrapped.info("te%s%d", argsArray);
        wrapped.info("test", throwable);
        wrapped.info(throwable);

        verify(loggerMock, times(1)).trace("test");
        verify(loggerMock, times(1)).trace("te%s", "st");
        verify(loggerMock, times(1)).trace("te%s%d", "st", 1);
        verify(loggerMock, times(1)).trace("te%s%d", argsArray);
        verify(loggerMock, times(1)).trace("test", throwable);
        verify(loggerMock, times(1)).trace(throwable);

        verify(loggerMock, times(1)).debug("test");
        verify(loggerMock, times(1)).debug("te%s", "st");
        verify(loggerMock, times(1)).debug("te%s%d", "st", 1);
        verify(loggerMock, times(1)).debug("te%s%d", argsArray);
        verify(loggerMock, times(1)).debug("test", throwable);
        verify(loggerMock, times(1)).debug(throwable);

        verify(loggerMock, times(1)).info("test");
        verify(loggerMock, times(1)).info("te%s", "st");
        verify(loggerMock, times(1)).info("te%s%d", "st", 1);
        verify(loggerMock, times(1)).info("te%s%d", argsArray);
        verify(loggerMock, times(1)).info("test", throwable);
        verify(loggerMock, times(1)).info(throwable);
    }

    @Test
    void test_logMethods_passThroughToDelegate() {
        WarnErrLoggerFilter wrapped = new WarnErrLoggerFilter(loggerMock, false, false);

        useAllLogMethods(wrapped, InternalLogLevel.TRACE);
        useAllLogMethods(wrapped, InternalLogLevel.DEBUG);
        useAllLogMethods(wrapped, InternalLogLevel.INFO);

        List<InternalLogLevel> logLevels = new ArrayList<>();
        logLevels.add(InternalLogLevel.TRACE);
        logLevels.add(InternalLogLevel.DEBUG);
        logLevels.add(InternalLogLevel.INFO);

        for (InternalLogLevel logLevel : logLevels) {
            verify(loggerMock, times(1)).log(logLevel, "test");
            verify(loggerMock, times(1)).log(logLevel, "te%s", "st");
            verify(loggerMock, times(1)).log(logLevel, "te%s%d", "st", 1);
            verify(loggerMock, times(1)).log(logLevel, "te%s%d", argsArray);
            verify(loggerMock, times(1)).log(logLevel, "test", throwable);
            verify(loggerMock, times(1)).log(logLevel, throwable);
        }
    }

    @Test
    void test_isLevelEnabled_passThroughToDelegate() {
        WarnErrLoggerFilter wrapped = new WarnErrLoggerFilter(loggerMock, false, false);

        wrapped.isTraceEnabled();
        wrapped.isDebugEnabled();
        wrapped.isInfoEnabled();
        wrapped.isWarnEnabled();
        wrapped.isErrorEnabled();

        verify(loggerMock, times(1)).isTraceEnabled();
        verify(loggerMock, times(1)).isDebugEnabled();
        verify(loggerMock, times(1)).isInfoEnabled();
        verify(loggerMock, times(1)).isWarnEnabled();
        verify(loggerMock, times(1)).isErrorEnabled();

        assertEquals(loggerMock.isTraceEnabled(), wrapped.isTraceEnabled());
        assertEquals(loggerMock.isDebugEnabled(), wrapped.isDebugEnabled());
        assertEquals(loggerMock.isInfoEnabled(), wrapped.isInfoEnabled());
        assertEquals(loggerMock.isWarnEnabled(), wrapped.isWarnEnabled());
        assertEquals(loggerMock.isErrorEnabled(), wrapped.isErrorEnabled());
    }

    @Test
    void test_isEnabled_passThroughToDelegate() {
        WarnErrLoggerFilter wrapped = new WarnErrLoggerFilter(loggerMock, false, false);

        wrapped.isEnabled(InternalLogLevel.TRACE);
        wrapped.isEnabled(InternalLogLevel.DEBUG);
        wrapped.isEnabled(InternalLogLevel.INFO);
        wrapped.isEnabled(InternalLogLevel.WARN);
        wrapped.isEnabled(InternalLogLevel.ERROR);

        verify(loggerMock, times(1)).isEnabled(InternalLogLevel.TRACE);
        verify(loggerMock, times(1)).isEnabled(InternalLogLevel.DEBUG);
        verify(loggerMock, times(1)).isEnabled(InternalLogLevel.INFO);
        verify(loggerMock, times(1)).isEnabled(InternalLogLevel.WARN);
        verify(loggerMock, times(1)).isEnabled(InternalLogLevel.ERROR);

        assertEquals(loggerMock.isEnabled(InternalLogLevel.TRACE), wrapped.isEnabled(InternalLogLevel.TRACE));
        assertEquals(loggerMock.isEnabled(InternalLogLevel.DEBUG), wrapped.isEnabled(InternalLogLevel.DEBUG));
        assertEquals(loggerMock.isEnabled(InternalLogLevel.INFO), wrapped.isEnabled(InternalLogLevel.INFO));
        assertEquals(loggerMock.isEnabled(InternalLogLevel.WARN), wrapped.isEnabled(InternalLogLevel.WARN));
        assertEquals(loggerMock.isEnabled(InternalLogLevel.ERROR), wrapped.isEnabled(InternalLogLevel.ERROR));
    }

    @Test
    void test_assertNameIsTakenFromDelegate() {
        WarnErrLoggerFilter wrapped = new WarnErrLoggerFilter(loggerMock, false, false);

        wrapped.name();

        verify(loggerMock, times(1)).name();

        assertEquals(loggerMock.name(), wrapped.name());
    }

    private void useAllWarnMethods(InternalLogger logger) {
        logger.warn("test");
        logger.warn("te%s", "st");
        logger.warn("te%s%d", "st", 1);
        logger.warn("te%s%d", argsArray);
        logger.warn("test", throwable);
        logger.warn(throwable);
    }

    private void useAllErrorMethods(InternalLogger logger) {
        logger.error("test");
        logger.error("te%s", "st");
        logger.error("te%s%d", "st", 1);
        logger.error("te%s%d", argsArray);
        logger.error("test", throwable);
        logger.error(throwable);
    }

    private void useAllLogMethods(InternalLogger logger, InternalLogLevel level) {
        logger.log(level, "test");
        logger.log(level, "te%s", "st");
        logger.log(level, "te%s%d", "st", 1);
        logger.log(level, "te%s%d", argsArray);
        logger.log(level, "test", throwable);
        logger.log(level, throwable);
    }

}
