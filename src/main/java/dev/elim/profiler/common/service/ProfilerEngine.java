package dev.elim.profiler.common.service;

import dev.elim.profiler.common.bridge.BridgePackets;
import dev.elim.profiler.common.config.ProfilerConfig;
import dev.elim.profiler.common.io.AlertLogWriter;
import dev.elim.profiler.common.model.AlertRecord;
import dev.elim.profiler.common.model.AlertSeverity;
import dev.elim.profiler.common.model.PlayerProfile;
import dev.elim.profiler.common.model.PlayerSnapshot;
import dev.elim.profiler.common.model.ProbeType;
import dev.elim.profiler.common.util.FormatUtil;
import dev.elim.profiler.common.util.ProfilerLogger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

public final class ProfilerEngine {
    private static final Set<String> SUSPICIOUS_SIGNATURES = Set.of(
            "wurst",
            "meteor",
            "liquidbounce",
            "vape",
            "sigma",
            "impact",
            "future",
            "inertia",
            "aristois",
            "rise"
    );

    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final ProfilerLogger logger;
    private final AlertLogWriter logWriter;
    private final Map<UUID, PlayerProfile> profiles = new ConcurrentHashMap<>();
    private final Map<String, UUID> nameIndex = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> alertSubscriptions = new ConcurrentHashMap<>();
    private final Map<String, Set<UUID>> accountsByIp = new ConcurrentHashMap<>();
    private final Map<String, Deque<Instant>> reconnectsByIp = new ConcurrentHashMap<>();

    private volatile ProfilerConfig config;

    public ProfilerEngine(ProfilerConfig config, ProfilerLogger logger, AlertLogWriter logWriter) {
        this.config = config;
        this.logger = logger;
        this.logWriter = logWriter;
    }

    public void reload(ProfilerConfig config) {
        this.config = config;
        cleanup();
    }

    public ProfilerConfig config() {
        return config;
    }

    public PlayerProfile touchPlayer(UUID uniqueId, String name) {
        PlayerProfile profile = profiles.computeIfAbsent(uniqueId, id -> new PlayerProfile(id, name));
        profile.lastKnownName(name);
        nameIndex.put(name.toLowerCase(Locale.ROOT), uniqueId);
        return profile;
    }

    public Optional<PlayerProfile> findByName(String name) {
        UUID uniqueId = nameIndex.get(name.toLowerCase(Locale.ROOT));
        if (uniqueId == null) {
            return Optional.empty();
        }
        PlayerProfile profile = profiles.get(uniqueId);
        if (profile == null) {
            return Optional.empty();
        }
        profile.cleanupExpired(config);
        return Optional.of(profile);
    }

    public Optional<PlayerProfile> findById(UUID uniqueId) {
        PlayerProfile profile = profiles.get(uniqueId);
        if (profile == null) {
            return Optional.empty();
        }
        profile.cleanupExpired(config);
        return Optional.of(profile);
    }

    public Collection<PlayerProfile> allProfiles() {
        cleanup();
        return profiles.values();
    }

    public List<AlertRecord> recordProxyJoin(
            UUID uniqueId,
            String name,
            String currentServer,
            String remoteAddress,
            String virtualHost,
            int protocolVersion,
            long ping
    ) {
        PlayerProfile profile = touchPlayer(uniqueId, name);
        profile.currentServer(FormatUtil.sanitize(currentServer, "proxy"));
        profile.remoteAddress(FormatUtil.sanitize(remoteAddress, "unknown"));
        profile.virtualHost(FormatUtil.sanitize(virtualHost, "unknown"));
        profile.protocolVersion(protocolVersion);
        profile.ping(ping);
        logWriter.logEvent("proxy_join", name, remoteAddress + " via " + virtualHost);

        List<AlertRecord> alerts = new ArrayList<>();
        if (!remoteAddress.equalsIgnoreCase("unknown")) {
            accountsByIp.computeIfAbsent(remoteAddress, ignored -> ConcurrentHashMap.newKeySet()).add(uniqueId);
            int accountCount = accountsByIp.get(remoteAddress).size();
            if (accountCount >= config.altAccountThreshold()) {
                alerts.add(raiseAlert(
                        uniqueId,
                        name,
                        currentServer,
                        "proxy_alt_account",
                        template("alert-proxy-alt-account", Map.of("accounts", String.valueOf(accountCount))),
                        "proxy_alt_account:" + remoteAddress,
                        AlertSeverity.MEDIUM,
                        7
                ));
            }

            Deque<Instant> reconnects = reconnectsByIp.computeIfAbsent(remoteAddress, ignored -> new ConcurrentLinkedDeque<>());
            Instant now = Instant.now();
            reconnects.addLast(now);
            Instant cutoff = now.minusSeconds(config.reconnectWindowSeconds());
            while (!reconnects.isEmpty() && reconnects.peekFirst().isBefore(cutoff)) {
                reconnects.removeFirst();
            }
            if (reconnects.size() >= config.reconnectThreshold()) {
                alerts.add(raiseAlert(
                        uniqueId,
                        name,
                        currentServer,
                        "proxy_reconnect_spike",
                        template("alert-proxy-reconnect-spike", Map.of(
                                "joins", String.valueOf(reconnects.size()),
                                "window", String.valueOf(config.reconnectWindowSeconds())
                        )),
                        "proxy_reconnect_spike:" + remoteAddress,
                        AlertSeverity.MEDIUM,
                        8
                ));
            }
        }
        return alerts;
    }

