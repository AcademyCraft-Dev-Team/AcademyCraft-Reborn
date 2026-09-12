package org.academy.internal.gui.map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.academy.api.client.resources.R;
import org.academy.internal.client.ability.teleport.ChunkMapTexture;
import org.academy.internal.common.ability.teleport.ChunkLeapPackets;
import org.academy.internal.common.ability.teleport.ChunkLeapRegion;
import org.academy.internal.common.ability.teleport.ChunkMapClientState;
import org.academy.internal.common.ability.teleport.MapTileBuilder;

import java.util.List;

/**
 * Draws the 区块跃迁 map: the chunk raster, the chunk grid, selection rectangles and entity markers.
 *
 * <p>All geometry derives from {@link Geometry}, which is pure screen/pixel math and therefore
 * testable. The raster is drawn as a single textured quad, clamped to the intersection of the viewport
 * and the texture's anchor window — sampling outside the window is what previously smeared the map
 * across the screen when panning or zooming. Grid lines and selections are thin filled rects; together
 * that keeps a full-screen map inside a handful of draw calls instead of one per chunk.
 */
public final class ChunkMapRenderer {
    private static final int PLACEHOLDER = 0xFF0E0E12;
    private static final int GRID_MINOR = 0x14FFFFFF;
    private static final int GRID_MAJOR = 0x28FFFFFF;
    private static final int SOURCE_FILL = 0x557B3FA1;
    private static final int SOURCE_BORDER = 0xFFC77DFF;
    private static final int TARGET_FILL = 0x5579C58D;
    private static final int TARGET_BORDER = 0xFF9BE3AE;
    private static final int DANGER_BORDER = 0xFFE16F68;
    private static final int MAJOR_EVERY = 8;
    private static final int MIN_CHUNK_SIZE = 2;
    private static final int MAX_MARKER_HIT = 6;
    private static final int MAX_ENTITY_THUMBNAILS = 64;

    private final ChunkMapTexture texture;
    private final EntityRadarThumbnailRenderer entityThumbnails = new EntityRadarThumbnailRenderer();

    public ChunkMapRenderer(ChunkMapTexture texture) {
        this.texture = texture;
    }

    /** Visible viewport and view transform, in screen pixels and chunk units. */
    public record Geometry(int left, int top, int width, int height,
                           double centerChunkX, double centerChunkZ, double zoom) {
        public int right() {
            return left + width;
        }

        public int bottom() {
            return top + height;
        }

        public double chunkSize() {
            return Math.max(MIN_CHUNK_SIZE, 16.0 * zoom);
        }

        /** Screen X of a chunk boundary. */
        public double screenX(double chunkX) {
            return left + width / 2.0 + (chunkX - centerChunkX) * chunkSize();
        }

        public double screenZ(double chunkZ) {
            return top + height / 2.0 + (chunkZ - centerChunkZ) * chunkSize();
        }

        public int minVisibleChunkX() {
            return (int) Math.floor(centerChunkX - width / 2.0 / chunkSize());
        }

        public int maxVisibleChunkX() {
            return (int) Math.ceil(centerChunkX + width / 2.0 / chunkSize());
        }

        public int minVisibleChunkZ() {
            return (int) Math.floor(centerChunkZ - height / 2.0 / chunkSize());
        }

        public int maxVisibleChunkZ() {
            return (int) Math.ceil(centerChunkZ + height / 2.0 / chunkSize());
        }
    }

    /** Applies queued tile patches incrementally; a full rebuild happens only on re-anchor. */
    public void applyTilePatches(ResourceKey<Level> dimension) {
        if (dimension == null) return;
        var patches = ChunkMapClientState.drainTilePatches();
        if (patches.isEmpty()) return;
        var map = ChunkMapClientState.map(dimension.identifier().toString());
        for (var patch : patches) {
            if (!patch.dimensionId().equals(dimension.identifier().toString())) continue;
            for (var tile : patch.tiles()) {
                var texels = map.tile(tile.chunkX(), tile.chunkZ());
                if (texels == null) continue;
                texture.setChunk(tile.chunkX(), tile.chunkZ(), texels);
            }
        }
    }

    /** Rebuilds the whole window from the cache; used after a re-anchor or dimension switch. */
    public void resyncAll(ResourceKey<Level> dimension) {
        if (dimension == null) return;
        var map = ChunkMapClientState.map(dimension.identifier().toString());
        // Only the chunks the server was asked for are in the cache, so walk those rather than the
        // whole window: a full-window walk would be 65k cache probes for a few hundred entries.
        for (var z = 0; z < ChunkMapTexture.WINDOW_CHUNKS; z++) {
            for (var x = 0; x < ChunkMapTexture.WINDOW_CHUNKS; x++) {
                var chunkX = texture.originChunkX() + x;
                var chunkZ = texture.originChunkZ() + z;
                var texels = map.tile(chunkX, chunkZ);
                if (texels == null) continue;
                texture.setChunk(chunkX, chunkZ, texels);
            }
        }
    }

