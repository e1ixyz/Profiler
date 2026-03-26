package dev.elim.profiler.paper;

import dev.elim.profiler.common.model.PlayerSnapshot;
import dev.elim.profiler.common.model.ProbeType;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

public final class PlayerTelemetry {
    private static final int SAMPLE_LIMIT = 40;
    private static final int INTERVAL_LIMIT = 30;

    private final Deque<Long> clickTimes = new ArrayDeque<>();
    private final Deque<Long> clickIntervals = new ArrayDeque<>();
    private final Deque<Long> attackTimes = new ArrayDeque<>();
    private final Deque<Long> placeTimes = new ArrayDeque<>();
    private final Deque<Double> speedSamples = new ArrayDeque<>();
    private final Deque<Double> rotationSamples = new ArrayDeque<>();
    private final Map<String, Long> alertCooldowns = new HashMap<>();

    private long lastClickAt = -1L;
    private long lastMoveAt = -1L;
    private double peakCps = 0.0D;
    private double peakReach = 0.0D;
    private String lastProbeSummary = "-";
    private ProbeSession probeSession;

    public void recordClick(long now) {
        if (lastClickAt > 0) {
            clickIntervals.addLast(now - lastClickAt);
            while (clickIntervals.size() > INTERVAL_LIMIT) {
                clickIntervals.removeFirst();
            }
        }
        lastClickAt = now;
        clickTimes.addLast(now);
        trimWindow(clickTimes, now - 1000L);
        peakCps = Math.max(peakCps, clickTimes.size());
        if (probeSession != null) {
            probeSession.captureClicks(clickTimes.size());
        }
    }

    public void recordAttack(long now, double reach) {
        attackTimes.addLast(now);
        trimWindow(attackTimes, now - 1000L);
        peakReach = Math.max(peakReach, reach);
        if (probeSession != null) {
            probeSession.captureAttacks(attackTimes.size(), reach);
        }
    }

    public void recordPlace(long now) {
        placeTimes.addLast(now);
        trimWindow(placeTimes, now - 1000L);
        if (probeSession != null) {
            probeSession.capturePlace(placeTimes.size());
        }
    }

    public MovementSample recordMove(long now, double horizontalDistance, double rotationDelta) {
        double speed = 0.0D;
        if (lastMoveAt > 0 && horizontalDistance > 0.0D) {
            double seconds = Math.max(0.05D, (now - lastMoveAt) / 1000.0D);
            speed = horizontalDistance / seconds;
            speedSamples.addLast(speed);
            while (speedSamples.size() > SAMPLE_LIMIT) {
                speedSamples.removeFirst();
            }
        }
        lastMoveAt = now;
        if (rotationDelta > 0.0D) {
            rotationSamples.addLast(rotationDelta);
            while (rotationSamples.size() > SAMPLE_LIMIT) {
                rotationSamples.removeFirst();
            }
        }
        if (probeSession != null) {
            probeSession.captureMovement(speed, rotationDelta);
        }
        return new MovementSample(speed, rotationDelta);
    }

    public boolean allowAlert(String key, long cooldownMillis) {
        long now = System.currentTimeMillis();
        long last = alertCooldowns.getOrDefault(key, 0L);
        if (now - last < cooldownMillis) {
            return false;
        }
        alertCooldowns.put(key, now);
        return true;
    }

    public double currentCps() {
        trimWindow(clickTimes, System.currentTimeMillis() - 1000L);
        return clickTimes.size();
    }

    public double currentAps() {
        trimWindow(attackTimes, System.currentTimeMillis() - 1000L);
        return attackTimes.size();
    }

    public double currentPlaceRate() {
        trimWindow(placeTimes, System.currentTimeMillis() - 1000L);
        return placeTimes.size();
    }

    public double clickStdDev() {
        if (clickIntervals.size() < 6) {
            return Double.MAX_VALUE;
        }
        double average = clickIntervals.stream().mapToLong(Long::longValue).average().orElse(0.0D);
        double variance = 0.0D;
        for (long interval : clickIntervals) {
            double delta = interval - average;
            variance += delta * delta;
        }
        variance /= clickIntervals.size();
        return Math.sqrt(variance);
    }

    public double averageSpeed() {
        return speedSamples.stream().mapToDouble(Double::doubleValue).average().orElse(0.0D);
    }

    public double maxSpeed() {
        return speedSamples.stream().mapToDouble(Double::doubleValue).max().orElse(0.0D);
    }

    public double maxRotation() {
        return rotationSamples.stream().mapToDouble(Double::doubleValue).max().orElse(0.0D);
    }

    public double peakCps() {
        return peakCps;
    }

    public double peakReach() {
        return peakReach;
    }

    public String lastProbeSummary() {
        return lastProbeSummary;
    }

    public void startProbe(ProbeType type, String actor, int durationSeconds) {
        this.probeSession = new ProbeSession(type, actor, durationSeconds);
    }

    public ProbeSession probeSession() {
        return probeSession;
    }

    public ProbeSession finishProbe() {
        ProbeSession finished = probeSession;
        if (finished != null) {
            this.lastProbeSummary = finished.summary();
        }
        this.probeSession = null;
        return finished;
    }

    public PlayerSnapshot buildSnapshot(String serverName, long ping, int viewDistance) {
        PlayerSnapshot snapshot = new PlayerSnapshot();
        snapshot.currentServer(serverName);
        snapshot.ping(ping);
        snapshot.protocolVersion(-1);
        snapshot.clientBrand("");
        snapshot.modSummary("");
        snapshot.locale("");
        snapshot.viewDistance(viewDistance);
        snapshot.averageCps(currentCps());
        snapshot.peakCps(peakCps());
        snapshot.attacksPerSecond(currentAps());
        snapshot.averageSpeed(averageSpeed());
        snapshot.maxSpeed(maxSpeed());
        snapshot.maxReach(peakReach());
        snapshot.maxRotation(maxRotation());
        snapshot.placementsPerSecond(currentPlaceRate());
        snapshot.lastProbeSummary(lastProbeSummary);
        return snapshot;
    }

    private void trimWindow(Deque<Long> timestamps, long cutoff) {
        while (!timestamps.isEmpty() && timestamps.peekFirst() < cutoff) {
            timestamps.removeFirst();
        }
    }

    public record MovementSample(double speed, double rotation) {
    }
}
