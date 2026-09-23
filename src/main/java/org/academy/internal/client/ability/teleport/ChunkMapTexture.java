package org.academy.internal.client.ability.teleport;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.academy.AcademyCraft;
import org.academy.internal.common.ability.teleport.map.MapTileBuilder;

import java.util.function.Supplier;

/**
 * A single dynamic texture holding the 区块跃迁 map at {@link MapTileBuilder#DETAIL} texels per chunk.
 *
 * <p>Drawing the map as one textured quad rather than one quad per chunk keeps the whole viewport in a
 * single batch. The texture is anchored at a chunk origin and only re-anchored when the view walks off
 * its edge; tile writes are incremental and GPU uploads are throttled, so a stream of tile packets
 * costs neither a full-image rewrite nor an upload per packet.
 */
public final class ChunkMapTexture {
    /**
     * Texture size in texels. It must be large enough that the anchor window always covers the
     * viewport: if the window can be smaller than the visible area, the screen re-anchors — clearing
     * and refilling the whole image — on every single frame, which is what made the map stutter and
     * appear to lose its content. 1024 texels at {@link MapTileBuilder#DETAIL} 8 covers 128 chunks
     * per axis: more than the largest requestable view (40 chunks) plus 16 chunks of margin on each
     * side, while keeping the image at 4 MB instead of 16 MB.
     */
    public static final int SIZE = 2048;
    /** How many chunks the window covers per axis. */
    public static final int WINDOW_CHUNKS = SIZE / MapTileBuilder.DETAIL;
    /** Minimum gap between GPU uploads while tiles stream in. */
    private static final long UPLOAD_INTERVAL_MS = 200L;

    private static final Identifier TEXTURE_ID = AcademyCraft.academy("chunk_leap/map");

    private final NativeImage image;
    private DynamicTexture texture;
    private int originChunkX;
    private int originChunkZ;
    private boolean dirty;
    private long lastUploadMs;

    public ChunkMapTexture(int originChunkX, int originChunkZ) {
        this.originChunkX = originChunkX;
        this.originChunkZ = originChunkZ;
        this.image = new NativeImage(NativeImage.Format.RGBA, SIZE, SIZE, true);
        this.texture = new DynamicTexture((Supplier<String>) () -> "academy_chunk_leap_map", image);
        Minecraft.getInstance().getTextureManager().register(TEXTURE_ID, texture);
    }

    public int originChunkX() {
        return originChunkX;
    }

    public int originChunkZ() {
        return originChunkZ;
    }

    /** Writes one texel of one chunk if it falls inside the current anchor window. */
    public void setTexel(int chunkX, int chunkZ, int subX, int subZ, int argb) {
        var x = (chunkX - originChunkX) * MapTileBuilder.DETAIL + subX;
        var z = (chunkZ - originChunkZ) * MapTileBuilder.DETAIL + subZ;
        if (x < 0 || z < 0 || x >= SIZE || z >= SIZE) return;
        image.setPixel(x, z, argb);
        dirty = true;
    }

    /**
     * Writes a whole chunk tile, computing the destination offset once.
     *
     * <p>A full re-anchor resync touches every cached chunk, so doing the bounds arithmetic per texel
     * (rather than per chunk) is the difference between a brief hitch and a visible freeze.
     *
     * @param texels {@link MapTileBuilder#texelCount()} ARGB values, row-major
     */
    public void setChunk(int chunkX, int chunkZ, int[] texels) {
        if (texels == null || texels.length < MapTileBuilder.texelCount()) return;
        var detail = MapTileBuilder.DETAIL;
        var baseX = (chunkX - originChunkX) * detail;
        var baseZ = (chunkZ - originChunkZ) * detail;
        // Skip entirely when the tile lies outside the window.
        if (baseX + detail <= 0 || baseZ + detail <= 0 || baseX >= SIZE || baseZ >= SIZE) return;
        for (var subZ = 0; subZ < detail; subZ++) {
            var z = baseZ + subZ;
            if (z < 0 || z >= SIZE) continue;
            var rowStart = subZ * detail;
            for (var subX = 0; subX < detail; subX++) {
                var x = baseX + subX;
                if (x < 0 || x >= SIZE) continue;
                image.setPixel(x, z, texels[rowStart + subX]);
            }
        }
        dirty = true;
    }

    /**
     * True when the window covers the rectangle with the given margin to spare.
     *
     * <p>The margin is hysteresis: without it, a view that merely grazes the window edge would
     * re-anchor on consecutive frames. With it, re-anchoring happens once per margin-width of panning.
     */
    public boolean coversWithMargin(int minChunkX, int minChunkZ, int maxChunkX, int maxChunkZ, int margin) {
        return minChunkX - margin >= originChunkX && minChunkZ - margin >= originChunkZ
                && maxChunkX + margin < originChunkX + WINDOW_CHUNKS
                && maxChunkZ + margin < originChunkZ + WINDOW_CHUNKS;
    }

    /** Clears the image; the caller must re-request the visible tiles afterwards. */
    public void reanchor(int minChunkX, int minChunkZ) {
        originChunkX = minChunkX;
        originChunkZ = minChunkZ;
        image.fillRect(0, 0, SIZE, SIZE, 0);
        dirty = true;
        // Force the cleared image out immediately so the next frame cannot show stale terrain.
        lastUploadMs = 0;
        upload(true);
    }

    /** Pushes pending texel writes to the GPU, at most every {@link #UPLOAD_INTERVAL_MS}. */
    public void upload() {
        upload(false);
    }

    private void upload(boolean force) {
        if (!dirty && !force) return;
        var now = net.minecraft.util.Util.getMillis();
        if (!force && now - lastUploadMs < UPLOAD_INTERVAL_MS) return;
        dirty = false;
        lastUploadMs = now;
        texture.upload();
    }

    public Identifier textureId() {
        return TEXTURE_ID;
    }

    public void close() {
        if (texture != null) {
            texture.close();
            texture = null;
        }
        image.close();
    }
}
