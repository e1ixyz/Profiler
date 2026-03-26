package dev.elim.profiler.common.bridge;

import dev.elim.profiler.common.model.AlertSeverity;
import dev.elim.profiler.common.model.ProbeType;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.UUID;

public final class BridgePackets {
    private static final int VERSION = 1;
    private static final int TYPE_ALERT = 1;
    private static final int TYPE_SNAPSHOT = 2;
    private static final int TYPE_FREEZE = 3;
    private static final int TYPE_PROBE = 4;

    private BridgePackets() {
    }

    public static byte[] encode(Packet packet) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            DataOutputStream stream = new DataOutputStream(output);
            stream.writeInt(VERSION);
            if (packet instanceof AlertPacket alert) {
                stream.writeInt(TYPE_ALERT);
                writeUuid(stream, alert.playerId());
                writeString(stream, alert.playerName());
                writeString(stream, alert.sourceServer());
                writeString(stream, alert.code());
                writeString(stream, alert.message());
                writeString(stream, alert.fingerprint());
                writeString(stream, alert.severity().name());
                stream.writeInt(alert.riskDelta());
            } else if (packet instanceof SnapshotPacket snapshot) {
                stream.writeInt(TYPE_SNAPSHOT);
                writeUuid(stream, snapshot.playerId());
                writeString(stream, snapshot.playerName());
                writeString(stream, snapshot.currentServer());
                stream.writeLong(snapshot.ping());
                stream.writeInt(snapshot.protocolVersion());
                writeString(stream, snapshot.clientBrand());
                writeString(stream, snapshot.modSummary());
                writeString(stream, snapshot.locale());
                stream.writeInt(snapshot.viewDistance());
                stream.writeDouble(snapshot.averageCps());
                stream.writeDouble(snapshot.peakCps());
                stream.writeDouble(snapshot.attacksPerSecond());
                stream.writeDouble(snapshot.averageSpeed());
                stream.writeDouble(snapshot.maxSpeed());
                stream.writeDouble(snapshot.maxReach());
                stream.writeDouble(snapshot.maxRotation());
                stream.writeDouble(snapshot.placementsPerSecond());
                writeString(stream, snapshot.lastProbeSummary());
            } else if (packet instanceof FreezePacket freeze) {
                stream.writeInt(TYPE_FREEZE);
                writeUuid(stream, freeze.playerId());
                writeString(stream, freeze.playerName());
                stream.writeBoolean(freeze.frozen());
                writeString(stream, freeze.actor());
                writeString(stream, freeze.reason());
            } else if (packet instanceof ProbePacket probe) {
                stream.writeInt(TYPE_PROBE);
                writeUuid(stream, probe.playerId());
                writeString(stream, probe.playerName());
                writeString(stream, probe.probeType().name());
                writeString(stream, probe.actor());
                stream.writeInt(probe.durationSeconds());
            } else {
                throw new IllegalArgumentException("Unknown bridge packet type: " + packet.getClass().getName());
            }
            stream.flush();
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to encode bridge packet.", exception);
        }
    }

    public static Packet decode(byte[] data) {
        try {
            DataInputStream input = new DataInputStream(new ByteArrayInputStream(data));
            int version = input.readInt();
            if (version != VERSION) {
                throw new IOException("Unsupported bridge version " + version);
            }
            int type = input.readInt();
            return switch (type) {
                case TYPE_ALERT -> new AlertPacket(
                        readUuid(input),
                        readString(input),
                        readString(input),
                        readString(input),
                        readString(input),
                        readString(input),
                        AlertSeverity.valueOf(readString(input)),
                        input.readInt()
                );
                case TYPE_SNAPSHOT -> new SnapshotPacket(
                        readUuid(input),
                        readString(input),
                        readString(input),
                        input.readLong(),
                        input.readInt(),
                        readString(input),
                        readString(input),
                        readString(input),
                        input.readInt(),
                        input.readDouble(),
                        input.readDouble(),
                        input.readDouble(),
                        input.readDouble(),
                        input.readDouble(),
                        input.readDouble(),
                        input.readDouble(),
                        input.readDouble(),
                        readString(input)
                );
                case TYPE_FREEZE -> new FreezePacket(
                        readUuid(input),
                        readString(input),
                        input.readBoolean(),
                        readString(input),
                        readString(input)
                );
                case TYPE_PROBE -> new ProbePacket(
                        readUuid(input),
                        readString(input),
                        ProbeType.valueOf(readString(input)),
                        readString(input),
                        input.readInt()
                );
                default -> throw new IOException("Unknown bridge packet type " + type);
            };
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to decode bridge packet.", exception);
        }
    }

    private static void writeUuid(DataOutputStream stream, UUID uuid) throws IOException {
        stream.writeLong(uuid.getMostSignificantBits());
        stream.writeLong(uuid.getLeastSignificantBits());
    }

    private static UUID readUuid(DataInputStream stream) throws IOException {
        return new UUID(stream.readLong(), stream.readLong());
    }

    private static void writeString(DataOutputStream stream, String value) throws IOException {
        stream.writeUTF(value == null ? "" : value);
    }

    private static String readString(DataInputStream stream) throws IOException {
        return stream.readUTF();
    }

    public sealed interface Packet permits AlertPacket, SnapshotPacket, FreezePacket, ProbePacket {
    }

    public record AlertPacket(
            UUID playerId,
            String playerName,
            String sourceServer,
            String code,
            String message,
            String fingerprint,
            AlertSeverity severity,
            int riskDelta
    ) implements Packet {
    }

    public record SnapshotPacket(
            UUID playerId,
            String playerName,
            String currentServer,
            long ping,
            int protocolVersion,
            String clientBrand,
            String modSummary,
            String locale,
            int viewDistance,
            double averageCps,
            double peakCps,
            double attacksPerSecond,
            double averageSpeed,
            double maxSpeed,
            double maxReach,
            double maxRotation,
            double placementsPerSecond,
            String lastProbeSummary
    ) implements Packet {
    }

    public record FreezePacket(
            UUID playerId,
            String playerName,
            boolean frozen,
            String actor,
            String reason
    ) implements Packet {
    }

    public record ProbePacket(
            UUID playerId,
            String playerName,
            ProbeType probeType,
            String actor,
            int durationSeconds
    ) implements Packet {
    }
}
