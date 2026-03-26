package dev.elim.profiler.common.model;

import java.time.Instant;

public final class AlertRecord {
    private final String fingerprint;
    private final String code;
    private final AlertSeverity severity;
    private final int riskDelta;
    private final String sourceServer;
    private final Instant firstSeen;
    private String message;
    private Instant lastSeen;
    private int stackCount;

    public AlertRecord(
            String fingerprint,
            String code,
            AlertSeverity severity,
            int riskDelta,
            String sourceServer,
            String message,
            Instant seenAt
    ) {
        this.fingerprint = fingerprint;
        this.code = code;
        this.severity = severity;
        this.riskDelta = riskDelta;
        this.sourceServer = sourceServer;
        this.message = message;
        this.firstSeen = seenAt;
        this.lastSeen = seenAt;
        this.stackCount = 1;
    }

    public AlertRecord copy() {
        AlertRecord record = new AlertRecord(fingerprint, code, severity, riskDelta, sourceServer, message, firstSeen);
        record.message = message;
        record.lastSeen = lastSeen;
        record.stackCount = stackCount;
        return record;
    }

    public void stack(String updatedMessage, Instant seenAt) {
        this.message = updatedMessage;
        this.lastSeen = seenAt;
        this.stackCount++;
    }

    public int effectiveRisk() {
        return riskDelta * Math.min(stackCount, 5);
    }

    public String fingerprint() {
        return fingerprint;
    }

    public String code() {
        return code;
    }

    public AlertSeverity severity() {
        return severity;
    }

    public int riskDelta() {
        return riskDelta;
    }

    public String sourceServer() {
        return sourceServer;
    }

    public String message() {
        return message;
    }

    public Instant firstSeen() {
        return firstSeen;
    }

    public Instant lastSeen() {
        return lastSeen;
    }

    public int stackCount() {
        return stackCount;
    }
}
