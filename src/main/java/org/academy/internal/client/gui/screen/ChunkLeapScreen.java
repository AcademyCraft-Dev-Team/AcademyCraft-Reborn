package org.academy.internal.client.gui.screen;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.academy.api.client.gui.layout.Gravity;
import org.academy.api.client.gui.layout.SizeMode;
import org.academy.api.client.gui.screen.UiScreen;
import org.academy.api.client.gui.widget.BlendQuadWidget;
import org.academy.api.client.gui.widget.FrameLayoutWidget;
import org.academy.internal.client.ability.teleport.ChunkLeapClientConfig;
import org.academy.internal.client.ability.teleport.ChunkLeapTransition;
import org.academy.internal.client.ability.teleport.ChunkLeapGodView;
import org.academy.internal.client.ability.teleport.ChunkLeapGodViewClient;
import org.academy.internal.client.ability.teleport.ChunkMapTexture;
import org.academy.internal.client.ability.teleport.ChunkMapViewClient;
import org.academy.internal.common.ability.teleport.chunk.ChunkLeapCost;
import org.academy.internal.common.ability.teleport.chunk.ChunkLeapPackets;
import org.academy.internal.common.ability.teleport.chunk.ChunkLeapRegion;
import org.academy.internal.common.ability.teleport.map.ChunkMapClientState;
import org.academy.internal.gui.map.ChunkMapRenderer;
import org.academy.internal.gui.map.ChunkMapSelection;
import org.misaka.MisakaNetworkClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Full-screen 区块跃迁 map: a zoomable, draggable chunk grid with box selection.
 *
 * <p>The shell is drawn immediately against {@link GuiGraphicsExtractor}; all rects are derived from
 * the window size each frame, so the layout adapts instead of overflowing on small windows. Everything
 * operable by keyboard (mode, slot, dimension) is also a clickable header button.
 *
 * <p>Input notes: {@code mouseDragged(event, dx, dy)} receives per-frame deltas in {@code dx/dy} and
 * the current position in {@code event.x()/y()} — mixing those up is what previously anchored every
 * selection near the viewport's top-left and made panning fly off.
 */
public final class ChunkLeapScreen extends UiScreen {
    private static final int PANEL_BORDER = 0xFFC77DFF;
    private static final int BORDER_DIM = 0x60FFFFFF;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int DIM = 0xBFFFFFFF;
    private static final int MUTED = 0x8DFFFFFF;
    private static final int ACCENT = 0xFFC77DFF;
    private static final int GOOD = 0xFF79C58D;
    private static final int DANGER = 0xFFE16F68;
    private static final int HEADER_H = 22;
    private static final int FOOTER_H = 24;
    private static final int ROW_H = 16;
    private static final int CARD_H = 26;
    private static final float PANEL_ALPHA = 0.12f;
    /** How much closer than the full region view the player may zoom in. */
    private static final double ZOOM_IN_FACTOR = 8.0;
    /** Coverage kept in reserve so a re-anchor is not triggered by the last visible row/column. */
    private static final int ANCHOR_MARGIN_CHUNKS = 16;

    private enum Page {
        BLOCKS,
        ENTITIES
    }

    private final ChunkMapSelection selection = new ChunkMapSelection();
    private final ChunkMapViewClient viewClient = new ChunkMapViewClient();
    /** Remembered view centre per dimension, so switching pages and back loses nothing. */
    private final Map<String, double[]> centerByDimension = new HashMap<>();
    private ChunkMapTexture mapTexture;
    private ChunkMapRenderer renderer;
    private List<ResourceKey<Level>> dimensions = new ArrayList<>();
    private int dimensionIndex;
    private ResourceKey<Level> dimension = ChunkMapViewClient.playerDimension();

    private Page page = Page.BLOCKS;
    /** When true, the next click places the derived target instead of editing the source. */
    private boolean placingTarget;
    private boolean selectionClamped;
    private boolean panning;
    private double panChunkX;
    private double panChunkZ;
    private double zoom = 1.0;
    private ChunkPos dragAnchor;
    private boolean dragging;
    private String statusMessage = "";
    private int statusColor = MUTED;
    private UUID pendingOpId;
    private int confirmTicks;
    /** Block-resolution preview currently shown over the map, if any. */
    /**
     * True while the map shows the real 1:1 view of one chunk instead of the overview raster.
     *
     * <p>The overview is for finding a place; this is for judging it. Centred on the hovered chunk and
     * toggled with C, so inspecting a candidate never means leaving the map.
     */


    /** Clickable header control rects, recomputed every frame before use. */
    private final Map<String, int[]> buttonRects = new HashMap<>();
    private int[] executeRect = {-1, -1, 0, 0};


    /** Independent block-coordinate entries; pressing Enter in either moves the map centre. */
    private EditBox coordinateXBox;
    private EditBox coordinateZBox;
    /** Remembered per dimension so the loading banner can show that page's own progress. */
    /** Last chunk we asked the server about, so hover does not spam inspect requests. */
    private long inspectedChunk = Long.MIN_VALUE;
    private long lastInspectRequestMs;
    /** Chunk currently under the cursor, used for the inspection readout. */
    private long hoveredChunk = Long.MIN_VALUE;
    /** Latest cursor position, so a key action can resolve the exact block under it. */
    private double lastMouseX = Double.NaN;
    private double lastMouseY = Double.NaN;
    private final ChunkMapClientState.Listener dataListener = this::onDataChanged;

    public ChunkLeapScreen() {
        super(Component.translatable("skill.academy.chunk_leap"));
    }

    // ---------------------------------------------------------------- lifecycle

    @Override
    protected void onInit() {
        getRoot().addChild("chunk_leap_layout", fallbackLayout());

        coordinateXBox = createCoordinateBox("chunk_leap.coord.x_hint");
        coordinateZBox = createCoordinateBox("chunk_leap.coord.z_hint");

        dimensions = discoverDimensions();
        dimensionIndex = Math.max(0, dimensions.indexOf(dimension));
        if (dimensions.isEmpty()) dimensions = List.of(dimension);

        if (mapTexture == null) {
            mapTexture = new ChunkMapTexture(0, 0);
            renderer = new ChunkMapRenderer(mapTexture);
        }
        zoom = minZoom();
        viewClient.newEpoch();
        ChunkMapClientState.addListener(dataListener);
        ChunkMapClientState.beginView(dimension, viewClient.epoch());
        jumpToPlayer(false);
    }

    @Override
    public void removed() {
        super.removed();
        // Closing the map must not leave the server streaming terrain or the camera detached.
        if (ChunkLeapGodView.isActive() || ChunkLeapGodViewClient.isReady()) {
            ChunkLeapGodViewClient.reset();
            if (dimension != null) {
                MisakaNetworkClient.send(new ChunkLeapPackets.GodViewRequestPacket(
                        dimension.identifier().toString(), 0, 0, false));
            }
        }
        ChunkMapClientState.removeListener(dataListener);
        if (dimension != null) viewClient.release(dimension);
        if (mapTexture != null) {
            mapTexture.close();
            mapTexture = null;
        }
    }

    /** Dimensions known to the connection, current one first in cycle order. */
    private List<ResourceKey<Level>> discoverDimensions() {
        var connection = minecraft.getConnection();
        var result = new ArrayList<ResourceKey<Level>>();
        if (connection != null && connection.levels() != null) {
            result.addAll(connection.levels());
        }
        if (result.isEmpty() && minecraft.level != null) {
            result.add(minecraft.level.dimension());
        }
        result.sort((a, b) -> a.identifier().toString().compareTo(b.identifier().toString()));
        return result;
    }

