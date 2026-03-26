package dev.elim.profiler.common.io;

import dev.elim.profiler.common.model.AlertRecord;
import dev.elim.profiler.common.model.PlayerProfile;
import dev.elim.profiler.common.util.ProfilerLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class AlertLogWriter implements AutoCloseable {
    private final ProfilerLogger logger;
    private final Path logFile;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "profiler-log-writer");
        thread.setDaemon(true);
        return thread;
    });

    public AlertLogWriter(Path dataDirectory, ProfilerLogger logger) {
        this.logger = logger;
        try {
            Path logsDirectory = dataDirectory.resolve("logs");
            Files.createDirectories(logsDirectory);
            this.logFile = logsDirectory.resolve("alerts.jsonl");
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to create Profiler log directory.", exception);
        }
    }

    public void logAlert(PlayerProfile profile, AlertRecord alert) {
        submit(Map.of(
                "timestamp", Instant.now().toString(),
                "player", profile.lastKnownName(),
                "uuid", profile.uniqueId().toString(),
                "server", alert.sourceServer(),
                "code", alert.code(),
                "severity", alert.severity().name(),
                "stack", String.valueOf(alert.stackCount()),
                "message", alert.message()
        ));
    }

    public void logEvent(String type, String player, String details) {
        submit(Map.of(
                "timestamp", Instant.now().toString(),
                "type", type,
                "player", player,
                "details", details
        ));
    }

    private void submit(Map<String, String> values) {
        executor.execute(() -> {
            try {
                Files.writeString(
                        logFile,
                        toJson(values) + System.lineSeparator(),
                        StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.APPEND
                );
            } catch (IOException exception) {
                logger.error("Failed to append Profiler log record.", exception);
            }
        });
    }

    private String toJson(Map<String, String> values) {
        StringBuilder builder = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append('"').append(escape(entry.getKey())).append('"');
            builder.append(':');
            builder.append('"').append(escape(entry.getValue())).append('"');
        }
        builder.append('}');
        return builder.toString();
    }

    private String escape(String input) {
        return input
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    @Override
    public void close() {
        executor.shutdown();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
