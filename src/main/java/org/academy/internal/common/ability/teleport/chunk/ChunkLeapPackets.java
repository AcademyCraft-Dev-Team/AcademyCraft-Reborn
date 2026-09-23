package org.academy.internal.common.ability.teleport.chunk;

import org.academy.internal.common.ability.teleport.map.ChunkMapClientState;
import org.academy.internal.common.ability.teleport.map.ChunkMapViewService;
import org.academy.internal.common.ability.teleport.map.MapTileBuilder;


import io.netty.buffer.ByteBuf;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.academy.internal.client.ability.teleport.ChunkLeapGodViewClient;
import org.academy.internal.common.network.PacketTypes;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Wire protocol for 区块跃迁.
 *
 * <p>Layout: C2S requests are view management (which chunks the client is looking at), swap
 * commits, and single-entity teleports. S2C payloads are the map raster (one texel per chunk),
 * entity markers, and operation results. All list lengths and coordinates are clamped on decode so
 * a malformed packet cannot drive the server into a degenerate operation.
 */
public final class ChunkLeapPackets {
    private static boolean clientInitialized;

    /** Coarse entity category, used to pick a map marker icon client-side. */
    public static final byte CAT_HOSTILE = 0;
    public static final byte CAT_PASSIVE = 1;
    public static final byte CAT_NEUTRAL = 2;
    public static final byte CAT_PLAYER = 3;
    public static final byte CAT_ITEM = 4;
    public static final byte CAT_PROJECTILE = 5;
    public static final byte CAT_OTHER = 6;
    public static final byte CATEGORY_COUNT = 7;

    public static void initClient() {
        if (clientInitialized) return;
        clientInitialized = true;
        MisakaNetworkClient.NETWORK_MANAGER.register(Client.class);
    }

