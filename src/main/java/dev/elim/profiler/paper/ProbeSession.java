package dev.elim.profiler.paper;

import dev.elim.profiler.common.model.ProbeType;
import dev.elim.profiler.common.util.FormatUtil;

import java.util.ArrayList;
import java.util.List;

public final class ProbeSession {
    private final ProbeType type;
    private final String actor;
    private final long endsAt;
    private final List<Double> cpsSamples = new ArrayList<>();
    private final List<Double> apsSamples = new ArrayList<>();
    private final List<Double> speedSamples = new ArrayList<>();
    private final List<Double> reachSamples = new ArrayList<>();
    private final List<Double> rotationSamples = new ArrayList<>();
    private final List<Double> placeSamples = new ArrayList<>();

    public ProbeSession(ProbeType type, String actor, int durationSeconds) {
        this.type = type;
        this.actor = actor;
        this.endsAt = System.currentTimeMillis() + (durationSeconds * 1000L);
    }

    public ProbeType type() {
        return type;
    }

    public String actor() {
        return actor;
    }

    public boolean expired(long now) {
        return now >= endsAt;
    }

    public void captureClicks(double cps) {
        cpsSamples.add(cps);
    }

    public void captureAttacks(double aps, double reach) {
        apsSamples.add(aps);
        if (reach > 0) {
            reachSamples.add(reach);
        }
    }

    public void captureMovement(double speed, double rotation) {
        if (speed > 0) {
            speedSamples.add(speed);
        }
        if (rotation > 0) {
            rotationSamples.add(rotation);
        }
    }

    public void capturePlace(double placeRate) {
        placeSamples.add(placeRate);
    }

    public String summary() {
        return switch (type) {
            case LIVE -> "CPS " + metric(cpsSamples) + ", APS " + metric(apsSamples)
                    + ", speed " + metric(speedSamples)
                    + ", reach " + metric(reachSamples)
                    + ", rot " + metric(rotationSamples)
                    + ", place " + metric(placeSamples);
            case MOVEMENT -> "speed " + metric(speedSamples) + ", rot " + metric(rotationSamples);
            case COMBAT -> "CPS " + metric(cpsSamples) + ", APS " + metric(apsSamples)
                    + ", reach " + metric(reachSamples);
            case BUILD -> "place " + metric(placeSamples) + ", speed " + metric(speedSamples)
                    + ", rot " + metric(rotationSamples);
            case CLICKS -> "CPS " + metric(cpsSamples) + ", APS " + metric(apsSamples);
        };
    }

    private String metric(List<Double> values) {
        if (values.isEmpty()) {
            return "-";
        }
        double sum = 0.0D;
        double max = 0.0D;
        for (double value : values) {
            sum += value;
            max = Math.max(max, value);
        }
        return FormatUtil.formatDouble(sum / values.size()) + "/" + FormatUtil.formatDouble(max);
    }
}
