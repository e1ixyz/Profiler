package dev.elim.profiler.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.player.PlayerClientBrandEvent;
import com.velocitypowered.api.event.player.PlayerModInfoEvent;
import com.velocitypowered.api.event.player.PlayerSettingsChangedEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import dev.elim.profiler.ProfilerConstants;
import dev.elim.profiler.common.bridge.BridgePackets;
import dev.elim.profiler.common.config.ConfigIO;
import dev.elim.profiler.common.config.ProfilerConfig;
import dev.elim.profiler.common.io.AlertLogWriter;
import dev.elim.profiler.common.model.AlertRecord;
import dev.elim.profiler.common.model.PlayerProfile;
import dev.elim.profiler.common.model.ProbeType;
import dev.elim.profiler.common.service.ProfilerEngine;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Plugin(
        id = ProfilerConstants.PLUGIN_ID,
        name = ProfilerConstants.PLUGIN_NAME,
        version = ProfilerConstants.PLUGIN_VERSION,
        authors = {"Elim"}
)
public final class VelocityProfilerPlugin {
    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;
    private final MinecraftChannelIdentifier channelIdentifier = MinecraftChannelIdentifier.from(ProfilerConstants.CHANNEL);
    private final PlainTextComponentSerializer plainText = PlainTextComponentSerializer.plainText();

    private AlertLogWriter logWriter;
    private ProfilerEngine engine;
    private ProfilerConfig config;

    @Inject
    public VelocityProfilerPlugin(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        this.logWriter = new AlertLogWriter(dataDirectory, new VelocityLoggerAdapter(logger));
        this.config = ConfigIO.load(dataDirectory, getClass().getClassLoader(), new VelocityLoggerAdapter(logger));
        this.engine = new ProfilerEngine(config, new VelocityLoggerAdapter(logger), logWriter);

        server.getChannelRegistrar().register(channelIdentifier);

        CommandMeta commandMeta = server.getCommandManager()
                .metaBuilder("profiler")
                .aliases("ac")
                .plugin(this)
                .build();
        server.getCommandManager().register(commandMeta, new VelocityProfilerCommand());

        server.getScheduler().buildTask(this, engine::cleanup)
                .repeat(1L, TimeUnit.MINUTES)
                .schedule();

        logger.info("Profiler proxy runtime enabled.");
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (logWriter != null) {
            logWriter.close();
        }
        server.getChannelRegistrar().unregister(channelIdentifier);
    }

