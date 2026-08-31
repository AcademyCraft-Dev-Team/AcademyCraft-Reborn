package org.academy.internal.client.ability.mentalout;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.academy.api.client.gui.screen.UiScreen;
import org.academy.api.client.render.Render;
import org.academy.internal.common.ability.mentalout.MentaloutRequestGuard;
import org.academy.internal.common.ability.mentalout.skills.lv5.WideAreaInterference;
import org.lwjgl.glfw.GLFW;
import org.misaka.MisakaNetworkClient;

import java.util.*;

/** Full-screen Mentalout command workspace with a roster, large display, and extensible action rail. */
public final class WideAreaInterferenceScreen extends UiScreen {
    private static final int PANEL = 0xB8000000;
    private static final int PANEL_SOFT = 0x78000000;
    private static final int ROW = 0x1FFFFFFF;
    private static final int ROW_HOVER = 0x32FFFFFF;
    private static final int ROW_SELECTED = 0x4AFFFFFF;
    private static final int PRIMARY = 0xFFFFFFFF;
    private static final int SECONDARY = 0xBFFFFFFF;
    private static final int DISABLED = 0x45FFFFFF;
    private static final int SELECTION = 0xFF1177D6;
    private static final int CONTROLLED = 0xFFFF7A18;
    private static final int ROW_HEIGHT = 26;
    private static final int GAP = 4;
    private static final int INSET = 10;
    private static final int HEADER_HEIGHT = 24;
    private static final int ACTION_HEIGHT = 22;
    private static final int MAX_VIEW_RANGE = 96;

    private final LinkedHashSet<UUID> selectedTargets = new LinkedHashSet<>();
    private final LinkedHashSet<UUID> viewedTargets = new LinkedHashSet<>();
    private final LinkedHashSet<UUID> pendingSelection = new LinkedHashSet<>();
    private final boolean[] movementKeys = new boolean[4];
    private int rosterScroll;
    private Rect panel;
    private Rect roster;
    private Rect display;
    private Rect controls;
    private Rect selectAllButton;
    private Rect viewModeButton;
    private Rect[] actionButtons;
    private Rect[] reservedButtons;
    private WideAreaInterference.Action armedAction;
    private DragMode dragMode = DragMode.NONE;
    private double dragStartX;
    private double dragStartY;
    private double dragEndX;
    private double dragEndY;
    private boolean additiveSelection;
    private BlockPos regionFirst;
    private BlockPos regionLast;
    private int regionHeight = 1;
    private int regionVerticalOffset;

    public WideAreaInterferenceScreen() {
        super(Component.translatable("screen.academy.wide_area_interference.title"));
    }

    @Override
    protected void onInit() {
        updateLayout();
        WideAreaInterferenceClientState.open();
    }

    private void updateLayout() {
        var outerWidth = Math.max(360, width - 24);
        var outerHeight = Math.max(240, height - 24);
        panel = new Rect((width - outerWidth) / 2, (height - outerHeight) / 2, outerWidth, outerHeight);
        var contentY = panel.y + HEADER_HEIGHT;
        var contentHeight = panel.height - HEADER_HEIGHT;
        var rosterWidth = Math.clamp(Math.round(outerWidth * 0.19f), 210, 330);
        roster = new Rect(panel.x, contentY, rosterWidth, contentHeight);
        var rightX = roster.right() + GAP;
        var rightWidth = panel.right() - rightX;
        var controlsHeight = Math.clamp(Math.round(contentHeight * 0.20f), 104, 132);
        display = new Rect(rightX, contentY, rightWidth, contentHeight - controlsHeight - GAP);
        controls = new Rect(rightX, display.bottom() + GAP, rightWidth, controlsHeight);
        selectAllButton = new Rect(roster.right() - 53, roster.y + 4, 47, 16);
        viewModeButton = new Rect(display.x + 6, display.y + 6, 110, 20);

        var actionY = controls.y + 29;
        var available = controls.width - INSET * 2 - GAP * 5;
        var actionWidth = Math.max(68, available / 6);
        actionButtons = new Rect[6];
        for (var index = 0; index < actionButtons.length; index++) {
            var x = controls.x + INSET + index * (actionWidth + GAP);
            var actualWidth = index == actionButtons.length - 1
                    ? controls.right() - INSET - x : actionWidth;
            actionButtons[index] = new Rect(x, actionY, actualWidth, ACTION_HEIGHT);
        }
        var reservedY = actionY + ACTION_HEIGHT + GAP;
        reservedButtons = new Rect[6];
        for (var index = 0; index < reservedButtons.length; index++) {
            var source = actionButtons[index];
            reservedButtons[index] = new Rect(source.x, reservedY, source.width, ACTION_HEIGHT);
        }
    }

