package dev.elim.profiler.common.model;

public enum AlertSeverity {
    LOW(4, "<yellow>LOW</yellow>"),
    MEDIUM(8, "<gold>MEDIUM</gold>"),
    HIGH(12, "<red>HIGH</red>"),
    CRITICAL(16, "<dark_red>CRITICAL</dark_red>");

    private final int defaultRisk;
    private final String displayName;

    AlertSeverity(int defaultRisk, String displayName) {
        this.defaultRisk = defaultRisk;
        this.displayName = displayName;
    }

    public int defaultRisk() {
        return defaultRisk;
    }

    public String displayName() {
        return displayName;
    }
}
