package dev.elim.profiler.common.config;

import dev.elim.profiler.common.util.FormatUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings("unchecked")
public final class ProfilerConfig {
    private final int activeAlertMinutes;
    private final int archivedAlertLimit;
    private final int overviewLimit;
    private final int stackBroadcastInterval;
    private final boolean alertsDefaultEnabled;
    private final boolean consoleAlerts;
    private final int snapshotIntervalSeconds;
    private final int probeDurationSeconds;
    private final int reconnectWindowSeconds;
    private final int reconnectThreshold;
    private final int altAccountThreshold;
    private final int serverHopWindowSeconds;
    private final int serverHopThreshold;
    private final int clickWarnCps;
    private final int clickHighCps;
    private final int attackWarnAps;
    private final int attackHighAps;
    private final double reachWarn;
    private final double reachHigh;
    private final double speedWarn;
    private final double speedHigh;
    private final double rotationWarn;
    private final double rotationHigh;
    private final double placeWarn;
    private final double placeHigh;
    private final List<String> allowedFrozenCommands;
    private final Map<String, String> messages;

    private ProfilerConfig(
            int activeAlertMinutes,
            int archivedAlertLimit,
            int overviewLimit,
            int stackBroadcastInterval,
            boolean alertsDefaultEnabled,
            boolean consoleAlerts,
            int snapshotIntervalSeconds,
            int probeDurationSeconds,
            int reconnectWindowSeconds,
            int reconnectThreshold,
            int altAccountThreshold,
            int serverHopWindowSeconds,
            int serverHopThreshold,
            int clickWarnCps,
            int clickHighCps,
            int attackWarnAps,
            int attackHighAps,
            double reachWarn,
            double reachHigh,
            double speedWarn,
            double speedHigh,
            double rotationWarn,
            double rotationHigh,
            double placeWarn,
            double placeHigh,
            List<String> allowedFrozenCommands,
            Map<String, String> messages
    ) {
        this.activeAlertMinutes = activeAlertMinutes;
        this.archivedAlertLimit = archivedAlertLimit;
        this.overviewLimit = overviewLimit;
        this.stackBroadcastInterval = stackBroadcastInterval;
        this.alertsDefaultEnabled = alertsDefaultEnabled;
        this.consoleAlerts = consoleAlerts;
        this.snapshotIntervalSeconds = snapshotIntervalSeconds;
        this.probeDurationSeconds = probeDurationSeconds;
        this.reconnectWindowSeconds = reconnectWindowSeconds;
        this.reconnectThreshold = reconnectThreshold;
        this.altAccountThreshold = altAccountThreshold;
        this.serverHopWindowSeconds = serverHopWindowSeconds;
        this.serverHopThreshold = serverHopThreshold;
        this.clickWarnCps = clickWarnCps;
        this.clickHighCps = clickHighCps;
        this.attackWarnAps = attackWarnAps;
        this.attackHighAps = attackHighAps;
        this.reachWarn = reachWarn;
        this.reachHigh = reachHigh;
        this.speedWarn = speedWarn;
        this.speedHigh = speedHigh;
        this.rotationWarn = rotationWarn;
        this.rotationHigh = rotationHigh;
        this.placeWarn = placeWarn;
        this.placeHigh = placeHigh;
        this.allowedFrozenCommands = List.copyOf(allowedFrozenCommands);
        this.messages = Map.copyOf(messages);
    }

    public static ProfilerConfig from(Map<String, Object> root) {
        Map<String, Object> settings = childMap(root, "settings");
        Map<String, Object> thresholds = childMap(root, "thresholds");
        Map<String, Object> frozen = childMap(root, "frozen");
        Map<String, Object> rawMessages = childMap(root, "messages");
        Map<String, String> messages = new LinkedHashMap<>(defaults().messages());
        for (Map.Entry<String, Object> entry : rawMessages.entrySet()) {
            messages.put(entry.getKey(), String.valueOf(entry.getValue()));
        }

        return new ProfilerConfig(
                intValue(settings, "active-alert-minutes", 25),
                intValue(settings, "archived-alert-limit", 25),
                intValue(settings, "overview-limit", 12),
                intValue(settings, "stack-broadcast-interval", 5),
                boolValue(settings, "alerts-default-enabled", true),
                boolValue(settings, "console-alerts", true),
                intValue(settings, "snapshot-interval-seconds", 15),
                intValue(settings, "probe-duration-seconds", 15),
                intValue(settings, "reconnect-window-seconds", 30),
                intValue(settings, "reconnect-threshold", 4),
                intValue(settings, "alt-account-threshold", 2),
                intValue(settings, "server-hop-window-seconds", 20),
                intValue(settings, "server-hop-threshold", 4),
                intValue(thresholds, "click-warn-cps", 16),
                intValue(thresholds, "click-high-cps", 22),
                intValue(thresholds, "attack-warn-aps", 12),
                intValue(thresholds, "attack-high-aps", 17),
                doubleValue(thresholds, "reach-warn", 3.35D),
                doubleValue(thresholds, "reach-high", 3.80D),
                doubleValue(thresholds, "speed-warn", 0.82D),
                doubleValue(thresholds, "speed-high", 1.02D),
                doubleValue(thresholds, "rotation-warn", 95.0D),
                doubleValue(thresholds, "rotation-high", 145.0D),
                doubleValue(thresholds, "place-warn", 9.0D),
                doubleValue(thresholds, "place-high", 14.0D),
                stringList(frozen, "allowed-commands"),
                messages
        );
    }