    /** Draws the raster, clipped to the anchor window, over a placeholder backdrop. */
    public void renderMap(GuiGraphicsExtractor graphics, Geometry g) {
        graphics.enableScissor(g.left(), g.top(), g.right(), g.bottom());
        graphics.fill(g.left(), g.top(), g.right(), g.bottom(), PLACEHOLDER);

        // Intersect the visible chunk range with the texture window.
        var x0 = Math.max(g.minVisibleChunkX(), texture.originChunkX());
        var z0 = Math.max(g.minVisibleChunkZ(), texture.originChunkZ());
        var x1 = Math.min(g.maxVisibleChunkX(), texture.originChunkX() + ChunkMapTexture.WINDOW_CHUNKS - 1);
        var z1 = Math.min(g.maxVisibleChunkZ(), texture.originChunkZ() + ChunkMapTexture.WINDOW_CHUNKS - 1);
        if (x1 >= x0 && z1 >= z0) {
            var left = (int) Math.round(g.screenX(x0));
            var top = (int) Math.round(g.screenZ(z0));
            var right = (int) Math.round(g.screenX(x1 + 1));
            var bottom = (int) Math.round(g.screenZ(z1 + 1));
            var detail = (float) MapTileBuilder.DETAIL;
            var u0 = (x0 - texture.originChunkX()) * detail / ChunkMapTexture.SIZE;
            var u1 = (x1 + 1 - texture.originChunkX()) * detail / ChunkMapTexture.SIZE;
            var v0 = (z0 - texture.originChunkZ()) * detail / ChunkMapTexture.SIZE;
            var v1 = (z1 + 1 - texture.originChunkZ()) * detail / ChunkMapTexture.SIZE;
            graphics.blit(texture.textureId(), left, top, right, bottom, u0, u1, v0, v1);
        }
        graphics.disableScissor();
    }

    /** Chunk grid: fine lines every chunk, brighter every {@link #MAJOR_EVERY}. */
    public void renderGrid(GuiGraphicsExtractor graphics, Geometry g) {
        graphics.enableScissor(g.left(), g.top(), g.right(), g.bottom());
        var size = g.chunkSize();
        if (size >= 4) {
            for (var chunkX = g.minVisibleChunkX(); chunkX <= g.maxVisibleChunkX(); chunkX++) {
                var x = (int) Math.round(g.screenX(chunkX));
                graphics.fill(x, g.top(), x + 1, g.bottom(),
                        chunkX % MAJOR_EVERY == 0 ? GRID_MAJOR : GRID_MINOR);
            }
            for (var chunkZ = g.minVisibleChunkZ(); chunkZ <= g.maxVisibleChunkZ(); chunkZ++) {
                var z = (int) Math.round(g.screenZ(chunkZ));
                graphics.fill(g.left(), z, g.right(), z + 1,
                        chunkZ % MAJOR_EVERY == 0 ? GRID_MAJOR : GRID_MINOR);
            }
        }
        graphics.disableScissor();
    }

    /**
     * Draws the source chunks and the derived target chunks.
     *
     * <p>Chunks are filled individually rather than as one rectangle, because a selection may be
     * disconnected (ctrl-clicked) and must show exactly which chunks it contains.
     */
    public void renderSelection(GuiGraphicsExtractor graphics, Geometry g,
                                java.util.Set<net.minecraft.world.level.ChunkPos> sourceChunks,
                                java.util.Set<net.minecraft.world.level.ChunkPos> targetChunks,
                                boolean clamped) {
        graphics.enableScissor(g.left(), g.top(), g.right(), g.bottom());
        var sourceBorder = clamped ? DANGER_BORDER : SOURCE_BORDER;
        for (var chunk : sourceChunks) {
            renderChunkCell(graphics, g, chunk, SOURCE_FILL, sourceBorder);
        }
        for (var chunk : targetChunks) {
            renderChunkCell(graphics, g, chunk, TARGET_FILL, TARGET_BORDER);
        }
        graphics.disableScissor();
    }