    private FrameLayoutWidget fallbackLayout() {
        var layout = new FrameLayoutWidget();
        layout.setLayoutParams(new FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT));
        var background = new BlendQuadWidget();
        background.setAlpha(PANEL_ALPHA);
        background.setDrawLine(false);
        background.setLayoutParams(new FrameLayoutWidget.LayoutParams()
                .sizeMode(SizeMode.MATCH_PARENT).margin(4, 4, 4, 4));
        layout.addChild("panel_background", background);
        // Geometry is computed in code; these slots exist so the debug layout tool has anchors.
        var canvas = new org.academy.api.client.gui.widget.EmptyWidget();
        canvas.setLayoutParams(new FrameLayoutWidget.LayoutParams()
                .sizeMode(SizeMode.MATCH_PARENT, SizeMode.MATCH_PARENT)
                .margin(HEADER_H + 4, 4, 210, FOOTER_H));
        layout.addChild("map_canvas", canvas);
        var side = new org.academy.api.client.gui.widget.EmptyWidget();
        side.setLayoutParams(new FrameLayoutWidget.LayoutParams()
                .sizeMode(SizeMode.FIXED, SizeMode.MATCH_PARENT)
                .size(200, 0).gravity(Gravity.RIGHT | Gravity.TOP)
                .margin(0, HEADER_H + 4, 4, FOOTER_H));
        layout.addChild("info_panel", side);
        return layout;
    }

    // ---------------------------------------------------------------- data

    private void onDataChanged() {
        for (var result : ChunkMapClientState.drainSwapResults()) {
            if (result.success()) {
                flash("chunk_leap.status.swapped", GOOD);
                viewClient.invalidate();
                // Swapped tiles were dropped from the cache; rebuild the visible window.
                renderer.resyncAll(dimension);
            } else {
                flash(result.reasonKey(), DANGER);
            }
            // The operation is over either way: drop the gate and stop attributing later dimension
            // changes to chunk leap, so an unrelated portal trip shows the vanilla screen.
            pendingOpId = null;
            ChunkLeapTransition.clearPossibleTransition();
        }
        for (var result : ChunkMapClientState.drainTeleportResults()) {
            flash(result.success() ? "chunk_leap.status.teleported" : result.reasonKey(),
                    result.success() ? GOOD : DANGER);
        }
    }

    /**
     * Reads the independent X/Z fields and recentres the map on that block position.
     *
     * <p>Typing coordinates is the only practical way to reach a far-off location: the map only loads
     * a bounded region around whatever it is centred on, so dragging there would take a very long time.
     * Empty fields deliberately mean zero, so either axis can be entered on its own.
     */
    private void submitCoordinates() {
        try {
            var x = coordinateValue(coordinateXBox == null ? null : coordinateXBox.getValue());
            var z = coordinateValue(coordinateZBox == null ? null : coordinateZBox.getValue());
            panChunkX = x >> 4;
            panChunkZ = z >> 4;
            // A new centre is a new region; re-anchor and refetch around it.
            zoom = minZoom();
            syncAnchor();
            flash("chunk_leap.status.moved", MUTED);
            clearCoordinateFocus();
        } catch (RuntimeException bad) {
            flash("chunk_leap.coord.invalid", DANGER);
        }
    }

    /** Parses one axis; a missing value is the explicitly documented zero default. */
    static int coordinateValue(String value) {
        if (value == null || value.isBlank()) return 0;
        return Integer.parseInt(value.strip());
    }

    private EditBox createCoordinateBox(String hintKey) {
        var box = new EditBox(font, 0, 0, 1, 16, Component.empty());
        box.setHint(Component.translatable(hintKey));
        box.setMaxLength(12);
        box.setBordered(false);
        box.setTextColor(TEXT);
        addRenderableWidget(box);
        return box;
    }

    private void jumpToPlayer(boolean ownDimensionOnly) {
        if (ownDimensionOnly && minecraft.player != null
                && !minecraft.player.level().dimension().equals(dimension)) {
            switchDimension(minecraft.player.level().dimension());
            return;
        }
        if (minecraft.player != null && minecraft.player.level().dimension().equals(dimension)) {
            panChunkX = minecraft.player.chunkPosition().x();
            panChunkZ = minecraft.player.chunkPosition().z();
        } else {
            var remembered = centerByDimension.get(dimension.identifier().toString());
            if (remembered != null) {
                panChunkX = remembered[0];
                panChunkZ = remembered[1];
            } else {
                panChunkX = 0;
                panChunkZ = 0;
            }
        }
        viewClient.invalidate();
        ensureAnchorCoversView();
    }

    /** Switches the viewed dimension page, remembering the current centre. */
    private void switchDimension(ResourceKey<Level> next) {
        if (next == null || next.equals(dimension)) return;
        centerByDimension.put(dimension.identifier().toString(), new double[]{panChunkX, panChunkZ});
        dimension = next;
        dimensionIndex = dimensions.indexOf(next);
        viewClient.newEpoch();
        ChunkMapClientState.beginView(dimension, viewClient.epoch());
        jumpToPlayer(false);
        // Clear the raster even if the window did not move: the two dimensions share one texture, so
        // without this the previous dimension's pixels stay visible in every chunk the new one has not
        // reported yet — including void chunks, which report no blocks at all.
        syncAnchor();
        flash("chunk_leap.status.dimension", MUTED);
    }

    private void cycleDimension(int direction) {
        if (dimensions.isEmpty()) return;
        var next = (dimensionIndex + direction + dimensions.size()) % dimensions.size();
        switchDimension(dimensions.get(next));
    }

    private void syncAnchor() {
        if (mapTexture == null) return;
        var minX = (int) (panChunkX - ChunkMapTexture.WINDOW_CHUNKS / 2.0);
        var minZ = (int) (panChunkZ - ChunkMapTexture.WINDOW_CHUNKS / 2.0);
        mapTexture.reanchor(minX, minZ);
        renderer.resyncAll(dimension);
        viewClient.invalidate();
    }

    // ---------------------------------------------------------------- geometry

    /** The map viewport rect, computed from the window so the layout adapts. */
    private int[] canvasRect() {
        var panelW = panelWidth();
        return new int[]{6, HEADER_H + 4, Math.max(80, width - panelW - 22), height - HEADER_H - FOOTER_H - 10};
    }

    private int panelWidth() {
        return Math.clamp(width * 28 / 100, 150, 210);
    }

    /**
     * Chunks per axis this client is willing to load, which bounds how much the map may show.
     */
    private static int loadableChunksPerAxis() {
        return Math.max(4, Math.min(ChunkMapViewClient.MAX_REQUEST_SIDE,
                ChunkLeapClientConfig.viewRadiusChunks() * 2));
    }

    /**
     * The zoom at which the loadable region exactly fills the viewport — the widest useful view.
     *
     * <p>This is the floor, and it is also the entry zoom. Zooming out further would only reveal
     * terrain the client never requests, so the map would look mostly empty; the honest limit is "as
     * much as we are willing to load". Zooming in from here trades area for detail.
     */
    private double minZoom() {
        var r = canvasRect();
        var largestSide = Math.max(r[2], r[3]);
        // Inset by a chunk border: the viewport should sit inside loaded terrain, never on its edge,
        // so the player never sees a placeholder strip at the screen border.
        var usable = Math.max(4, loadableChunksPerAxis() - 2) * 16.0;
        return Math.max(0.05, largestSide / usable);
    }

    /** Ceiling: {@link #ZOOM_IN_FACTOR} times closer than the full-region view. */
    private double maxZoom() {
        return minZoom() * ZOOM_IN_FACTOR;
    }

    /** Clamps zoom into the range the current window and load budget can actually support. */
    private double clampZoom(double value) {
        return Math.clamp(value, minZoom(), maxZoom());
    }

    /**
     * Block X under a screen coordinate: the exact inverse of the renderer's chunk projection.
     *
     * <p>{@code chunkSize} is pixels per CHUNK, so a pixel offset converts to blocks by multiplying by
     * {@code 16 / chunkSize} (blocks per pixel). The reciprocal form agrees only at exactly 1× zoom, which
     * makes the mistake invisible in the default view; the test sweeps several zoom levels for that reason.
     */
    private int blockXAt(double mouseX) {
        var g = geometry();
        return (int) Math.floor(g.centerChunkX() * 16.0
                + (mouseX - g.left() - g.width() / 2.0) * 16.0 / g.chunkSize());
    }

    /** Block Z under a screen coordinate; see {@link #blockXAt(double)} for the factor derivation. */
    private int blockZAt(double mouseY) {
        var g = geometry();
        return (int) Math.floor(g.centerChunkZ() * 16.0
                + (mouseY - g.top() - g.height() / 2.0) * 16.0 / g.chunkSize());
    }
    private ChunkMapRenderer.Geometry geometry() {
        var r = canvasRect();
        return new ChunkMapRenderer.Geometry(r[0], r[1], r[2], r[3], panChunkX, panChunkZ, zoom);
    }

    /** Chunk coordinates under a screen point. */
    private ChunkPos chunkAt(double mouseX, double mouseY) {
        var g = geometry();
        var chunkX = (int) Math.floor(g.centerChunkX() + (mouseX - g.left() - g.width() / 2.0) / g.chunkSize());
        var chunkZ = (int) Math.floor(g.centerChunkZ() + (mouseY - g.top() - g.height() / 2.0) / g.chunkSize());
        return new ChunkPos(chunkX, chunkZ);
    }

    private ChunkLeapRegion visibleRegion() {
        var g = geometry();
        var halfWidth = (int) Math.ceil(g.width() / 2.0 / g.chunkSize());
        var halfHeight = (int) Math.ceil(g.height() / 2.0 / g.chunkSize());
        var minX = (int) Math.floor(panChunkX) - halfWidth;
        var minZ = (int) Math.floor(panChunkZ) - halfHeight;
        return ChunkLeapRegion.ofChunks(dimension, minX, minZ, halfWidth * 2, halfHeight * 2);
    }

    // ---------------------------------------------------------------- render

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // God view replaces the whole map: the detached camera renders real terrain behind this screen, so
        // only a small hint bar is drawn. The screen stays open to keep receiving the C and Esc keys.
        if (ChunkLeapGodView.isActive()) {
            renderGodViewOverlay(graphics);
            return;
        }
        // Drive the view request each frame; the client throttles internally.
        // A resize moves the zoom floor, so re-clamp before using it this frame.
        zoom = clampZoom(zoom);
        if (dimension != null) {
            viewClient.tick(dimension, visibleRegion(), ChunkLeapClientConfig.active().isAutoLoadMap());
        }
        if (mapTexture != null) {
            renderer.applyTilePatches(dimension);
            mapTexture.upload();
        }
        var g = geometry();

        renderHeader(graphics, mouseX, mouseY);
        if (renderer != null) {
            renderer.renderMap(graphics, g);
            renderer.renderGrid(graphics, g);
            // Each side is drawn only on its own page: a cross-dimension selection lives on two pages,
            // and drawing it on the wrong one would put chunks over unrelated terrain.
            var visibleSource = selection.sourceIsIn(dimension)
                    ? selection.sourceChunks() : java.util.Set.<ChunkPos>of();
            var visibleTarget = selection.targetIsIn(dimension)
                    ? selection.targetChunks() : java.util.Set.<ChunkPos>of();
            renderer.renderSelection(graphics, g, visibleSource, visibleTarget, selectionClamped);
            if (ChunkLeapClientConfig.showEntities()) {
                var hovered = hoveredEntityId(mouseX, mouseY);
                renderer.renderMarkers(graphics, g, dimension, selection.entityId(), hovered,
                        minecraft.player == null ? null : minecraft.player.getUUID());
            }
        }
        updateHoverInspection(g, mouseX, mouseY);
        renderLoadingBanner(graphics, g, mouseX, mouseY);

        // Viewport frame
        graphics.fill(g.left(), g.top(), g.right(), g.top() + 1, BORDER_DIM);
        graphics.fill(g.left(), g.bottom() - 1, g.right(), g.bottom(), BORDER_DIM);
        graphics.fill(g.left(), g.top(), g.left() + 1, g.bottom(), BORDER_DIM);
        graphics.fill(g.right() - 1, g.top(), g.right(), g.bottom(), BORDER_DIM);

        renderSidePanel(graphics, mouseX, mouseY);
        if (coordinateXBox != null && coordinateZBox != null) {
            coordinateXBox.extractRenderState(graphics, mouseX, mouseY, partialTick);
            coordinateZBox.extractRenderState(graphics, mouseX, mouseY, partialTick);
        }
        renderFooter(graphics);

        if (confirmTicks > 0) {
            renderConfirmOverlay(graphics);
        }
        if (statusTicksInternal > 0) statusTicksInternal--;
        renderEntityTooltip(graphics, mouseX, mouseY);
    }

    private int statusTicksInternal;

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // In the god view the real world IS the content, so no blur or dimming is applied: the player must
        // see the terrain exactly as the game draws it, not through a tinted overlay.
        if (ChunkLeapGodView.isActive()) return;
        extractBlurredBackground(graphics);
        extractTransparentBackground(graphics);
    }

    private void renderHeader(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.fill(4, 4, width - 4, HEADER_H + 2, 0x50000000);
        graphics.fill(4, HEADER_H + 2, width - 4, HEADER_H + 3, PANEL_BORDER);

        var x = 10;
        graphics.text(font, Component.translatable("skill.academy.chunk_leap"), x, 10, TEXT);
        x += font.width(Component.translatable("skill.academy.chunk_leap")) + 14;

        // Dimension cycle: ‹ name ›, clickable arrows.
        var dimName = Component.literal(dimension.identifier().getPath());
        var dimW = font.width(dimName);
        buttonRects.put("dim_prev", new int[]{x, 7, 10, 12});
        buttonRects.put("dim_next", new int[]{x + 14 + dimW + 4, 7, 10, 12});
        headerControl(graphics, "‹", x, 7, 10, 12, mouseX, mouseY);
        graphics.text(font, dimName, x + 14, 10, ACCENT);
        headerControl(graphics, "›", x + 14 + dimW + 4, 7, 10, 12, mouseX, mouseY);
        x += 14 + dimW + 4 + 10 + 14;

        // Mode toggle: 区块 / 实体.
        var blocksLabel = Component.translatable("chunk_leap.page.blocks");
        var entitiesLabel = Component.translatable("chunk_leap.page.entities");
        var modeW = Math.max(font.width(blocksLabel), font.width(entitiesLabel)) + 12;
        buttonRects.put("mode", new int[]{x, 6, modeW, 14});
        var modeHovered = inside(mouseX, mouseY, x, 6, modeW, 14);
        graphics.fill(x, 6, x + modeW, 6 + 14, modeHovered ? 0x50C77DFF : 0x28C77DFF);
        graphics.text(font, page == Page.BLOCKS ? blocksLabel : entitiesLabel,
                x + 6, 10, page == Page.BLOCKS ? TEXT : MUTED);
        x += modeW + 8;

        // Slot toggle: 源 / 目标 (blocks page only).
        if (page == Page.BLOCKS) {
            var sourceLabel = Component.translatable("chunk_leap.slot.source");
            var targetLabel = Component.translatable("chunk_leap.slot.target");
            var slotW = font.width(sourceLabel) + font.width(targetLabel) + 18;
            buttonRects.put("slot_source", new int[]{x, 6, slotW / 2, 14});
            buttonRects.put("slot_target", new int[]{x + slotW / 2, 6, slotW - slotW / 2, 14});
            var half = slotW / 2;
            var srcActive = !placingTarget;
            slotTab(graphics, x, 6, half, sourceLabel, srcActive, mouseX, mouseY);
            slotTab(graphics, x + half, 6, slotW - half, targetLabel, !srcActive, mouseX, mouseY);
        } else {
            buttonRects.remove("slot_source");
            buttonRects.remove("slot_target");
        }

        // Status message, right-aligned against the panel.
        if (!statusMessage.isEmpty()) {
            var msg = Component.translatable(statusMessage);
            var msgW = font.width(msg);
            var right = width - panelWidth() - 16;
            graphics.text(font, msg, right - msgW, 10, statusColor);
        }
    }

    private void headerControl(GuiGraphicsExtractor graphics, String glyph, int x, int y, int w, int h,
                               double mouseX, double mouseY) {
        var hovered = inside(mouseX, mouseY, x, y, w, h);
        graphics.fill(x, y, x + w, y + h, hovered ? 0x40FFFFFF : 0x18FFFFFF);
        graphics.text(font, Component.literal(glyph), x + w / 2 - 2, y + 2, hovered ? TEXT : DIM);
    }

    private void slotTab(GuiGraphicsExtractor graphics, int x, int y, int w, Component label,
                         boolean active, double mouseX, double mouseY) {
        var hovered = inside(mouseX, mouseY, x, y, w, 14);
        graphics.fill(x, y, x + w, y + 14,
                active ? (hovered ? 0x50C77DFF : 0x38C77DFF) : 0x14000000);
        graphics.text(font, label, x + 5, y + 3, active ? TEXT : MUTED);
    }

    /**
     * Draws load progress over the map while the viewed region is still filling in.
     *
     * <p>Chunks arrive a few per tick, so on a fresh or cross-dimension page the map is visibly
     * incomplete for a while. Without an explicit indicator that reads as "broken" rather than
     * "loading" — which is exactly how the blank cross-dimension map was reported.
     */
    private void renderLoadingBanner(GuiGraphicsExtractor graphics, ChunkMapRenderer.Geometry g,
                                     int mouseX, int mouseY) {
        if (dimension == null) return;
        var map = ChunkMapClientState.map(dimension.identifier().toString());
        var total = map.total();
        var loaded = map.loaded();
        // Only the server knows the full picture; prefer its counters, fall back to tile coverage.
        if (total <= 0) {
            var visible = visibleRegion();
            total = (int) Math.min(Integer.MAX_VALUE, visible.chunkCount());
            loaded = total - ChunkMapClientState.missingTileCount(dimension, visible);
        }
        if (total <= 0 || loaded >= total) return;
        var ratio = Math.clamp(loaded / (float) total, 0f, 1f);
        var barW = Math.min(320, g.width() - 40);
        var barX = g.left() + (g.width() - barW) / 2;
        var barY = g.top() + 8;
        graphics.fill(barX - 6, barY - 6, barX + barW + 6, barY + 24, 0xC0101014);
        border(graphics, barX - 6, barY - 6, barW + 12, 30, BORDER_DIM);
        var label = Component.translatable("chunk_leap.loading",
                Math.round(ratio * 100), loaded, total);
        graphics.text(font, label, barX, barY - 1, TEXT);
        graphics.fill(barX, barY + 11, barX + barW, barY + 17, 0x40FFFFFF);
        graphics.fill(barX, barY + 11, barX + (int) (barW * ratio), barY + 17, ACCENT);
    }
    /**
     * Requests a report for the chunk under the cursor, at most one per hovered chunk and rate-limited.
     *
     * <p>Hover-to-inspect is what lets the player judge a target before committing: the raster gives
     * colours, but only the server can say which blocks and biome are actually there. Requests are keyed
     * by chunk so moving within one chunk does not re-ask, and results are cached client-side.
     */
    private void updateHoverInspection(ChunkMapRenderer.Geometry g, int mouseX, int mouseY) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        if (dimension == null || !inside(mouseX, mouseY, g.left(), g.top(), g.width(), g.height())) {
            hoveredChunk = Long.MIN_VALUE;
            return;
        }
        var chunk = chunkAt(mouseX, mouseY);
        var key = ChunkPos.pack(chunk.x(), chunk.z());
        hoveredChunk = key;
        if (key == inspectedChunk) return;
        var dimensionId = dimension.identifier().toString();
        if (ChunkMapClientState.inspection(dimensionId, chunk.x(), chunk.z()) != null) {
            inspectedChunk = key;
            return;
        }
        var now = System.currentTimeMillis();
        if (now - lastInspectRequestMs < 250L) return;
        lastInspectRequestMs = now;
        inspectedChunk = key;
        MisakaNetworkClient.send(new ChunkLeapPackets.InspectRequestPacket(
                dimensionId, chunk.x(), chunk.z()));
    }

    /** Renders the inspection readout for the hovered chunk, if one has arrived. */
    private int renderInspection(GuiGraphicsExtractor graphics, int x, int y, int inner) {
        if (hoveredChunk == Long.MIN_VALUE || dimension == null) return y;
        var chunk = net.minecraft.world.level.ChunkPos.unpack(hoveredChunk);
        var report = ChunkMapClientState.inspection(
                dimension.identifier().toString(), chunk.x(), chunk.z());
        graphics.text(font, Component.translatable("chunk_leap.inspect.title"), x, y, DIM);
        y += 10;
        graphics.text(font, Component.translatable("chunk_leap.inspect.chunk", chunk.x(), chunk.z()),
                x, y, ACCENT);
        y += 10;
        if (report == null) {
            graphics.text(font, Component.translatable("chunk_leap.inspect.pending"), x, y, MUTED);
            return y + 11;
        }
        if (!report.loaded()) {
            graphics.text(font, Component.translatable("chunk_leap.inspect.unloaded"), x, y, MUTED);
            return y + 11;
        }
        var surface = Component.translatable("chunk_leap.inspect.surface",
                shortId(report.surfaceBlock()), report.surfaceY());
        graphics.text(font, surface, x, y, TEXT);
        y += 10;
        if (!report.biome().isEmpty()) {
            graphics.text(font, Component.translatable("chunk_leap.inspect.biome", shortId(report.biome())),
                    x, y, TEXT);
            y += 10;
        }
        graphics.text(font, Component.translatable("chunk_leap.inspect.entities", report.entityCount()),
                x, y, TEXT);
        y += 11;
        for (var entry : report.palette()) {
            graphics.text(font, Component.literal("· " + shortId(entry.blockId()) + " ×" + entry.count()),
                    x, y, MUTED);
            y += 9;
        }
        return y + 2;
    }

    /** Trims a namespaced id to its path, which is all that fits in the panel. */
    private static String shortId(String id) {
        var colon = id.indexOf(':');
        return colon < 0 ? id : id.substring(colon + 1);
    }
    private void renderSidePanel(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        var panelW = panelWidth();
        var x = width - panelW - 6;
        var y = HEADER_H + 4;
        var h = height - HEADER_H - FOOTER_H - 10;
        graphics.fill(x, y, x + panelW, y + h, 0x50000000);
        graphics.fill(x, y, x + 1, y + h, BORDER_DIM);
        var inner = panelW - 10;
        var cursorY = y + 6;

        // Region cards
        graphics.text(font, Component.translatable("chunk_leap.side.source"), x + 6, cursorY, ACCENT);
        cursorY = renderRegionCard(graphics, x + 5, cursorY + 10, inner, sourceCardText());
        graphics.text(font, Component.translatable("chunk_leap.side.target"), x + 6, cursorY + 4, ACCENT);
        cursorY = renderRegionCard(graphics, x + 5, cursorY + 14, inner, targetCardText());
        cursorY += 6;

        if (selectionClamped && page == Page.BLOCKS) {
            graphics.text(font, Component.translatable("chunk_leap.validate.clamped",
                    ChunkLeapClientConfig.maxRegionChunks()), x + 6, cursorY, DANGER);
            cursorY += 11;
        }

        // Validation
        var validation = validationMessage();
        graphics.text(font, validation.component(), x + 6, cursorY,
                validation.valid() ? GOOD : DANGER);
        cursorY += 12;

        // Entity mode: name the picked entity and its destination, so the player can confirm the
        // selection without hunting for the dot again.
        if (page == Page.ENTITIES) {
            cursorY = renderEntityDetails(graphics, x + 6, cursorY, inner);
        }

        // Cost
        var cost = page == Page.BLOCKS
                ? ChunkLeapCost.forSwap(selection.sourceCount(), selection.hasTarget() ? 0 : 0)
                : ChunkLeapCost.forEntityTeleport();
        graphics.text(font, Component.translatable("chunk_leap.side.cost", cost), x + 6, cursorY, TEXT);
        cursorY += 12;

        // View load progress
        var map = ChunkMapClientState.map(dimension.identifier().toString());
        var total = Math.max(1, map.total());
        var ratio = Math.min(1f, map.loaded() / (float) total);
        graphics.fill(x + 6, cursorY, x + 6 + inner, cursorY + 4, 0x30FFFFFF);
        graphics.fill(x + 6, cursorY, x + 6 + (int) (inner * ratio), cursorY + 4, ACCENT);
        cursorY += 6;
        graphics.text(font, Component.translatable("chunk_leap.side.loaded", map.loaded(), map.total()),
                x + 6, cursorY, MUTED);
        cursorY += 12;

        // Destination preload while an operation is in flight
        if (pendingOpId != null) {
            var preload = ChunkMapClientState.preloadProgress(pendingOpId);
            var preloadRatio = preload == null ? 0f : preload.fraction();
            graphics.fill(x + 6, cursorY, x + 6 + inner, cursorY + 4, 0x30FFFFFF);
            graphics.fill(x + 6, cursorY, x + 6 + (int) (inner * preloadRatio), cursorY + 4,
                    preload != null && preload.ready() ? GOOD : ACCENT);
            cursorY += 6;
            graphics.text(font, Component.translatable("chunk_leap.side.preload",
                    Math.round(preloadRatio * 100)), x + 6, cursorY, MUTED);
            cursorY += 12;
        }

        // Inspection readout for the hovered chunk: the real blocks, biome and entity count.
        cursorY = renderInspection(graphics, x + 6, cursorY, inner);

        // Coordinate entry: the only practical way to reach a far location, since the map only loads
        // a bounded region around its centre.
        graphics.text(font, Component.translatable("chunk_leap.coord.label"), x + 6, cursorY, DIM);
        cursorY += 11;
        if (coordinateXBox != null && coordinateZBox != null) {
            var gap = 4;
            var fieldWidth = (inner - gap) / 2;
            var zWidth = inner - gap - fieldWidth;
            var xLeft = x + 6;
            var zLeft = xLeft + fieldWidth + gap;
            placeCoordinateBox(coordinateXBox, xLeft, cursorY, fieldWidth);
            placeCoordinateBox(coordinateZBox, zLeft, cursorY, zWidth);
            renderCoordinateFrame(graphics, xLeft, cursorY - 2, fieldWidth, coordinateXBox);
            renderCoordinateFrame(graphics, zLeft, cursorY - 2, zWidth, coordinateZBox);
            cursorY += 18;
        }

        // Execute button
        var canExecute = validation.valid() && pendingOpId == null;
        var executeHovered = inside(mouseX, mouseY, x + 6, cursorY, inner, ROW_H);
        graphics.fill(x + 6, cursorY, x + 6 + inner, cursorY + ROW_H,
                canExecute ? (executeHovered ? 0x50C77DFF : 0x30C77DFF) : 0x20000000);
        border(graphics, x + 6, cursorY, inner, ROW_H, canExecute ? PANEL_BORDER : BORDER_DIM);
        var label = page == Page.BLOCKS
                ? Component.translatable("chunk_leap.action.swap")
                : Component.translatable("chunk_leap.action.teleport");
        graphics.text(font, label, x + 6 + (inner - font.width(label)) / 2, cursorY + 4,
                canExecute ? TEXT : MUTED);
        executeRect = new int[]{x + 6, cursorY, inner, ROW_H};
        cursorY += ROW_H + 6;

        // Secondary hints
        graphics.text(font, Component.translatable("chunk_leap.action.clear_hint"), x + 6, cursorY, MUTED);
    }

    private static void placeCoordinateBox(EditBox box, int frameX, int textY, int frameWidth) {
        box.setX(frameX + 2);
        box.setY(textY);
        box.setWidth(Math.max(1, frameWidth - 4));
    }

    private static void renderCoordinateFrame(GuiGraphicsExtractor graphics, int x, int y, int width,
                                              EditBox box) {
        graphics.fill(x, y, x + width, y + 14,
                box.isFocused() ? 0x40000000 : 0x24000000);
        border(graphics, x, y, width, 14,
                box.isFocused() ? PANEL_BORDER : BORDER_DIM);
    }

    private int renderRegionCard(GuiGraphicsExtractor graphics, int x, int y, int w, RegionCard card) {
        graphics.fill(x, y, x + w, y + CARD_H, 0x28000000);
        border(graphics, x, y, w, CARD_H, BORDER_DIM);
        if (card == null) {
            graphics.text(font, Component.translatable("chunk_leap.region.none"), x + 4, y + 3, MUTED);
            graphics.text(font, Component.translatable("chunk_leap.region.none_hint"), x + 4, y + 14, MUTED);
            return y + CARD_H;
        }
        graphics.text(font, Component.literal(card.dimension().identifier().getPath()), x + 4, y + 3, ACCENT);
        var bounds = card.bounds();
        var width = bounds == null ? 1 : bounds.width();
        var height = bounds == null ? 1 : bounds.height();
        graphics.text(font, Component.translatable("chunk_leap.region.count", card.count(), width, height),
                x + 4, y + 14, TEXT);
        return y + CARD_H;
    }
    private record Validation(boolean valid, Component component) {
    }

    private Validation validationMessage() {
        if (page == Page.ENTITIES) {
            return new Validation(selection.entityId() >= 0 && selection.hasEntityTarget(),
                    Component.translatable(selection.entityId() < 0
                            ? "chunk_leap.validate.entity_none"
                            : selection.hasEntityTarget()
                                    ? "chunk_leap.validate.entity_ready"
                                    : "chunk_leap.validate.entity_pick_destination"));
        }
        // A swap already in flight locks the button instead of validating a second selection.
        if (pendingOpId != null) {
            var progress = ChunkMapClientState.preloadProgress(pendingOpId);
            if (progress == null) {
                return new Validation(false, Component.translatable("chunk_leap.validate.preparing"));
            }
            if (!progress.ready()) {
                return new Validation(false, Component.translatable("chunk_leap.validate.preloading",
                        Math.round(progress.fraction() * 100)));
            }
            return new Validation(false, Component.translatable("chunk_leap.validate.committing"));
        }
        if (selection.isEmpty()) {
            return new Validation(false, Component.translatable("chunk_leap.validate.no_source"));
        }
        if (!selection.hasTarget()) {
            return new Validation(false, Component.translatable("chunk_leap.validate.no_target"));
        }
        var max = ChunkLeapClientConfig.maxRegionChunks();
        if (selection.sourceCount() > max) {
            return new Validation(false, Component.translatable("chunk_leap.validate.too_large", max));
        }
        // Equal size and matching shape are guaranteed: the target is the source shape re-anchored, so
        // the only remaining checks are load coverage and the size cap.
        var source = selection.sourceSelection();
        var target = selection.targetSelection();
        if (source == null || target == null) {
            return new Validation(false, Component.translatable("chunk_leap.validate.incomplete"));
        }
        // Only the viewed dimension has tile data on this client, so coverage is gated per side and only
        // for the page currently shown. The server load-checks both sides before committing anyway.
        if (source.dimension().equals(dimension)) {
            var missing = ChunkMapClientState.missingTileCount(source.dimension(), source.bounds());
            if (missing > 0) {
                return new Validation(false,
                        Component.translatable("chunk_leap.validate.loading", missing));
            }
        }
        if (target.dimension().equals(dimension)) {
            var targetMissing =
                    ChunkMapClientState.missingTileCount(target.dimension(), target.bounds());
            if (targetMissing > 0) {
                return new Validation(false,
                        Component.translatable("chunk_leap.validate.loading", targetMissing));
            }
        }
        return new Validation(true, Component.translatable("chunk_leap.validate.ok"));
    }

    /** Source card text: count plus bounding box, or null when nothing is selected. */
    private RegionCard sourceCardText() {
        if (selection.isEmpty()) return null;
        return new RegionCard(selection.sourceDimension(), selection.sourceCount(), selection.sourceBounds());
    }

    /** Target card text: the derived shape at its placed origin, or null before it is placed. */
    private RegionCard targetCardText() {
        var target = selection.targetSelection();
        if (target == null) return null;
        return new RegionCard(selection.targetDimension(), target.count(), target.bounds());
    }

    /** What a region card shows: a dimension, a chunk count and a bounding box. */
    private record RegionCard(ResourceKey<Level> dimension, int count, ChunkLeapRegion bounds) {
    }
    private void renderFooter(GuiGraphicsExtractor graphics) {
        var r = canvasRect();
        var cx = r[0] + r[2] / 2;
        var line1 = Component.translatable("chunk_leap.hint.line1");
        var line2 = Component.translatable("chunk_leap.hint.line2");
        graphics.text(font, line1, cx - font.width(line1) / 2, height - FOOTER_H + 2, MUTED);
        graphics.text(font, line2, cx - font.width(line2) / 2, height - FOOTER_H + 13, MUTED);
    }

    private void renderConfirmOverlay(GuiGraphicsExtractor graphics) {
        var result = validationMessage();
        if (!result.valid()) {
            confirmTicks = 0;
            return;
        }
        var boxW = Math.min(300, width - 40);
        var boxH = 60;
        var x = (width - boxW) / 2;
        var y = (height - boxH) / 2;
        graphics.fill(x, y, x + boxW, y + boxH, 0xE0101014);
        border(graphics, x, y, boxW, boxH, PANEL_BORDER);
        // Entity and chunk modes are different operations, so the confirmation must not reuse the
        // chunk-swap wording for an entity teleport.
        var prefix = page == Page.ENTITIES ? "chunk_leap.confirm_entity." : "chunk_leap.confirm.";
        graphics.text(font, Component.translatable(prefix + "title"), x + 10, y + 10, ACCENT);
        graphics.text(font, Component.translatable(prefix + "body"), x + 10, y + 24, TEXT);
        graphics.text(font, Component.translatable(prefix + "keys"), x + 10, y + 42, MUTED);
    }

    private static void border(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int color) {
        graphics.fill(x, y, x + w, y + 1, color);
        graphics.fill(x, y + h - 1, x + w, y + h, color);
        graphics.fill(x, y, x + 1, y + h, color);
        graphics.fill(x + w - 1, y, x + w, y + h, color);
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        var mouseX = event.x();
        var mouseY = event.y();

        // In the god view the map is not on screen, so clicks must not act on it.
        if (ChunkLeapGodView.isActive()) {
            return true;
        }

        // Native EditBox input only works when the click itself reaches the box; setting the focus flag
        // without forwarding the click leaves the caret/input handler uninitialised.
        if (event.button() == 0 && (focusCoordinateBox(event, doubleClick, coordinateXBox)
                || focusCoordinateBox(event, doubleClick, coordinateZBox))) {
            return true;
        }
        clearCoordinateFocus();

        // Header controls first.
        for (var entry : buttonRects.entrySet()) {
            var r = entry.getValue();
            if (!inside(mouseX, mouseY, r[0], r[1], r[2], r[3])) continue;
            switch (entry.getKey()) {
                case "dim_prev" -> cycleDimension(-1);
                case "dim_next" -> cycleDimension(1);
                case "mode" -> page = page == Page.BLOCKS ? Page.ENTITIES : Page.BLOCKS;
                case "slot_source" -> {
                    page = Page.BLOCKS;
                    placingTarget = false;
                }
                case "slot_target" -> {
                    page = Page.BLOCKS;
                    placingTarget = true;
                }
                default -> {
                    return super.mouseClicked(event, doubleClick);
                }
            }
            return true;
        }

        // An armed confirmation is cancelled by clicking outside the execute button, then the click is
        // swallowed so it cannot also start a selection drag.
        if (confirmTicks > 0 && !(executeRect[0] >= 0 && inside(mouseX, mouseY, executeRect[0],
                executeRect[1], executeRect[2], executeRect[3]))) {
            confirmTicks = 0;
            flash("chunk_leap.status.cancelled", MUTED);
            return true;
        }



        // Preview button.

        // Execute button.
        if (executeRect[0] >= 0 && inside(mouseX, mouseY, executeRect[0], executeRect[1],
                executeRect[2], executeRect[3])) {
            if (confirmTicks > 0) {
                confirmTicks = 0;
                commit();
            } else if (validationMessage().valid()) {
                if (ChunkLeapClientConfig.confirmSwap()) {
                    confirmTicks = 200;
                } else {
                    commit();
                }
            } else {
                flash("chunk_leap.status.invalid", DANGER);
            }
            return true;
        }

        var g = geometry();
        if (inside(mouseX, mouseY, g.left(), g.top(), g.width(), g.height())) {
            if (event.button() == 1 || event.button() == 2 || event.hasShiftDown()) {
                panning = true;
            } else if (event.button() == 0) {
                if (page == Page.ENTITIES) {
                    handleEntityClick(mouseX, mouseY);
                } else if (event.hasControlDown()) {
                    // Ctrl-click edits the selection chunk by chunk, so a selection need not be a
                    // rectangle: isolated chunks can be added or removed individually.
                    var chunk = chunkAt(mouseX, mouseY);
                    var atLimit = selection.sourceIsIn(dimension)
                            && selection.sourceCount() >= ChunkLeapClientConfig.maxRegionChunks();
                    if (!selection.isSelected(chunk) && atLimit) {
                        selectionClamped = true;
                        flash("chunk_leap.status.selection_limit", DANGER);
                        return true;
                    }
                    var added = selection.toggle(dimension, chunk);
                    selectionClamped = selection.sourceCount() >= ChunkLeapClientConfig.maxRegionChunks();
                    flash(added ? "chunk_leap.status.chunk_added" : "chunk_leap.status.chunk_removed", MUTED);
                } else if (placingTarget) {
                    // One click places the target and the mode STAYS armed, so the player can keep
                    // clicking to fine-tune the position instead of re-arming after every attempt.
                    // The shape is the source shape, so nothing needs drawing by hand.
                    var chunk = chunkAt(mouseX, mouseY);
                    selection.setTargetOrigin(dimension, chunk);
                    flash("chunk_leap.status.target_set", MUTED);
                } else {
                    // Clicking selects the chunk under the cursor immediately; dragging from here
                    // grows the same selection into a rectangle, so a single click needs no drag.
                    dragging = true;
                    dragAnchor = chunkAt(mouseX, mouseY);
                    selection.setRectangle(dimension, dragAnchor, dragAnchor);
                    selectionClamped = false;
                }
            }
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    private boolean focusCoordinateBox(MouseButtonEvent event, boolean doubleClick, EditBox box) {
        if (box == null || !box.isMouseOver(event.x(), event.y())) return false;
        setCoordinateFocus(box);
        box.mouseClicked(event, doubleClick);
        return true;
    }

    private void setCoordinateFocus(EditBox box) {
        setFocused(box);
        if (coordinateXBox != null) coordinateXBox.setFocused(box == coordinateXBox);
        if (coordinateZBox != null) coordinateZBox.setFocused(box == coordinateZBox);
    }

    private void clearCoordinateFocus() {
        if (coordinateXBox != null) coordinateXBox.setFocused(false);
        if (coordinateZBox != null) coordinateZBox.setFocused(false);
        if (getFocused() == coordinateXBox || getFocused() == coordinateZBox) {
            setFocused(null);
        }
    }

    private boolean coordinateInputFocused() {
        return coordinateXBox != null && coordinateXBox.isFocused()
                || coordinateZBox != null && coordinateZBox.isFocused();
    }

    /**
     * Drag handling. The framework passes per-frame deltas as {@code dx/dy} and the current cursor in
     * {@code event.x()/y()}; the previous build treated the deltas as coordinates, which pinned the
     * selection's moving corner near the viewport top-left and wrecked panning.
     */
    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        var g = geometry();
        if (panning) {
            panChunkX -= dx / g.chunkSize();
            panChunkZ -= dy / g.chunkSize();
            viewClient.invalidate();
            ensureAnchorCoversView();
            return true;
        }
        if (dragging) {
            // Defensive: never let a drag run without its anchor being set.
            if (dragAnchor == null) dragAnchor = chunkAt(event.x(), event.y());
            var current = chunkAt(event.x(), event.y());
            var cap = ChunkLeapClientConfig.maxRegionChunks();
            var region = ChunkLeapRegion.of(dimension, dragAnchor, current).clampArea(dragAnchor, cap);
            // The anchor corner stays put while clamping, so the rectangle never jumps under the cursor.
            selection.setRectangle(dimension,
                    region.minChunkX() == dragAnchor.x() ? dragAnchor : current,
                    region.minChunkX() == dragAnchor.x() ? current : dragAnchor);
            selectionClamped = selection.sourceCount() >= cap;
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (panning || dragging) {
            panning = false;
            dragging = false;
            dragAnchor = null;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // In the god view the wheel changes camera height: from overhead, distance is the zoom control.
        if (ChunkLeapGodView.isActive()) {
            if (scrollY != 0) {
                // Scroll up means closer, matching the map view: lower the camera to see more detail.
                ChunkLeapGodView.adjustHeight(scrollY < 0);
                flash("chunk_leap.status.height", MUTED);
            }
            return true;
        }
        var g = geometry();
        if (!inside(mouseX, mouseY, g.left(), g.top(), g.width(), g.height())) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        var factor = scrollY > 0 ? 1.15 : 1 / 1.15;
        // Zoom about the cursor so the chunk under the pointer stays put.
        var before = chunkAt(mouseX, mouseY);
        zoom = clampZoom(zoom * factor);
        var after = chunkAt(mouseX, mouseY);
        panChunkX += before.x() - after.x();
        panChunkZ += before.z() - after.z();
        ensureAnchorCoversView();
        viewClient.invalidate();
        return true;
    }

    private void ensureAnchorCoversView() {
        if (mapTexture == null) return;
        var visible = visibleRegion();
        if (!mapTexture.coversWithMargin(visible.minChunkX(), visible.minChunkZ(),
                visible.maxChunkX(), visible.maxChunkZ(), ANCHOR_MARGIN_CHUNKS)) {
            syncAnchor();
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        // While typing, input belongs to the focused axis. Enter submits both values, Tab switches axes,
        // and Escape leaves coordinate entry without closing the screen.
        if (coordinateInputFocused()) {
            if (event.key() == InputConstants.KEY_ESCAPE) {
                clearCoordinateFocus();
                return true;
            }
            if (event.key() == InputConstants.KEY_TAB) {
                setCoordinateFocus(coordinateXBox.isFocused() ? coordinateZBox : coordinateXBox);
                return true;
            }
            if (event.key() == InputConstants.KEY_RETURN
                    || event.key() == InputConstants.KEY_NUMPADENTER) {
                submitCoordinates();
                return true;
            }
            return super.keyPressed(event);
        }
        // WASD pans whichever view is showing. Handled after the text fields so screen shortcuts can never
        // steal keys from an active editor.
        if (handlePanKey(event.key())) return true;
        switch (event.key()) {
            case InputConstants.KEY_TAB -> {
                page = page == Page.BLOCKS ? Page.ENTITIES : Page.BLOCKS;
                return true;
            }
            case InputConstants.KEY_1 -> {
                page = Page.BLOCKS;
                placingTarget = false;
                return true;
            }
            case InputConstants.KEY_2 -> {
                page = Page.BLOCKS;
                placingTarget = true;
                flash("chunk_leap.status.pick_target", MUTED);
                return true;
            }
            case InputConstants.KEY_LBRACKET -> {
                cycleDimension(-1);
                return true;
            }
            case InputConstants.KEY_RBRACKET -> {
                cycleDimension(1);
                return true;
            }
            case InputConstants.KEY_T -> {
                teleportToHovered();
                return true;
            }
            case InputConstants.KEY_F5 -> {
                refreshMapCache();
                return true;
            }
            case InputConstants.KEY_C -> {
                toggleGodView();
                return true;
            }
            case InputConstants.KEY_R -> {
                selection.clear();
                selectionClamped = false;
                return true;
            }
            case InputConstants.KEY_F -> {
                zoom = minZoom();
                jumpToPlayer(true);
                syncAnchor();
                return true;
            }
            case InputConstants.KEY_G -> {
                var own = ChunkMapViewClient.playerDimension();
                switchDimension(own);
                if (minecraft.player != null) {
                    panChunkX = minecraft.player.chunkPosition().x();
                    panChunkZ = minecraft.player.chunkPosition().z();
                }
                syncAnchor();
                return true;
            }
            case InputConstants.KEY_EQUALS -> {
                zoom = clampZoom(zoom * 1.15);
                ensureAnchorCoversView();
                viewClient.invalidate();
                return true;
            }
            case InputConstants.KEY_MINUS -> {
                zoom = clampZoom(zoom / 1.15);
                ensureAnchorCoversView();
                viewClient.invalidate();
                return true;
            }
            case InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> {
                // Two-stage confirm, matching the execute button. Previously Enter re-armed the confirm
                // unconditionally, so a second press could never complete it — the gate could be entered
                // but never passed from the keyboard.
                if (confirmTicks > 0) {
                    confirmTicks = 0;
                    commit();
                } else if (validationMessage().valid()) {
                    if (ChunkLeapClientConfig.confirmSwap()) {
                        confirmTicks = 200;
                    } else {
                        commit();
                    }
                } else {
                    flash("chunk_leap.status.invalid", DANGER);
                }
                return true;
            }
            case InputConstants.KEY_ESCAPE -> {
                // Escape unwinds one layer at a time: the god view, then an armed confirmation, then the map.
                if (ChunkLeapGodView.isActive()) {
                    exitGodView();
                    return true;
                }
                if (confirmTicks > 0) {
                    confirmTicks = 0;
                    flash("chunk_leap.status.cancelled", MUTED);
                    return true;
                }
                onClose();
                return true;
            }
            default -> {
            }
        }
        return super.keyPressed(event);
    }

    // ---------------------------------------------------------------- actions

    private void commit() {
        if (page == Page.ENTITIES) {
            var entityId = selection.entityId();
            var target = selection.entityTarget();
            if (entityId < 0 || target == null) return;
            MisakaNetworkClient.send(new ChunkLeapPackets.EntityTeleportPacket(
                    entityId, dimension.identifier().toString(),
                    target.getX(), target.getY(), target.getZ()));
            flash("chunk_leap.status.entity_requested", MUTED);
            return;
        }
        var source = selection.sourceSelection();
        var target = selection.targetSelection();
        if (source == null || target == null) return;
        pendingOpId = UUID.randomUUID();
        // A cross-dimension swap will respawn the local player, so arm the attribution window that
        // lets ChunkLeapTransition substitute its lightweight loading screen for ours only.
        if (minecraft.player != null && !source.dimension().equals(target.dimension())) {
            ChunkLeapTransition.markPossibleTransition();
        }
        MisakaNetworkClient.send(new ChunkLeapPackets.SwapRequestPacket(pendingOpId, source,
                target.dimension(), target.originChunkX(), target.originChunkZ(),
                ChunkLeapClientConfig.movePlayers()));
        flash("chunk_leap.status.requested", MUTED);
    }

    /**
     * Entity mode click order: first click picks a marker, later clicks set the destination.
     *
     * <p>The destination is the exact block under the cursor, resolved to that column's real surface by
     * the server. Picking a point on the map therefore means that block, not the chunk it happened to be
     * in — the previous build snapped every destination to a chunk corner.
     */
    private void handleEntityClick(double mouseX, double mouseY) {
        if (renderer == null) return;
        var picked = renderer.pickMarker(geometry(), mouseX, mouseY, dimension);
        if (picked != null) {
            selection.setEntity(picked.entityId());
            flash("chunk_leap.status.entity_picked", MUTED);
            return;
        }
        if (selection.entityId() < 0) {
            flash("chunk_leap.status.entity_none", DANGER);
            return;
        }
        // A destination click stores the exact block column; the Y is filled in server-side from the
        // real surface there, so the map never has to guess a height.
        var blockX = blockXAt(mouseX);
        var blockZ = blockZAt(mouseY);
        selection.setEntityTarget(blockX, 0, blockZ);
        flash("chunk_leap.status.target_set", MUTED);
    }

    /** Entity id of the marker under the cursor, or -1; drives the dot highlight. */
    private int hoveredEntityId(double mouseX, double mouseY) {
        if (renderer == null) return -1;
        var hovered = renderer.hoveredMarker(geometry(), mouseX, mouseY, dimension);
        return hovered == null ? -1 : hovered.entityId();
    }

    /** Localized name of an entity type id, for tooltips and the panel. */
    private Component entityName(int typeId) {
        var type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.byId(typeId);
        return type == null ? Component.literal("?") : type.getDescription();
    }
    /** Draws the localized name of whatever entity dot is under the cursor. */
    private void renderEntityTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (page != Page.ENTITIES || renderer == null || dimension == null) return;
        var hovered = renderer.hoveredMarker(geometry(), mouseX, mouseY, dimension);
        if (hovered == null) return;
        graphics.setTooltipForNextFrame(font, entityName(hovered.typeId()), mouseX, mouseY);
    }

    /** Names the selected entity and shows where it will be sent. */
    private int renderEntityDetails(GuiGraphicsExtractor graphics, int x, int y, int inner) {
        var marker = selectedMarker();
        if (marker == null) {
            graphics.text(font, Component.translatable("chunk_leap.entity.none"), x, y, MUTED);
            return y + 12;
        }
        graphics.text(font, Component.translatable("chunk_leap.entity.selected", entityName(marker.typeId())),
                x, y, ACCENT);
        y += 11;
        var target = selection.entityTarget();
        if (target != null) {
            graphics.text(font, Component.translatable("chunk_leap.entity.destination",
                    target.getX(), target.getZ()), x, y, TEXT);
            y += 11;
        } else {
            graphics.text(font, Component.translatable("chunk_leap.entity.pick_destination"), x, y, MUTED);
            y += 11;
        }
        return y + 2;
    }

    /** The marker record for the selected entity id, or null when none is picked. */
    private ChunkLeapPackets.Marker selectedMarker() {
        if (selection.entityId() < 0 || dimension == null) return null;
        for (var marker : ChunkMapClientState.map(dimension.identifier().toString()).markers()) {
            if (marker.entityId() == selection.entityId()) return marker;
        }
        return null;
    }
    /**
     * Enters or leaves the god view for the hovered chunk.
     *
     * <p>The god view is the real thing: the server streams the actual chunks around the target and a
     * detached camera looks down at them, so the player sees the terrain as the game renders it. The camera
     * only rises once the server confirms the terrain has arrived, which is why this merely asks and the
     * status packet does the switching.
     */
    private void toggleGodView() {
        if (ChunkLeapGodView.isActive()) {
            exitGodView();
            return;
        }
        if (hoveredChunk == Long.MIN_VALUE) {
            flash("chunk_leap.status.hover_first", DANGER);
            return;
        }
        var chunk = ChunkPos.unpack(hoveredChunk);
        MisakaNetworkClient.send(new ChunkLeapPackets.GodViewRequestPacket(
                dimension.identifier().toString(),
                chunk.getMinBlockX() + 8, chunk.getMinBlockZ() + 8, true));
        flash("chunk_leap.status.god_view_requested", MUTED);
    }

    /** Leaves the god view and tells the server to give the chunk window back. */
    private void exitGodView() {
        ChunkLeapGodViewClient.reset();
        if (dimension != null) {
            MisakaNetworkClient.send(new ChunkLeapPackets.GodViewRequestPacket(
                    dimension.identifier().toString(), 0, 0, false));
        }
        // Terrain was streamed into the client cache, so the map must refetch what it is showing.
        viewClient.invalidate();
        flash("chunk_leap.status.god_view_left", MUTED);
    }

    /** The only UI shown during the god view: a caption saying how to get back. */
    private void renderGodViewOverlay(GuiGraphicsExtractor graphics) {
        var label = Component.translatable("chunk_leap.god_view.caption");
        var padding = 6;
        var boxW = font.width(label) + padding * 2;
        var x = (width - boxW) / 2;
        graphics.fill(x, 6, x + boxW, 6 + 14, 0xA0101014);
        graphics.text(font, label, x + padding, 10, ACCENT);
    }
    /**
     * Pans the active view when a movement key is pressed.
     *
     * <p>WASD rather than drag-only because both views are things the player looks around in, and reaching
     * for the mouse to move becomes tedious over long distances. The map pans in chunk units; the god view
     * moves its camera focus and asks the server for the surface height under the new position.
     *
     * @return true when the key was consumed
     */
    private boolean handlePanKey(int key) {
        double forward = 0;
        double strafe = 0;
        switch (key) {
            case InputConstants.KEY_W -> forward += 1;
            case InputConstants.KEY_S -> forward -= 1;
            case InputConstants.KEY_A -> strafe -= 1;
            case InputConstants.KEY_D -> strafe += 1;
            default -> {
                return false;
            }
        }
        if (ChunkLeapGodView.isActive()) {
            ChunkLeapGodView.move(forward, strafe);
            // The height above the new terrain is unknown, so ask the server where the surface is there.
            requestGodViewAtCurrentFocus();
            return true;
        }
        if (dimension == null) return true;
        var g = geometry();
        var step = 4.0;
        panChunkX += strafe * step;
        panChunkZ -= forward * step;
        viewClient.invalidate();
        ensureAnchorCoversView();
        return true;
    }

    /** Re-asks the server for the god view at the camera focus, to pick up the new surface height. */
    private void requestGodViewAtCurrentFocus() {
        if (dimension == null) return;
        MisakaNetworkClient.send(new ChunkLeapPackets.GodViewRequestPacket(
                dimension.identifier().toString(),
                (int) Math.floor(ChunkLeapGodView.focusX()),
                (int) Math.floor(ChunkLeapGodView.focusZ()), true));
    }
    /**
     * Sends the player to the map column under the cursor.
     *
     * <p>The client supplies only the column; the server resolves the surface and a safe standing place, so
     * clicking on a cliff or a cave does not put the player inside blocks. Refused while a swap is in flight,
     * since the terrain under the cursor is about to change.
     */
    private void teleportToHovered() {
        if (hoveredChunk == Long.MIN_VALUE) {
            flash("chunk_leap.status.hover_first", DANGER);
            return;
        }
        if (pendingOpId != null) {
            flash("chunk_leap.validate.committing", DANGER);
            return;
        }
        var chunk = ChunkPos.unpack(hoveredChunk);
        // Aim at the block under the cursor, not the chunk corner, so the landing spot matches the click.
        var r = canvasRect();
        var targetX = blockXAt(lastMouseX);
        var targetZ = blockZAt(lastMouseY);
        MisakaNetworkClient.send(new ChunkLeapPackets.PlayerTeleportPacket(
                dimension.identifier().toString(), targetX, targetZ));
        flash("chunk_leap.status.teleport_requested", MUTED);
    }

    /**
     * Refetches the map for the current dimension.
     *
     * <p>Terrain can change while off-screen — another player, a swap, or the god view streaming over a
     * cache slot — and the client only learns about it from the server. Dropping this dimension and starting a
     * fresh epoch makes the next request rebuild from real blocks rather than reusing stale tiles.
     */
    private void refreshMapCache() {
        if (dimension == null) return;
        ChunkMapClientState.invalidateDimension(dimension.identifier().toString());
        viewClient.newEpoch();
        ChunkMapClientState.beginView(dimension, viewClient.epoch());
        // Clears the raster too, so no stale pixels survive while the refetch is in flight.
        syncAnchor();
        flash("chunk_leap.status.refreshed", MUTED);
    }
    private void flash(String key, int color) {
        statusMessage = key == null ? "" : key;
        statusColor = color;
        statusTicksInternal = 120;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