    @Subscribe
    public void onPostLogin(PostLoginEvent event) {
        Player player = event.getPlayer();
        List<AlertRecord> alerts = engine.recordProxyJoin(
                player.getUniqueId(),
                player.getUsername(),
                player.getCurrentServer().map(connection -> connection.getServerInfo().getName()).orElse("proxy"),
                player.getRemoteAddress().getAddress().getHostAddress(),
                player.getRawVirtualHost().orElse("unknown"),
                player.getProtocolVersion().getProtocol(),
                player.getPing()
        );
        alerts.forEach(alert -> broadcastAlert(player.getUniqueId(), player.getUsername(), alert));
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        Player player = event.getPlayer();
        engine.recordDisconnect(player.getUniqueId(), player.getUsername());
    }

    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        Player player = event.getPlayer();
        List<AlertRecord> alerts = engine.recordServerSwitch(
                player.getUniqueId(),
                player.getUsername(),
                event.getPreviousServer().map(server -> server.getServerInfo().getName()).orElse("unknown"),
                event.getServer().getServerInfo().getName()
        );
        alerts.forEach(alert -> broadcastAlert(player.getUniqueId(), player.getUsername(), alert));
    }

    @Subscribe
    public void onBrand(PlayerClientBrandEvent event) {
        engine.recordBrand(
                        event.getPlayer().getUniqueId(),
                        event.getPlayer().getUsername(),
                        event.getPlayer().getCurrentServer().map(connection -> connection.getServerInfo().getName()).orElse("proxy"),
                        event.getBrand()
                )
                .ifPresent(alert -> broadcastAlert(event.getPlayer().getUniqueId(), event.getPlayer().getUsername(), alert));
    }

    @Subscribe
    public void onModInfo(PlayerModInfoEvent event) {
        String mods = event.getModInfo().getMods().stream()
                .map(mod -> mod.getId() + ":" + mod.getVersion())
                .collect(Collectors.joining(", "));
        engine.recordModInfo(
                        event.getPlayer().getUniqueId(),
                        event.getPlayer().getUsername(),
                        event.getPlayer().getCurrentServer().map(connection -> connection.getServerInfo().getName()).orElse("proxy"),
                        mods.isBlank() ? "vanilla" : mods
                )
                .ifPresent(alert -> broadcastAlert(event.getPlayer().getUniqueId(), event.getPlayer().getUsername(), alert));
    }

    @Subscribe
    public void onSettings(PlayerSettingsChangedEvent event) {
        engine.recordSettings(
                event.getPlayer().getUniqueId(),
                event.getPlayer().getUsername(),
                event.getPlayerSettings().getLocale().toLanguageTag(),
                event.getPlayerSettings().getViewDistance()
        );
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!channelIdentifier.equals(event.getIdentifier())) {
            return;
        }
        if (!(event.getSource() instanceof ServerConnection)) {
            return;
        }

        event.setResult(PluginMessageEvent.ForwardResult.handled());
        try {
            BridgePackets.Packet packet = BridgePackets.decode(event.getData());
            if (packet instanceof BridgePackets.AlertPacket alertPacket) {
                AlertRecord alert = engine.acceptBackendAlert(alertPacket);
                broadcastAlert(alertPacket.playerId(), alertPacket.playerName(), alert);
            } else if (packet instanceof BridgePackets.SnapshotPacket snapshotPacket) {
                engine.acceptBackendSnapshot(snapshotPacket);
            } else if (packet instanceof BridgePackets.FreezePacket freezePacket) {
                engine.setFrozen(freezePacket.playerId(), freezePacket.playerName(), freezePacket.frozen(), freezePacket.reason());
            }
        } catch (Exception exception) {
            logger.error("Failed to decode backend bridge packet.", exception);
        }
    }

    private void broadcastAlert(UUID playerId, String playerName, AlertRecord alert) {
        Optional<PlayerProfile> profileOptional = engine.findById(playerId);
        if (profileOptional.isEmpty()) {
            profileOptional = engine.findByName(playerName);
        }
        if (profileOptional.isEmpty()) {
            return;
        }
        PlayerProfile profile = profileOptional.get();
        if (!engine.shouldBroadcast(alert)) {
            return;
        }

        Component message = engine.renderAlertBroadcast(profile, alert);
        for (Player online : server.getAllPlayers()) {
            if (online.hasPermission(ProfilerConstants.PERMISSION_ALERTS)
                    && engine.alertsEnabled(online.getUniqueId())) {
                online.sendMessage(message);
            }
        }

        if (config.consoleAlerts()) {
            logger.info(plainText.serialize(message));
        }
    }

    private boolean sendToBackend(Player player, BridgePackets.Packet packet) {
        return player.getCurrentServer()
                .map(connection -> connection.sendPluginMessage(channelIdentifier, BridgePackets.encode(packet)))
                .orElse(false);
    }

    private void send(CommandSource source, Component message) {
        source.sendMessage(message);
    }

    private void send(CommandSource source, List<Component> lines) {
        lines.forEach(source::sendMessage);
    }

    private final class VelocityProfilerCommand implements SimpleCommand {
        @Override
        public void execute(Invocation invocation) {
            CommandSource source = invocation.source();
            String[] arguments = invocation.arguments();
            if (arguments.length == 0) {
                if (!source.hasPermission(ProfilerConstants.PERMISSION_VIEW)) {
                    send(source, engine.renderNoPermission());
                    return;
                }
                send(source, engine.renderOverview());
                return;
            }

            String subcommand = arguments[0].toLowerCase(Locale.ROOT);
            switch (subcommand) {
                case "alerts" -> handleAlerts(source, arguments);
                case "freeze" -> handleFreeze(source, arguments, true);
                case "unfreeze" -> handleFreeze(source, arguments, false);
                case "check" -> handleCheck(source, arguments);
                case "reload" -> handleReload(source);
                default -> handlePlayerView(source, arguments[0]);
            }
        }

        @Override
        public List<String> suggest(Invocation invocation) {
            String[] arguments = invocation.arguments();
            if (arguments.length == 0) {
                return List.of();
            }
            if (arguments.length == 1) {
                List<String> suggestions = new ArrayList<>(List.of("alerts", "freeze", "unfreeze", "check", "reload"));
                suggestions.addAll(server.getAllPlayers().stream()
                        .map(Player::getUsername)
                        .sorted(Comparator.naturalOrder())
                        .toList());
                return match(arguments[0], suggestions);
            }
            if (arguments.length == 2 && arguments[0].equalsIgnoreCase("alerts")) {
                return match(arguments[1], List.of("on", "off"));
            }
            if (arguments.length == 2 && (arguments[0].equalsIgnoreCase("freeze")
                    || arguments[0].equalsIgnoreCase("unfreeze"))) {
                return match(arguments[1], server.getAllPlayers().stream().map(Player::getUsername).toList());
            }
            if (arguments.length == 2 && arguments[0].equalsIgnoreCase("check")) {
                return match(arguments[1], List.of("live", "movement", "combat", "build", "clicks"));
            }
            if (arguments.length == 3 && arguments[0].equalsIgnoreCase("check")) {
                return match(arguments[2], server.getAllPlayers().stream().map(Player::getUsername).toList());
            }
            return List.of();
        }

        @Override
        public boolean hasPermission(Invocation invocation) {
            return invocation.source().hasPermission(ProfilerConstants.PERMISSION_USE);
        }

        private void handleAlerts(CommandSource source, String[] arguments) {
            if (!source.hasPermission(ProfilerConstants.PERMISSION_ALERTS)) {
                send(source, engine.renderNoPermission());
                return;
            }
            if (!(source instanceof Player player)) {
                send(source, engine.renderUsage());
                return;
            }
            if (arguments.length < 2) {
                send(source, engine.renderUsage());
                return;
            }
            boolean enabled = arguments[1].equalsIgnoreCase("on");
            engine.alertsEnabled(player.getUniqueId(), enabled);
            send(source, engine.renderAlertsToggle(enabled));
        }

        private void handleFreeze(CommandSource source, String[] arguments, boolean frozen) {
            if (!source.hasPermission(ProfilerConstants.PERMISSION_FREEZE)) {
                send(source, engine.renderNoPermission());
                return;
            }
            if (arguments.length < 2) {
                send(source, engine.renderUsage());
                return;
            }
            Optional<Player> targetOptional = server.getPlayer(arguments[1]);
            if (targetOptional.isEmpty()) {
                send(source, engine.renderFreezeTargetOffline(arguments[1]));
                return;
            }
            Player target = targetOptional.get();
            if (source instanceof Player player && player.getUniqueId().equals(target.getUniqueId())) {
                send(source, engine.renderFreezeSelf());
                return;
            }

            engine.setFrozen(target.getUniqueId(), target.getUsername(), frozen, "proxy command");
            send(source, engine.renderFreeze(frozen, target.getUsername()));

            boolean delivered = sendToBackend(target, new BridgePackets.FreezePacket(
                    target.getUniqueId(),
                    target.getUsername(),
                    frozen,
                    source instanceof Player player ? player.getUsername() : "console",
                    frozen ? "Frozen by staff" : "Released by staff"
            ));
            if (!delivered) {
                send(source, engine.renderFreezeTargetOffline(target.getUsername()));
            }
        }

        private void handleCheck(CommandSource source, String[] arguments) {
            if (!source.hasPermission(ProfilerConstants.PERMISSION_CHECK)) {
                send(source, engine.renderNoPermission());
                return;
            }
            if (arguments.length < 3) {
                send(source, engine.renderUsage());
                return;
            }
            ProbeType type = ProbeType.fromInput(arguments[1]);
            if (type == null) {
                send(source, engine.renderUsage());
                return;
            }
            Optional<Player> targetOptional = server.getPlayer(arguments[2]);
            if (targetOptional.isEmpty()) {
                send(source, engine.renderFreezeTargetOffline(arguments[2]));
                return;
            }
            Player target = targetOptional.get();
            boolean delivered = sendToBackend(target, new BridgePackets.ProbePacket(
                    target.getUniqueId(),
                    target.getUsername(),
                    type,
                    source instanceof Player player ? player.getUsername() : "console",
                    type == ProbeType.LIVE ? 3 : config.probeDurationSeconds()
            ));
            if (!delivered) {
                send(source, engine.renderFreezeTargetOffline(target.getUsername()));
                return;
            }
            send(source, engine.renderProbeStart(target.getUsername(), type));
        }

        private void handleReload(CommandSource source) {
            if (!source.hasPermission(ProfilerConstants.PERMISSION_RELOAD)) {
                send(source, engine.renderNoPermission());
                return;
            }
            config = ConfigIO.load(dataDirectory, getClass().getClassLoader(), new VelocityLoggerAdapter(logger));
            engine.reload(config);
            send(source, engine.renderReloadComplete());
        }

        private void handlePlayerView(CommandSource source, String target) {
            if (!source.hasPermission(ProfilerConstants.PERMISSION_VIEW)) {
                send(source, engine.renderNoPermission());
                return;
            }
            engine.findByName(target)
                    .ifPresentOrElse(
                            profile -> send(source, engine.renderPlayerDetails(profile)),
                            () -> send(source, engine.renderPlayerNotFound())
                    );
        }

        private List<String> match(String input, List<String> choices) {
            String lowered = input.toLowerCase(Locale.ROOT);
            return choices.stream()
                    .filter(choice -> choice.toLowerCase(Locale.ROOT).startsWith(lowered))
                    .distinct()
                    .sorted()
                    .toList();
        }
    }
}
