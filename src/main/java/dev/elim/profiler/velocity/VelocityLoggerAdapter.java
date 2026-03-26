package dev.elim.profiler.velocity;

import dev.elim.profiler.common.util.ProfilerLogger;
import org.slf4j.Logger;

public final class VelocityLoggerAdapter implements ProfilerLogger {
    private final Logger logger;

    public VelocityLoggerAdapter(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void info(String message) {
        logger.info(message);
    }

    @Override
    public void warn(String message) {
        logger.warn(message);
    }

    @Override
    public void error(String message, Throwable throwable) {
        logger.error(message, throwable);
    }
}