    public static ProfilerConfig defaults() {
        Map<String, String> messages = new LinkedHashMap<>();
        messages.put("prefix", "<gray>[<gold>Profiler</gold>]</gray> ");
        messages.put("no-permission", "{prefix}<red>You do not have permission to use this command.</red>");
        messages.put("active-header", "{prefix}<gold>Active risks</gold> <gray>({count})</gray>");
        messages.put("active-entry", "<gray>•</gray> <yellow>{player}</yellow> <gray>|</gray> <red>risk {risk}</red> <gray>|</gray> <white>{alert_count} alerts</white> <gray>|</gray> <aqua>{server}</aqua> <gray>|</gray> <white>{last_age} ago</white>");
        messages.put("active-empty", "{prefix}<green>No active risks are currently tracked.</green>");
        messages.put("player-header", "{prefix}<gold>{player}</gold> <gray>| risk</gray> <red>{risk}</red> <gray>| server</gray> <aqua>{server}</aqua> <gray>| frozen</gray> <white>{frozen}</white>");
        messages.put("player-summary", "<gray>Ping {ping}ms | Protocol {protocol} | Brand {brand} | Mods {mods}</gray>");
        messages.put("player-metrics", "<gray>CPS {cps_avg}/{cps_peak} | APS {aps} | Reach {reach_max} | Speed {speed_avg}/{speed_max} | Rot {rotation_max} | Place {place_rate}</gray>");
        messages.put("player-alert-entry", "<gray>•</gray> <red>{severity}</red> <white>{message}</white> <gray>{count_suffix} • {last_age} ago</gray>");
        messages.put("player-no-alerts", "{prefix}<gray>No alerts are stored for {player}.</gray>");
        messages.put("player-not-found", "{prefix}<red>That player is not tracked.</red>");
        messages.put("alerts-enabled", "{prefix}<green>Live staff alerts enabled.</green>");
        messages.put("alerts-disabled", "{prefix}<yellow>Live staff alerts disabled.</yellow>");
        messages.put("freeze-applied", "{prefix}<yellow>{player}</yellow> has been frozen.");
        messages.put("freeze-removed", "{prefix}<green>{player}</green> has been unfrozen.");
        messages.put("freeze-target-offline", "{prefix}<red>{player} is offline or not on a backend server.</red>");
        messages.put("freeze-self", "{prefix}<red>You cannot freeze yourself.</red>");
        messages.put("freeze-notice", "{prefix}<red>You are frozen for a staff check. Do not log out or attempt to evade.</red>");
        messages.put("freeze-chat-blocked", "{prefix}<red>You are frozen and cannot do that right now.</red>");
        messages.put("check-started", "{prefix}<gold>{type}</gold> probe started on <yellow>{player}</yellow>.");
        messages.put("check-result", "{prefix}<gold>{type}</gold> probe finished for <yellow>{player}</yellow><gray>:</gray> <white>{summary}</white>");
        messages.put("reload-complete", "{prefix}<green>Profiler configuration reloaded.</green>");
        messages.put("usage", "{prefix}<gray>/ac [player]</gray> <dark_gray>|</dark_gray> <gray>/ac alerts <on|off></gray> <dark_gray>|</dark_gray> <gray>/ac freeze <player></gray> <dark_gray>|</dark_gray> <gray>/ac unfreeze <player></gray> <dark_gray>|</dark_gray> <gray>/ac check <live|movement|combat|build|clicks> <player></gray>");
        messages.put("alert-broadcast", "{prefix}<red>{player}</red> <gray>{code}</gray> <white>{message}</white> <gray>{count_suffix}</gray>");
        messages.put("alert-proxy-alt-account", "Shared address with {accounts} tracked accounts.");
        messages.put("alert-proxy-reconnect-spike", "Rapid reconnect pattern: {joins} joins in {window}s.");
        messages.put("alert-proxy-server-hop", "Moved across {hops} backend servers in {window}s.");
        messages.put("alert-proxy-client-brand", "Client brand contains suspicious signature '{signature}'.");
        messages.put("alert-proxy-mod-signature", "Mod list contains suspicious signature '{signature}'.");
        messages.put("alert-movement-speed", "Horizontal speed {value} exceeded the {level} threshold.");
        messages.put("alert-rotation-snap", "Rotation snap {value} exceeded the {level} threshold.");
        messages.put("alert-autoclicker", "{value} CPS with low interval variance.");
        messages.put("alert-reach", "Attack reach {value} exceeded the {level} threshold.");
        messages.put("alert-attack-burst", "Attack burst {value} APS exceeded the {level} threshold.");
        messages.put("alert-fast-place", "Placement burst {value} PPS exceeded the {level} threshold.");
        messages.put("alert-scaffold-pattern", "Rapid under-foot placements while moving ({value} speed).");
        messages.put("alert-probe-result", "{type} probe: {summary}");
        return new ProfilerConfig(
                25, 25, 12, 5, true, true, 15, 15, 30, 4, 2, 20, 4, 16, 22, 12, 17,
                3.35D, 3.80D, 0.82D, 1.02D, 95.0D, 145.0D, 9.0D, 14.0D,
                List.of("/msg", "/r", "/reply", "/helpop", "/report"),
                messages
        );
    }

