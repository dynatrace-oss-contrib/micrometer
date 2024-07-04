package io.micrometer.dynatrace.types;

import io.micrometer.common.util.internal.logging.InternalLogLevel;
import io.micrometer.common.util.internal.logging.InternalLogger;

public class WarnErrLoggerFilter implements InternalLogger {
    private final InternalLogger delegate;
    private final boolean shouldLogError;
    private final boolean shouldLogWarning;

    public WarnErrLoggerFilter(InternalLogger delegate, boolean shouldLogError, boolean shouldLogWarning) {
        this.delegate = delegate;
        this.shouldLogError = shouldLogError;
        this.shouldLogWarning = shouldLogWarning;
    }

    @Override
    public String name() {
        return delegate.name();
    }

    @Override
    public boolean isTraceEnabled() {
        return delegate.isTraceEnabled();
    }

    @Override
    public void trace(String msg) {
        delegate.trace(msg);
    }

    @Override
    public void trace(String format, Object arg) {
        delegate.trace(format, arg);
    }

    @Override
    public void trace(String format, Object argA, Object argB) {
        delegate.trace(format, argA, argB);
    }

    @Override
    public void trace(String format, Object... arguments) {
        delegate.trace(format, arguments);
    }

    @Override
    public void trace(String msg, Throwable t) {
        delegate.trace(msg, t);
    }

    @Override
    public void trace(Throwable t) {
        delegate.trace(t);
    }

    @Override
    public boolean isDebugEnabled() {
        return delegate.isDebugEnabled();
    }

    @Override
    public void debug(String msg) {
        delegate.debug(msg);
    }

    @Override
    public void debug(String format, Object arg) {
        delegate.debug(format, arg);
    }

    @Override
    public void debug(String format, Object argA, Object argB) {
        delegate.debug(format, argA, argB);
    }

    @Override
    public void debug(String format, Object... arguments) {
        delegate.debug(format, arguments);
    }

    @Override
    public void debug(String msg, Throwable t) {
        delegate.debug(msg, t);
    }

    @Override
    public void debug(Throwable t) {
        delegate.debug(t);
    }

    @Override
    public boolean isInfoEnabled() {
        return delegate.isInfoEnabled();
    }

    @Override
    public void info(String msg) {
        delegate.info(msg);
    }

    @Override
    public void info(String format, Object arg) {
        delegate.info(format, arg);
    }

    @Override
    public void info(String format, Object argA, Object argB) {
        delegate.info(format, argA, argB);
    }

    @Override
    public void info(String format, Object... arguments) {
        delegate.info(format, arguments);
    }

    @Override
    public void info(String msg, Throwable t) {
        delegate.info(msg, t);
    }

    @Override
    public void info(Throwable t) {
        delegate.info(t);
    }

    @Override
    public boolean isWarnEnabled() {
        return delegate.isWarnEnabled();
    }

    @Override
    public void warn(String msg) {
        if (!shouldLogWarning) {
            delegate.info(msg);
        } else {
            delegate.warn(msg);
        }
    }

    @Override
    public void warn(String format, Object arg) {
        if (!shouldLogWarning) {
            delegate.info(format, arg);
        } else {
            delegate.warn(format, arg);
        }
    }

    @Override
    public void warn(String format, Object... arguments) {
        if (!shouldLogWarning) {
            delegate.info(format, arguments);
        } else {
            delegate.warn(format, arguments);
        }
    }

    @Override
    public void warn(String format, Object argA, Object argB) {
        if (!shouldLogWarning) {
            delegate.info(format, argA, argB);
        } else {
            delegate.warn(format, argA, argB);
        }
    }

    @Override
    public void warn(String msg, Throwable t) {
        if (!shouldLogWarning) {
            delegate.info(msg, t);
        } else {
            delegate.warn(msg, t);
        }
    }

    @Override
    public void warn(Throwable t) {
        if (!shouldLogWarning) {
            delegate.info(t);
        } else {
            delegate.warn(t);
        }
    }

    @Override
    public boolean isErrorEnabled() {
        return delegate.isErrorEnabled();
    }

    @Override
    public void error(String msg) {
        if (!shouldLogError) {
            delegate.info(msg);
        } else {
            delegate.error(msg);
        }
    }

    @Override
    public void error(String format, Object arg) {
        if (!shouldLogError) {
            delegate.info(format, arg);
        } else {
            delegate.error(format, arg);
        }
    }

    @Override
    public void error(String format, Object argA, Object argB) {
        if (!shouldLogError) {
            delegate.info(format, argA, argB);
        } else {
            delegate.error(format, argA, argB);
        }
    }

    @Override
    public void error(String format, Object... arguments) {
        if (!shouldLogError) {
            delegate.info(format, arguments);
        } else {
            delegate.error(format, arguments);
        }
    }

    @Override
    public void error(String msg, Throwable t) {
        if (!shouldLogError) {
            delegate.info(msg, t);
        } else {
            delegate.error(msg, t);
        }
    }

    @Override
    public void error(Throwable t) {
        if (!shouldLogError) {
            delegate.info(t);
        } else {
            delegate.error(t);
        }
    }

    @Override
    public boolean isEnabled(InternalLogLevel level) {
        return delegate.isEnabled(level);
    }

    @Override
    public void log(InternalLogLevel level, String msg) {
        if (level == InternalLogLevel.ERROR) {
            error(msg);
        } else if (level == InternalLogLevel.WARN) {
            warn(msg);
        } else {
            delegate.log(level, msg);
        }
    }

    @Override
    public void log(InternalLogLevel level, String format, Object arg) {
        if (level == InternalLogLevel.ERROR) {
            error(format, arg);
        } else if (level == InternalLogLevel.WARN) {
            warn(format, arg);
        } else {
            delegate.log(level, format, arg);
        }
    }

    @Override
    public void log(InternalLogLevel level, String format, Object argA, Object argB) {
        if (level == InternalLogLevel.ERROR) {
            error(format, argA, argB);
        } else if (level == InternalLogLevel.WARN) {
            warn(format, argA, argB);
        } else {
            delegate.log(level, format, argA, argB);
        }
    }

    @Override
    public void log(InternalLogLevel level, String format, Object... arguments) {
        if (level == InternalLogLevel.ERROR) {
            error(format, arguments);
        } else if (level == InternalLogLevel.WARN) {
            warn(format, arguments);
        } else {
            delegate.log(level, format, arguments);
        }
    }

    @Override
    public void log(InternalLogLevel level, String msg, Throwable t) {
        if (level == InternalLogLevel.ERROR) {
            error(msg, t);
        } else if (level == InternalLogLevel.WARN) {
            warn(msg, t);
        } else {
            delegate.log(level, msg, t);
        }
    }

    @Override
    public void log(InternalLogLevel level, Throwable t) {
        if (level == InternalLogLevel.ERROR) {
            error(t);
        } else if (level == InternalLogLevel.WARN) {
            warn(t);
        } else {
            delegate.log(level, t);
        }
    }
}
