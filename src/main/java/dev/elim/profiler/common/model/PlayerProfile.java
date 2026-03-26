package dev.elim.profiler.common.model;

import dev.elim.profiler.common.config.ProfilerConfig;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PlayerProfile {
    private final UUID uniqueId;
    private final Map<String, AlertRecord> activeAlerts = new LinkedHashMap<>();
    private final Deque<AlertRecord> archivedAlerts = new ArrayDeque<>();
    private final Deque<Instant> serverHops = new ArrayDeque<>();
    private final PlayerSnapshot snapshot = new PlayerSnapshot();

    private String lastKnownName;
    private String currentServer = "unknown";
    private String remoteAddress = "unknown";
    private String virtualHost = "unknown";
    private boolean frozen;
    private int riskScore;
    private Instant firstSeen = Instant.now();
    private Instant lastSeen = Instant.now();
    private Instant lastAlertSeen;

    public PlayerProfile(UUID uniqueId, String lastKnownName) {
        this.uniqueId = uniqueId;
        this.lastKnownName = lastKnownName;
    }

    public synchronized AlertRecord addAlert(
            String fingerprint,
            String code,
            AlertSeverity severity,
            int riskDelta,
            String sourceServer,
            String message,
            ProfilerConfig config
    ) {
        cleanupExpired(config);
        Instant now = Instant.now();
        AlertRecord existing = activeAlerts.get(fingerprint);
        if (existing != null) {
            existing.stack(message, now);
            lastAlertSeen = now;
            recalculateRisk();
            return existing.copy();
        }

        AlertRecord record = new AlertRecord(fingerprint, code, severity, riskDelta, sourceServer, message, now);
        activeAlerts.put(fingerprint, record);
        lastAlertSeen = now;
        recalculateRisk();
        return record.copy();
    }

    public synchronized void cleanupExpired(ProfilerConfig config) {
        Instant cutoff = Instant.now().minusSeconds(config.activeAlertMinutes() * 60L);
        Iterator<Map.Entry<String, AlertRecord>> iterator = activeAlerts.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, AlertRecord> entry = iterator.next();
            AlertRecord record = entry.getValue();
            if (record.lastSeen().isBefore(cutoff)) {
                archivedAlerts.addFirst(record.copy());
                iterator.remove();
            }
        }
        while (archivedAlerts.size() > config.archivedAlertLimit()) {
            archivedAlerts.removeLast();
        }
        recalculateRisk();
    }

    public synchronized void recalculateRisk() {
        this.riskScore = activeAlerts.values().stream().mapToInt(AlertRecord::effectiveRisk).sum();
    }

    public synchronized List<AlertRecord> activeAlerts() {
        return activeAlerts.values().stream()
                .sorted((left, right) -> right.lastSeen().compareTo(left.lastSeen()))
                .map(AlertRecord::copy)
                .toList();
    }

    public synchronized List<AlertRecord> archivedAlerts() {
        return archivedAlerts.stream().map(AlertRecord::copy).toList();
    }

    public synchronized List<AlertRecord> alertHistory(int limit) {
        List<AlertRecord> alerts = new ArrayList<>();
        alerts.addAll(activeAlerts());
        if (alerts.size() < limit) {
            for (AlertRecord record : archivedAlerts) {
                if (alerts.size() >= limit) {
                    break;
                }
                alerts.add(record.copy());
            }
        }
        return alerts;
    }

    public synchronized void recordHop(int windowSeconds) {
        Instant now = Instant.now();
        serverHops.addLast(now);
        Instant cutoff = now.minusSeconds(windowSeconds);
        while (!serverHops.isEmpty() && serverHops.peekFirst().isBefore(cutoff)) {
            serverHops.removeFirst();
        }
    }

    public synchronized int currentHopCount() {
        return serverHops.size();
    }

    public synchronized UUID uniqueId() {
        return uniqueId;
    }

    public synchronized String lastKnownName() {
        return lastKnownName;
    }

    public synchronized void lastKnownName(String lastKnownName) {
        this.lastKnownName = lastKnownName;
        this.lastSeen = Instant.now();
    }

    public synchronized String currentServer() {
        return currentServer;
    }

    public synchronized void currentServer(String currentServer) {
        this.currentServer = currentServer;
        this.snapshot.currentServer(currentServer);
        this.lastSeen = Instant.now();
    }

    public synchronized String remoteAddress() {
        return remoteAddress;
    }

    public synchronized void remoteAddress(String remoteAddress) {
        this.remoteAddress = remoteAddress;
        this.lastSeen = Instant.now();
    }

    public synchronized String virtualHost() {
        return virtualHost;
    }

    public synchronized void virtualHost(String virtualHost) {
        this.virtualHost = virtualHost;
        this.lastSeen = Instant.now();
    }

    public synchronized boolean frozen() {
        return frozen;
    }

    public synchronized void frozen(boolean frozen) {
        this.frozen = frozen;
        this.lastSeen = Instant.now();
    }

    public synchronized int riskScore() {
        return riskScore;
    }

    public synchronized Instant firstSeen() {
        return firstSeen;
    }

    public synchronized Instant lastSeen() {
        return lastSeen;
    }

    public synchronized Instant lastAlertSeen() {
        return lastAlertSeen;
    }

    public synchronized PlayerSnapshot snapshot() {
        return snapshot.copy();
    }

    public synchronized void mergeSnapshot(PlayerSnapshot update) {
        if (update.ping() >= 0) {
            snapshot.ping(update.ping());
        }
        if (update.protocolVersion() >= 0) {
            snapshot.protocolVersion(update.protocolVersion());
        }
        if (!update.currentServer().isBlank() && !"unknown".equalsIgnoreCase(update.currentServer())) {
            snapshot.currentServer(update.currentServer());
            this.currentServer = update.currentServer();
        }
        if (!update.clientBrand().isBlank() && !"unknown".equalsIgnoreCase(update.clientBrand())) {
            snapshot.clientBrand(update.clientBrand());
        }
        if (!update.modSummary().isBlank() && !"unknown".equalsIgnoreCase(update.modSummary())) {
            snapshot.modSummary(update.modSummary());
        }
        if (!update.locale().isBlank() && !"unknown".equalsIgnoreCase(update.locale())) {
            snapshot.locale(update.locale());
        }
        if (update.viewDistance() >= 0) {
            snapshot.viewDistance(update.viewDistance());
        }
        snapshot.averageCps(update.averageCps());
        snapshot.peakCps(update.peakCps());
        snapshot.attacksPerSecond(update.attacksPerSecond());
        snapshot.averageSpeed(update.averageSpeed());
        snapshot.maxSpeed(update.maxSpeed());
        snapshot.maxReach(update.maxReach());
        snapshot.maxRotation(update.maxRotation());
        snapshot.placementsPerSecond(update.placementsPerSecond());
        if (!update.lastProbeSummary().isBlank() && !"-".equals(update.lastProbeSummary())) {
            snapshot.lastProbeSummary(update.lastProbeSummary());
        }
        this.lastSeen = Instant.now();
    }

    public synchronized void ping(long ping) {
        snapshot.ping(ping);
        this.lastSeen = Instant.now();
    }

    public synchronized void protocolVersion(int protocolVersion) {
        snapshot.protocolVersion(protocolVersion);
        this.lastSeen = Instant.now();
    }

    public synchronized void clientBrand(String brand) {
        snapshot.clientBrand(brand);
        this.lastSeen = Instant.now();
    }

    public synchronized void modSummary(String modSummary) {
        snapshot.modSummary(modSummary);
        this.lastSeen = Instant.now();
    }

    public synchronized void locale(String locale) {
        snapshot.locale(locale);
        this.lastSeen = Instant.now();
    }

    public synchronized void viewDistance(int viewDistance) {
        snapshot.viewDistance(viewDistance);
        this.lastSeen = Instant.now();
    }
}
