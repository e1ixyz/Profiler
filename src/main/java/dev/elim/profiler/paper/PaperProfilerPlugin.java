package dev.elim.profiler.paper;

import dev.elim.profiler.ProfilerConstants;
import dev.elim.profiler.common.bridge.BridgePackets;
import dev.elim.profiler.common.config.ConfigIO;
import dev.elim.profiler.common.config.ProfilerConfig;
import dev.elim.profiler.common.io.AlertLogWriter;
import dev.elim.profiler.common.model.AlertRecord;
import dev.elim.profiler.common.model.AlertSeverity;
import dev.elim.profiler.common.model.PlayerProfile;
import dev.elim.profiler.common.model.PlayerSnapshot;
import dev.elim.profiler.common.model.ProbeType;
import dev.elim.profiler.common.service.ProfilerEngine;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PaperProfilerPlugin extends org.bukkit.plugin.java.JavaPlugin
        implements Listener, CommandExecutor, TabCompleter, PluginMessageListener {
    private final Set<UUID> frozenPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, PlayerTelemetry> telemetry = new ConcurrentHashMap<>();
    private final PlainTextComponentSerializer plainText = PlainTextComponentSerializer.plainText();

    private AlertLogWriter logWriter;
    private ProfilerEngine engine;
    private ProfilerConfig config;
    private BukkitTask snapshotTask;

    @Override
    public void onEnable() {
        Path dataDirectory = getDataFolder().toPath();
        this.logWriter = new AlertLogWriter(dataDirectory, new PaperLoggerAdapter(getLogger()));
        this.config = ConfigIO.load(dataDirectory, getClassLoader(), new PaperLoggerAdapter(getLogger()));
        this.engine = new ProfilerEngine(config, new PaperLoggerAdapter(getLogger()), logWriter);

        Bukkit.getPluginManager().registerEvents(this, this);
        getServer().getMessenger().registerIncomingPluginChannel(this, ProfilerConstants.CHANNEL, this);
        getServer().getMessenger().registerOutgoingPluginChannel(this, ProfilerConstants.CHANNEL);

        if (getCommand("profiler") != null) {
            getCommand("profiler").setExecutor(this);
            getCommand("profiler").setTabCompleter(this);
        }

        scheduleSnapshots();
        getLogger().info("Profiler backend runtime enabled.");
    }

    @Override
    public void onDisable() {
        if (snapshotTask != null) {
            snapshotTask.cancel();
        }
        getServer().getMessenger().unregisterIncomingPluginChannel(this);
        getServer().getMessenger().unregisterOutgoingPluginChannel(this);
        if (logWriter != null) {
            logWriter.close();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerTelemetry state = state(player);
        engine.touchPlayer(player.getUniqueId(), player.getName());
        if (frozenPlayers.contains(player.getUniqueId())) {
            player.sendMessage(engine.render("freeze-notice", Map.of()));
        }
        flushSnapshot(player, state);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        engine.recordDisconnect(player.getUniqueId(), player.getName());
        telemetry.remove(player.getUniqueId());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (isFrozen(player)) {
            if (event.getTo() != null && moved(event.getFrom(), event.getTo())) {
                event.setTo(event.getFrom());
                notifyFrozen(player);
            }
            return;
        }
        if (event.getTo() == null) {
            return;
        }

        PlayerTelemetry state = state(player);
        double horizontal = horizontalDistance(event.getFrom(), event.getTo());
        double rotation = rotationDelta(event.getFrom(), event.getTo());
        PlayerTelemetry.MovementSample sample = state.recordMove(System.currentTimeMillis(), horizontal, rotation);

        if (!movementExempt(player) && sample.speed() >= config.speedHigh() && state.allowAlert("movement_speed_high", 2000L)) {
            raiseBackendAlert(
                    player,
                    "movement_speed",
                    engine.template("alert-movement-speed", Map.of(
                            "value", format(sample.speed()),
                            "level", "high"
                    )),
                    "movement_speed_high",
                    AlertSeverity.HIGH,
                    12
            );
        } else if (!movementExempt(player) && sample.speed() >= config.speedWarn() && state.allowAlert("movement_speed_warn", 2500L)) {
            raiseBackendAlert(
                    player,
                    "movement_speed",
                    engine.template("alert-movement-speed", Map.of(
                            "value", format(sample.speed()),
                            "level", "warn"
                    )),
                    "movement_speed_warn",
                    AlertSeverity.MEDIUM,
                    8
            );
        }

        if (sample.rotation() >= config.rotationHigh() && state.allowAlert("rotation_high", 2500L)) {
            raiseBackendAlert(
                    player,
                    "combat_rotation_snap",
                    engine.template("alert-rotation-snap", Map.of(
                            "value", format(sample.rotation()),
                            "level", "high"
                    )),
                    "combat_rotation_high",
                    AlertSeverity.HIGH,
                    10
            );
        } else if (sample.rotation() >= config.rotationWarn() && state.allowAlert("rotation_warn", 3000L)) {
            raiseBackendAlert(
                    player,
                    "combat_rotation_snap",
                    engine.template("alert-rotation-snap", Map.of(
                            "value", format(sample.rotation()),
                            "level", "warn"
                    )),
                    "combat_rotation_warn",
                    AlertSeverity.LOW,
                    5
            );
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (isFrozen(player)) {
            event.setCancelled(true);
            notifyFrozen(player);
            return;
        }

        switch (event.getAction()) {
            case LEFT_CLICK_AIR, LEFT_CLICK_BLOCK -> {
                PlayerTelemetry state = state(player);
                state.recordClick(System.currentTimeMillis());
                double cps = state.currentCps();
                if (cps >= config.clickHighCps() && state.clickStdDev() < 18.0D && state.allowAlert("click_high", 1500L)) {
                    raiseBackendAlert(
                            player,
                            "combat_autoclicker_pattern",
                            engine.template("alert-autoclicker", Map.of("value", format(cps))),
                            "combat_autoclicker_high",
                            AlertSeverity.HIGH,
                            12
                    );
                } else if (cps >= config.clickWarnCps() && state.clickStdDev() < 24.0D && state.allowAlert("click_warn", 1800L)) {
                    raiseBackendAlert(
                            player,
                            "combat_autoclicker_pattern",
                            engine.template("alert-autoclicker", Map.of("value", format(cps))),
                            "combat_autoclicker_warn",
                            AlertSeverity.MEDIUM,
                            8
                    );
                }
            }
            default -> {
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player attacker) {
            if (isFrozen(attacker)) {
                event.setCancelled(true);
                notifyFrozen(attacker);
                return;
            }
            PlayerTelemetry state = state(attacker);
            double reach = reachDistance(attacker, event.getEntity());
            state.recordAttack(System.currentTimeMillis(), reach);
            double aps = state.currentAps();

            if (reach >= config.reachHigh() && state.allowAlert("reach_high", 1200L)) {
                raiseBackendAlert(
                        attacker,
                        "combat_reach",
                        engine.template("alert-reach", Map.of(
                                "value", format(reach),
                                "level", "high"
                        )),
                        "combat_reach_high",
                        AlertSeverity.HIGH,
                        12
                );
            } else if (reach >= config.reachWarn() && state.allowAlert("reach_warn", 1500L)) {
                raiseBackendAlert(
                        attacker,
                        "combat_reach",
                        engine.template("alert-reach", Map.of(
                                "value", format(reach),
                                "level", "warn"
                        )),
                        "combat_reach_warn",
                        AlertSeverity.MEDIUM,
                        8
                );
            }

            if (aps >= config.attackHighAps() && state.allowAlert("aps_high", 1200L)) {
                raiseBackendAlert(
                        attacker,
                        "combat_attack_burst",
                        engine.template("alert-attack-burst", Map.of(
                                "value", format(aps),
                                "level", "high"
                        )),
                        "combat_attack_high",
                        AlertSeverity.HIGH,
                        10
                );
            } else if (aps >= config.attackWarnAps() && state.allowAlert("aps_warn", 1500L)) {
                raiseBackendAlert(
                        attacker,
                        "combat_attack_burst",
                        engine.template("alert-attack-burst", Map.of(
                                "value", format(aps),
                                "level", "warn"
                        )),
                        "combat_attack_warn",
                        AlertSeverity.MEDIUM,
                        7
                );
            }
        }

        if (event.getEntity() instanceof Player victim && isFrozen(victim)) {
            event.setCancelled(true);
            notifyFrozen(victim);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (isFrozen(player)) {
            event.setCancelled(true);
            notifyFrozen(player);
            return;
        }

        PlayerTelemetry state = state(player);
        state.recordPlace(System.currentTimeMillis());
        double placeRate = state.currentPlaceRate();

        if (placeRate >= config.placeHigh() && state.allowAlert("place_high", 1800L)) {
            raiseBackendAlert(
                    player,
                    "build_fast_place",
                    engine.template("alert-fast-place", Map.of(
                            "value", format(placeRate),
                            "level", "high"
                    )),
                    "build_fast_place_high",
                    AlertSeverity.HIGH,
                    10
            );
        } else if (placeRate >= config.placeWarn() && state.allowAlert("place_warn", 2200L)) {
            raiseBackendAlert(
                    player,
                    "build_fast_place",
                    engine.template("alert-fast-place", Map.of(
                            "value", format(placeRate),
                            "level", "warn"
                    )),
                    "build_fast_place_warn",
                    AlertSeverity.MEDIUM,
                    6
            );
        }

        if (isUnderFoot(player, event.getBlockPlaced())
                && state.currentPlaceRate() >= Math.max(3.0D, config.placeWarn() - 2.0D)
                && state.averageSpeed() >= 0.25D
                && state.allowAlert("scaffold_pattern", 1800L)) {
            raiseBackendAlert(
                    player,
                    "build_scaffold_pattern",
                    engine.template("alert-scaffold-pattern", Map.of("value", format(state.averageSpeed()))),
                    "build_scaffold_pattern",
                    AlertSeverity.MEDIUM,
                    8
            );
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (!isFrozen(event.getPlayer())) {
            return;
        }
        event.setCancelled(true);
        Bukkit.getScheduler().runTask(this, () -> notifyFrozen(event.getPlayer()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        if (!isFrozen(event.getPlayer())) {
            return;
        }
        String lower = event.getMessage().toLowerCase(Locale.ROOT);
        boolean allowed = config.allowedFrozenCommands().stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(lower::startsWith);
        if (!allowed) {
            event.setCancelled(true);
            notifyFrozen(event.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && isFrozen(player)) {
            event.setCancelled(true);
            notifyFrozen(player);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (isFrozen(event.getPlayer())) {
            event.setCancelled(true);
            notifyFrozen(event.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        if (isFrozen(event.getPlayer())) {
            event.setCancelled(true);
            notifyFrozen(event.getPlayer());
        }
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player carrier, byte @NotNull [] message) {
        if (!ProfilerConstants.CHANNEL.equals(channel)) {
            return;
        }
        try {
            BridgePackets.Packet packet = BridgePackets.decode(message);
            if (packet instanceof BridgePackets.FreezePacket freezePacket) {
                applyFreeze(freezePacket.playerId(), freezePacket.playerName(), freezePacket.frozen(), freezePacket.reason());
            } else if (packet instanceof BridgePackets.ProbePacket probePacket) {
                Player target = Bukkit.getPlayer(probePacket.playerId());
                if (target != null) {
                    state(target).startProbe(probePacket.probeType(), probePacket.actor(), probePacket.durationSeconds());
                }
            }
        } catch (Exception exception) {
            getLogger().severe("Failed to decode proxy bridge packet: " + exception.getMessage());
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission(ProfilerConstants.PERMISSION_USE)) {
            sender.sendMessage(engine.renderNoPermission());
            return true;
        }
        if (args.length == 0) {
            if (!sender.hasPermission(ProfilerConstants.PERMISSION_VIEW)) {
                sender.sendMessage(engine.renderNoPermission());
                return true;
            }
            engine.renderOverview().forEach(sender::sendMessage);
            return true;
        }

        String subcommand = args[0].toLowerCase(Locale.ROOT);
        switch (subcommand) {
            case "alerts" -> handleAlerts(sender, args);
            case "freeze" -> handleFreeze(sender, args, true);
            case "unfreeze" -> handleFreeze(sender, args, false);
            case "check" -> handleCheck(sender, args);
            case "reload" -> handleReload(sender);
            default -> handlePlayerView(sender, args[0]);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>(List.of("alerts", "freeze", "unfreeze", "check", "reload"));
            suggestions.addAll(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
            return match(args[0], suggestions);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("alerts")) {
            return match(args[1], List.of("on", "off"));
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("freeze") || args[0].equalsIgnoreCase("unfreeze"))) {
            return match(args[1], Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("check")) {
            return match(args[1], List.of("live", "movement", "combat", "build", "clicks"));
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("check")) {
            return match(args[2], Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
        }
        return List.of();
    }

    private void handleAlerts(CommandSender sender, String[] args) {
        if (!sender.hasPermission(ProfilerConstants.PERMISSION_ALERTS)) {
            sender.sendMessage(engine.renderNoPermission());
            return;
        }
        if (!(sender instanceof Player player) || args.length < 2) {
            sender.sendMessage(engine.renderUsage());
            return;
        }
        boolean enabled = args[1].equalsIgnoreCase("on");
        engine.alertsEnabled(player.getUniqueId(), enabled);
        sender.sendMessage(engine.renderAlertsToggle(enabled));
    }

    private void handleFreeze(CommandSender sender, String[] args, boolean frozen) {
        if (!sender.hasPermission(ProfilerConstants.PERMISSION_FREEZE)) {
            sender.sendMessage(engine.renderNoPermission());
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(engine.renderUsage());
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            target = Bukkit.getOnlinePlayers().stream()
                    .filter(player -> player.getName().equalsIgnoreCase(args[1]))
                    .findFirst()
                    .orElse(null);
        }
        if (target == null) {
            sender.sendMessage(engine.renderFreezeTargetOffline(args[1]));
            return;
        }
        if (sender instanceof Player player && player.getUniqueId().equals(target.getUniqueId())) {
            sender.sendMessage(engine.renderFreezeSelf());
            return;
        }

        applyFreeze(target.getUniqueId(), target.getName(), frozen, frozen ? "Frozen by staff" : "Released by staff");
        sender.sendMessage(engine.renderFreeze(frozen, target.getName()));
    }

    private void handleCheck(CommandSender sender, String[] args) {
        if (!sender.hasPermission(ProfilerConstants.PERMISSION_CHECK)) {
            sender.sendMessage(engine.renderNoPermission());
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(engine.renderUsage());
            return;
        }
        ProbeType type = ProbeType.fromInput(args[1]);
        if (type == null) {
            sender.sendMessage(engine.renderUsage());
            return;
        }
        Player target = Bukkit.getOnlinePlayers().stream()
                .filter(player -> player.getName().equalsIgnoreCase(args[2]))
                .findFirst()
                .orElse(null);
        if (target == null) {
            sender.sendMessage(engine.renderFreezeTargetOffline(args[2]));
            return;
        }
        state(target).startProbe(type, sender.getName(), type == ProbeType.LIVE ? 3 : config.probeDurationSeconds());
        sender.sendMessage(engine.renderProbeStart(target.getName(), type));
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission(ProfilerConstants.PERMISSION_RELOAD)) {
            sender.sendMessage(engine.renderNoPermission());
            return;
        }
        this.config = ConfigIO.load(getDataFolder().toPath(), getClassLoader(), new PaperLoggerAdapter(getLogger()));
        this.engine.reload(config);
        scheduleSnapshots();
        sender.sendMessage(engine.renderReloadComplete());
    }

    private void handlePlayerView(CommandSender sender, String target) {
        if (!sender.hasPermission(ProfilerConstants.PERMISSION_VIEW)) {
            sender.sendMessage(engine.renderNoPermission());
            return;
        }
        engine.findByName(target)
                .ifPresentOrElse(
                        profile -> engine.renderPlayerDetails(profile).forEach(sender::sendMessage),
                        () -> sender.sendMessage(engine.renderPlayerNotFound())
                );
    }

    private void scheduleSnapshots() {
        if (snapshotTask != null) {
            snapshotTask.cancel();
        }
        long period = Math.max(20L, config.snapshotIntervalSeconds() * 20L);
        snapshotTask = Bukkit.getScheduler().runTaskTimer(this, this::snapshotTick, period, period);
    }

    private void snapshotTick() {
        engine.cleanup();
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerTelemetry state = state(player);
            ProbeSession probe = state.probeSession();
            if (probe != null && probe.expired(now)) {
                finishProbe(player, state, probe);
            }
            flushSnapshot(player, state);
        }
    }

    private void finishProbe(Player player, PlayerTelemetry state, ProbeSession probe) {
        ProbeSession finished = state.finishProbe();
        if (finished == null) {
            return;
        }
        String summary = finished.summary();
        AlertRecord alert = engine.raiseAlert(
                player.getUniqueId(),
                player.getName(),
                serverName(),
                "probe_" + finished.type().name().toLowerCase(Locale.ROOT),
                engine.template("alert-probe-result", Map.of(
                        "type", finished.type().name().toLowerCase(Locale.ROOT),
                        "summary", summary
                )),
                "probe:" + finished.type().name().toLowerCase(Locale.ROOT),
                AlertSeverity.LOW,
                0
        );
        broadcastAlert(player, alert);
        Player actor = Bukkit.getPlayerExact(finished.actor());
        if (actor != null) {
            actor.sendMessage(engine.renderProbeResult(player.getName(), finished.type(), summary));
        }
    }

    private void flushSnapshot(Player player, PlayerTelemetry state) {
        PlayerSnapshot snapshot = state.buildSnapshot(serverName(), player.getPing(), player.getClientViewDistance());
        engine.touchPlayer(player.getUniqueId(), player.getName()).mergeSnapshot(snapshot);
        player.sendPluginMessage(this, ProfilerConstants.CHANNEL, BridgePackets.encode(new BridgePackets.SnapshotPacket(
                player.getUniqueId(),
                player.getName(),
                snapshot.currentServer(),
                snapshot.ping(),
                snapshot.protocolVersion(),
                snapshot.clientBrand(),
                snapshot.modSummary(),
                snapshot.locale(),
                snapshot.viewDistance(),
                snapshot.averageCps(),
                snapshot.peakCps(),
                snapshot.attacksPerSecond(),
                snapshot.averageSpeed(),
                snapshot.maxSpeed(),
                snapshot.maxReach(),
                snapshot.maxRotation(),
                snapshot.placementsPerSecond(),
                snapshot.lastProbeSummary()
        )));
    }

    private void raiseBackendAlert(Player player, String code, String message, String fingerprint, AlertSeverity severity, int riskDelta) {
        AlertRecord alert = engine.raiseAlert(
                player.getUniqueId(),
                player.getName(),
                serverName(),
                code,
                message,
                fingerprint,
                severity,
                riskDelta
        );
        broadcastAlert(player, alert);
    }

    private void broadcastAlert(Player player, AlertRecord alert) {
        if (!engine.shouldBroadcast(alert)) {
            return;
        }
        Optional<PlayerProfile> profileOptional = engine.findById(player.getUniqueId());
        if (profileOptional.isEmpty()) {
            return;
        }
        Component broadcast = engine.renderAlertBroadcast(profileOptional.get(), alert);
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.hasPermission(ProfilerConstants.PERMISSION_ALERTS)
                    && engine.alertsEnabled(online.getUniqueId())) {
                online.sendMessage(broadcast);
            }
        }
        if (config.consoleAlerts()) {
            getLogger().info(plainText.serialize(broadcast));
        }
        player.sendPluginMessage(this, ProfilerConstants.CHANNEL, BridgePackets.encode(new BridgePackets.AlertPacket(
                player.getUniqueId(),
                player.getName(),
                serverName(),
                alert.code(),
                alert.message(),
                alert.fingerprint(),
                alert.severity(),
                alert.riskDelta()
        )));
    }

    private PlayerTelemetry state(Player player) {
        return telemetry.computeIfAbsent(player.getUniqueId(), ignored -> new PlayerTelemetry());
    }

    private boolean isFrozen(Player player) {
        return frozenPlayers.contains(player.getUniqueId());
    }

    private void applyFreeze(UUID uniqueId, String playerName, boolean frozen, String reason) {
        if (frozen) {
            frozenPlayers.add(uniqueId);
        } else {
            frozenPlayers.remove(uniqueId);
        }
        engine.setFrozen(uniqueId, playerName, frozen, reason);
        Player target = Bukkit.getPlayer(uniqueId);
        if (target != null) {
            target.sendMessage(engine.render(frozen ? "freeze-notice" : "freeze-removed", Map.of("player", target.getName())));
        }
    }

    private void notifyFrozen(Player player) {
        PlayerTelemetry state = state(player);
        if (state.allowAlert("freeze_notice", 1500L)) {
            player.sendMessage(engine.render("freeze-chat-blocked", Map.of()));
        }
    }

    private boolean movementExempt(Player player) {
        return player.isFlying()
                || player.getAllowFlight()
                || player.isGliding()
                || player.isSwimming()
                || player.isInsideVehicle();
    }

    private double horizontalDistance(Location from, Location to) {
        double deltaX = to.getX() - from.getX();
        double deltaZ = to.getZ() - from.getZ();
        return Math.hypot(deltaX, deltaZ);
    }

    private boolean moved(Location from, Location to) {
        return from.getBlockX() != to.getBlockX()
                || from.getBlockY() != to.getBlockY()
                || from.getBlockZ() != to.getBlockZ()
                || Math.abs(from.getYaw() - to.getYaw()) > 0.001F
                || Math.abs(from.getPitch() - to.getPitch()) > 0.001F;
    }

    private double rotationDelta(Location from, Location to) {
        float yawDelta = Math.abs(normalizeYaw(to.getYaw() - from.getYaw()));
        float pitchDelta = Math.abs(to.getPitch() - from.getPitch());
        return Math.max(yawDelta, pitchDelta);
    }

    private float normalizeYaw(float yaw) {
        float normalized = yaw % 360.0F;
        if (normalized <= -180.0F) {
            normalized += 360.0F;
        }
        if (normalized > 180.0F) {
            normalized -= 360.0F;
        }
        return normalized;
    }

    private double reachDistance(Player attacker, Entity target) {
        Location eye = attacker.getEyeLocation();
        BoundingBox box = target.getBoundingBox();
        double clampedX = clamp(eye.getX(), box.getMinX(), box.getMaxX());
        double clampedY = clamp(eye.getY(), box.getMinY(), box.getMaxY());
        double clampedZ = clamp(eye.getZ(), box.getMinZ(), box.getMaxZ());
        return eye.toVector().distance(new org.bukkit.util.Vector(clampedX, clampedY, clampedZ));
    }

    private boolean isUnderFoot(Player player, Block block) {
        Location location = player.getLocation();
        return Math.abs(block.getX() + 0.5D - location.getX()) < 1.6D
                && Math.abs(block.getZ() + 0.5D - location.getZ()) < 1.6D
                && block.getY() <= location.getY() - 0.8D;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private String format(double value) {
        return String.format(Locale.US, "%.2f", value);
    }

    private String serverName() {
        String configured = getServer().getIp();
        if (configured == null || configured.isBlank()) {
            return "paper-" + getServer().getPort();
        }
        return configured + ":" + getServer().getPort();
    }

    private List<String> match(String input, List<String> suggestions) {
        String lowered = input.toLowerCase(Locale.ROOT);
        return suggestions.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lowered))
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();
    }
}