    public List<AlertRecord> recordServerSwitch(UUID uniqueId, String name, String fromServer, String toServer) {
        PlayerProfile profile = touchPlayer(uniqueId, name);
        profile.currentServer(FormatUtil.sanitize(toServer, "unknown"));
        profile.recordHop(config.serverHopWindowSeconds());
        logWriter.logEvent("server_switch", name, FormatUtil.sanitize(fromServer, "unknown") + " -> " + toServer);

        if (profile.currentHopCount() >= config.serverHopThreshold()) {
            return List.of(raiseAlert(
                    uniqueId,
                    name,
                    toServer,
                    "proxy_server_hop",
                    template("alert-proxy-server-hop", Map.of(
                            "hops", String.valueOf(profile.currentHopCount()),
                            "window", String.valueOf(config.serverHopWindowSeconds())
                    )),
                    "proxy_server_hop",
                    AlertSeverity.LOW,
                    5
            ));
        }
        return List.of();
    }

    public Optional<AlertRecord> recordBrand(UUID uniqueId, String name, String currentServer, String brand) {
        PlayerProfile profile = touchPlayer(uniqueId, name);
        profile.clientBrand(FormatUtil.sanitize(brand, "unknown"));
        String suspicious = suspiciousSignature(brand);
        if (suspicious == null) {
            return Optional.empty();
        }
        return Optional.of(raiseAlert(
                uniqueId,
                name,
                currentServer,
                "proxy_client_brand",
                template("alert-proxy-client-brand", Map.of("signature", suspicious)),
                "proxy_client_brand:" + suspicious,
                AlertSeverity.HIGH,
                12
        ));
    }

    public Optional<AlertRecord> recordModInfo(UUID uniqueId, String name, String currentServer, String mods) {
        PlayerProfile profile = touchPlayer(uniqueId, name);
        profile.modSummary(FormatUtil.sanitize(mods, "vanilla"));
        String suspicious = suspiciousSignature(mods);
        if (suspicious == null) {
            return Optional.empty();
        }
        return Optional.of(raiseAlert(
                uniqueId,
                name,
                currentServer,
                "proxy_mod_signature",
                template("alert-proxy-mod-signature", Map.of("signature", suspicious)),
                "proxy_mod_signature:" + suspicious,
                AlertSeverity.HIGH,
                12
        ));
    }

    public void recordSettings(UUID uniqueId, String name, String locale, int viewDistance) {
        PlayerProfile profile = touchPlayer(uniqueId, name);
        profile.locale(FormatUtil.sanitize(locale, "unknown"));
        profile.viewDistance(viewDistance);
    }

    public void recordDisconnect(UUID uniqueId, String name) {
        PlayerProfile profile = touchPlayer(uniqueId, name);
        profile.currentServer("offline");
        logWriter.logEvent("disconnect", name, "offline");
    }

    public AlertRecord acceptBackendAlert(BridgePackets.AlertPacket packet) {
        return raiseAlert(
                packet.playerId(),
                packet.playerName(),
                packet.sourceServer(),
                packet.code(),
                packet.message(),
                packet.fingerprint(),
                packet.severity(),
                packet.riskDelta()
        );
    }

