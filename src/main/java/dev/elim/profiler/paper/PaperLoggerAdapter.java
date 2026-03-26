package dev.elim.profiler.paper;

import dev.elim.profiler.common.util.ProfilerLogger;

import java.util.logging.Level;
import java.util.logging.Logger;

public final class PaperLoggerAdapter implements ProfilerLogger {
    private final Logger logger;

    public PaperLoggerAdapter(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void info(String message) {
        logger.info(message);
    }

    @Override
    public void warn(String message) {
        logger.warning(message);
    }

    @Override
    public void error(String message, Throwable throwable) {
        logger.log(Level.SEVERE, message, throwable);
    }
}
