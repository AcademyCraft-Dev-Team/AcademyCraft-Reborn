package org.academy.internal.common.ability.teleport;

import org.academy.internal.common.ability.teleport.chunk.ChunkLeapPackets;
import org.academy.internal.common.ability.teleport.chunk.ChunkLeapRegion;
import org.academy.internal.common.ability.teleport.chunk.ChunkLeapSelection;
import org.academy.internal.common.ability.teleport.map.MapTileBuilder;


import io.netty.buffer.Unpooled;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Round-trips every 区块跃迁 packet through its codec. These guard the wire contract: a field added
 * on one side without the other shows up here rather than as a silent desync in game.
 */
class ChunkLeapPacketsCodecTest {
    /** Built from an identifier so the plain JUnit run never initialises {@code Level}. */
    private static final ResourceKey<Level> OVERWORLD =
            ResourceKey.create(Registries.DIMENSION, Identifier.parse("minecraft:overworld"));

    @Test
    void viewRequestPreservesRegionAndEpoch() {
        var region = ChunkLeapRegion.ofChunks(OVERWORLD, -12, 34, 8, 5);
        var packet = new ChunkLeapPackets.ViewRequestPacket(region, 7);
        var decoded = roundTrip(packet, ChunkLeapPackets.ViewRequestPacket.CODEC);
        assertEquals(region, decoded.region());
        assertEquals(7, decoded.epoch());
        assertEquals(0, decoded.entityRefreshTicks(), "an unset cadence means server default");
    }

    @Test
    void viewRequestCarriesRequestedEntityCadence() {
        var region = ChunkLeapRegion.ofChunks(OVERWORLD, 0, 0, 4, 4);
        var packet = new ChunkLeapPackets.ViewRequestPacket(region, 3, 25);
        var decoded = roundTrip(packet, ChunkLeapPackets.ViewRequestPacket.CODEC);
        assertEquals(25, decoded.entityRefreshTicks());
    }

    @Test
    void preloadStatusPreservesProgressAndReadyFlag() {
        var opId = UUID.randomUUID();
        var packet = new ChunkLeapPackets.PreloadStatusPacket(
                opId, "minecraft:the_nether", true, 12, 16);
        var decoded = roundTrip(packet, ChunkLeapPackets.PreloadStatusPacket.CODEC);
        assertEquals(opId, decoded.opId());
        assertEquals("minecraft:the_nether", decoded.dimensionId());
        assertTrue(decoded.ready());
        assertEquals(12, decoded.loaded());
        assertEquals(16, decoded.total());
    }

    @Test
    void viewReleasePreservesDimension() {
        var packet = new ChunkLeapPackets.ViewReleasePacket(Identifier.parse("minecraft:the_nether").toString());
        var decoded = roundTrip(packet, ChunkLeapPackets.ViewReleasePacket.CODEC);
        assertEquals(packet.dimensionId(), decoded.dimensionId());
    }

    @Test
    void swapRequestPreservesShapeAndBothAnchors() {
        // A deliberately non-rectangular, disconnected selection: the shape is what must survive the
        // wire, because both sides pair chunks by offset index.
        var source = ChunkLeapSelection.of(OVERWORLD, List.of(
                new net.minecraft.world.level.ChunkPos(0, 0),
                new net.minecraft.world.level.ChunkPos(3, 0),
                new net.minecraft.world.level.ChunkPos(0, 5)));
        var opId = UUID.randomUUID();
        var packet = new ChunkLeapPackets.SwapRequestPacket(opId, source, OVERWORLD, 100, -60, false);
        var decoded = roundTrip(packet, ChunkLeapPackets.SwapRequestPacket.CODEC);
        assertEquals(opId, decoded.opId());
        assertEquals(source.offsets(), decoded.source().offsets(), "the offset list must round-trip");
        assertEquals(source.originChunkX(), decoded.source().originChunkX());
        assertEquals(100, decoded.target().originChunkX(), "target origin X");
        assertEquals(-60, decoded.target().originChunkZ(), "target origin Z");
        // The derived target must be the same shape as the source.
        assertEquals(source.count(), decoded.target().count());
        assertFalse(decoded.movePlayers());
    }