    public void acceptBackendSnapshot(BridgePackets.SnapshotPacket packet) {
        PlayerProfile profile = touchPlayer(packet.playerId(), packet.playerName());
        PlayerSnapshot snapshot = new PlayerSnapshot();
        snapshot.currentServer(FormatUtil.sanitize(packet.currentServer(), "unknown"));
        snapshot.ping(packet.ping());
        snapshot.protocolVersion(packet.protocolVersion());
        snapshot.clientBrand(FormatUtil.sanitize(packet.clientBrand(), "unknown"));
        snapshot.modSummary(FormatUtil.sanitize(packet.modSummary(), "vanilla"));
        snapshot.locale(FormatUtil.sanitize(packet.locale(), "unknown"));
        snapshot.viewDistance(packet.viewDistance());
        snapshot.averageCps(packet.averageCps());
        snapshot.peakCps(packet.peakCps());
        snapshot.attacksPerSecond(packet.attacksPerSecond());
        snapshot.averageSpeed(packet.averageSpeed());
        snapshot.maxSpeed(packet.maxSpeed());
        snapshot.maxReach(packet.maxReach());
        snapshot.maxRotation(packet.maxRotation());
        snapshot.placementsPerSecond(packet.placementsPerSecond());
        snapshot.lastProbeSummary(FormatUtil.sanitize(packet.lastProbeSummary(), "-"));
        profile.mergeSnapshot(snapshot);
    }

    public void setFrozen(UUID uniqueId, String name, boolean frozen, String reason) {
        PlayerProfile profile = touchPlayer(uniqueId, name);
        profile.frozen(frozen);
        logWriter.logEvent(frozen ? "freeze" : "unfreeze", name, reason);
    }

    public AlertRecord raiseAlert(
            UUID uniqueId,
            String name,
            String sourceServer,
            String code,
            String message,
            String fingerprint,
            AlertSeverity severity,
            int riskDelta
    ) {
        PlayerProfile profile = touchPlayer(uniqueId, name);
        AlertRecord record = profile.addAlert(
                fingerprint,
                code,
                severity,
                riskDelta < 0 ? severity.defaultRisk() : riskDelta,
                FormatUtil.sanitize(sourceServer, "unknown"),
                message,
                config
        );
        logWriter.logAlert(profile, record);
        return record;
    }

    public boolean shouldBroadcast(AlertRecord record) {
        return record.stackCount() == 1
                || record.stackCount() % Math.max(1, config.stackBroadcastInterval()) == 0;
    }

    public void cleanup() {
        profiles.values().forEach(profile -> profile.cleanupExpired(config));
    }

    public List<PlayerProfile> activeProfiles() {
        cleanup();
        return profiles.values().stream()
                .filter(profile -> profile.riskScore() > 0 || profile.frozen())
                .sorted(Comparator
                        .comparingInt(PlayerProfile::riskScore).reversed()
                        .thenComparing(profile -> Optional.ofNullable(profile.lastAlertSeen()).orElse(Instant.EPOCH), Comparator.reverseOrder()))
                .limit(config.overviewLimit())
                .toList();
    }

    public boolean alertsEnabled(UUID uniqueId) {
        return alertSubscriptions.getOrDefault(uniqueId, config.alertsDefaultEnabled());
    }

    public void alertsEnabled(UUID uniqueId, boolean enabled) {
        alertSubscriptions.put(uniqueId, enabled);
    }

    public String template(String key, Map<String, String> placeholders) {
        Map<String, String> values = new LinkedHashMap<>(placeholders);
        values.putIfAbsent("prefix", config.message("prefix"));
        return FormatUtil.replacePlaceholders(config.message(key), values);
    }

    public Component render(String key, Map<String, String> placeholders) {
        return miniMessage.deserialize(template(key, placeholders));
    }

    public Component renderUsage() {
        return render("usage", Map.of());
    }