    /** Fills one chunk cell and outlines it, clipped to the viewport. */
    private void renderChunkCell(GuiGraphicsExtractor graphics, Geometry g,
                                 net.minecraft.world.level.ChunkPos chunk, int fill, int borderColor) {
        var x0 = (int) Math.round(g.screenX(chunk.x()));
        var z0 = (int) Math.round(g.screenZ(chunk.z()));
        var x1 = (int) Math.round(g.screenX(chunk.x() + 1));
        var z1 = (int) Math.round(g.screenZ(chunk.z() + 1));
        var left = Math.max(g.left(), x0);
        var top = Math.max(g.top(), z0);
        var right = Math.min(g.right(), x1);
        var bottom = Math.min(g.bottom(), z1);
        if (right <= left || bottom <= top) return;
        graphics.fill(left, top, right, bottom, fill);
        graphics.fill(left, top, right, top + 1, borderColor);
        graphics.fill(left, bottom - 1, right, bottom, borderColor);
        graphics.fill(left, top, left + 1, bottom, borderColor);
        graphics.fill(right - 1, top, right, bottom, borderColor);
    }
    /**
     * Draws one marker per entity: player skin faces, type-cached living-entity previews, then category dots.
     *
     * <p>Entity previews use the vanilla GUI model renderer rather than copied texture assumptions. Unsupported
     * entities keep their disposition dot, and the per-frame preview budget prevents dense radar scenes from
     * turning hundreds of model renders into a frame-time spike.
     *
     * <p>The local player is ringed in blue so the map answers "where am I" without a legend.
     */
    public void renderMarkers(GuiGraphicsExtractor graphics, Geometry g, ResourceKey<Level> dimension,
                              int selectedEntityId, int hoveredEntityId,
                              java.util.UUID localPlayerId) {
        if (dimension == null) return;
        var markers = ChunkMapClientState.map(dimension.identifier().toString()).markers();
        graphics.enableScissor(g.left(), g.top(), g.right(), g.bottom());
        var size = g.chunkSize();
        // Portraits read best a little larger than a bare dot, but must not swallow the terrain at low zoom.
        var radius = Math.max(3, Math.min(6, (int) Math.round(size / 7.0)));
        var diameter = radius * 2;
        var thumbnailsRendered = 0;
        for (var marker : markers) {
            var centreX = g.screenX((marker.blockX() + 0.5) / 16.0);
            var centreZ = g.screenZ((marker.blockZ() + 0.5) / 16.0);
            if (centreX < g.left() - 10 || centreX > g.right() + 10
                    || centreZ < g.top() - 10 || centreZ > g.bottom() + 10) continue;
            var x = (int) Math.round(centreX);
            var z = (int) Math.round(centreZ);
            var self = localPlayerId != null && localPlayerId.equals(marker.playerId());

            var portrait = portraitFor(marker);
            if (portrait != null) {
                // Keep a dark silhouette behind the two skin layers so pale faces remain visible on bright terrain.
                blitDot(graphics, x, z, diameter + 2, 0xB0000000);
                blitFace(graphics, portrait, x, z, diameter, 0xFFFFFFFF, false);
                blitFace(graphics, portrait, x, z, diameter, 0xFFFFFFFF, true);
            } else {
                var color = markerColor(marker.category());
                blitDot(graphics, x, z, diameter + 2, 0xB0000000);
                blitDot(graphics, x, z, diameter, color);
                var priority = self || marker.entityId() == hoveredEntityId
                        || marker.entityId() == selectedEntityId;
                if ((priority || thumbnailsRendered < MAX_ENTITY_THUMBNAILS)
                        && entityThumbnails.render(graphics, marker, x, z, diameter)) {
                    thumbnailsRendered++;
                    bracket(graphics, x, z, diameter + 2, color & 0xAFFFFFFF);
                }
            }

            if (self) {
                // The one thing worth knowing at a glance: where the viewer is.
                bracket(graphics, x, z, diameter + 6, 0xFF4D9BFF);
                bracket(graphics, x, z, diameter + 8, 0x804D9BFF);
            }
            if (marker.entityId() == hoveredEntityId) {
                bracket(graphics, x, z, diameter + 10, 0x90FFFFFF);
            }
            if (marker.entityId() == selectedEntityId) {
                bracket(graphics, x, z, diameter + 12, 0xFFFFFFFF);
                bracket(graphics, x, z, diameter + 14, 0x66C77DFF);
            }
        }
        graphics.disableScissor();
    }

