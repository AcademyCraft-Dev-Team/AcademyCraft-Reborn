package org.academy.internal.common.ability.mentalout;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.academy.internal.client.ability.mentalout.MentaloutRosterClientState;
import org.academy.internal.common.network.PacketTypes;
import org.jetbrains.annotations.Nullable;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class MentaloutRosterPackets {
    public static final int MAX_FULL_CHUNK_ENTRIES = 64;
    public static final byte DELTA_UPSERT = 0;
    public static final byte DELTA_REMOVE = 1;

    private MentaloutRosterPackets() {
    }

    public static void initClient() {
        MisakaNetworkClient.NETWORK_MANAGER.register(Client.class);
        MentaloutRosterClientState.setResyncRequester(
                revision -> MisakaNetworkClient.send(new ResyncPacket(revision))
        );
    }

    public static void initServer() {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static void sendFull(
            ServerPlayer player,
            long revision,
            List<RosterEntry> entries,
            int stuporCp,
            int impressionCp
    ) {
        var safeEntries = List.copyOf(entries);
        var totalChunks = safeEntries.isEmpty()
                ? 0
                : 1 + (safeEntries.size() - 1) / MAX_FULL_CHUNK_ENTRIES;
        MisakaNetworkServer.send(player, new FullStartPacket(
                revision,
                totalChunks,
                safeEntries.size(),
                stuporCp,
                impressionCp
        ));
        for (var chunkIndex = 0; chunkIndex < totalChunks; chunkIndex++) {
            var from = chunkIndex * MAX_FULL_CHUNK_ENTRIES;
            var to = Math.min(safeEntries.size(), from + MAX_FULL_CHUNK_ENTRIES);
            MisakaNetworkServer.send(player, new FullChunkPacket(
                    revision,
                    chunkIndex,
                    safeEntries.subList(from, to)
            ));
        }
    }

    public static void sendUpsert(
            ServerPlayer player,
            long revision,
            RosterEntry entry,
            int stuporCp,
            int impressionCp
    ) {
        MisakaNetworkServer.send(player, new DeltaPacket(
                revision,
                DELTA_UPSERT,
                entry,
                null,
                stuporCp,
                impressionCp
        ));
    }

    public static void sendRemove(
            ServerPlayer player,
            long revision,
            UUID targetUuid,
            int stuporCp,
            int impressionCp
    ) {
        MisakaNetworkServer.send(player, new DeltaPacket(
                revision,
                DELTA_REMOVE,
                null,
                targetUuid,
                stuporCp,
                impressionCp
        ));
    }

    public static void sendClear(ServerPlayer player, long revision) {
        MisakaNetworkServer.send(player, new ClearPacket(revision));
    }

    private static void encodeEntry(ByteBuf buf, RosterEntry entry) {
        encodeUuid(buf, entry.targetUuid);
        ByteBufCodecs.VAR_INT.encode(buf, entry.entityId);
        ByteBufCodecs.STRING_UTF8.encode(buf, entry.entityTypeId);
        ByteBufCodecs.STRING_UTF8.encode(buf, entry.displayName);
        buf.writeFloat(entry.health);
        buf.writeFloat(entry.maxHealth);
        buf.writeFloat(entry.distance);
        buf.writeByte(entry.support);
        buf.writeByte(entry.flags);
        ByteBufCodecs.VAR_INT.encode(buf, entry.misidentificationTicks);
    }

    private static RosterEntry decodeEntry(ByteBuf buf) {
        return new RosterEntry(
                decodeUuid(buf),
                ByteBufCodecs.VAR_INT.decode(buf),
                ByteBufCodecs.STRING_UTF8.decode(buf),
                ByteBufCodecs.STRING_UTF8.decode(buf),
                buf.readFloat(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readByte(),
                buf.readByte(),
                ByteBufCodecs.VAR_INT.decode(buf)
        );
    }

    private static void encodeUuid(ByteBuf buf, UUID uuid) {
        buf.writeLong(uuid.getMostSignificantBits());
        buf.writeLong(uuid.getLeastSignificantBits());
    }

    private static UUID decodeUuid(ByteBuf buf) {
        return new UUID(buf.readLong(), buf.readLong());
    }

    public record RosterEntry(
            UUID targetUuid,
            int entityId,
            String entityTypeId,
            String displayName,
            float health,
            float maxHealth,
            float distance,
            byte support,
            byte flags,
            int misidentificationTicks
    ) {
        public RosterEntry {
            Objects.requireNonNull(targetUuid, "targetUuid");
            entityTypeId = sanitize(entityTypeId, 128, "minecraft:unknown");
            displayName = sanitize(displayName, 96, entityTypeId);
            health = finiteNonNegative(health);
            maxHealth = finiteNonNegative(maxHealth);
            distance = Float.isFinite(distance) && distance >= 0 ? distance : Float.MAX_VALUE;
            misidentificationTicks = Math.max(0, misidentificationTicks);
        }

        private static String sanitize(String value, int maxLength, String fallback) {
            if (value == null || value.isBlank()) return fallback;
            return value.length() <= maxLength ? value : value.substring(0, maxLength);
        }

        private static float finiteNonNegative(float value) {
            return Float.isFinite(value) ? Math.max(0, value) : 0;
        }
    }

    public static final class Client {
        private Client() {
        }

        @SubscribePacket
        public static void handle(FullStartPacket packet) {
            MentaloutRosterClientState.applyFullStart(
                    packet.revision,
                    packet.totalChunks,
                    packet.totalEntries,
                    packet.stuporCp,
                    packet.impressionCp
            );
        }

        @SubscribePacket
        public static void handle(FullChunkPacket packet) {
            MentaloutRosterClientState.applyFullChunk(
                    packet.revision,
                    packet.chunkIndex,
                    packet.entries.stream().map(Client::toClientEntry).toList()
            );
        }

        @SubscribePacket
        public static void handle(DeltaPacket packet) {
            MentaloutRosterClientState.applyDelta(
                    packet.revision,
                    packet.operation,
                    packet.entry == null ? null : toClientEntry(packet.entry),
                    packet.targetUuid,
                    packet.stuporCp,
                    packet.impressionCp
            );
        }

        @SubscribePacket
        public static void handle(ClearPacket packet) {
            MentaloutRosterClientState.clear(packet.revision);
        }

        private static MentaloutRosterClientState.Entry toClientEntry(RosterEntry entry) {
            return new MentaloutRosterClientState.Entry(
                    entry.targetUuid,
                    entry.entityId,
                    entry.entityTypeId,
                    entry.displayName,
                    entry.health,
                    entry.maxHealth,
                    entry.distance,
                    entry.support,
                    entry.flags,
                    entry.misidentificationTicks
            );
        }
    }

    public static final class Server {
        private Server() {
        }

        @SubscribePacket
        public static void handle(ResyncPacket packet) {
            var listener = packet.getPacketListener();
            var player = listener.getPlayer();
            if (!MentaloutRequestGuard.acceptRosterResync(
                    listener,
                    player.level().getServer().getTickCount()
            )) return;
            MentaloutControlContext.handleResync(player, packet.clientRevision);
        }
    }

    @PacketTarget(ThreadType.CLIENT)
    public static final class FullStartPacket extends Packet<ClientPacketListener, FullStartPacket> {
        public static final StreamCodec<ByteBuf, FullStartPacket> CODEC = StreamCodec.of(
                (buf, packet) -> {
                    buf.writeLong(packet.revision);
                    ByteBufCodecs.VAR_INT.encode(buf, packet.totalChunks);
                    ByteBufCodecs.VAR_INT.encode(buf, packet.totalEntries);
                    ByteBufCodecs.VAR_INT.encode(buf, packet.stuporCp);
                    ByteBufCodecs.VAR_INT.encode(buf, packet.impressionCp);
                },
                buf -> new FullStartPacket(
                        buf.readLong(),
                        ByteBufCodecs.VAR_INT.decode(buf),
                        ByteBufCodecs.VAR_INT.decode(buf),
                        ByteBufCodecs.VAR_INT.decode(buf),
                        ByteBufCodecs.VAR_INT.decode(buf)
                )
        );
        private final long revision;
        private final int totalChunks;
        private final int totalEntries;
        private final int stuporCp;
        private final int impressionCp;

        public FullStartPacket(long revision, int totalChunks, int totalEntries, int stuporCp, int impressionCp) {
            this.revision = Math.max(0, revision);
            this.totalChunks = Math.max(0, totalChunks);
            this.totalEntries = Math.max(0, totalEntries);
            this.stuporCp = Math.max(0, stuporCp);
            this.impressionCp = Math.max(0, impressionCp);
        }

        @Override
        public PacketType<ClientPacketListener, FullStartPacket> getPacketType() {
            return PacketTypes.MENTALOUT_ROSTER_FULL_START.get();
        }
    }

    @PacketTarget(ThreadType.CLIENT)
    public static final class FullChunkPacket extends Packet<ClientPacketListener, FullChunkPacket> {
        public static final StreamCodec<ByteBuf, FullChunkPacket> CODEC = StreamCodec.of(
                (buf, packet) -> {
                    buf.writeLong(packet.revision);
                    ByteBufCodecs.VAR_INT.encode(buf, packet.chunkIndex);
                    ByteBufCodecs.VAR_INT.encode(buf, packet.entries.size());
                    packet.entries.forEach(entry -> encodeEntry(buf, entry));
                },
                buf -> {
                    var revision = buf.readLong();
                    var chunkIndex = ByteBufCodecs.VAR_INT.decode(buf);
                    var count = ByteBufCodecs.VAR_INT.decode(buf);
                    if (count < 0 || count > MAX_FULL_CHUNK_ENTRIES) {
                        throw new DecoderException("Invalid mentalout roster chunk size: " + count);
                    }
                    var entries = new ArrayList<RosterEntry>(count);
                    for (var index = 0; index < count; index++) entries.add(decodeEntry(buf));
                    return new FullChunkPacket(revision, chunkIndex, entries);
                }
        );
        private final long revision;
        private final int chunkIndex;
        private final List<RosterEntry> entries;

        public FullChunkPacket(long revision, int chunkIndex, List<RosterEntry> entries) {
            if (entries.size() > MAX_FULL_CHUNK_ENTRIES) {
                throw new IllegalArgumentException("Mentalout roster chunk exceeds " + MAX_FULL_CHUNK_ENTRIES);
            }
            this.revision = Math.max(0, revision);
            this.chunkIndex = Math.max(0, chunkIndex);
            this.entries = List.copyOf(entries);
        }

        @Override
        public PacketType<ClientPacketListener, FullChunkPacket> getPacketType() {
            return PacketTypes.MENTALOUT_ROSTER_FULL_CHUNK.get();
        }
    }

    @PacketTarget(ThreadType.CLIENT)
    public static final class DeltaPacket extends Packet<ClientPacketListener, DeltaPacket> {
        public static final StreamCodec<ByteBuf, DeltaPacket> CODEC = StreamCodec.of(
                (buf, packet) -> {
                    buf.writeLong(packet.revision);
                    buf.writeByte(packet.operation);
                    buf.writeBoolean(packet.entry != null);
                    if (packet.entry != null) encodeEntry(buf, packet.entry);
                    buf.writeBoolean(packet.targetUuid != null);
                    if (packet.targetUuid != null) encodeUuid(buf, packet.targetUuid);
                    ByteBufCodecs.VAR_INT.encode(buf, packet.stuporCp);
                    ByteBufCodecs.VAR_INT.encode(buf, packet.impressionCp);
                },
                buf -> new DeltaPacket(
                        buf.readLong(),
                        buf.readByte(),
                        buf.readBoolean() ? decodeEntry(buf) : null,
                        buf.readBoolean() ? decodeUuid(buf) : null,
                        ByteBufCodecs.VAR_INT.decode(buf),
                        ByteBufCodecs.VAR_INT.decode(buf)
                )
        );
        private final long revision;
        private final byte operation;
        private final @Nullable RosterEntry entry;
        private final @Nullable UUID targetUuid;
        private final int stuporCp;
        private final int impressionCp;

        public DeltaPacket(
                long revision,
                byte operation,
                @Nullable RosterEntry entry,
                @Nullable UUID targetUuid,
                int stuporCp,
                int impressionCp
        ) {
            if (operation != DELTA_UPSERT && operation != DELTA_REMOVE) {
                throw new IllegalArgumentException("Unknown roster delta operation: " + operation);
            }
            if (operation == DELTA_UPSERT && entry == null) {
                throw new IllegalArgumentException("Roster upsert requires an entry");
            }
            if (operation == DELTA_REMOVE && targetUuid == null) {
                throw new IllegalArgumentException("Roster removal requires a target UUID");
            }
            this.revision = Math.max(0, revision);
            this.operation = operation;
            this.entry = entry;
            this.targetUuid = targetUuid;
            this.stuporCp = Math.max(0, stuporCp);
            this.impressionCp = Math.max(0, impressionCp);
        }

        @Override
        public PacketType<ClientPacketListener, DeltaPacket> getPacketType() {
            return PacketTypes.MENTALOUT_ROSTER_DELTA.get();
        }
    }

    @PacketTarget(ThreadType.CLIENT)
    public static final class ClearPacket extends Packet<ClientPacketListener, ClearPacket> {
        public static final StreamCodec<ByteBuf, ClearPacket> CODEC = StreamCodec.of(
                (buf, packet) -> buf.writeLong(packet.revision),
                buf -> new ClearPacket(buf.readLong())
        );
        private final long revision;

        public ClearPacket(long revision) {
            this.revision = Math.max(0, revision);
        }

        @Override
        public PacketType<ClientPacketListener, ClearPacket> getPacketType() {
            return PacketTypes.MENTALOUT_ROSTER_CLEAR.get();
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class ResyncPacket extends Packet<ServerGamePacketListenerImpl, ResyncPacket> {
        public static final StreamCodec<ByteBuf, ResyncPacket> CODEC = StreamCodec.of(
                (buf, packet) -> buf.writeLong(packet.clientRevision),
                buf -> new ResyncPacket(buf.readLong())
        );
        private final long clientRevision;

        public ResyncPacket(long clientRevision) {
            this.clientRevision = Math.max(0, clientRevision);
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, ResyncPacket> getPacketType() {
            return PacketTypes.MENTALOUT_ROSTER_RESYNC.get();
        }
    }
}
