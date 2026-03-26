package dev.elim.profiler.common.util;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;

public final class FormatUtil {
    private FormatUtil() {
    }

    public static String replacePlaceholders(String input, Map<String, String> placeholders) {
        String result = input;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }

    public static String ageSince(Instant instant) {
        if (instant == null) {
            return "never";
        }
        Duration duration = Duration.between(instant, Instant.now()).abs();
        long seconds = duration.getSeconds();
        if (seconds < 60) {
            return seconds + "s";
        }
        long minutes = seconds / 60;
        if (minutes < 60) {
            return minutes + "m";
        }
        long hours = minutes / 60;
        if (hours < 48) {
            return hours + "h";
        }
        long days = hours / 24;
        return days + "d";
    }

    public static String formatDouble(double value) {
        if (value < 0) {
            return "-";
        }
        return String.format(Locale.US, "%.2f", value);
    }

    public static String countSuffix(int count) {
        return count > 1 ? "x" + count : "";
    }

    public static String sanitize(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }
}