    /**
     * The skin texture for a player marker, or null when the entity needs a model thumbnail/fallback dot.
     *
     * <p>Players resolve through the tab list, which already holds every visible player skin. Non-player
     * thumbnails are rendered from their registered entity type instead of pretending every model has a head UV.
     */
    private static net.minecraft.resources.Identifier portraitFor(ChunkLeapPackets.Marker marker) {
        if (marker.playerId() == null) return null;
        var connection = net.minecraft.client.Minecraft.getInstance().getConnection();
        if (connection == null) return null;
        var info = connection.getPlayerInfo(marker.playerId());
        if (info == null) return null;
        var skin = info.getSkin();
        return skin == null ? null : skin.body().texturePath();
    }

    /**
     * Draws the face patch of a skin sheet, centred on a screen point.
     *
     * <p>The face lives at texels (8,8)-(16,16) of the 64x64 skin; the hat layer, which carries the
     * overlaid hair and accessories, is the same rectangle one sheet-width to the right. Drawing the face
     * first and the hat over it composites them the way the game does.
     */
    private static void blitFace(GuiGraphicsExtractor graphics, net.minecraft.resources.Identifier skin,
                                 int centreX, int centreZ, int diameter, int tint, boolean hatLayer) {
        var half = diameter / 2;
        var u = hatLayer ? 40 : 8;
        var v = 8;
        var size = 8;
        var textureSize = 64f;
        graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, skin,
                centreX - half, centreZ - half,
                u, v, diameter, diameter, size, size, (int) textureSize, (int) textureSize, tint);
    }
    /** Draws one tinted disc centred on a screen point. */
    private static void blitDot(GuiGraphicsExtractor graphics, int centreX, int centreZ, int diameter,
                                int color) {
        var half = diameter / 2;
        graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
                R.textures.gui.element.map_marker_dot, centreX - half, centreZ - half,
                0f, 0f, diameter, diameter, 16, 16, color);
    }

    /** Draws four corner brackets forming a square outline outside a marker. */
    private static void bracket(GuiGraphicsExtractor graphics, int centreX, int centreZ, int size,
                                int color) {
        var half = size / 2;
        var left = centreX - half;
        var top = centreZ - half;
        var right = left + size;
        var bottom = top + size;
        var arm = Math.max(3, size / 3);
        graphics.fill(left, top, left + arm, top + 1, color);
        graphics.fill(left, top, left + 1, top + arm, color);
        graphics.fill(right - arm, top, right, top + 1, color);
        graphics.fill(right - 1, top, right, top + arm, color);
        graphics.fill(left, bottom - 1, left + arm, bottom, color);
        graphics.fill(left, bottom - arm, left + 1, bottom, color);
        graphics.fill(right - arm, bottom - 1, right, bottom, color);
        graphics.fill(right - 1, bottom - arm, right, bottom, color);
    }

    /** Returns the marker nearest the cursor within a small pick radius, or null. */
    public ChunkLeapPackets.Marker pickMarker(Geometry g, double mouseX, double mouseY,
                                             ResourceKey<Level> dimension) {
        if (dimension == null) return null;
        var markers = ChunkMapClientState.map(dimension.identifier().toString()).markers();
        ChunkLeapPackets.Marker best = null;
        var bestDistance = MAX_MARKER_HIT * MAX_MARKER_HIT * 4.0;
        for (var marker : markers) {
            var centreX = g.screenX((marker.blockX() + 0.5) / 16.0);
            var centreZ = g.screenZ((marker.blockZ() + 0.5) / 16.0);
            var dx = centreX - mouseX;
            var dz = centreZ - mouseY;
            var distance = dx * dx + dz * dz;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = marker;
            }
        }
        return best;
    }

    /** The marker under the cursor, or null; also used to drive the hover tooltip. */
    public ChunkLeapPackets.Marker hoveredMarker(Geometry g, double mouseX, double mouseY,
                                                 ResourceKey<Level> dimension) {
        return pickMarker(g, mouseX, mouseY, dimension);
    }
    /**
     * Fallback colour by disposition: hostile red, neutral yellow, friendly green.
     *
     * <p>Used only when no portrait is available, so the map still distinguishes a threat from a bystander
     * at a glance.
     */
    static int markerColor(byte category) {
        return switch (category) {
            case ChunkLeapPackets.CAT_HOSTILE -> 0xFFE16F68;
            case ChunkLeapPackets.CAT_PASSIVE -> 0xFF79C58D;
            case ChunkLeapPackets.CAT_NEUTRAL -> 0xFFF0CE7A;
            case ChunkLeapPackets.CAT_PLAYER -> 0xFF9FD8F0;
            case ChunkLeapPackets.CAT_ITEM -> 0xFFBFBFBF;
            case ChunkLeapPackets.CAT_PROJECTILE -> 0xFFF0A45A;
            default -> 0xFFB8B1A3;
        };
    }

}
