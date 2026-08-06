package org.academy.internal.client.gui.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.academy.AcademyCraft;
import org.academy.api.client.gui.layout.Gravity;
import org.academy.api.client.gui.layout.SizeMode;
import org.academy.api.client.gui.screen.UiScreen;
import org.academy.api.client.gui.widget.EmptyWidget;
import org.academy.api.client.gui.widget.FrameLayoutWidget;
import org.academy.api.client.gui.widget.Widget;
import org.academy.internal.client.gui.SerializedUiLayout;
import org.academy.internal.client.gui.debug.SerializedUiDebugHost;
import org.academy.internal.common.ability.teleport.skills.lv3.LocationTeleport;
import org.academy.internal.common.skilldata.LocationTeleportData.Mark;
import org.misaka.MisakaNetworkClient;

import java.util.ArrayList;
import java.util.List;

public final class LocationTeleportScreen extends UiScreen implements SerializedUiDebugHost {
    private static final int PANEL_BG = 0xE60E1216;
    private static final int BORDER = 0xFF2E9CCB;
    private static final int TEXT = 0xFFC1CFD5;
    private static final int DIM = 0xFFA2A2A2;
    private static final int HOVER = 0x332E9CCB;
    private static final int SELECTED = 0x552E9CCB;
    private static final int PANEL_W = 300;
    private static final int PANEL_H = 212;
    private static final int ROW_H = 18;
    private static final int TELEPORT_ACTION_WIDTH = 24;
    private static final int REMOVE_ACTION_WIDTH = 18;

    private final List<Mark> marks = new ArrayList<>();
    private EditBox nameBox;
    private EditBox xBox;
    private EditBox yBox;
    private EditBox zBox;
    private int panelX;
    private int panelY;
    private int listTop;
    private int listBottom;
    private int selectedIndex = -1;
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

    @Override
    protected void onInit() {
        var layout = SerializedUiLayout.load(
                AcademyCraft.academy("ui/layout/location_teleport.json"),
                List.of("panel", "name_input", "coordinates", "mark_current", "add_mark", "marks", "refresh", "done"),
                this::fallbackLayout
        );
        serializedLayout = layout;
        getRoot().addChild("serialized_layout", layout);
        panelLayout = SerializedUiLayout.require(layout, "panel");
        nameInputLayout = SerializedUiLayout.require(layout, "name_input");
        coordinatesLayout = SerializedUiLayout.require(layout, "coordinates");
        markCurrentLayout = SerializedUiLayout.require(layout, "mark_current");
        addMarkLayout = SerializedUiLayout.require(layout, "add_mark");
        marksLayout = SerializedUiLayout.require(layout, "marks");
        refreshLayout = SerializedUiLayout.require(layout, "refresh");
        doneLayout = SerializedUiLayout.require(layout, "done");

        panelX = (width - PANEL_W) / 2;
        panelY = (height - PANEL_H) / 2;
        var left = panelX + 12;
        var contentWidth = PANEL_W - 24;
        nameBox = new EditBox(font, left, panelY + 25, contentWidth, 16, Component.empty());
        nameBox.setHint(Component.translatable("academy.location_teleport.name"));
        nameBox.setMaxLength(64);
        addRenderableWidget(nameBox);
        var coordWidth = (contentWidth - 8) / 3;
        xBox = coordinateBox(left, panelY + 47, coordWidth, "X");
        yBox = coordinateBox(left + coordWidth + 4, panelY + 47, coordWidth, "Y");
        zBox = coordinateBox(left + (coordWidth + 4) * 2, panelY + 47,
                contentWidth - (coordWidth + 4) * 2, "Z");
        listTop = panelY + 91;
        listBottom = panelY + PANEL_H - 28;
        MisakaNetworkClient.send(LocationTeleport.RequestMarksPacket.INSTANCE);
    }