    @Override
    public void tick() {
        super.tick();
        updateLayout();
        var forward = (movementKeys[0] ? 1.0 : 0.0) - (movementKeys[1] ? 1.0 : 0.0);
        var right = (movementKeys[3] ? 1.0 : 0.0) - (movementKeys[2] ? 1.0 : 0.0);
        WideAreaInterferenceClientState.setMovementInput(forward, right);
        WideAreaInterferenceClientState.tick();

        var entries = MentaloutRosterClientState.snapshot().entries();
        var rosterIds = new HashSet<UUID>();
        entries.forEach(entry -> rosterIds.add(entry.targetUuid()));
        selectedTargets.retainAll(rosterIds);
        viewedTargets.retainAll(rosterIds);
        for (var id : List.copyOf(pendingSelection)) {
            if (rosterIds.contains(id)) {
                selectedTargets.add(id);
                pendingSelection.remove(id);
            }
        }
        syncViewedTargets(entries);
        syncWorldState();
    }

    private void syncViewedTargets(List<MentaloutRosterClientState.Entry> entries) {
        WideAreaInterferenceClientState.setViewedTargets(entries.stream()
                .filter(entry -> viewedTargets.contains(entry.targetUuid()))
                .limit(WideAreaInterferenceClientState.MAX_TARGET_VIEWS)
                .toList());
    }

    private void syncWorldState() {
        WideAreaInterferenceClientState.setSelectedTargets(selectedTargets);
        if (dragMode == DragMode.REGION && regionFirst != null && regionLast != null) {
            WideAreaInterferenceClientState.setWorkRegionPreview(
                    regionFirst, regionLast, regionHeight, regionVerticalOffset);
        } else {
            WideAreaInterferenceClientState.clearWorkRegionPreview();
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // The live world is deliberately retained as the display-area background.
    }

    @Override
    public void extractRenderState(
            GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        updateLayout();
        syncWorldState();
        if (WideAreaInterferenceClientState.isTargetView()) renderPlayerBackground(graphics);
        graphics.fill(panel.x, panel.y, panel.right(), panel.bottom(), 0x26000000);
        graphics.fill(roster.x, roster.y, roster.right(), roster.bottom(), PANEL);
        graphics.fill(controls.x, controls.y, controls.right(), controls.bottom(), PANEL);
        graphics.fill(panel.x, panel.y, panel.right(), panel.y + HEADER_HEIGHT, PANEL_SOFT);
        border(graphics, panel, PRIMARY);
        border(graphics, roster, 0x88FFFFFF);
        border(graphics, display, PRIMARY);
        border(graphics, controls, 0x88FFFFFF);
        graphics.fill(panel.x + 4, panel.y + HEADER_HEIGHT - 1,
                panel.right() - 4, panel.y + HEADER_HEIGHT, PRIMARY);
        graphics.centeredText(font, title, panel.x + panel.width / 2, panel.y + 8, PRIMARY);

        if (WideAreaInterferenceClientState.isTargetView()) {
            graphics.fill(display.x, display.y, display.right(), display.bottom(), 0xFF000000);
            renderTargetGrid(graphics);
        }
        renderRoster(graphics, mouseX, mouseY);
        renderDisplayOverlay(graphics, mouseX, mouseY);
        renderControls(graphics, mouseX, mouseY);
    }

    private void renderRoster(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(font, Component.translatable("screen.academy.wide_area_interference.roster"),
                roster.x + 6, roster.y + 7, PRIMARY, false);
        button(graphics, selectAllButton,
                Component.translatable("screen.academy.wide_area_interference.select_all"),
                mouseX, mouseY, true, false);
        var entries = MentaloutRosterClientState.snapshot().entries();
        var listTop = roster.y + HEADER_HEIGHT;
        var listBottom = roster.bottom() - 18;
        var visible = Math.max(1, (listBottom - listTop) / ROW_HEIGHT);
        rosterScroll = Mth.clamp(rosterScroll, 0, Math.max(0, entries.size() - visible));
        graphics.enableScissor(roster.x + 2, listTop, roster.right() - 2, listBottom);
        if (entries.isEmpty()) {
            graphics.text(font,
                    Component.translatable("screen.academy.wide_area_interference.roster.empty"),
                    roster.x + 8, listTop + 8, SECONDARY, false);
        }
        for (var visualRow = 0; visualRow < visible && rosterScroll + visualRow < entries.size(); visualRow++) {
            var entry = entries.get(rosterScroll + visualRow);
            var bounds = rowBounds(visualRow);
            var selected = selectedTargets.contains(entry.targetUuid());
            var hover = bounds.contains(mouseX, mouseY);
            graphics.fill(bounds.x, bounds.y, bounds.right(), bounds.bottom(),
                    selected ? ROW_SELECTED : hover ? ROW_HOVER : ROW);
            if (selected) graphics.fill(bounds.x, bounds.y, bounds.x + 2, bounds.bottom(), SELECTION);
            var remove = removeButton(bounds);
            var eye = eyeButton(bounds);
            var textRight = eye.x - 3;
            var nameWidth = Math.max(4, textRight - bounds.x - 7);
            graphics.text(font, Component.literal(trimToWidth(entry.displayName(), nameWidth)),
                    bounds.x + 5, bounds.y + 3, selected ? PRIMARY : SECONDARY, false);
            var coordinates = "[" + entry.blockX() + ", " + entry.blockY() + ", " + entry.blockZ() + "]";
            var status = statusText(entry);
            var detail = coordinates + (status.isEmpty() ? "" : "  " + status);
            graphics.text(font, Component.literal(trimToWidth(detail, nameWidth)),
                    bounds.x + 5, bounds.y + 14,
                    entry.hasFlag(MentaloutRosterClientState.FLAG_MISIDENTIFICATION)
                            ? CONTROLLED : 0x99FFFFFF, false);
            iconButton(graphics, eye, "◉", mouseX, mouseY, true,
                    viewedTargets.contains(entry.targetUuid()));
            iconButton(graphics, remove, "×", mouseX, mouseY, true, false);
        }
        graphics.disableScissor();
        graphics.text(font, Component.translatable(
                        "screen.academy.wide_area_interference.selected", selectedTargets.size()),
                roster.x + 6, roster.bottom() - 13, SECONDARY, false);
    }

    private String statusText(MentaloutRosterClientState.Entry entry) {
        var values = new ArrayList<String>(3);
        if (entry.hasFlag(MentaloutRosterClientState.FLAG_IMPRESSION)) {
            values.add(Component.translatable("screen.academy.wide_area_interference.status.impression").getString());
        }
        if (entry.hasFlag(MentaloutRosterClientState.FLAG_STUPOR)) {
            values.add(Component.translatable("screen.academy.wide_area_interference.status.stupor").getString());
        }
        if (entry.hasFlag(MentaloutRosterClientState.FLAG_MISIDENTIFICATION)) {
            values.add(Component.translatable("screen.academy.wide_area_interference.status.misidentification").getString());
        }
        return String.join("/", values);
    }

    private void renderTargetGrid(GuiGraphicsExtractor graphics) {
        var ids = WideAreaInterferenceClientState.viewedTargets();
        if (ids.isEmpty()) return;
        var count = ids.size();
        var columns = count == 1 ? 1 : count <= 4 ? 2 : 3;
        var rows = (count + columns - 1) / columns;
        var tileWidth = display.width / columns;
        var tileHeight = display.height / rows;
        for (var index = 0; index < count; index++) {
            var column = index % columns;
            var row = index / columns;
            var tile = new Rect(
                    display.x + column * tileWidth,
                    display.y + row * tileHeight,
                    column == columns - 1 ? display.right() - display.x - column * tileWidth : tileWidth,
                    row == rows - 1 ? display.bottom() - display.y - row * tileHeight : tileHeight);
            var texture = WideAreaInterferenceClientState.targetFrame(ids.get(index));
            if (texture != null) {
                graphics.innerBlit(
                        Render.RenderPipelines.IMAGE_OPAQUE,
                        texture,
                        RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR),
                        tile.x, tile.y, tile.right(), tile.bottom(),
                        0.0f, 1.0f, 1.0f, 0.0f, -1);
            } else {
                graphics.fill(tile.x, tile.y, tile.right(), tile.bottom(), 0xD0000000);
            }
            border(graphics, tile, 0x99FFFFFF);
            var entry = findEntry(ids.get(index));
            if (entry != null) {
                graphics.fill(tile.x + 1, tile.bottom() - 15, tile.right() - 1, tile.bottom() - 1, 0xA0000000);
                graphics.text(font, Component.literal(trimToWidth(entry.displayName(), tile.width - 8)),
                        tile.x + 4, tile.bottom() - 12, PRIMARY, false);
            }
        }
    }