    public List<Component> renderOverview() {
        List<PlayerProfile> active = activeProfiles();
        List<Component> lines = new ArrayList<>();
        if (active.isEmpty()) {
            lines.add(render("active-empty", Map.of()));
            return lines;
        }

        lines.add(render("active-header", Map.of("count", String.valueOf(active.size()))));
        for (PlayerProfile profile : active) {
            lines.add(render("active-entry", Map.of(
                    "player", profile.lastKnownName(),
                    "risk", String.valueOf(profile.riskScore()),
                    "alert_count", String.valueOf(profile.activeAlerts().size()),
                    "server", profile.currentServer(),
                    "last_age", FormatUtil.ageSince(profile.lastAlertSeen())
            )));
        }
        return lines;
    }

    public List<Component> renderPlayerDetails(PlayerProfile profile) {
        profile.cleanupExpired(config);
        PlayerSnapshot snapshot = profile.snapshot();
        List<Component> lines = new ArrayList<>();
        lines.add(render("player-header", Map.of(
                "player", profile.lastKnownName(),
                "risk", String.valueOf(profile.riskScore()),
                "server", profile.currentServer(),
                "frozen", profile.frozen() ? "yes" : "no"
        )));
        lines.add(render("player-summary", Map.of(
                "ping", String.valueOf(snapshot.ping()),
                "protocol", String.valueOf(snapshot.protocolVersion()),
                "brand", snapshot.clientBrand(),
                "mods", snapshot.modSummary()
        )));
        lines.add(render("player-metrics", Map.of(
                "cps_avg", FormatUtil.formatDouble(snapshot.averageCps()),
                "cps_peak", FormatUtil.formatDouble(snapshot.peakCps()),
                "aps", FormatUtil.formatDouble(snapshot.attacksPerSecond()),
                "reach_max", FormatUtil.formatDouble(snapshot.maxReach()),
                "speed_avg", FormatUtil.formatDouble(snapshot.averageSpeed()),
                "speed_max", FormatUtil.formatDouble(snapshot.maxSpeed()),
                "rotation_max", FormatUtil.formatDouble(snapshot.maxRotation()),
                "place_rate", FormatUtil.formatDouble(snapshot.placementsPerSecond())
        )));

        List<AlertRecord> history = profile.alertHistory(config.archivedAlertLimit());
        if (history.isEmpty()) {
            lines.add(render("player-no-alerts", Map.of("player", profile.lastKnownName())));
            return lines;
        }

        history.stream().limit(config.archivedAlertLimit()).forEach(alert -> lines.add(render("player-alert-entry", Map.of(
                "severity", alert.severity().displayName(),
                "message", alert.message(),
                "count_suffix", FormatUtil.countSuffix(alert.stackCount()),
                "last_age", FormatUtil.ageSince(alert.lastSeen())
        ))));
        return lines;
    }

    public Component renderPlayerNotFound() {
        return render("player-not-found", Map.of());
    }

    public Component renderNoPermission() {
        return render("no-permission", Map.of());
    }

    public Component renderAlertsToggle(boolean enabled) {
        return render(enabled ? "alerts-enabled" : "alerts-disabled", Map.of());
    }

    public Component renderFreeze(boolean frozen, String player) {
        return render(frozen ? "freeze-applied" : "freeze-removed", Map.of("player", player));
    }

    public Component renderFreezeTargetOffline(String player) {
        return render("freeze-target-offline", Map.of("player", player));
    }

    public Component renderFreezeSelf() {
        return render("freeze-self", Map.of());
    }

    public Component renderReloadComplete() {
        return render("reload-complete", Map.of());
    }

    public Component renderProbeStart(String player, ProbeType type) {
        return render("check-started", Map.of(
                "player", player,
                "type", type.name().toLowerCase(Locale.ROOT)
        ));
    }

    public Component renderProbeResult(String player, ProbeType type, String summary) {
        return render("check-result", Map.of(
                "player", player,
                "type", type.name().toLowerCase(Locale.ROOT),
                "summary", summary
        ));
    }

    public Component renderAlertBroadcast(PlayerProfile profile, AlertRecord alert) {
        return render("alert-broadcast", Map.of(
                "player", profile.lastKnownName(),
                "code", alert.code(),
                "message", alert.message(),
                "count_suffix", FormatUtil.countSuffix(alert.stackCount())
        ));
    }

    private String suspiciousSignature(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String lowered = value.toLowerCase(Locale.ROOT);
        for (String token : SUSPICIOUS_SIGNATURES) {
            if (lowered.contains(token)) {
                return token;
            }
        }
        return null;
    }
}
