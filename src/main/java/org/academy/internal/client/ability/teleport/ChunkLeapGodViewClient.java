package org.academy.internal.client.ability.teleport;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.ChunkPos;
import org.academy.internal.common.ability.teleport.chunk.ChunkLeapPackets;

/**
 * Receives god-view status and drives the client side of it.
 *
 * <p>The server sends the real chunks before reporting readiness, so this only has to raise the camera once
 * the terrain is actually present. If the target lies in another dimension the client holds no level for it
 * — a client keeps exactly one level — so the god view is refused rather than showing the wrong world.
 */
public final class ChunkLeapGodViewClient {
    /** True once the server has confirmed the target's terrain is streamed and the camera may move. */
    private static volatile boolean ready;

    private ChunkLeapGodViewClient() {
    }

    public static void onStatus(ChunkLeapPackets.GodViewStatusPacket packet) {
        if (!packet.active()) {
            ready = false;
            ChunkLeapGodView.leave();
            return;
        }
        if (!packet.sameDimension()) {
            // Terrain from another dimension cannot be rendered from inside this one.
            ready = false;
            ChunkLeapGodView.leave();
            return;
        }
        // Raise the camera only now: the chunks arrived before this packet by construction.
        ready = true;
        if (ChunkLeapGodView.isActive()) {
            // Already looking: this is a pan, so move without touching the altitude or the zoom level.
            ChunkLeapGodView.refocus(packet.blockX() + 0.5, packet.blockZ() + 0.5);
        } else {
            // First entry: establish the altitude reference from the surface here.
            ChunkLeapGodView.enter(packet.blockX() + 0.5, packet.blockY(), packet.blockZ() + 0.5);
        }
    }

    public static boolean isReady() {
        return ready && ChunkLeapGodView.isActive();
    }

    public static void reset() {
        ready = false;
        ChunkLeapGodView.leave();
    }

    /** The client's current chunk, used to restore the cache window when leaving the god view. */
    public static ChunkPos localChunk() {
        var player = Minecraft.getInstance().player;
        return player == null ? new ChunkPos(0, 0) : player.chunkPosition();
    }
}
