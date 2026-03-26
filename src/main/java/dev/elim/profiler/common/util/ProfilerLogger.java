package dev.elim.profiler.common.util;

public interface ProfilerLogger {
    void info(String message);

    void warn(String message);

    void error(String message, Throwable throwable);
}