    private static Map<String, Object> childMap(Map<String, Object> root, String key) {
        Object value = root.get(key);
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> child = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                child.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return child;
        }
        return Map.of();
    }

    private static int intValue(Map<String, Object> map, String key, int fallback) {
        Object value = map.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static double doubleValue(Map<String, Object> map, String key, double fallback) {
        Object value = map.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static boolean boolValue(Map<String, Object> map, String key, boolean fallback) {
        Object value = map.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value == null) {
            return fallback;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private static List<String> stringList(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof List<?> list) {
            List<String> output = new ArrayList<>();
            for (Object entry : list) {
                output.add(FormatUtil.sanitize(String.valueOf(entry), ""));
            }
            return output;
        }
        return List.of();
    }

    public int activeAlertMinutes() {
        return activeAlertMinutes;
    }

    public int archivedAlertLimit() {
        return archivedAlertLimit;
    }

    public int overviewLimit() {
        return overviewLimit;
    }

    public int stackBroadcastInterval() {
        return stackBroadcastInterval;
    }

    public boolean alertsDefaultEnabled() {
        return alertsDefaultEnabled;
    }

    public boolean consoleAlerts() {
        return consoleAlerts;
    }

    public int snapshotIntervalSeconds() {
        return snapshotIntervalSeconds;
    }

    public int probeDurationSeconds() {
        return probeDurationSeconds;
    }

    public int reconnectWindowSeconds() {
        return reconnectWindowSeconds;
    }

    public int reconnectThreshold() {
        return reconnectThreshold;
    }

    public int altAccountThreshold() {
        return altAccountThreshold;
    }

    public int serverHopWindowSeconds() {
        return serverHopWindowSeconds;
    }

    public int serverHopThreshold() {
        return serverHopThreshold;
    }

    public int clickWarnCps() {
        return clickWarnCps;
    }

    public int clickHighCps() {
        return clickHighCps;
    }

    public int attackWarnAps() {
        return attackWarnAps;
    }

    public int attackHighAps() {
        return attackHighAps;
    }

    public double reachWarn() {
        return reachWarn;
    }

    public double reachHigh() {
        return reachHigh;
    }

    public double speedWarn() {
        return speedWarn;
    }

    public double speedHigh() {
        return speedHigh;
    }

    public double rotationWarn() {
        return rotationWarn;
    }

    public double rotationHigh() {
        return rotationHigh;
    }

    public double placeWarn() {
        return placeWarn;
    }

    public double placeHigh() {
        return placeHigh;
    }

    public List<String> allowedFrozenCommands() {
        return allowedFrozenCommands;
    }

    public Map<String, String> messages() {
        return messages;
    }

    public String message(String key) {
        return messages.getOrDefault(key, defaults().messages().getOrDefault(key, key));
    }
}
