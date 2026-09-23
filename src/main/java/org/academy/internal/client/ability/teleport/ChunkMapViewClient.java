package org.academy.internal.client.ability.teleport;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.academy.internal.common.ability.teleport.chunk.ChunkLeapPackets;
import org.academy.internal.common.ability.teleport.chunk.ChunkLeapRegion;
import org.misaka.MisakaNetworkClient;

/**
 * Client-side view requester for the 区块跃迁 map.
 *
 * <p>Translates the screen's visible chunk rectangle into a server view request, throttled so that
 * panning and zooming do not produce a request per frame. Only the parts of the view that changed
 * are re-requested; the server keeps the whole rectangle loaded while the view stays put.
 */
public final class ChunkMapViewClient {
    /** Minimum gap between view requests, in milliseconds. */
    private static final long REQUEST_DEBOUNCE_MS = 150L;
    /** Hardest rectangle side the client will ask for, regardless of zoom. */
    public static final int MAX_REQUEST_SIDE = 40;
    /** Hardest chunk count the client will ask for in one view. */
    public static final int MAX_REQUEST_CHUNKS = 1600;

    private int epoch;
    private ResourceKey<Level> lastDimension;
    private ChunkLeapRegion lastRequested;
    private long lastRequestAt;
    private boolean viewDirty = true;

    /** Marks the view as needing a request (pan, zoom, page switch, or fresh open). */
    public void invalidate() {
        viewDirty = true;
    }

    public int epoch() {
        return epoch;
    }

    /** Starts a fresh epoch, so patches from the previous page are ignored by the client cache. */
    public void newEpoch() {
        epoch++;
        lastRequested = null;
        viewDirty = true;
    }

    public void release(ResourceKey<Level> dimension) {
        if (dimension == null) return;
        MisakaNetworkClient.send(new ChunkLeapPackets.ViewReleasePacket(dimension.identifier().toString()));
    }

    /**
     * Sends a view request when the visible rectangle has changed and the debounce has elapsed.
     *
     * @param visible the chunk rectangle currently on screen, before clamping
     */
    public void tick(ResourceKey<Level> dimension, ChunkLeapRegion visible, boolean autoLoad) {
        if (dimension == null) return;
        if (lastDimension != null && !lastDimension.equals(dimension)) {
            release(lastDimension);
            newEpoch();
        }
        lastDimension = dimension;
        if (!autoLoad) return;
        // A forced invalidate (a swap reshuffled terrain) must reach the server even though the
        // rectangle is unchanged: deduplicating it away silently dropped the refresh and the map kept
        // showing pre-swap terrain until the screen was reopened.
        var forced = viewDirty;
        if (!forced && !needsRequest(dimension, visible)) return;

        var now = System.currentTimeMillis();
        if (now - lastRequestAt < REQUEST_DEBOUNCE_MS) return;
        lastRequestAt = now;
        viewDirty = false;

        var clamped = clamp(dimension, visible);
        if (!forced && clamped.equals(lastRequested) && epoch != 0) return;
        lastRequested = clamped;
        MisakaNetworkClient.send(new ChunkLeapPackets.ViewRequestPacket(
                clamped, epoch, ChunkLeapClientConfig.active().getEntityRefreshTicks()));
    }

    private boolean needsRequest(ResourceKey<Level> dimension, ChunkLeapRegion visible) {
        if (lastRequested == null) return true;
        if (!lastRequested.dimension().equals(dimension)) return true;
        return !lastRequested.equals(clamp(dimension, visible));
    }

    /**
     * Shrinks an oversized rectangle around its centre so zooming out cannot flood the server.
     *
     * <p>Bounded by the configured view radius, the hard side cap and the hard area cap; all three,
     * because a 48×12 rectangle fits the side cap but is still 576 chunks of terrain generation.
     */
    private static ChunkLeapRegion clamp(ResourceKey<Level> dimension, ChunkLeapRegion visible) {
        var radiusSide = ChunkLeapClientConfig.viewRadiusChunks() * 2;
        var maxSide = Math.max(1, Math.min(MAX_REQUEST_SIDE, radiusSide));
        var width = Math.min(visible.width(), maxSide);
        var height = Math.min(visible.height(), maxSide);
        if ((long) width * height > MAX_REQUEST_CHUNKS) {
            while ((long) width * height > MAX_REQUEST_CHUNKS) {
                if (width >= height && width > 1) width--;
                else if (height > 1) height--;
                else break;
            }
        }
        return ChunkLeapRegion.ofChunks(dimension,
                visible.minChunkX() + (visible.width() - width) / 2,
                visible.minChunkZ() + (visible.height() - height) / 2,
                width, height);
    }

    /** Local player's dimension, for "jump to me" and the default page. */
    public static ResourceKey<Level> playerDimension() {
        var level = Minecraft.getInstance().level;
        return level == null ? Level.OVERWORLD : level.dimension();
    }
}