    private void renderPlayerBackground(GuiGraphicsExtractor graphics) {
        graphics.fill(0, 0, width, height, 0xFF000000);
        var texture = WideAreaInterferenceClientState.playerFrame();
        if (texture == null) return;
        graphics.innerBlit(
                Render.RenderPipelines.IMAGE_OPAQUE,
                texture,
                RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR),
                0, 0, width, height,
                0.0f, 1.0f, 1.0f, 0.0f, -1);
    }

    private void renderDisplayOverlay(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        var canSwitch = WideAreaInterferenceClientState.hasTargetViews();
        var key = WideAreaInterferenceClientState.isRtsView()
                ? "screen.academy.wide_area_interference.switch.target"
                : "screen.academy.wide_area_interference.switch.rts";
        button(graphics, viewModeButton, Component.translatable(key), mouseX, mouseY,
                canSwitch || WideAreaInterferenceClientState.isTargetView(),
                WideAreaInterferenceClientState.isTargetView());
        var modeKey = WideAreaInterferenceClientState.isRtsView()
                ? "screen.academy.wide_area_interference.mode.god"
                : "screen.academy.wide_area_interference.mode.target";
        graphics.text(font, Component.translatable(modeKey), display.right() - 90,
                display.y + 12, SECONDARY, false);
        if (WideAreaInterferenceClientState.isRtsView()) {
            if (dragMode != DragMode.NONE && dragMode != DragMode.PAN) {
                var selection = normalizedDrag();
                var color = dragMode == DragMode.HIDE
                        ? CONTROLLED : dragMode == DragMode.REGION ? PRIMARY : SELECTION;
                border(graphics, selection, color);
                graphics.fill(selection.x, selection.y, selection.right(), selection.bottom(), color & 0x18FFFFFF);
            }
        }
        if (armedAction != null) {
            var prompt = armedAction == WideAreaInterference.Action.GATHER
                    || armedAction == WideAreaInterference.Action.FARM
                    ? Component.translatable("screen.academy.wide_area_interference.drag_region", regionHeight)
                    : Component.translatable("screen.academy.wide_area_interference.place_target");
            graphics.centeredText(font, prompt, display.x + display.width / 2, display.bottom() - 14, PRIMARY);
        }
        if ((dragMode == DragMode.REGION || dragMode == DragMode.HIDE)
                && regionFirst != null && regionLast != null) {
            var sizes = regionDimensions();
            graphics.centeredText(font, Component.literal(sizes[0] + " × " + sizes[1] + " × " + sizes[2]),
                    display.x + display.width / 2, display.bottom() - 27, PRIMARY);
        }
    }

    private void renderControls(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(font, Component.translatable("screen.academy.wide_area_interference.operations"),
                controls.x + INSET, controls.y + 9, PRIMARY, false);
        var enabled = WideAreaInterferenceClientState.isRtsView() && !selectedTargets.isEmpty();
        var labels = new String[]{
                "screen.academy.wide_area_interference.move",
                "screen.academy.wide_area_interference.misidentification",
                "screen.academy.wide_area_interference.stupor",
                "screen.academy.wide_area_interference.impression",
                "screen.academy.wide_area_interference.gather",
                "screen.academy.wide_area_interference.farm"
        };
        var actions = operationActions();
        for (var index = 0; index < actionButtons.length; index++) {
            button(graphics, actionButtons[index], Component.translatable(labels[index]),
                    mouseX, mouseY, enabled, armedAction == actions[index]);
            graphics.text(font, Component.literal(Integer.toString(index + 1)),
                    actionButtons[index].x + 3, actionButtons[index].y + 7,
                    enabled ? SECONDARY : DISABLED, false);
        }
        for (var reserved : reservedButtons) {
            button(graphics, reserved, Component.literal("+"), mouseX, mouseY, false, false);
        }
        graphics.text(font, Component.translatable("screen.academy.wide_area_interference.god_only"),
                controls.x + INSET, controls.bottom() - 14,
                WideAreaInterferenceClientState.isRtsView() ? SECONDARY : DISABLED, false);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        updateLayout();
        var x = event.x();
        var y = event.y();
        if (event.button() == 0) {
            if (selectAllButton.contains(x, y)) {
                MentaloutRosterClientState.snapshot().entries()
                        .forEach(entry -> selectedTargets.add(entry.targetUuid()));
                return true;
            }
            var visualRow = rosterVisualRowAt(x, y);
            if (visualRow >= 0) {
                var entry = MentaloutRosterClientState.snapshot().entries().get(rosterScroll + visualRow);
                var bounds = rowBounds(visualRow);
                if (eyeButton(bounds).contains(x, y)) toggleViewed(entry);
                else if (removeButton(bounds).contains(x, y)) release(entry.targetUuid());
                else toggleSelection(entry.targetUuid());
                return true;
            }
            if (viewModeButton.contains(x, y)) {
                toggleViewMode();
                return true;
            }
            if (WideAreaInterferenceClientState.isRtsView() && !selectedTargets.isEmpty()) {
                var actions = operationActions();
                for (var index = 0; index < actionButtons.length; index++) {
                    if (actionButtons[index].contains(x, y)) {
                        activateAction(actions[index]);
                        return true;
                    }
                }
            }
            if (display.contains(x, y) && WideAreaInterferenceClientState.isRtsView()) {
                if (altDown()) {
                    beginDrag(DragMode.HIDE, x, y);
                    regionFirst = raycastBlock(x, y);
                    regionLast = regionFirst;
                    return true;
                }
                if (armedAction == WideAreaInterference.Action.MOVE) {
                    var point = raycastMoveDestination(x, y);
                    if (point != null) issue(armedAction, point, point, null);
                    armedAction = null;
                    return true;
                }
                if (armedAction == WideAreaInterference.Action.MISIDENTIFICATION) {
                    var target = raycastEntity(x, y);
                    if (target != null) issue(armedAction, BlockPos.ZERO, BlockPos.ZERO, target.getUUID());
                    armedAction = null;
                    return true;
                }
                if (armedAction == WideAreaInterference.Action.GATHER
                        || armedAction == WideAreaInterference.Action.FARM) {
                    regionHeight = 1;
                    regionVerticalOffset = 0;
                    regionFirst = raycastBlock(x, y);
                    regionLast = regionFirst;
                    if (regionFirst != null) beginDrag(DragMode.REGION, x, y);
                    return true;
                }
                additiveSelection = controlDown();
                beginDrag(DragMode.SELECT, x, y);
                return true;
            }
        } else if (event.button() == 1 && display.contains(x, y)
                && WideAreaInterferenceClientState.isRtsView()) {
            if (altDown()) WideAreaInterferenceClientState.clearHiddenBlocks();
            else beginDrag(DragMode.PAN, x, y);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (dragMode == DragMode.PAN && event.button() == 1) {
            WideAreaInterferenceClientState.panFromMouse(dragX, dragY);
            return true;
        }
        if (dragMode != DragMode.NONE && event.button() == 0) {
            dragEndX = Mth.clamp(event.x(), display.x, display.right());
            dragEndY = Mth.clamp(event.y(), display.y, display.bottom());
            if (dragMode == DragMode.REGION || dragMode == DragMode.HIDE) {
                var point = raycastBlock(dragEndX, dragEndY);
                if (point != null) regionLast = point;
            }
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragMode == DragMode.PAN && event.button() == 1) {
            dragMode = DragMode.NONE;
            return true;
        }
        if (dragMode != DragMode.NONE && event.button() == 0) {
            dragEndX = Mth.clamp(event.x(), display.x, display.right());
            dragEndY = Mth.clamp(event.y(), display.y, display.bottom());
            var completed = dragMode;
            dragMode = DragMode.NONE;
            if (completed == DragMode.SELECT) completeWorldSelection();
            else if (completed == DragMode.REGION) completeRegionCommand();
            else if (completed == DragMode.HIDE && regionFirst != null && regionLast != null) {
                WideAreaInterferenceClientState.hideRegion(regionFirst, regionLast);
            }
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (roster.contains(mouseX, mouseY)) {
            rosterScroll -= (int) Math.signum(scrollY);
            return true;
        }
        if (display.contains(mouseX, mouseY) && WideAreaInterferenceClientState.isRtsView()) {
            if (dragMode == DragMode.REGION) {
                var step = (int) Math.signum(scrollY);
                if (altDown()) {
                    regionVerticalOffset += step;
                } else {
                    var horizontal = regionDimensions();
                    var maxHeight = Math.max(1,
                            Math.min(32, 4096 / Math.max(1, horizontal[0] * horizontal[2])));
                    regionHeight = Math.clamp(regionHeight + step, 1, maxHeight);
                }
                clampRegionVerticalOffset();
            } else {
                WideAreaInterferenceClientState.zoom(-scrollY * 2.5);
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (WideAreaInterferenceClientState.isRtsView()) {
            switch (event.key()) {
                case InputConstants.KEY_W, InputConstants.KEY_UP -> movementKeys[0] = true;
                case InputConstants.KEY_S, InputConstants.KEY_DOWN -> movementKeys[1] = true;
                case InputConstants.KEY_A, InputConstants.KEY_LEFT -> movementKeys[2] = true;
                case InputConstants.KEY_D, InputConstants.KEY_RIGHT -> movementKeys[3] = true;
                case InputConstants.KEY_Q -> WideAreaInterferenceClientState.rotate(-5.0f);
                case InputConstants.KEY_E -> WideAreaInterferenceClientState.rotate(5.0f);
                case InputConstants.KEY_1 -> activateAction(WideAreaInterference.Action.MOVE);
                case InputConstants.KEY_2 -> activateAction(WideAreaInterference.Action.MISIDENTIFICATION);
                case InputConstants.KEY_3 -> activateAction(WideAreaInterference.Action.STUPOR);
                case InputConstants.KEY_4 -> activateAction(WideAreaInterference.Action.IMPRESSION);
                case InputConstants.KEY_5 -> activateAction(WideAreaInterference.Action.GATHER);
                case InputConstants.KEY_6 -> activateAction(WideAreaInterference.Action.FARM);
                default -> {
                    return super.keyPressed(event);
                }
            }
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        switch (event.key()) {
            case InputConstants.KEY_W, InputConstants.KEY_UP -> movementKeys[0] = false;
            case InputConstants.KEY_S, InputConstants.KEY_DOWN -> movementKeys[1] = false;
            case InputConstants.KEY_A, InputConstants.KEY_LEFT -> movementKeys[2] = false;
            case InputConstants.KEY_D, InputConstants.KEY_RIGHT -> movementKeys[3] = false;
            default -> {
                return super.keyReleased(event);
            }
        }
        return true;
    }

    private void activateAction(WideAreaInterference.Action action) {
        if (!WideAreaInterferenceClientState.isRtsView() || selectedTargets.isEmpty()) return;
        if (action == WideAreaInterference.Action.STUPOR
                || action == WideAreaInterference.Action.IMPRESSION) {
            issue(action, BlockPos.ZERO, BlockPos.ZERO, null);
            armedAction = null;
        } else {
            armedAction = armedAction == action ? null : action;
        }
    }

    private void toggleViewMode() {
        armedAction = null;
        if (WideAreaInterferenceClientState.isTargetView()) WideAreaInterferenceClientState.showRts();
        else WideAreaInterferenceClientState.showTargetViews();
    }

    private void toggleViewed(MentaloutRosterClientState.Entry entry) {
        if (!viewedTargets.remove(entry.targetUuid())) {
            if (viewedTargets.size() >= WideAreaInterferenceClientState.MAX_TARGET_VIEWS) {
                if (minecraft.player != null) minecraft.player.sendOverlayMessage(Component.translatable(
                        "message.academy.wide_area_interference.view_limit"));
                return;
            }
            viewedTargets.add(entry.targetUuid());
        }
        syncViewedTargets(MentaloutRosterClientState.snapshot().entries());
    }

    private void release(UUID targetUuid) {
        selectedTargets.remove(targetUuid);
        viewedTargets.remove(targetUuid);
        issue(WideAreaInterference.Action.RELEASE, BlockPos.ZERO, BlockPos.ZERO, null, List.of(targetUuid));
    }

    private void completeRegionCommand() {
        if (armedAction == null || regionFirst == null || regionLast == null) return;
        var minX = Math.min(regionFirst.getX(), regionLast.getX());
        var minZ = Math.min(regionFirst.getZ(), regionLast.getZ());
        var maxX = Math.max(regionFirst.getX(), regionLast.getX());
        var maxZ = Math.max(regionFirst.getZ(), regionLast.getZ());
        var topY = Math.min(regionFirst.getY(), regionLast.getY()) + regionVerticalOffset;
        var first = new BlockPos(minX, topY - regionHeight + 1, minZ);
        var second = new BlockPos(maxX, topY, maxZ);
        issue(armedAction, first, second, null);
        armedAction = null;
        regionFirst = regionLast = null;
        regionHeight = 1;
        regionVerticalOffset = 0;
    }

    private void issue(
            WideAreaInterference.Action action,
            BlockPos first,
            BlockPos second,
            UUID entityTarget
    ) {
        issue(action, first, second, entityTarget, List.copyOf(selectedTargets));
    }

    private void issue(
            WideAreaInterference.Action action,
            BlockPos first,
            BlockPos second,
            UUID entityTarget,
            List<UUID> targets
    ) {
        MisakaNetworkClient.send(new WideAreaInterference.CommandPacket(
                MentaloutRequestGuard.nextClientSequence(), action, targets, first, second, entityTarget));
    }

    private void completeWorldSelection() {
        var drag = normalizedDrag();
        var isClick = drag.width < 5 && drag.height < 5;
        if (isClick) {
            var entity = raycastEntity(dragEndX, dragEndY);
            if (!additiveSelection) selectedTargets.clear();
            if (entity != null) selectOrEnroll(entity, additiveSelection);
            return;
        }
        if (!additiveSelection) selectedTargets.clear();
        for (var entity : visibleEntities()) {
            var point = project(entity);
            if (point != null && drag.contains(point.x, point.y)) selectOrEnroll(entity, false);
        }
    }

    private void selectOrEnroll(LivingEntity entity, boolean toggle) {
        var id = entity.getUUID();
        if (MentaloutRosterClientState.isControlledTarget(id)) {
            if (toggle && !selectedTargets.add(id)) selectedTargets.remove(id);
            else selectedTargets.add(id);
        } else {
            pendingSelection.add(id);
            MisakaNetworkClient.send(new WideAreaInterference.EnrollPacket(
                    MentaloutRequestGuard.nextClientSequence(), List.of(id)));
        }
    }

    private LivingEntity raycastEntity(double mouseX, double mouseY) {
        if (minecraft.level == null) return null;
        LivingEntity best = null;
        var bestDistance = Double.MAX_VALUE;
        var camera = minecraft.gameRenderer.mainCamera().position();
        for (var entity : visibleEntities()) {
            var bounds = projectBounds(entity.getBoundingBox().inflate(0.20));
            if (bounds == null || !bounds.contains(mouseX, mouseY)) continue;
            var distance = camera.distanceToSqr(entity.getBoundingBox().getCenter());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = entity;
            }
        }
        return best;
    }

    private BlockPos raycastBlock(double mouseX, double mouseY) {
        var hit = raycastBlockHit(mouseX, mouseY);
        return hit == null ? null : hit.getBlockPos().immutable();
    }

    private BlockPos raycastMoveDestination(double mouseX, double mouseY) {
        var hit = raycastBlockHit(mouseX, mouseY);
        return hit == null ? null : hit.getBlockPos().relative(hit.getDirection()).immutable();
    }

    private BlockHitResult raycastBlockHit(double mouseX, double mouseY) {
        if (minecraft.level == null) return null;
        var camera = minecraft.gameRenderer.mainCamera();
        var origin = camera.position();
        var direction = rayDirection(mouseX, mouseY);
        var hit = minecraft.level.clip(new ClipContext(
                origin,
                origin.add(direction.scale(MAX_VIEW_RANGE)),
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                minecraft.player));
        return hit.getType() == HitResult.Type.BLOCK ? hit : null;
    }

    private Vec3 rayDirection(double mouseX, double mouseY) {
        var renderedDirection = WideAreaInterferenceClientState.rayDirection(mouseX, mouseY, width, height);
        if (renderedDirection != null) return renderedDirection;
        var camera = minecraft.gameRenderer.mainCamera();
        var forward = Vec3.directionFromRotation(camera.xRot(), camera.yRot());
        var right = forward.cross(new Vec3(0.0, 1.0, 0.0)).normalize();
        var up = right.cross(forward).normalize();
        var nx = (mouseX - width / 2.0) / (width / 2.0);
        var ny = (mouseY - height / 2.0) / (height / 2.0);
        var tangent = Math.tan(Math.toRadians(minecraft.options.fov().get()) / 2.0);
        var aspect = (double) width / height;
        return forward.add(right.scale(nx * tangent * aspect))
                .subtract(up.scale(ny * tangent)).normalize();
    }

    private List<LivingEntity> visibleEntities() {
        if (minecraft.level == null || minecraft.player == null) return List.of();
        var camera = minecraft.gameRenderer.mainCamera().position();
        return minecraft.level.getEntitiesOfClass(
                LivingEntity.class,
                new AABB(camera, camera).inflate(MAX_VIEW_RANGE),
                entity -> entity != minecraft.player && entity.isAlive() && !entity.isRemoved());
    }

    private ScreenPoint project(LivingEntity entity) {
        return project(entity.getBoundingBox().getCenter());
    }

    private ScreenPoint project(Vec3 point) {
        var rendered = WideAreaInterferenceClientState.projectWorld(point, width, height);
        if (rendered != null) return new ScreenPoint(rendered.x(), rendered.y());
        var camera = minecraft.gameRenderer.mainCamera();
        var forward = Vec3.directionFromRotation(camera.xRot(), camera.yRot());
        var right = forward.cross(new Vec3(0.0, 1.0, 0.0)).normalize();
        var up = right.cross(forward).normalize();
        var delta = point.subtract(camera.position());
        var depth = delta.dot(forward);
        if (depth <= 0.1) return null;
        var tangent = Math.tan(Math.toRadians(minecraft.options.fov().get()) / 2.0);
        var aspect = (double) width / height;
        var nx = delta.dot(right) / (depth * tangent * aspect);
        var ny = delta.dot(up) / (depth * tangent);
        return new ScreenPoint((nx + 1.0) * width / 2.0, (1.0 - ny) * height / 2.0);
    }

    private ProjectedBounds projectBounds(AABB box) {
        var minX = Double.POSITIVE_INFINITY;
        var minY = Double.POSITIVE_INFINITY;
        var maxX = Double.NEGATIVE_INFINITY;
        var maxY = Double.NEGATIVE_INFINITY;
        var projected = false;
        for (var x : new double[]{box.minX, box.maxX}) {
            for (var y : new double[]{box.minY, box.maxY}) {
                for (var z : new double[]{box.minZ, box.maxZ}) {
                    var point = project(new Vec3(x, y, z));
                    if (point == null) continue;
                    projected = true;
                    minX = Math.min(minX, point.x);
                    minY = Math.min(minY, point.y);
                    maxX = Math.max(maxX, point.x);
                    maxY = Math.max(maxY, point.y);
                }
            }
        }
        return projected ? new ProjectedBounds(minX, minY, maxX, maxY) : null;
    }

    private void beginDrag(DragMode mode, double x, double y) {
        dragMode = mode;
        dragStartX = dragEndX = x;
        dragStartY = dragEndY = y;
    }

    private int[] regionDimensions() {
        if (regionFirst == null || regionLast == null) return new int[]{1, regionHeight, 1};
        return new int[]{
                Math.abs(regionFirst.getX() - regionLast.getX()) + 1,
                regionHeight,
                Math.abs(regionFirst.getZ() - regionLast.getZ()) + 1
        };
    }

    private void clampRegionVerticalOffset() {
        if (minecraft.level == null || regionFirst == null || regionLast == null) return;
        var baseTopY = Math.min(regionFirst.getY(), regionLast.getY());
        var minimumOffset = minecraft.level.getMinY() + regionHeight - 1 - baseTopY;
        var maximumOffset = minecraft.level.getMaxY() - 1 - baseTopY;
        regionVerticalOffset = Math.clamp(regionVerticalOffset, minimumOffset, maximumOffset);
    }

    private Rect normalizedDrag() {
        var x = (int) Math.floor(Math.min(dragStartX, dragEndX));
        var y = (int) Math.floor(Math.min(dragStartY, dragEndY));
        var right = (int) Math.ceil(Math.max(dragStartX, dragEndX));
        var bottom = (int) Math.ceil(Math.max(dragStartY, dragEndY));
        return new Rect(x, y, Math.max(1, right - x), Math.max(1, bottom - y));
    }

    private int rosterVisualRowAt(double mouseX, double mouseY) {
        var listTop = roster.y + HEADER_HEIGHT;
        var listBottom = roster.bottom() - 18;
        if (mouseX < roster.x || mouseX >= roster.right()
                || mouseY < listTop || mouseY >= listBottom) return -1;
        var visual = (int) ((mouseY - listTop) / ROW_HEIGHT);
        var index = rosterScroll + visual;
        return index >= 0 && index < MentaloutRosterClientState.snapshot().entries().size() ? visual : -1;
    }

    private Rect rowBounds(int visualRow) {
        return new Rect(roster.x + 3, roster.y + HEADER_HEIGHT + visualRow * ROW_HEIGHT,
                roster.width - 6, ROW_HEIGHT - 1);
    }

    private static Rect removeButton(Rect row) {
        return new Rect(row.right() - 18, row.y + 3, 15, 19);
    }

    private static Rect eyeButton(Rect row) {
        return new Rect(row.right() - 37, row.y + 3, 17, 19);
    }

    private void toggleSelection(UUID id) {
        if (!selectedTargets.remove(id)) selectedTargets.add(id);
    }

    private MentaloutRosterClientState.Entry findEntry(UUID id) {
        return MentaloutRosterClientState.snapshot().entries().stream()
                .filter(entry -> entry.targetUuid().equals(id)).findFirst().orElse(null);
    }

    private String trimToWidth(String value, int maximumWidth) {
        if (value == null || value.isEmpty() || maximumWidth <= 0) return "";
        if (font.width(value) <= maximumWidth) return value;
        var suffix = "…";
        var end = value.length();
        while (end > 0 && font.width(value.substring(0, end) + suffix) > maximumWidth) end--;
        return value.substring(0, end) + suffix;
    }

    private boolean controlDown() {
        var window = minecraft.getWindow().handle();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
    }

    private boolean altDown() {
        var window = minecraft.getWindow().handle();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_ALT) == GLFW.GLFW_PRESS;
    }

    private static WideAreaInterference.Action[] operationActions() {
        return new WideAreaInterference.Action[]{
                WideAreaInterference.Action.MOVE,
                WideAreaInterference.Action.MISIDENTIFICATION,
                WideAreaInterference.Action.STUPOR,
                WideAreaInterference.Action.IMPRESSION,
                WideAreaInterference.Action.GATHER,
                WideAreaInterference.Action.FARM
        };
    }

    private static void border(GuiGraphicsExtractor graphics, Rect rect, int color) {
        graphics.fill(rect.x, rect.y, rect.right(), rect.y + 1, color);
        graphics.fill(rect.x, rect.bottom() - 1, rect.right(), rect.bottom(), color);
        graphics.fill(rect.x, rect.y, rect.x + 1, rect.bottom(), color);
        graphics.fill(rect.right() - 1, rect.y, rect.right(), rect.bottom(), color);
    }

    private void button(
            GuiGraphicsExtractor graphics,
            Rect bounds,
            Component label,
            int mouseX,
            int mouseY,
            boolean enabled,
            boolean selected
    ) {
        var hover = enabled && bounds.contains(mouseX, mouseY);
        graphics.fill(bounds.x, bounds.y, bounds.right(), bounds.bottom(),
                selected ? ROW_SELECTED : hover ? ROW_HOVER : enabled ? ROW : 0x10000000);
        if (selected) graphics.fill(bounds.x, bounds.bottom() - 2, bounds.right(), bounds.bottom(), SELECTION);
        graphics.centeredText(font, label, bounds.x + bounds.width / 2, bounds.y + 7,
                enabled ? PRIMARY : DISABLED);
    }

    private void iconButton(
            GuiGraphicsExtractor graphics,
            Rect bounds,
            String glyph,
            int mouseX,
            int mouseY,
            boolean enabled,
            boolean selected
    ) {
        var hover = enabled && bounds.contains(mouseX, mouseY);
        graphics.fill(bounds.x, bounds.y, bounds.right(), bounds.bottom(),
                selected ? ROW_SELECTED : hover ? ROW_HOVER : 0x10000000);
        if (selected) graphics.fill(bounds.x, bounds.bottom() - 2, bounds.right(), bounds.bottom(), SELECTION);
        graphics.centeredText(font, Component.literal(glyph),
                bounds.x + bounds.width / 2, bounds.y + 6, enabled ? PRIMARY : DISABLED);
    }

    @Override
    public void removed() {
        WideAreaInterferenceClientState.close();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private enum DragMode {
        NONE,
        SELECT,
        REGION,
        HIDE,
        PAN
    }

    private record Rect(int x, int y, int width, int height) {
        private int right() {
            return x + width;
        }

        private int bottom() {
            return y + height;
        }

        private boolean contains(double pointX, double pointY) {
            return pointX >= x && pointX < right() && pointY >= y && pointY < bottom();
        }
    }

    private record ScreenPoint(double x, double y) {
    }

    private record ProjectedBounds(double minX, double minY, double maxX, double maxY) {
        private boolean contains(double x, double y) {
            return x >= minX && x <= maxX && y >= minY && y <= maxY;
        }
    }
}
