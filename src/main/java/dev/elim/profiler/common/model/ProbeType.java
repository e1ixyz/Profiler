package dev.elim.profiler.common.model;

public enum ProbeType {
    LIVE,
    MOVEMENT,
    COMBAT,
    BUILD,
    CLICKS;

    public static ProbeType fromInput(String input) {
        for (ProbeType type : values()) {
            if (type.name().equalsIgnoreCase(input)) {
                return type;
            }
        }
        return null;
    }
}
