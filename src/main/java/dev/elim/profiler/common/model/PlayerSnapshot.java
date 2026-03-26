package dev.elim.profiler.common.model;

import java.time.Instant;

public final class PlayerSnapshot {
    private long ping = -1L;
    private int protocolVersion = -1;
    private String currentServer = "unknown";
    private String clientBrand = "unknown";
    private String modSummary = "vanilla";
    private String locale = "unknown";
    private int viewDistance = -1;
    private double averageCps = 0.0D;
    private double peakCps = 0.0D;
    private double attacksPerSecond = 0.0D;
    private double averageSpeed = 0.0D;
    private double maxSpeed = 0.0D;
    private double maxReach = 0.0D;
    private double maxRotation = 0.0D;
    private double placementsPerSecond = 0.0D;
    private String lastProbeSummary = "-";
    private Instant updatedAt = Instant.now();

    public PlayerSnapshot copy() {
        PlayerSnapshot snapshot = new PlayerSnapshot();
        snapshot.ping = ping;
        snapshot.protocolVersion = protocolVersion;
        snapshot.currentServer = currentServer;
        snapshot.clientBrand = clientBrand;
        snapshot.modSummary = modSummary;
        snapshot.locale = locale;
        snapshot.viewDistance = viewDistance;
        snapshot.averageCps = averageCps;
        snapshot.peakCps = peakCps;
        snapshot.attacksPerSecond = attacksPerSecond;
        snapshot.averageSpeed = averageSpeed;
        snapshot.maxSpeed = maxSpeed;
        snapshot.maxReach = maxReach;
        snapshot.maxRotation = maxRotation;
        snapshot.placementsPerSecond = placementsPerSecond;
        snapshot.lastProbeSummary = lastProbeSummary;
        snapshot.updatedAt = updatedAt;
        return snapshot;
    }

    public long ping() {
        return ping;
    }

    public void ping(long ping) {
        this.ping = ping;
        this.updatedAt = Instant.now();
    }

    public int protocolVersion() {
        return protocolVersion;
    }

    public void protocolVersion(int protocolVersion) {
        this.protocolVersion = protocolVersion;
        this.updatedAt = Instant.now();
    }

    public String currentServer() {
        return currentServer;
    }

    public void currentServer(String currentServer) {
        this.currentServer = currentServer;
        this.updatedAt = Instant.now();
    }

    public String clientBrand() {
        return clientBrand;
    }

    public void clientBrand(String clientBrand) {
        this.clientBrand = clientBrand;
        this.updatedAt = Instant.now();
    }

    public String modSummary() {
        return modSummary;
    }

    public void modSummary(String modSummary) {
        this.modSummary = modSummary;
        this.updatedAt = Instant.now();
    }

    public String locale() {
        return locale;
    }

    public void locale(String locale) {
        this.locale = locale;
        this.updatedAt = Instant.now();
    }

    public int viewDistance() {
        return viewDistance;
    }

    public void viewDistance(int viewDistance) {
        this.viewDistance = viewDistance;
        this.updatedAt = Instant.now();
    }

    public double averageCps() {
        return averageCps;
    }

    public void averageCps(double averageCps) {
        this.averageCps = averageCps;
        this.updatedAt = Instant.now();
    }

    public double peakCps() {
        return peakCps;
    }

    public void peakCps(double peakCps) {
        this.peakCps = peakCps;
        this.updatedAt = Instant.now();
    }

    public double attacksPerSecond() {
        return attacksPerSecond;
    }

    public void attacksPerSecond(double attacksPerSecond) {
        this.attacksPerSecond = attacksPerSecond;
        this.updatedAt = Instant.now();
    }

    public double averageSpeed() {
        return averageSpeed;
    }

    public void averageSpeed(double averageSpeed) {
        this.averageSpeed = averageSpeed;
        this.updatedAt = Instant.now();
    }

    public double maxSpeed() {
        return maxSpeed;
    }

    public void maxSpeed(double maxSpeed) {
        this.maxSpeed = maxSpeed;
        this.updatedAt = Instant.now();
    }

    public double maxReach() {
        return maxReach;
    }

    public void maxReach(double maxReach) {
        this.maxReach = maxReach;
        this.updatedAt = Instant.now();
    }

    public double maxRotation() {
        return maxRotation;
    }

    public void maxRotation(double maxRotation) {
        this.maxRotation = maxRotation;
        this.updatedAt = Instant.now();
    }

    public double placementsPerSecond() {
        return placementsPerSecond;
    }

    public void placementsPerSecond(double placementsPerSecond) {
        this.placementsPerSecond = placementsPerSecond;
        this.updatedAt = Instant.now();
    }

    public String lastProbeSummary() {
        return lastProbeSummary;
    }

    public void lastProbeSummary(String lastProbeSummary) {
        this.lastProbeSummary = lastProbeSummary;
        this.updatedAt = Instant.now();
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