    private FrameLayoutWidget fallbackLayout() {
        var layout = new FrameLayoutWidget();
        layout.setLayoutParams(new FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT));
        var panel = new FrameLayoutWidget();
        panel.setLayoutParams(new FrameLayoutWidget.LayoutParams().size(PANEL_W, PANEL_H).gravity(Gravity.CENTER));
        addSlot(panel, "name_input", 12, 25, 276, 16);
        addSlot(panel, "coordinates", 12, 47, 276, 16);
        addSlot(panel, "mark_current", 12, 69, 132, 16);
        addSlot(panel, "add_mark", 156, 69, 132, 16);
        addSlot(panel, "marks", 12, 91, 276, 93);
        addSlot(panel, "refresh", 12, 190, 132, 16);
        addSlot(panel, "done", 156, 190, 132, 16);
        layout.addChild("panel", panel);
        return layout;
    }

    private static void addSlot(FrameLayoutWidget panel, String name, int x, int y, int width, int height) {
        var slot = new EmptyWidget();
        slot.setLayoutParams(new FrameLayoutWidget.LayoutParams().size(width, height).margin(x, y, 0, 0));
        panel.addChild(name, slot);
    }

    private void syncSerializedLayout() {
        if (panelLayout == null || panelLayout.getWidth() <= 0.0f) return;
        var panel = rect(panelLayout);
        panelX = panel.x;
        panelY = panel.y;
        var name = rect(nameInputLayout);
        nameBox.setX(name.x);
        nameBox.setY(name.y);
        nameBox.setWidth(name.width);
        var coordinates = rect(coordinatesLayout);
        var coordWidth = (coordinates.width - 8) / 3;
        place(xBox, coordinates.x, coordinates.y, coordWidth);
        place(yBox, coordinates.x + coordWidth + 4, coordinates.y, coordWidth);
        place(zBox, coordinates.x + (coordWidth + 4) * 2, coordinates.y,
                coordinates.width - (coordWidth + 4) * 2);
        var marks = rect(marksLayout);
        listTop = marks.y;
        listBottom = marks.y + marks.height;
    }

    private static void place(EditBox box, int x, int y, int width) {
        box.setX(x);
        box.setY(y);
        box.setWidth(width);
    }

    private EditBox coordinateBox(int x, int y, int width, String hint) {
        var box = new EditBox(font, x, y, width, 16, Component.empty());
        box.setHint(Component.literal(hint));
        box.setMaxLength(12);
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
        nameBox.extractRenderState(graphics, mouseX, mouseY, partialTick);
        xBox.extractRenderState(graphics, mouseX, mouseY, partialTick);
        yBox.extractRenderState(graphics, mouseX, mouseY, partialTick);
        zBox.extractRenderState(graphics, mouseX, mouseY, partialTick);
        var panel = rect(panelLayout);
        graphics.centeredText(font, title, panel.x + panel.width / 2, panel.y + 6, TEXT);
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
        var left = panelX + 12;
        var right = panelX + PANEL_W - 12;
        var visible = Math.max(1, (listBottom - listTop) / ROW_H);
        scroll = Math.clamp(scroll, 0, Math.max(0, marks.size() - visible));
        graphics.enableScissor(left, listTop, right, listBottom);
        for (var row = 0; row < visible && scroll + row < marks.size(); row++) {
            var index = scroll + row;
            var mark = marks.get(index);
            var y = listTop + row * ROW_H;
            var hover = mouseX >= left && mouseX <= right && mouseY >= y && mouseY < y + ROW_H - 1;
            graphics.fill(left, y, right, y + ROW_H - 1,
                    index == selectedIndex ? SELECTED : hover ? HOVER : 0x12FFFFFF);
            var name = mark.name() == null || mark.name().isBlank() ? "Mark " + (index + 1) : mark.name();
            graphics.text(font, font.plainSubstrByWidth(name, 130), left + 5, y + 5, TEXT, false);
            var coords = mark.x() + ", " + mark.y() + ", " + mark.z();
            var removeLeft = right - REMOVE_ACTION_WIDTH;
            var teleportLeft = removeLeft - TELEPORT_ACTION_WIDTH;
            graphics.text(font, coords, teleportLeft - 4 - font.width(coords), y + 5, DIM, false);
            graphics.centeredText(font, ">", teleportLeft + TELEPORT_ACTION_WIDTH / 2, y + 5,
                    0xFF55CC55);
            graphics.centeredText(font, "x", removeLeft + REMOVE_ACTION_WIDTH / 2, y + 5,
                    0xFFCC5555);
        }
        graphics.disableScissor();
        if (marks.isEmpty()) {
            graphics.centeredText(font, Component.translatable("academy.location_teleport.empty"),
                    panelX + PANEL_W / 2, listTop + 6, DIM);
        }
    }

    private void button(GuiGraphicsExtractor graphics, Rect bounds, Component text, int mouseX, int mouseY) {
        var x = bounds.x;
        var y = bounds.y;
        var width = bounds.width;
        var height = bounds.height;
        var hover = inside(mouseX, mouseY, x, y, width, height);
        graphics.fill(x, y, x + width, y + height, hover ? 0x40C1CFD5 : 0x1AFFFFFF);
        border(graphics, x, y, width, height, hover ? BORDER : 0x552E9CCB);
        graphics.centeredText(font, text, x + width / 2, y + 4, hover ? 0xFFFFFFFF : TEXT);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        var mouseX = event.x();
        var mouseY = event.y();
        syncSerializedLayout();
        if (event.button() == 0) {
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
                    var right = panelX + PANEL_W - 12;
                    var removeLeft = right - REMOVE_ACTION_WIDTH;
                    var teleportLeft = removeLeft - TELEPORT_ACTION_WIDTH;
                    if (mouseX >= removeLeft && mouseX < right) {
                        MisakaNetworkClient.send(new LocationTeleport.RemoveMarkPacket(index));
                    } else if (mouseX >= teleportLeft && mouseX < removeLeft) {
                        MisakaNetworkClient.send(new LocationTeleport.TeleportToMarkPacket(index));
                        onClose();
                    } else {
                        selectedIndex = index;
                        MisakaNetworkClient.send(new LocationTeleport.SelectMarkPacket(index));
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseY >= listTop && mouseY < listBottom) {
            scroll -= (int) Math.signum(scrollY);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    public void setMarks(List<Mark> marks, int selectedIndex) {
        this.marks.clear();
        this.marks.addAll(marks);
        this.selectedIndex = selectedIndex;
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    @Override
    public String debugLayoutId() {
        return "location_teleport";
    }

    @Override
    public FrameLayoutWidget debugLayoutRoot() {
        return serializedLayout;
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

    private static int integer(String value) {
        try {
            return Integer.parseInt(value.strip());
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    private static void border(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }

    private record Rect(int x, int y, int width, int height) {
    }
}
