package org.academy.internal.client.gui.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.academy.AcademyCraft;
import org.academy.api.client.gui.layout.Gravity;
import org.academy.api.client.gui.layout.SizeMode;
import org.academy.api.client.gui.screen.UiScreen;
import org.academy.api.client.gui.widget.BlendQuadWidget;
import org.academy.api.client.gui.widget.EmptyWidget;
import org.academy.api.client.gui.widget.FillWidget;
import org.academy.api.client.gui.widget.FrameLayoutWidget;
import org.academy.api.client.gui.widget.Widget;
import org.academy.internal.client.gui.SerializedUiLayout;
import org.academy.internal.client.gui.debug.SerializedUiDebugHost;
import org.academy.internal.common.ability.teleport.skills.lv3.LocationTeleport;
import org.academy.internal.common.skilldata.LocationTeleportData.Mark;
import org.misaka.MisakaNetworkClient;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.Mth;

public final class LocationTeleportScreen extends UiScreen implements SerializedUiDebugHost {
    private static final int ACTIVE = 0xFFFFFFFF;
    private static final int SECTION = 0x14000000;
    private static final int CONTROL = 0x0C000000;
    private static final int INPUT = 0x201F1F1F;
    private static final int INPUT_FOCUSED = 0x305A5A5A;
    private static final int ROW = 0x18FFFFFF;
    private static final int ROW_ALTERNATE = 0x10FFFFFF;
    private static final int ROW_HOVER = 0x28FFFFFF;
    private static final int ROW_SELECTED = 0x30FFFFFF;
    private static final int BORDER = 0x99FFFFFF;
    private static final int BORDER_DIM = 0x60FFFFFF;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int DIM = 0xBFFFFFFF;
    private static final int PANEL_BORDER = 0xFF7680DE;
    private static final int TELEPORT = 0xFF25C4FF;
    private static final int DANGER = 0xFFFF6C00;
    private static final int SCROLL_TRACK = 0x28000000;
    private static final int PANEL_W = 420;
    private static final int PANEL_H = 236;
    private static final int ROW_H = 18;
    private static final int PANEL_INSET = 12;
    private static final int CONTROL_H = 20;
    private static final int NAME_Y = 32;
    private static final int COORDINATES_Y = 58;
    private static final int ACTIONS_Y = 84;
    private static final int MARKS_Y = 108;
    private static final int MARKS_H = 94;
    private static final int FOOTER_Y = 208;
    private static final int INPUT_INSET_X = 4;
    private static final int INPUT_INSET_Y = 2;
    private static final int TELEPORT_ACTION_WIDTH = 24;
    private static final int QUICK_ACTION_WIDTH = 50;
    private static final int DEFENSIVE_ACTION_WIDTH = 50;
    private static final int REMOVE_ACTION_WIDTH = 18;
    private static final int SCROLLBAR_WIDTH = 5;
    private static final int SCROLLBAR_GAP = 2;
    private static final float PANEL_ALPHA = 0.12f;

    private final List<Mark> marks = new ArrayList<>();
    private EditBox nameBox;
    private EditBox xBox;
    private EditBox yBox;
    private EditBox zBox;
    private int panelX;
    private int panelY;
    private int panelWidth = PANEL_W;
    private int listTop;
    private int listBottom;
    private int quickMarkIndex = -1;
    private int defensiveMarkIndex = -1;
    private int scroll;
    private Widget panelLayout;
    private Widget nameInputLayout;
    private Widget coordinatesLayout;
    private Widget markCurrentLayout;
    private Widget addMarkLayout;
    private Widget marksLayout;
    private Widget refreshLayout;
    private Widget doneLayout;
    private FrameLayoutWidget serializedLayout;

    public LocationTeleportScreen() {
        super(Component.translatable("skill.academy.location_teleport"));
    }

    private static void addSlot(FrameLayoutWidget panel, String name, int x, int y, int width, int height) {
        var slot = new EmptyWidget();
        slot.setLayoutParams(new FrameLayoutWidget.LayoutParams().size(width, height).margin(x, y, 0, 0));
        panel.addChild(name, slot);
    }