    @Test
    void swapRequestDerivesAnIdenticalTargetShape() {
        var source = ChunkLeapSelection.rectangle(OVERWORLD,
                new net.minecraft.world.level.ChunkPos(0, 0), new net.minecraft.world.level.ChunkPos(3, 1));
        var packet = new ChunkLeapPackets.SwapRequestPacket(UUID.randomUUID(), source, OVERWORLD, 40, 40, true);
        var decoded = roundTrip(packet, ChunkLeapPackets.SwapRequestPacket.CODEC);
        assertEquals(decoded.source().offsets(), decoded.target().offsets(),
                "no shape matching is needed because the target reuses the source offsets");
        assertTrue(decoded.movePlayers());
    }
    @Test
    void entityTeleportPreservesTargetAndClampsCoordinates() {
        var packet = new ChunkLeapPackets.EntityTeleportPacket(
                42, "minecraft:overworld", 100, 70, -100);
        var decoded = roundTrip(packet, ChunkLeapPackets.EntityTeleportPacket.CODEC);
        assertEquals(42, decoded.entityId());
        assertEquals(100, decoded.targetX());
        assertEquals(70, decoded.targetY());
        assertEquals(-100, decoded.targetZ());
    }

    @Test
    void tilesPreservePerChunkRasters() {
        var texels = new int[MapTileBuilder.texelCount()];
        for (var i = 0; i < texels.length; i++) {
            texels[i] = 0xFF000000 | (i * 3 << 16) | (i * 5 << 8) | (i * 7 & 0xFF);
        }
        var tiles = List.of(
                new ChunkLeapPackets.Tile(-5, 9, texels.clone()),
                new ChunkLeapPackets.Tile(40, -7, texels.clone()));
        var packet = new ChunkLeapPackets.TilesPacket("minecraft:overworld", 3, tiles);
        var decoded = roundTrip(packet, ChunkLeapPackets.TilesPacket.CODEC);
        assertEquals(3, decoded.epoch());
        assertEquals(2, decoded.tiles().size());
        assertEquals(-5, decoded.tiles().getFirst().chunkX());
        assertEquals(9, decoded.tiles().getFirst().chunkZ());
        assertTrue(java.util.Arrays.equals(texels, decoded.tiles().getFirst().texels()),
                "a chunk's detail raster must survive the round trip unchanged");
    }

    @Test
    void entitiesPreserveMarkers() {
        var markers = List.of(
                new ChunkLeapPackets.Marker(1, ChunkLeapPackets.CAT_HOSTILE, 55, 100, 200),
                new ChunkLeapPackets.Marker(2, ChunkLeapPackets.CAT_PLAYER, -1, -100, -200));
        var packet = new ChunkLeapPackets.EntitiesPacket("minecraft:overworld", markers);
        var decoded = roundTrip(packet, ChunkLeapPackets.EntitiesPacket.CODEC);
        assertEquals(2, decoded.markers().size());
        assertEquals(1, decoded.markers().getFirst().entityId());
        assertEquals(ChunkLeapPackets.CAT_HOSTILE, decoded.markers().getFirst().category());
        assertEquals(-200, decoded.markers().get(1).blockZ());
    }

    @Test
    void swapResultPreservesReasonAndAffectedRegions() {
        var affected = List.of(ChunkLeapRegion.ofChunks(OVERWORLD, 1, 2, 3, 3));
        var opId = UUID.randomUUID();
        var packet = new ChunkLeapPackets.SwapResultPacket(opId, false,
                "chunk_leap.reason.overlap", affected);
        var decoded = roundTrip(packet, ChunkLeapPackets.SwapResultPacket.CODEC);
        assertEquals(opId, decoded.opId());
        assertFalse(decoded.success());
        assertEquals("chunk_leap.reason.overlap", decoded.reasonKey());
        assertEquals(affected, decoded.affected());
    }

    @Test
    void viewStatusPreservesCounters() {
        var packet = new ChunkLeapPackets.ViewStatusPacket("minecraft:overworld", 12, 30);
        var decoded = roundTrip(packet, ChunkLeapPackets.ViewStatusPacket.CODEC);
        assertEquals(12, decoded.loaded());
        assertEquals(30, decoded.total());
    }

    private static <P> P roundTrip(P packet, net.minecraft.network.codec.StreamCodec<io.netty.buffer.ByteBuf, P> codec) {
        var buffer = Unpooled.buffer();
        try {
            codec.encode(buffer, packet);
            var decoded = codec.decode(buffer);
            assertEquals(0, buffer.readableBytes(), "codec must consume exactly what it wrote");
            return decoded;
        } finally {
            buffer.release();
        }
    }
}