    public static void initServer() {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    private ChunkLeapPackets() {
    }

    // ------------------------------------------------------------------ C2S

    /** Tells the server which chunk rectangle of which dimension the client currently has open. */
    @PacketTarget(ThreadType.SERVER)
    public static final class ViewRequestPacket extends Packet<ServerGamePacketListenerImpl, ViewRequestPacket> {
        public static final StreamCodec<ByteBuf, ViewRequestPacket> CODEC = StreamCodec.of(
                (buf, packet) -> {
                    ChunkLeapRegion.STREAM_CODEC.encode(buf, packet.region);
                    ByteBufCodecs.VAR_INT.encode(buf, packet.epoch);
                    ByteBufCodecs.VAR_INT.encode(buf, packet.entityRefreshTicks);
                },
                buf -> new ViewRequestPacket(
                        ChunkLeapRegion.STREAM_CODEC.decode(buf),
                        ByteBufCodecs.VAR_INT.decode(buf),
                        ByteBufCodecs.VAR_INT.decode(buf)));

        private final ChunkLeapRegion region;
        private final int epoch;
        private final int entityRefreshTicks;

        public ViewRequestPacket(ChunkLeapRegion region, int epoch) {
            this(region, epoch, 0);
        }

        /**
         * @param entityRefreshTicks requested entity-marker cadence in ticks; {@code 0} means "server
         *                           default". The server clamps whatever arrives.
         */
        public ViewRequestPacket(ChunkLeapRegion region, int epoch, int entityRefreshTicks) {
            this.region = region;
            this.epoch = epoch;
            this.entityRefreshTicks = Math.max(0, entityRefreshTicks);
        }

        public ChunkLeapRegion region() {
            return region;
        }

        public int epoch() {
            return epoch;
        }

        public int entityRefreshTicks() {
            return entityRefreshTicks;
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, ViewRequestPacket> getPacketType() {
            return PacketTypes.CHUNK_LEAP_VIEW_REQUEST.get();
        }
    }

    /** Drops the receiver's view lease for a dimension (page closed, or map closed). */
    @PacketTarget(ThreadType.SERVER)
    public static final class ViewReleasePacket extends Packet<ServerGamePacketListenerImpl, ViewReleasePacket> {
        public static final StreamCodec<ByteBuf, ViewReleasePacket> CODEC = StreamCodec.of(
                (buf, packet) -> ByteBufCodecs.STRING_UTF8.encode(buf, packet.dimensionId),
                buf -> new ViewReleasePacket(ByteBufCodecs.STRING_UTF8.decode(buf)));

        private final String dimensionId;

        public ViewReleasePacket(String dimensionId) {
            this.dimensionId = dimensionId;
        }

        public String dimensionId() {
            return dimensionId;
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, ViewReleasePacket> getPacketType() {
            return PacketTypes.CHUNK_LEAP_VIEW_RELEASE.get();
        }
    }

    /**
     * Commits a swap between the source selection and the same shape re-anchored at the target.
     *
     * <p>Only one shape is sent. The target is defined as "these offsets, at that origin", so the two
     * sides are equal in count and identical in layout by construction — the client cannot request an
     * unequal or mismatched swap, and the player never has to re-draw the target by hand.
     */
    @PacketTarget(ThreadType.SERVER)
    public static final class SwapRequestPacket extends Packet<ServerGamePacketListenerImpl, SwapRequestPacket> {
        public static final StreamCodec<ByteBuf, SwapRequestPacket> CODEC = StreamCodec.of(
                (buf, packet) -> {
                    writeUuid(buf, packet.opId);
                    // Source selection: dimension, origin, and the shared offset list.
                    ByteBufCodecs.STRING_UTF8.encode(buf, packet.source.dimension().identifier().toString());
                    ByteBufCodecs.VAR_INT.encode(buf, packet.source.originChunkX());
                    ByteBufCodecs.VAR_INT.encode(buf, packet.source.originChunkZ());
                    ByteBufCodecs.VAR_INT.encode(buf, packet.source.count());
                    for (var offset : packet.source.offsets()) {
                        ByteBufCodecs.VAR_INT.encode(buf, offset.dx());
                        ByteBufCodecs.VAR_INT.encode(buf, offset.dz());
                    }
                    // Target: destination dimension and origin only.
                    ByteBufCodecs.STRING_UTF8.encode(buf, packet.targetDimension.identifier().toString());
                    ByteBufCodecs.VAR_INT.encode(buf, packet.targetOriginX);
                    ByteBufCodecs.VAR_INT.encode(buf, packet.targetOriginZ);
                    ByteBufCodecs.BOOL.encode(buf, packet.movePlayers);
                },
                buf -> {
                    var opId = readUuid(buf);
                    var dimension = ChunkLeapSelection.dimensionKey(ByteBufCodecs.STRING_UTF8.decode(buf));
                    var originX = clampCoord(ByteBufCodecs.VAR_INT.decode(buf));
                    var originZ = clampCoord(ByteBufCodecs.VAR_INT.decode(buf));
                    var count = ChunkLeapRegion.clamp(ByteBufCodecs.VAR_INT.decode(buf), 0,
                            ChunkLeapSelection.MAX_CHUNKS);
                    var offsets = new ArrayList<ChunkLeapSelection.Offset>(count);
                    for (var i = 0; i < count; i++) {
                        var dx = ChunkLeapRegion.clamp(ByteBufCodecs.VAR_INT.decode(buf), 0,
                                ChunkLeapSelection.MAX_OFFSET);
                        var dz = ChunkLeapRegion.clamp(ByteBufCodecs.VAR_INT.decode(buf), 0,
                                ChunkLeapSelection.MAX_OFFSET);
                        offsets.add(new ChunkLeapSelection.Offset(dx, dz));
                    }
                    var targetDimension = ChunkLeapSelection.dimensionKey(
                            ByteBufCodecs.STRING_UTF8.decode(buf));
                    var targetOriginX = clampCoord(ByteBufCodecs.VAR_INT.decode(buf));
                    var targetOriginZ = clampCoord(ByteBufCodecs.VAR_INT.decode(buf));
                    return new SwapRequestPacket(opId,
                            new ChunkLeapSelection(dimension, originX, originZ, offsets),
                            targetDimension, targetOriginX, targetOriginZ,
                            ByteBufCodecs.BOOL.decode(buf));
                });

        private final UUID opId;
        private final ChunkLeapSelection source;
        private final ResourceKey<Level> targetDimension;
        private final int targetOriginX;
        private final int targetOriginZ;
        private final boolean movePlayers;

        public SwapRequestPacket(UUID opId, ChunkLeapSelection source, ResourceKey<Level> targetDimension,
                                 int targetOriginX, int targetOriginZ, boolean movePlayers) {
            this.opId = opId;
            this.source = source;
            this.targetDimension = targetDimension;
            this.targetOriginX = targetOriginX;
            this.targetOriginZ = targetOriginZ;
            this.movePlayers = movePlayers;
        }

        public UUID opId() {
            return opId;
        }

        public ChunkLeapSelection source() {
            return source;
        }

        /** The target selection: the source shape re-anchored at the requested origin. */
        public ChunkLeapSelection target() {
            return source.anchoredAt(targetDimension, new ChunkPos(targetOriginX, targetOriginZ));
        }

        public boolean movePlayers() {
            return movePlayers;
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, SwapRequestPacket> getPacketType() {
            return PacketTypes.CHUNK_LEAP_SWAP.get();
        }
    }
    /**
     * Asks to send the requesting player to a map position of their own choosing.
     *
     * <p>Only the target is carried: the server resolves the column against its own blocks and picks a safe
     * standing place, so the client cannot name a position inside terrain.
     */
    @PacketTarget(ThreadType.SERVER)
    public static final class PlayerTeleportPacket extends Packet<ServerGamePacketListenerImpl, PlayerTeleportPacket> {
        public static final StreamCodec<ByteBuf, PlayerTeleportPacket> CODEC = StreamCodec.of(
                (buf, packet) -> {
                    ByteBufCodecs.STRING_UTF8.encode(buf, packet.dimensionId);
                    ByteBufCodecs.VAR_INT.encode(buf, packet.blockX);
                    ByteBufCodecs.VAR_INT.encode(buf, packet.blockZ);
                },
                buf -> new PlayerTeleportPacket(
                        ByteBufCodecs.STRING_UTF8.decode(buf),
                        clampCoord(ByteBufCodecs.VAR_INT.decode(buf)),
                        clampCoord(ByteBufCodecs.VAR_INT.decode(buf))));

        private final String dimensionId;
        private final int blockX;
        private final int blockZ;

        public PlayerTeleportPacket(String dimensionId, int blockX, int blockZ) {
            this.dimensionId = dimensionId;
            this.blockX = blockX;
            this.blockZ = blockZ;
        }

        public String dimensionId() {
            return dimensionId;
        }

        public int blockX() {
            return blockX;
        }

        public int blockZ() {
            return blockZ;
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, PlayerTeleportPacket> getPacketType() {
            return PacketTypes.CHUNK_LEAP_PLAYER_TELEPORT.get();
        }
    }
    /** Teleports one entity to a target block position; the map picks both. */
    @PacketTarget(ThreadType.SERVER)
    public static final class EntityTeleportPacket extends Packet<ServerGamePacketListenerImpl, EntityTeleportPacket> {
        public static final StreamCodec<ByteBuf, EntityTeleportPacket> CODEC = StreamCodec.of(
                (buf, packet) -> {
                    ByteBufCodecs.VAR_INT.encode(buf, packet.entityId);
                    ByteBufCodecs.STRING_UTF8.encode(buf, packet.dimensionId);
                    ByteBufCodecs.VAR_INT.encode(buf, packet.targetX);
                    ByteBufCodecs.VAR_INT.encode(buf, packet.targetY);
                    ByteBufCodecs.VAR_INT.encode(buf, packet.targetZ);
                },
                buf -> new EntityTeleportPacket(
                        ByteBufCodecs.VAR_INT.decode(buf),
                        ByteBufCodecs.STRING_UTF8.decode(buf),
                        clampCoord(ByteBufCodecs.VAR_INT.decode(buf)),
                        clampCoord(ByteBufCodecs.VAR_INT.decode(buf)),
                        clampCoord(ByteBufCodecs.VAR_INT.decode(buf))));

        private final int entityId;
        private final String dimensionId;
        private final int targetX;
        private final int targetY;
        private final int targetZ;

        public EntityTeleportPacket(int entityId, String dimensionId, int targetX, int targetY, int targetZ) {
            this.entityId = entityId;
            this.dimensionId = dimensionId;
            this.targetX = targetX;
            this.targetY = targetY;
            this.targetZ = targetZ;
        }

        public int entityId() {
            return entityId;
        }

        public String dimensionId() {
            return dimensionId;
        }

        public int targetX() {
            return targetX;
        }

        public int targetY() {
            return targetY;
        }

        public int targetZ() {
            return targetZ;
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, EntityTeleportPacket> getPacketType() {
            return PacketTypes.CHUNK_LEAP_ENTITY_TELEPORT.get();
        }
    }

    // ------------------------------------------------------------------ S2C

    /** One sampled chunk: absolute chunk coordinates plus its {@code detail×detail} ARGB texels. */
    public record Tile(int chunkX, int chunkZ, int[] texels) {
    }

    /**
     * One map raster batch. Only sampled (loaded) chunks are included; the client renders everything
     * else as a placeholder, so unloaded chunks cost nothing on the wire.
     */
    @PacketTarget(ThreadType.CLIENT)
    public static final class TilesPacket extends Packet<ClientPacketListener, TilesPacket> {
        public static final int MAX_TILES = 32;
        public static final int MAX_TEXELS_PER_TILE = ChunkLeapRegion.MAX_CHUNKS;

        public static final StreamCodec<ByteBuf, TilesPacket> CODEC = StreamCodec.of(
                (buf, packet) -> {
                    ByteBufCodecs.STRING_UTF8.encode(buf, packet.dimensionId);
                    ByteBufCodecs.VAR_INT.encode(buf, packet.epoch);
                    ByteBufCodecs.VAR_INT.encode(buf, MapTileBuilder.DETAIL);
                    ByteBufCodecs.VAR_INT.encode(buf, packet.tiles.size());
                    for (var tile : packet.tiles) {
                        ByteBufCodecs.VAR_INT.encode(buf, tile.chunkX());
                        ByteBufCodecs.VAR_INT.encode(buf, tile.chunkZ());
                        for (var texel : tile.texels()) {
                            buf.writeInt(texel);
                        }
                    }
                },
                buf -> {
                    var dimensionId = ByteBufCodecs.STRING_UTF8.decode(buf);
                    var epoch = ByteBufCodecs.VAR_INT.decode(buf);
                    var detail = ChunkLeapRegion.clamp(ByteBufCodecs.VAR_INT.decode(buf), 1, 16);
                    var texelsPerTile = detail * detail;
                    var count = ChunkLeapRegion.clamp(ByteBufCodecs.VAR_INT.decode(buf), 0, MAX_TILES);
                    var tiles = new ArrayList<Tile>(count);
                    for (var i = 0; i < count; i++) {
                        var chunkX = clampCoord(ByteBufCodecs.VAR_INT.decode(buf));
                        var chunkZ = clampCoord(ByteBufCodecs.VAR_INT.decode(buf));
                        var texels = new int[Math.min(texelsPerTile, MAX_TEXELS_PER_TILE)];
                        for (var t = 0; t < texels.length; t++) {
                            texels[t] = buf.readInt();
                        }
                        tiles.add(new Tile(chunkX, chunkZ, texels));
                    }
                    return new TilesPacket(dimensionId, epoch, tiles);
                });

        private final String dimensionId;
        private final int epoch;
        private final List<Tile> tiles;

        public TilesPacket(String dimensionId, int epoch, List<Tile> tiles) {
            this.dimensionId = dimensionId;
            this.epoch = epoch;
            this.tiles = List.copyOf(tiles);
        }

        public String dimensionId() {
            return dimensionId;
        }

        public int epoch() {
            return epoch;
        }

        public List<Tile> tiles() {
            return tiles;
        }

        @Override
        public PacketType<ClientPacketListener, TilesPacket> getPacketType() {
            return PacketTypes.CHUNK_LEAP_TILES.get();
        }
    }

    /**
     * One entity on the map.
     *
     * <p>Carries the disposition category for colouring, the type for the hover name and client-side model
     * thumbnail, and — for players only — the UUID used to resolve their real skin from the tab list. Unsupported
     * entity types retain the category-colour fallback.
     */
    public record Marker(int entityId, byte category, int typeId, int blockX, int blockZ,
                         @Nullable UUID playerId) {
        public static final StreamCodec<ByteBuf, Marker> CODEC = StreamCodec.of(
                (buf, marker) -> {
                    ByteBufCodecs.VAR_INT.encode(buf, marker.entityId);
                    ByteBufCodecs.BYTE.encode(buf, marker.category);
                    ByteBufCodecs.VAR_INT.encode(buf, marker.typeId);
                    ByteBufCodecs.VAR_INT.encode(buf, marker.blockX);
                    ByteBufCodecs.VAR_INT.encode(buf, marker.blockZ);
                    ByteBufCodecs.BOOL.encode(buf, marker.playerId != null);
                    if (marker.playerId != null) {
                        buf.writeLong(marker.playerId.getMostSignificantBits());
                        buf.writeLong(marker.playerId.getLeastSignificantBits());
                    }
                },
                buf -> {
                    var entityId = ByteBufCodecs.VAR_INT.decode(buf);
                    var category = ByteBufCodecs.BYTE.decode(buf);
                    var typeId = ByteBufCodecs.VAR_INT.decode(buf);
                    var blockX = clampCoord(ByteBufCodecs.VAR_INT.decode(buf));
                    var blockZ = clampCoord(ByteBufCodecs.VAR_INT.decode(buf));
                    UUID playerId = null;
                    if (ByteBufCodecs.BOOL.decode(buf)) {
                        playerId = new UUID(buf.readLong(), buf.readLong());
                    }
                    return new Marker(entityId, category, typeId, blockX, blockZ, playerId);
                });

        /** Convenience for a non-player marker. */
        public Marker(int entityId, byte category, int typeId, int blockX, int blockZ) {
            this(entityId, category, typeId, blockX, blockZ, null);
        }
    }
    /** Entity markers for the currently viewed area; rate-limited server-side. */
    @PacketTarget(ThreadType.CLIENT)
    public static final class EntitiesPacket extends Packet<ClientPacketListener, EntitiesPacket> {
        public static final int MAX_MARKERS = 512;

        public static final StreamCodec<ByteBuf, EntitiesPacket> CODEC = StreamCodec.of(
                (buf, packet) -> {
                    ByteBufCodecs.STRING_UTF8.encode(buf, packet.dimensionId);
                    ByteBufCodecs.VAR_INT.encode(buf, packet.markers.size());
                    for (var marker : packet.markers) {
                        Marker.CODEC.encode(buf, marker);
                    }
                },
                buf -> {
                    var dimensionId = ByteBufCodecs.STRING_UTF8.decode(buf);
                    var count = ChunkLeapRegion.clamp(
                            ByteBufCodecs.VAR_INT.decode(buf), 0, MAX_MARKERS);
                    var markers = new ArrayList<Marker>(count);
                    for (var i = 0; i < count; i++) {
                        markers.add(Marker.CODEC.decode(buf));
                    }
                    return new EntitiesPacket(dimensionId, markers);
                });

        private final String dimensionId;
        private final List<Marker> markers;

        public EntitiesPacket(String dimensionId, List<Marker> markers) {
            this.dimensionId = dimensionId;
            this.markers = List.copyOf(markers);
        }

        public String dimensionId() {
            return dimensionId;
        }

        public List<Marker> markers() {
            return markers;
        }

        @Override
        public PacketType<ClientPacketListener, EntitiesPacket> getPacketType() {
            return PacketTypes.CHUNK_LEAP_ENTITIES.get();
        }
    }

    /** Result of a swap request; {@code affected} lists chunks whose map texels are now stale. */
    @PacketTarget(ThreadType.CLIENT)
    public static final class SwapResultPacket extends Packet<ClientPacketListener, SwapResultPacket> {
        public static final StreamCodec<ByteBuf, SwapResultPacket> CODEC = StreamCodec.of(
                (buf, packet) -> {
                    writeUuid(buf, packet.opId);
                    ByteBufCodecs.BOOL.encode(buf, packet.success);
                    ByteBufCodecs.STRING_UTF8.encode(buf, packet.reasonKey);
                    ByteBufCodecs.VAR_INT.encode(buf, packet.affected.size());
                    for (var region : packet.affected) {
                        ChunkLeapRegion.STREAM_CODEC.encode(buf, region);
                    }
                },
                buf -> {
                    var opId = readUuid(buf);
                    var success = ByteBufCodecs.BOOL.decode(buf);
                    var reasonKey = ByteBufCodecs.STRING_UTF8.decode(buf);
                    var count = ChunkLeapRegion.clamp(ByteBufCodecs.VAR_INT.decode(buf), 0, 4);
                    var affected = new ArrayList<ChunkLeapRegion>(count);
                    for (var i = 0; i < count; i++) {
                        affected.add(ChunkLeapRegion.STREAM_CODEC.decode(buf));
                    }
                    return new SwapResultPacket(opId, success, reasonKey, affected);
                });

        private final UUID opId;
        private final boolean success;
        private final String reasonKey;
        private final List<ChunkLeapRegion> affected;

        public SwapResultPacket(UUID opId, boolean success, String reasonKey, List<ChunkLeapRegion> affected) {
            this.opId = opId;
            this.success = success;
            this.reasonKey = reasonKey == null ? "" : reasonKey;
            this.affected = List.copyOf(affected);
        }

        public UUID opId() {
            return opId;
        }

        public boolean success() {
            return success;
        }

        public String reasonKey() {
            return reasonKey;
        }

        public List<ChunkLeapRegion> affected() {
            return affected;
        }

        @Override
        public PacketType<ClientPacketListener, SwapResultPacket> getPacketType() {
            return PacketTypes.CHUNK_LEAP_SWAP_RESULT.get();
        }
    }

    /** Result of a single-entity teleport. */
    @PacketTarget(ThreadType.CLIENT)
    public static final class TeleportResultPacket extends Packet<ClientPacketListener, TeleportResultPacket> {
        public static final StreamCodec<ByteBuf, TeleportResultPacket> CODEC = StreamCodec.of(
                (buf, packet) -> {
                    ByteBufCodecs.BOOL.encode(buf, packet.success);
                    ByteBufCodecs.STRING_UTF8.encode(buf, packet.reasonKey);
                },
                buf -> new TeleportResultPacket(
                        ByteBufCodecs.BOOL.decode(buf),
                        ByteBufCodecs.STRING_UTF8.decode(buf)));

        private final boolean success;
        private final String reasonKey;

        public TeleportResultPacket(boolean success, String reasonKey) {
            this.success = success;
            this.reasonKey = reasonKey == null ? "" : reasonKey;
        }

        public boolean success() {
            return success;
        }

        public String reasonKey() {
            return reasonKey;
        }

        @Override
        public PacketType<ClientPacketListener, TeleportResultPacket> getPacketType() {
            return PacketTypes.CHUNK_LEAP_TELEPORT_RESULT.get();
        }
    }

    /** Load progress for the currently viewed rectangle. */
    @PacketTarget(ThreadType.CLIENT)
    public static final class ViewStatusPacket extends Packet<ClientPacketListener, ViewStatusPacket> {
        public static final StreamCodec<ByteBuf, ViewStatusPacket> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, ViewStatusPacket::dimensionId,
                ByteBufCodecs.VAR_INT, ViewStatusPacket::loaded,
                ByteBufCodecs.VAR_INT, ViewStatusPacket::total,
                ViewStatusPacket::new);

        private final String dimensionId;
        private final int loaded;
        private final int total;

        public ViewStatusPacket(String dimensionId, int loaded, int total) {
            this.dimensionId = dimensionId;
            this.loaded = Math.max(0, loaded);
            this.total = Math.max(0, total);
        }

        public String dimensionId() {
            return dimensionId;
        }

        public int loaded() {
            return loaded;
        }

        public int total() {
            return total;
        }

        @Override
        public PacketType<ClientPacketListener, ViewStatusPacket> getPacketType() {
            return PacketTypes.CHUNK_LEAP_VIEW_STATUS.get();
        }
    }

    /**
     * Destination preload progress for an in-flight swap.
     *
     * <p>Sent while both swap regions are being pulled into memory, and once more with {@code ready}
     * set. The map screen shows this so the player can tell "still loading" from "stuck", which matters
     * because the server only performs the swap after the load completes.
     */
    @PacketTarget(ThreadType.CLIENT)
    public static final class PreloadStatusPacket extends Packet<ClientPacketListener, PreloadStatusPacket> {
        public static final StreamCodec<ByteBuf, PreloadStatusPacket> CODEC = StreamCodec.of(
                (buf, packet) -> {
                    writeUuid(buf, packet.opId);
                    ByteBufCodecs.STRING_UTF8.encode(buf, packet.dimensionId);
                    ByteBufCodecs.BOOL.encode(buf, packet.ready);
                    ByteBufCodecs.VAR_INT.encode(buf, packet.loaded);
                    ByteBufCodecs.VAR_INT.encode(buf, packet.total);
                },
                buf -> new PreloadStatusPacket(
                        readUuid(buf),
                        ByteBufCodecs.STRING_UTF8.decode(buf),
                        ByteBufCodecs.BOOL.decode(buf),
                        ByteBufCodecs.VAR_INT.decode(buf),
                        ByteBufCodecs.VAR_INT.decode(buf)));

        private final UUID opId;
        private final String dimensionId;
        private final boolean ready;
        private final int loaded;
        private final int total;

        public PreloadStatusPacket(UUID opId, String dimensionId, boolean ready, int loaded, int total) {
            this.opId = opId;
            this.dimensionId = dimensionId;
            this.ready = ready;
            this.loaded = Math.max(0, loaded);
            this.total = Math.max(0, total);
        }

        public UUID opId() {
            return opId;
        }

        public String dimensionId() {
            return dimensionId;
        }

        public boolean ready() {
            return ready;
        }

        public int loaded() {
            return loaded;
        }

        public int total() {
            return total;
        }

        @Override
        public PacketType<ClientPacketListener, PreloadStatusPacket> getPacketType() {
            return PacketTypes.CHUNK_LEAP_PRELOAD_STATUS.get();
        }
    }

    // ------------------------------------------------------------------ inspect

    /**
     * Asks what a chunk actually contains.
     *
     * <p>The map shows colours sampled from real blocks; this reports the blocks themselves, which is
     * what a player needs in order to decide whether a target is worth swapping to. It works in any
     * dimension, including ones the client holds no level for, because the server reads its own chunks.
     */
    @PacketTarget(ThreadType.SERVER)
    public static final class InspectRequestPacket extends Packet<ServerGamePacketListenerImpl, InspectRequestPacket> {
        public static final StreamCodec<ByteBuf, InspectRequestPacket> CODEC = StreamCodec.of(
                (buf, packet) -> {
                    ByteBufCodecs.STRING_UTF8.encode(buf, packet.dimensionId);
                    ByteBufCodecs.VAR_INT.encode(buf, packet.chunkX);
                    ByteBufCodecs.VAR_INT.encode(buf, packet.chunkZ);
                },
                buf -> new InspectRequestPacket(
                        ByteBufCodecs.STRING_UTF8.decode(buf),
                        clampCoord(ByteBufCodecs.VAR_INT.decode(buf)),
                        clampCoord(ByteBufCodecs.VAR_INT.decode(buf))));

        private final String dimensionId;
        private final int chunkX;
        private final int chunkZ;

        public InspectRequestPacket(String dimensionId, int chunkX, int chunkZ) {
            this.dimensionId = dimensionId;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
        }

        public String dimensionId() {
            return dimensionId;
        }

        public int chunkX() {
            return chunkX;
        }

        public int chunkZ() {
            return chunkZ;
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, InspectRequestPacket> getPacketType() {
            return PacketTypes.CHUNK_LEAP_INSPECT_REQUEST.get();
        }
    }

    /** Result of an inspect request; mirrors {@code ChunkLeapInspector.Report}. */
    @PacketTarget(ThreadType.CLIENT)
    public static final class InspectResultPacket extends Packet<ClientPacketListener, InspectResultPacket> {
        public static final int MAX_PALETTE = 5;

        public static final StreamCodec<ByteBuf, InspectResultPacket> CODEC = StreamCodec.of(
                (buf, packet) -> {
                    ByteBufCodecs.STRING_UTF8.encode(buf, packet.dimensionId);
                    ByteBufCodecs.VAR_INT.encode(buf, packet.chunkX);
                    ByteBufCodecs.VAR_INT.encode(buf, packet.chunkZ);
                    ByteBufCodecs.BOOL.encode(buf, packet.loaded);
                    ByteBufCodecs.STRING_UTF8.encode(buf, packet.surfaceBlock);
                    ByteBufCodecs.VAR_INT.encode(buf, packet.surfaceY);
                    ByteBufCodecs.STRING_UTF8.encode(buf, packet.biome);
                    ByteBufCodecs.VAR_INT.encode(buf, packet.entityCount);
                    ByteBufCodecs.VAR_INT.encode(buf, packet.palette.size());
                    for (var entry : packet.palette) {
                        ByteBufCodecs.STRING_UTF8.encode(buf, entry.blockId());
                        ByteBufCodecs.VAR_INT.encode(buf, entry.count());
                    }
                },
                buf -> {
                    var dimensionId = ByteBufCodecs.STRING_UTF8.decode(buf);
                    var chunkX = clampCoord(ByteBufCodecs.VAR_INT.decode(buf));
                    var chunkZ = clampCoord(ByteBufCodecs.VAR_INT.decode(buf));
                    var loaded = ByteBufCodecs.BOOL.decode(buf);
                    var surfaceBlock = ByteBufCodecs.STRING_UTF8.decode(buf);
                    var surfaceY = clampCoord(ByteBufCodecs.VAR_INT.decode(buf));
                    var biome = ByteBufCodecs.STRING_UTF8.decode(buf);
                    var entityCount = ByteBufCodecs.VAR_INT.decode(buf);
                    var count = ChunkLeapRegion.clamp(ByteBufCodecs.VAR_INT.decode(buf), 0, MAX_PALETTE);
                    var palette = new ArrayList<ChunkLeapInspector.BlockTally>(count);
                    for (var i = 0; i < count; i++) {
                        palette.add(new ChunkLeapInspector.BlockTally(
                                ByteBufCodecs.STRING_UTF8.decode(buf),
                                ByteBufCodecs.VAR_INT.decode(buf)));
                    }
                    return new InspectResultPacket(dimensionId, chunkX, chunkZ, loaded, surfaceBlock,
                            surfaceY, biome, entityCount, palette);
                });

        private final String dimensionId;
        private final int chunkX;
        private final int chunkZ;
        private final boolean loaded;
        private final String surfaceBlock;
        private final int surfaceY;
        private final String biome;
        private final int entityCount;
        private final List<ChunkLeapInspector.BlockTally> palette;

        public InspectResultPacket(String dimensionId, int chunkX, int chunkZ, boolean loaded,
                                   String surfaceBlock, int surfaceY, String biome, int entityCount,
                                   List<ChunkLeapInspector.BlockTally> palette) {
            this.dimensionId = dimensionId;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.loaded = loaded;
            this.surfaceBlock = surfaceBlock == null ? "" : surfaceBlock;
            this.surfaceY = surfaceY;
            this.biome = biome == null ? "" : biome;
            this.entityCount = Math.max(0, entityCount);
            this.palette = List.copyOf(palette);
        }

        public static InspectResultPacket from(ChunkLeapInspector.Report report) {
            return new InspectResultPacket(report.dimensionId(), report.chunkX(), report.chunkZ(),
                    report.loaded(), report.surfaceBlock(), report.surfaceY(), report.biome(),
                    report.entityCount(), report.palette());
        }

        public String dimensionId() {
            return dimensionId;
        }

        public int chunkX() {
            return chunkX;
        }

        public int chunkZ() {
            return chunkZ;
        }

        public boolean loaded() {
            return loaded;
        }

        public String surfaceBlock() {
            return surfaceBlock;
        }

        public int surfaceY() {
            return surfaceY;
        }

        public String biome() {
            return biome;
        }

        public int entityCount() {
            return entityCount;
        }

        public List<ChunkLeapInspector.BlockTally> palette() {
            return palette;
        }

        @Override
        public PacketType<ClientPacketListener, InspectResultPacket> getPacketType() {
            return PacketTypes.CHUNK_LEAP_INSPECT_RESULT.get();
        }
    }
    // ------------------------------------------------------------------ god view

    /**
     * Asks to see the real terrain at a location, or to stop.
     *
     * <p>Not a request for a picture: the server moves the client chunk-cache window to the target and
     * streams the real chunks there, so the ordinary renderer can draw them from a detached camera. The
     * client only needs the coordinates.
     */
    @PacketTarget(ThreadType.SERVER)
    public static final class GodViewRequestPacket extends Packet<ServerGamePacketListenerImpl, GodViewRequestPacket> {
        public static final StreamCodec<ByteBuf, GodViewRequestPacket> CODEC = StreamCodec.of(
                (buf, packet) -> {
                    ByteBufCodecs.STRING_UTF8.encode(buf, packet.dimensionId);
                    ByteBufCodecs.VAR_INT.encode(buf, packet.blockX);
                    ByteBufCodecs.VAR_INT.encode(buf, packet.blockZ);
                    ByteBufCodecs.BOOL.encode(buf, packet.enter);
                },
                buf -> new GodViewRequestPacket(
                        ByteBufCodecs.STRING_UTF8.decode(buf),
                        clampCoord(ByteBufCodecs.VAR_INT.decode(buf)),
                        clampCoord(ByteBufCodecs.VAR_INT.decode(buf)),
                        ByteBufCodecs.BOOL.decode(buf)));

        private final String dimensionId;
        private final int blockX;
        private final int blockZ;
        private final boolean enter;

        public GodViewRequestPacket(String dimensionId, int blockX, int blockZ, boolean enter) {
            this.dimensionId = dimensionId;
            this.blockX = blockX;
            this.blockZ = blockZ;
            this.enter = enter;
        }

        public String dimensionId() {
            return dimensionId;
        }

        public int blockX() {
            return blockX;
        }

        public int blockZ() {
            return blockZ;
        }

        public boolean enter() {
            return enter;
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, GodViewRequestPacket> getPacketType() {
            return PacketTypes.CHUNK_LEAP_GOD_VIEW_REQUEST.get();
        }
    }

    /**
     * Reports that the god view is ready (or has ended), and where the camera should sit.
     *
     * <p>The client must not move its camera until the chunks have actually been sent, or it would look at
     * empty space for a moment.
     */
    @PacketTarget(ThreadType.CLIENT)
    public static final class GodViewStatusPacket extends Packet<ClientPacketListener, GodViewStatusPacket> {
        public static final StreamCodec<ByteBuf, GodViewStatusPacket> CODEC = StreamCodec.of(
                (buf, packet) -> {
                    ByteBufCodecs.STRING_UTF8.encode(buf, packet.dimensionId);
                    ByteBufCodecs.BOOL.encode(buf, packet.active);
                    ByteBufCodecs.BOOL.encode(buf, packet.sameDimension);
                    ByteBufCodecs.VAR_INT.encode(buf, packet.blockX);
                    ByteBufCodecs.VAR_INT.encode(buf, packet.blockY);
                    ByteBufCodecs.VAR_INT.encode(buf, packet.blockZ);
                },
                buf -> new GodViewStatusPacket(
                        ByteBufCodecs.STRING_UTF8.decode(buf),
                        ByteBufCodecs.BOOL.decode(buf),
                        ByteBufCodecs.BOOL.decode(buf),
                        clampCoord(ByteBufCodecs.VAR_INT.decode(buf)),
                        clampCoord(ByteBufCodecs.VAR_INT.decode(buf)),
                        clampCoord(ByteBufCodecs.VAR_INT.decode(buf))));

        private final String dimensionId;
        private final boolean active;
        private final boolean sameDimension;
        private final int blockX;
        private final int blockY;
        private final int blockZ;

        public GodViewStatusPacket(String dimensionId, boolean active, boolean sameDimension,
                                   int blockX, int blockY, int blockZ) {
            this.dimensionId = dimensionId;
            this.active = active;
            this.sameDimension = sameDimension;
            this.blockX = blockX;
            this.blockY = blockY;
            this.blockZ = blockZ;
        }

        public String dimensionId() {
            return dimensionId;
        }

        public boolean active() {
            return active;
        }

        /**
         * Whether the target is in the dimension the client already holds.
         *
         * <p>A client keeps one level at a time, so terrain can only be shown in place when this is true;
         * otherwise the camera would be looking at another dimension from inside this one.
         */
        public boolean sameDimension() {
            return sameDimension;
        }

        public int blockX() {
            return blockX;
        }

        public int blockY() {
            return blockY;
        }

        public int blockZ() {
            return blockZ;
        }

        @Override
        public PacketType<ClientPacketListener, GodViewStatusPacket> getPacketType() {
            return PacketTypes.CHUNK_LEAP_GOD_VIEW_STATUS.get();
        }
    }
    // ------------------------------------------------------------------ handlers

    public static final class Client {
        private Client() {
        }

        @SubscribePacket
        public static void handleTiles(TilesPacket packet) {
            ChunkMapClientState.acceptTiles(packet);
        }

        @SubscribePacket
        public static void handleEntities(EntitiesPacket packet) {
            ChunkMapClientState.acceptEntities(packet);
        }

        @SubscribePacket
        public static void handleViewStatus(ViewStatusPacket packet) {
            ChunkMapClientState.acceptViewStatus(packet);
        }


        @SubscribePacket
        public static void handlePreloadStatus(PreloadStatusPacket packet) {
            ChunkMapClientState.acceptPreloadStatus(packet);
        }

        @SubscribePacket
        public static void handleSwapResult(SwapResultPacket packet) {
            ChunkMapClientState.handleSwapResult(packet);
        }

        @SubscribePacket
        public static void handleTeleportResult(TeleportResultPacket packet) {
            ChunkMapClientState.handleTeleportResult(packet);
        }

        @SubscribePacket
        public static void handleInspectResult(InspectResultPacket packet) {
            ChunkMapClientState.acceptInspectResult(packet);
        }


        @SubscribePacket
        public static void handleGodViewStatus(GodViewStatusPacket packet) {
            ChunkLeapGodViewClient.onStatus(packet);
        }
    }

    public static final class Server {
        private Server() {
        }

        @SubscribePacket
        public static void handleViewRequest(ViewRequestPacket packet) {
            ChunkMapViewService.onViewRequest(packet.getPacketListener().getPlayer(), packet);
        }

        @SubscribePacket
        public static void handleViewRelease(ViewReleasePacket packet) {
            ChunkMapViewService.onViewRelease(packet.getPacketListener().getPlayer(), packet);
        }

        @SubscribePacket
        public static void handleSwap(SwapRequestPacket packet) {
            ChunkSwapService.onSwapRequest(packet.getPacketListener().getPlayer(), packet);
        }

        @SubscribePacket
        public static void handleEntityTeleport(EntityTeleportPacket packet) {
            ChunkSwapService.onEntityTeleport(packet.getPacketListener().getPlayer(), packet);
        }

        @SubscribePacket
        public static void handlePlayerTeleport(PlayerTeleportPacket packet) {
            ChunkSwapService.onPlayerTeleport(packet.getPacketListener().getPlayer(), packet);
        }

        @SubscribePacket
        public static void handleInspectRequest(InspectRequestPacket packet) {
            ChunkSwapService.onInspectRequest(packet.getPacketListener().getPlayer(), packet);
        }


        @SubscribePacket
        public static void handleGodViewRequest(GodViewRequestPacket packet) {
            ChunkSwapService.onGodViewRequest(packet.getPacketListener().getPlayer(), packet);
        }
    }

    // ------------------------------------------------------------------ helpers

    static void writeUuid(ByteBuf buf, UUID uuid) {
        buf.writeLong(uuid.getMostSignificantBits());
        buf.writeLong(uuid.getLeastSignificantBits());
    }

    static UUID readUuid(ByteBuf buf) {
        return new UUID(buf.readLong(), buf.readLong());
    }

    static int clampCoord(int value) {
        return Math.max(-30_000_000, Math.min(30_000_000, value));
    }
}