    private static void place(EditBox box, Rect frame) {
        box.setX(frame.x + INPUT_INSET_X);
        box.setY(frame.y + INPUT_INSET_Y);
        box.setWidth(Math.max(1, frame.width - INPUT_INSET_X * 2));
    }

    private static void configureInput(EditBox box) {
        box.setBordered(false);
        box.setTextColor(TEXT);
    }

    private static void renderInputFrame(GuiGraphicsExtractor graphics, Rect frame, EditBox box) {
        graphics.fill(frame.x, frame.y, frame.x + frame.width, frame.y + frame.height,
                box.isFocused() ? INPUT_FOCUSED : INPUT);
        border(graphics, frame.x, frame.y, frame.width, frame.height,
                box.isFocused() ? ACTIVE : BORDER_DIM);
        if (box.isFocused()) {
            renderFocusBrackets(graphics, frame);
        }
    }

    private static void renderFocusBrackets(GuiGraphicsExtractor graphics, Rect frame) {
        var left = frame.x + 1;
        var right = frame.x + frame.width - 1;
        var top = frame.y + 2;
        var bottom = frame.y + frame.height - 2;
        graphics.fill(left, top, left + 2, bottom, ACTIVE);
        graphics.fill(left + 2, top, left + 6, top + 1, ACTIVE);
        graphics.fill(left + 2, bottom - 1, left + 6, bottom, ACTIVE);
        graphics.fill(right - 2, top, right, bottom, ACTIVE);
        graphics.fill(right - 6, top, right - 2, top + 1, ACTIVE);
        graphics.fill(right - 6, bottom - 1, right - 2, bottom, ACTIVE);
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    private static boolean inside(double mouseX, double mouseY, Rect bounds) {
        return inside(mouseX, mouseY, bounds.x, bounds.y, bounds.width, bounds.height);
    }

    private static Rect rect(Widget widget) {
        return new Rect(
                Math.round(widget.getAbsoluteX()),
                Math.round(widget.getAbsoluteY()),
                Math.round(widget.getWidth()),
                Math.round(widget.getHeight())
        );
    }

    private static Rect[] coordinateFrames(Rect bounds) {
        var width = (bounds.width - 8) / 3;
        return new Rect[]{
                new Rect(bounds.x, bounds.y, width, bounds.height),
                new Rect(bounds.x + width + 4, bounds.y, width, bounds.height),
                new Rect(bounds.x + (width + 4) * 2, bounds.y,
                        bounds.width - (width + 4) * 2, bounds.height)
        };
    }

    private static int integer(String value) {
        try {
            return Integer.parseInt(value.strip());
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    @Override
    protected void onInit() {
        var layout = SerializedUiLayout.INSTANCE.load(
                AcademyCraft.academy("ui/layout/location_teleport.json"),
                List.of(
                        "panel", "name_input", "coordinates", "mark_current", "add_mark",
                        "marks", "refresh", "done"
                ),
                this::fallbackLayout
        );
        if (!(SerializedUiLayout.INSTANCE.find(layout, "panel_background") instanceof BlendQuadWidget)) {
            AcademyCraft.getLogger().warn(
                    "[LocationTeleport] Ignoring legacy UI override without an Academy projection background"
            );
            layout = fallbackLayout();
        }
        serializedLayout = layout;
        getRoot().addChild("serialized_layout", layout);
        panelLayout = SerializedUiLayout.INSTANCE.require(layout, "panel");
        nameInputLayout = SerializedUiLayout.INSTANCE.require(layout, "name_input");
        coordinatesLayout = SerializedUiLayout.INSTANCE.require(layout, "coordinates");
        markCurrentLayout = SerializedUiLayout.INSTANCE.require(layout, "mark_current");
        addMarkLayout = SerializedUiLayout.INSTANCE.require(layout, "add_mark");
        marksLayout = SerializedUiLayout.INSTANCE.require(layout, "marks");
        refreshLayout = SerializedUiLayout.INSTANCE.require(layout, "refresh");
        doneLayout = SerializedUiLayout.INSTANCE.require(layout, "done");

        panelX = (width - PANEL_W) / 2;
        panelY = (height - PANEL_H) / 2;
        var left = panelX + PANEL_INSET;
        var contentWidth = PANEL_W - PANEL_INSET * 2;
        var nameFrame = new Rect(left, panelY + NAME_Y, contentWidth, CONTROL_H);
        nameBox = new EditBox(font, 0, 0, 1, 16, Component.empty());
        place(nameBox, nameFrame);
        nameBox.setHint(Component.translatable("academy.location_teleport.name"));
        nameBox.setMaxLength(64);
        configureInput(nameBox);
        addRenderableWidget(nameBox);
        var coordinates = coordinateFrames(new Rect(
                left, panelY + COORDINATES_Y, contentWidth, CONTROL_H
        ));
        xBox = coordinateBox(coordinates[0], "X");
        yBox = coordinateBox(coordinates[1], "Y");
        zBox = coordinateBox(coordinates[2], "Z");
        listTop = panelY + MARKS_Y + 2;
        listBottom = panelY + MARKS_Y + MARKS_H - 2;
        MisakaNetworkClient.send(LocationTeleport.RequestMarksPacket.INSTANCE);
    }

    private FrameLayoutWidget fallbackLayout() {
        var layout = new FrameLayoutWidget();
        layout.setLayoutParams(new FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT));
        var panel = new FrameLayoutWidget();
        panel.setLayoutParams(new FrameLayoutWidget.LayoutParams().size(PANEL_W, PANEL_H).gravity(Gravity.CENTER));
        var projection = new BlendQuadWidget();
        projection.setAlpha(PANEL_ALPHA);
        projection.setDrawLine(false);
        projection.setLayoutParams(new FrameLayoutWidget.LayoutParams()
                .sizeMode(SizeMode.MATCH_PARENT).margin(1, 0, 1, 0));
        panel.addChild("panel_background", projection);
        var topBorder = new FillWidget(PANEL_BORDER);
        topBorder.setLayoutParams(new FrameLayoutWidget.LayoutParams()
                .size(PANEL_W - 8, 1).gravity(Gravity.TOP).marginLeft(4));
        panel.addChild("border_top", topBorder);
        var bottomBorder = new FillWidget(PANEL_BORDER);
        bottomBorder.setLayoutParams(new FrameLayoutWidget.LayoutParams()
                .size(PANEL_W - 8, 1).gravity(Gravity.BOTTOM).marginLeft(4));
        panel.addChild("border_bottom", bottomBorder);
        var leftBorder = new FillWidget(PANEL_BORDER);
        leftBorder.setLayoutParams(new FrameLayoutWidget.LayoutParams()
                .size(1, PANEL_H - 8).gravity(Gravity.LEFT).marginTop(4));
        panel.addChild("border_left", leftBorder);
        var rightBorder = new FillWidget(PANEL_BORDER);
        rightBorder.setLayoutParams(new FrameLayoutWidget.LayoutParams()
                .size(1, PANEL_H - 8).gravity(Gravity.RIGHT).marginTop(4));
        panel.addChild("border_right", rightBorder);
        var titleDivider = new FillWidget(PANEL_BORDER);
        titleDivider.setLayoutParams(new FrameLayoutWidget.LayoutParams()
                .size(PANEL_W - 14, 1).margin(7, 24, 7, 0));
        panel.addChild("title_divider", titleDivider);
        addSlot(panel, "name_input", PANEL_INSET, NAME_Y, PANEL_W - PANEL_INSET * 2, CONTROL_H);
        addSlot(panel, "coordinates", PANEL_INSET, COORDINATES_Y,
                PANEL_W - PANEL_INSET * 2, CONTROL_H);
        addSlot(panel, "mark_current", PANEL_INSET, ACTIONS_Y, 194, CONTROL_H);
        addSlot(panel, "add_mark", PANEL_INSET + 202, ACTIONS_Y, 194, CONTROL_H);
        addSlot(panel, "marks", PANEL_INSET, MARKS_Y, PANEL_W - PANEL_INSET * 2, MARKS_H);
        addSlot(panel, "refresh", PANEL_INSET, FOOTER_Y, 194, CONTROL_H);
        addSlot(panel, "done", PANEL_INSET + 202, FOOTER_Y, 194, CONTROL_H);
        layout.addChild("panel", panel);
        return layout;
    }

    private void syncSerializedLayout() {
        if (panelLayout == null || panelLayout.getWidth() <= 0.0f) return;
        var panel = rect(panelLayout);
        panelX = panel.x;
        panelY = panel.y;
        panelWidth = panel.width;
        var name = rect(nameInputLayout);
        place(nameBox, name);
        var coordinates = coordinateFrames(rect(coordinatesLayout));
        place(xBox, coordinates[0]);
        place(yBox, coordinates[1]);
        place(zBox, coordinates[2]);
        var marks = rect(marksLayout);
        listTop = marks.y + 2;
        listBottom = marks.y + marks.height - 2;
    }

    private EditBox coordinateBox(Rect frame, String hint) {
        var box = new EditBox(font, 0, 0, 1, 16, Component.empty());
        place(box, frame);
        box.setHint(Component.literal(hint));
        box.setMaxLength(12);
        configureInput(box);
        addRenderableWidget(box);
        return box;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        syncSerializedLayout();
        var panel = rect(panelLayout);
        var marks = rect(marksLayout);
        fillSection(graphics, marks);
        var coordinates = coordinateFrames(rect(coordinatesLayout));
        renderInputFrame(graphics, rect(nameInputLayout), nameBox);
        renderInputFrame(graphics, coordinates[0], xBox);
        renderInputFrame(graphics, coordinates[1], yBox);
        renderInputFrame(graphics, coordinates[2], zBox);
        nameBox.extractRenderState(graphics, mouseX, mouseY, partialTick);
        xBox.extractRenderState(graphics, mouseX, mouseY, partialTick);
        yBox.extractRenderState(graphics, mouseX, mouseY, partialTick);
        zBox.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(font, title, panel.x + panel.width / 2, panel.y + 8, TEXT);
        button(graphics, rect(markCurrentLayout),
                Component.translatable("academy.location_teleport.mark_current"), mouseX, mouseY);
        button(graphics, rect(addMarkLayout),
                Component.translatable("academy.location_teleport.add_mark"), mouseX, mouseY);
        button(graphics, rect(refreshLayout),
                Component.translatable("academy.location_teleport.refresh"), mouseX, mouseY);
        button(graphics, rect(doneLayout),
                Component.translatable("gui.done"), mouseX, mouseY);
        renderMarks(graphics, mouseX, mouseY);
    }

    private void renderMarks(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        var content = marksContent();
        var left = content.x;
        var right = content.x + content.width;
        var visible = Math.max(1, (listBottom - listTop) / ROW_H);
        scroll = Mth.clamp(scroll, 0, Math.max(0, marks.size() - visible));
        graphics.enableScissor(left, listTop, right, listBottom);
        for (var row = 0; row < visible && scroll + row < marks.size(); row++) {
            var index = scroll + row;
            var mark = marks.get(index);
            var y = listTop + row * ROW_H;
            var hover = mouseX >= left && mouseX <= right && mouseY >= y && mouseY < y + ROW_H - 1;
            var quickSelected = index == quickMarkIndex;
            var defensiveSelected = index == defensiveMarkIndex;
            graphics.fill(left, y, right, y + ROW_H - 1,
                    quickSelected || defensiveSelected ? ROW_SELECTED
                            : hover ? ROW_HOVER
                            : index % 2 == 0 ? ROW : ROW_ALTERNATE);
            renderSelectionMarker(graphics, left, y, quickSelected, defensiveSelected);
            var name = mark.name() == null || mark.name().isBlank() ? "Mark " + (index + 1) : mark.name();
            var removeLeft = right - REMOVE_ACTION_WIDTH;
            var teleportLeft = removeLeft - TELEPORT_ACTION_WIDTH;
            var defensiveLeft = teleportLeft - DEFENSIVE_ACTION_WIDTH;
            var quickLeft = defensiveLeft - QUICK_ACTION_WIDTH;
            var textLeft = left + 6;
            var textRight = quickLeft - 6;
            var availableTextWidth = Math.max(2, textRight - textLeft - 6);
            var nameWidth = Math.max(1, availableTextWidth * 3 / 5);
            var coordinateWidth = Math.max(1, availableTextWidth - nameWidth);
            graphics.text(font, font.plainSubstrByWidth(name, nameWidth), textLeft, y + 5, TEXT, false);
            var coords = mark.x() + ", " + mark.y() + ", " + mark.z();
            var clippedCoords = font.plainSubstrByWidth(coords, coordinateWidth);
            graphics.text(font, clippedCoords,
                    textRight - font.width(clippedCoords), y + 5, DIM, false);
            rowAction(graphics, quickLeft, y, QUICK_ACTION_WIDTH,
                    Component.translatable("academy.location_teleport.quick_point"),
                    mouseX, mouseY, quickSelected, SelectionForm.RAIL);
            rowAction(graphics, defensiveLeft, y, DEFENSIVE_ACTION_WIDTH,
                    Component.translatable("academy.location_teleport.defensive_point"),
                    mouseX, mouseY, defensiveSelected, SelectionForm.BRACKETS);
            iconButton(graphics, teleportLeft, y, TELEPORT_ACTION_WIDTH, ">", TELEPORT, mouseX, mouseY);
            iconButton(graphics, removeLeft, y, REMOVE_ACTION_WIDTH, "x", DANGER, mouseX, mouseY);
        }
        graphics.disableScissor();
        renderScrollIndicator(graphics, rect(marksLayout), visible);
        if (marks.isEmpty()) {
            graphics.centeredText(font, Component.translatable("academy.location_teleport.empty"),
                    panelX + panelWidth / 2, listTop + 6, DIM);
        }
    }

    private void rowAction(
            GuiGraphicsExtractor graphics,
            int x,
            int y,
            int width,
            Component label,
            int mouseX,
            int mouseY,
            boolean selected,
            SelectionForm selectionForm
    ) {
        var hovered = inside(mouseX, mouseY, x, y, width, ROW_H - 1);
        buttonSurface(graphics, x, y, width, ROW_H - 1, selected, hovered, selectionForm);
        graphics.centeredText(
                font,
                font.plainSubstrByWidth(label.getString(), width - 4),
                x + width / 2,
                y + 5,
                selected || hovered ? TEXT : DIM
        );
    }

    private void iconButton(
            GuiGraphicsExtractor graphics,
            int x,
            int y,
            int width,
            String glyph,
            int semanticColor,
            int mouseX,
            int mouseY
    ) {
        var hovered = inside(mouseX, mouseY, x, y, width, ROW_H - 1);
        buttonSurface(graphics, x, y, width, ROW_H - 1, false, hovered, SelectionForm.NONE);
        graphics.centeredText(font, glyph, x + width / 2, y + 5, hovered ? TEXT : semanticColor);
    }

    private void button(GuiGraphicsExtractor graphics, Rect bounds, Component text, int mouseX, int mouseY) {
        var x = bounds.x;
        var y = bounds.y;
        var width = bounds.width;
        var height = bounds.height;
        var hover = inside(mouseX, mouseY, x, y, width, height);
        buttonSurface(graphics, x, y, width, height, false, hover, SelectionForm.NONE);
        graphics.centeredText(font, font.plainSubstrByWidth(text.getString(), width - 8),
                x + width / 2, y + (height - 8) / 2, hover ? TEXT : DIM);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        var mouseX = event.x();
        var mouseY = event.y();
        syncSerializedLayout();
        if (event.button() == 0) {
            var coordinates = coordinateFrames(rect(coordinatesLayout));
            if (focusInputAt(event, doubleClick, nameBox, rect(nameInputLayout))
                    || focusInputAt(event, doubleClick, xBox, coordinates[0])
                    || focusInputAt(event, doubleClick, yBox, coordinates[1])
                    || focusInputAt(event, doubleClick, zBox, coordinates[2])) {
                return true;
            }
            setFocused(null);
            if (inside(mouseX, mouseY, rect(markCurrentLayout))) {
                MisakaNetworkClient.send(new LocationTeleport.SaveMarkPacket(true, nameBox.getValue(), 0, 0, 0));
                return true;
            }
            if (inside(mouseX, mouseY, rect(addMarkLayout))) {
                MisakaNetworkClient.send(new LocationTeleport.SaveMarkPacket(false, nameBox.getValue(),
                        integer(xBox.getValue()), integer(yBox.getValue()), integer(zBox.getValue())));
                return true;
            }
            if (inside(mouseX, mouseY, rect(refreshLayout))) {
                MisakaNetworkClient.send(LocationTeleport.RequestMarksPacket.INSTANCE);
                return true;
            }
            if (inside(mouseX, mouseY, rect(doneLayout))) {
                onClose();
                return true;
            }
            if (mouseY >= listTop && mouseY < listBottom) {
                var index = scroll + (int) ((mouseY - listTop) / ROW_H);
                if (index >= 0 && index < marks.size()) {
                    var content = marksContent();
                    var right = content.x + content.width;
                    var removeLeft = right - REMOVE_ACTION_WIDTH;
                    var teleportLeft = removeLeft - TELEPORT_ACTION_WIDTH;
                    var defensiveLeft = teleportLeft - DEFENSIVE_ACTION_WIDTH;
                    var quickLeft = defensiveLeft - QUICK_ACTION_WIDTH;
                    if (mouseX >= removeLeft && mouseX < right) {
                        MisakaNetworkClient.send(new LocationTeleport.RemoveMarkPacket(index));
                    } else if (mouseX >= teleportLeft && mouseX < removeLeft) {
                        MisakaNetworkClient.send(new LocationTeleport.TeleportToMarkPacket(index));
                        onClose();
                    } else if (mouseX >= defensiveLeft && mouseX < teleportLeft) {
                        defensiveMarkIndex = defensiveMarkIndex == index ? -1 : index;
                        MisakaNetworkClient.send(new LocationTeleport.SelectMarkPacket(
                                defensiveMarkIndex,
                                true
                        ));
                    } else if (mouseX >= quickLeft && mouseX < defensiveLeft) {
                        quickMarkIndex = quickMarkIndex == index ? -1 : index;
                        MisakaNetworkClient.send(new LocationTeleport.SelectMarkPacket(
                                quickMarkIndex,
                                false
                        ));
                    } else {
                        nameBox.setValue(markValue(index).name());
                        xBox.setValue(Integer.toString(markValue(index).x()));
                        yBox.setValue(Integer.toString(markValue(index).y()));
                        zBox.setValue(Integer.toString(markValue(index).z()));
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private boolean focusInputAt(MouseButtonEvent event, boolean doubleClick, EditBox box, Rect frame) {
        if (!inside(event.x(), event.y(), frame)) return false;
        setFocused(box);
        nameBox.setFocused(box == nameBox);
        xBox.setFocused(box == xBox);
        yBox.setFocused(box == yBox);
        zBox.setFocused(box == zBox);
        if (inside(event.x(), event.y(), box.getX(), box.getY(), box.getWidth(), 16)) {
            box.mouseClicked(event, doubleClick);
        }
        return true;
    }

    private Rect marksContent() {
        var bounds = rect(marksLayout);
        return new Rect(
                bounds.x + 2,
                bounds.y + 2,
                Math.max(1, bounds.width - 4 - SCROLLBAR_GAP - SCROLLBAR_WIDTH),
                Math.max(1, bounds.height - 4)
        );
    }

    private static void renderSelectionMarker(
            GuiGraphicsExtractor graphics,
            int x,
            int y,
            boolean quickSelected,
            boolean defensiveSelected
    ) {
        if (!quickSelected && !defensiveSelected) return;
        if (quickSelected) {
            graphics.fill(x, y + 2, x + 2, y + ROW_H - 3, ACTIVE);
        }
        if (defensiveSelected) {
            var markerX = quickSelected ? x + 2 : x;
            graphics.fill(markerX, y + 2, markerX + 4, y + 3, ACTIVE);
            graphics.fill(markerX, y + ROW_H - 4, markerX + 4, y + ROW_H - 3, ACTIVE);
        }
    }

    private void renderScrollIndicator(GuiGraphicsExtractor graphics, Rect bounds, int visibleRows) {
        if (marks.size() <= visibleRows) return;
        var trackX = bounds.x + bounds.width - 2 - SCROLLBAR_WIDTH;
        var trackHeight = Math.max(1, listBottom - listTop);
        graphics.fill(trackX, listTop, trackX + SCROLLBAR_WIDTH, listBottom, SCROLL_TRACK);
        var thumbHeight = Math.max(12, trackHeight * visibleRows / marks.size());
        var maxScroll = marks.size() - visibleRows;
        var travel = Math.max(0, trackHeight - thumbHeight);
        var thumbY = listTop + travel * scroll / maxScroll;
        graphics.fill(trackX, thumbY, trackX + SCROLLBAR_WIDTH, thumbY + thumbHeight, DIM);
    }

    private static void fillSection(GuiGraphicsExtractor graphics, Rect bounds) {
        graphics.fill(
                bounds.x,
                bounds.y,
                bounds.x + bounds.width,
                bounds.y + bounds.height,
                SECTION
        );
        border(graphics, bounds.x, bounds.y, bounds.width, bounds.height, BORDER_DIM);
    }

    private static void buttonSurface(
            GuiGraphicsExtractor graphics,
            int x,
            int y,
            int width,
            int height,
            boolean selected,
            boolean hovered,
            SelectionForm selectionForm
    ) {
        graphics.fill(x, y, x + width, y + height,
                selected ? ROW_SELECTED : hovered ? ROW_HOVER : CONTROL);
        border(graphics, x, y, width, height,
                selected ? ACTIVE : hovered ? BORDER : BORDER_DIM);
        if (selected && selectionForm == SelectionForm.RAIL) {
            graphics.fill(x + 1, y + 2, x + 3, y + height - 2, ACTIVE);
        } else if (selected && selectionForm == SelectionForm.BRACKETS) {
            graphics.fill(x + 1, y + 2, x + 6, y + 3, ACTIVE);
            graphics.fill(x + 1, y + height - 3, x + 6, y + height - 2, ACTIVE);
        }
    }

    private static void border(
            GuiGraphicsExtractor graphics,
            int x,
            int y,
            int width,
            int height,
            int color
    ) {
        graphics.fill(x + 1, y, x + width - 1, y + 1, color);
        graphics.fill(x + 1, y + height - 1, x + width - 1, y + height, color);
        graphics.fill(x, y + 1, x + 1, y + height - 1, color);
        graphics.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }

    private Mark markValue(int index) {
        return marks.get(index);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseY >= listTop && mouseY < listBottom) {
            scroll -= Mth.sign(scrollY);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    public void setMarks(List<Mark> marks, int quickMarkIndex, int defensiveMarkIndex) {
        this.marks.clear();
        this.marks.addAll(marks);
        this.quickMarkIndex = quickMarkIndex;
        this.defensiveMarkIndex = defensiveMarkIndex;
    }

    @Override
    public String debugLayoutId() {
        return "location_teleport";
    }

    @Override
    public FrameLayoutWidget debugLayoutRoot() {
        return serializedLayout;
    }

    private record Rect(int x, int y, int width, int height) {
    }

    private enum SelectionForm {
        NONE,
        RAIL,
        BRACKETS
    }
}
