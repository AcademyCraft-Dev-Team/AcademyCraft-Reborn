package org.academy.internal.common.structure;

import it.unimi.dsi.fastutil.shorts.ShortOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.Collection;
import java.util.LinkedHashMap;

/** Publishes completed structure transfers even in visible, non-ticking host chunks. */
public final class StructureHostBlockUpdates {
    private StructureHostBlockUpdates() {
    }

    public static void publish(ServerLevel host, Collection<BlockPos> positions) {
        if (!host.getServer().isSameThread()) {
            throw new IllegalStateException("Host transfer updates require the server thread");
        }
        var sections = new LinkedHashMap<SectionPos, ShortOpenHashSet>();
        for (var position : positions) {
            sections.computeIfAbsent(SectionPos.of(position), ignored -> new ShortOpenHashSet())
                    .add(SectionPos.sectionRelativePos(position));
        }
        for (var entry : sections.entrySet()) {
            var section = entry.getKey();
            var chunk = host.getChunkSource().getChunk(section.x(), section.z(), ChunkStatus.FULL, false);
            if (chunk == null) continue;
            var players = host.getChunkSource().chunkMap.getPlayers(section.chunk(), false).stream()
                    .filter(player -> player.level() == host && player.connection.isAcceptingMessages()).toList();
            if (players.isEmpty()) continue; // A later initial chunk packet contains the current data.
            // Level.markAndNotifyBlock and ChunkHolder.blockChanged both require BLOCK_TICKING.
            // Capture accepts FULL chunks; an already visible chunk can otherwise retain
            // the old terrain indefinitely after a successful authoritative source removal.
            var packet = new ClientboundSectionBlocksUpdatePacket(section, entry.getValue(),
                    chunk.getSection(host.getSectionIndexFromSectionY(section.y())));
            for (var player : players) player.connection.send(packet);
            packet.runUpdates((position, state) -> {
                if (!state.hasBlockEntity()) return;
                var blockEntity = host.getBlockEntity(position);
                var update = blockEntity == null ? null : blockEntity.getUpdatePacket();
                if (update != null) for (var player : players) player.connection.send(update);
            });
        }
    }
}
