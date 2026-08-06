package org.academy.internal.client.ability.mentalout;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
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
import org.academy.internal.common.ability.mentalout.precision.PrecisionGraph;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class PrecisionOperationScreen extends UiScreen implements SerializedUiDebugHost {
    static final int NODE_W = 80;
    static final int NODE_HEADER_H = 11;
    static final int PORT_ROW_H = 8;
    static final int MIN_NODE_H = 26;
    static final double MIN_ZOOM = 0.5;
    static final double MAX_ZOOM = 1.6;
    private static final float SMALL_TEXT_SCALE = 0.75f;
    private static final int PANEL_BG = 0xF0121820;
    private static final int SECTION_BG = 0xF01B252E;
    private static final int BORDER = 0xAA4FA6C4;
    private static final int ACCENT = 0xFF53C7E8;
    private static final int TEXT = 0xFFE5F1F5;
    private static final int DIM = 0xFF91A4AD;
    private static final int ERROR = 0xFFFF7C72;
    private static final int TOP_H = 20;
    private static final int STATUS_H = 16;
    private static final int RAIL_W = 18;
    private static final int ROW_H = 14;
    private static final int TOOL_SIZE = 14;
    private static final int PORT_HIT = 14;
    private static final int SNAP_DISTANCE = 10;
    private static final String[] TOOL_LABELS = {
            "delete", "copy", "undo", "redo", "auto_layout", "fit", "save", "restore"
    };
    private static final String[] TOOL_GLYPHS = {"X", "C", "<", ">", "A", "F", "S", "R"};

    private final ArrayDeque<PrecisionGraph> undo = new ArrayDeque<>();
    private final ArrayDeque<PrecisionGraph> redo = new ArrayDeque<>();
    private PrecisionGraph graph;
    private long revision;
    private int slot;
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int leftX;
    private int leftW;
    private int rightX;
    private int rightW;
    private int canvasX;
    private int canvasY;
    private int canvasW;
    private int canvasH;
    private boolean compactLeft;
    private boolean compactRight;
    private boolean leftDrawerOpen;
    private boolean rightDrawerOpen;
    private EditBox search;
    private PrecisionGraph.NodeGroup selectedGroup = PrecisionGraph.NodeGroup.TARGET;
    private int paletteScroll;
    private int selectedNode = -1;
    private Integer draggingNode;
    private boolean panning;
    private boolean spaceDown;
    private double panX;
    private double panY;
    private double zoom = 1.0;
    private boolean initialView = true;
    private ConnectionDrag connection;
    private QuickInsert quickInsert;
    private PrecisionGraph.Diagnostic diagnostic = PrecisionGraph.Diagnostic.OK;
    private PrecisionGraph.Diagnostic transientDiagnostic = PrecisionGraph.Diagnostic.OK;
    private long transientUntil;
    private Widget panelLayout;
    private Widget paletteLayout;
    private Widget canvasLayout;
    private Widget inspectorLayout;
    private FrameLayoutWidget serializedLayout;
    private String serializedLayoutId;

    PrecisionOperationScreen(int slot, PrecisionGraph graph, long revision) {
        super(Component.translatable("screen.academy.precision_operation.title"));
        this.slot = Math.clamp(slot, 0, 3);
        this.graph = graph == null ? PrecisionGraph.EMPTY : graph;
        this.revision = revision;
    }

    @Override
    protected void onInit() {
        var layout = PrecisionEditorGeometry.layout(width, height);
        panelX = layout.panelX();
        panelY = layout.panelY();
        panelW = layout.panelW();
        panelH = layout.panelH();
        leftX = layout.leftX();
        leftW = layout.leftW();
        rightX = layout.rightX();
        rightW = layout.rightW();
        canvasX = layout.canvasX();
        canvasY = layout.canvasY();
        canvasW = layout.canvasW();
        canvasH = layout.canvasH();
        compactLeft = layout.compactLeft();
        compactRight = layout.compactRight();

        var layoutVariant = compactLeft ? "compact" : compactRight ? "medium" : "wide";
        serializedLayoutId = "precision_operation_" + layoutVariant;
        var serialized = SerializedUiLayout.load(
                AcademyCraft.academy("ui/layout/" + serializedLayoutId + ".json"),
                List.of("panel", "palette", "canvas", "inspector"),
                () -> fallbackLayout(layout)
        );
        serializedLayout = serialized;
        getRoot().addChild("serialized_layout", serialized);
        panelLayout = SerializedUiLayout.require(serialized, "panel");
        paletteLayout = SerializedUiLayout.require(serialized, "palette");
        canvasLayout = SerializedUiLayout.require(serialized, "canvas");
        inspectorLayout = SerializedUiLayout.require(serialized, "inspector");

        search = new EditBox(font, paletteX() + 4, panelY + TOP_H + 25, paletteWidth() - 8, 15,
                Component.empty());
        search.setHint(Component.translatable("screen.academy.precision_operation.search"));
        search.setMaxLength(48);
        search.visible = paletteVisible();
        addRenderableWidget(search);
        if (initialView) {
            initialView = false;
            if (!graph.nodes().isEmpty()) fitCanvas(true);
        }
    }

    private FrameLayoutWidget fallbackLayout(PrecisionEditorGeometry.Layout geometry) {
        var layout = new FrameLayoutWidget();
        layout.setLayoutParams(new FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT));
        var panel = new FrameLayoutWidget();
        panel.setLayoutParams(new FrameLayoutWidget.LayoutParams()
                .size(geometry.panelW(), geometry.panelH())
                .gravity(Gravity.TOP_LEFT)
                .margin(geometry.panelX(), geometry.panelY(), 0, 0));
        addLayoutSlot(panel, "palette",
                geometry.leftX() - geometry.panelX(), geometry.canvasY() - geometry.panelY(),
                geometry.leftW(), geometry.canvasH());
        addLayoutSlot(panel, "canvas",
                geometry.canvasX() - geometry.panelX(), geometry.canvasY() - geometry.panelY(),
                geometry.canvasW(), geometry.canvasH());
        addLayoutSlot(panel, "inspector",
                geometry.rightX() - geometry.panelX(), geometry.canvasY() - geometry.panelY(),
                geometry.rightW(), geometry.canvasH());
        layout.addChild("panel", panel);
        return layout;
    }

    private static void addLayoutSlot(
            FrameLayoutWidget panel,
            String name,
            int x,
            int y,
            int width,
            int height
    ) {
        var slot = new EmptyWidget();
        slot.setLayoutParams(new FrameLayoutWidget.LayoutParams().size(width, height).margin(x, y, 0, 0));
        panel.addChild(name, slot);
    }

    private void syncSerializedLayout() {
        if (panelLayout == null || panelLayout.getWidth() <= 0.0f) return;
        var panel = rect(panelLayout);
        var palette = rect(paletteLayout);
        var canvas = rect(canvasLayout);
        var inspector = rect(inspectorLayout);
        panelX = panel.x;
        panelY = panel.y;
        panelW = panel.width;
        panelH = panel.height;
        leftX = palette.x;
        leftW = palette.width;
        canvasX = canvas.x;
        canvasY = canvas.y;
        canvasW = canvas.width;
        canvasH = canvas.height;
        rightX = inspector.x;
        rightW = inspector.width;
        compactLeft = leftW <= RAIL_W;
        compactRight = rightW <= RAIL_W;
        updateSearchBounds();
    }

    void applyServerState(int selectedSlot, PrecisionGraph serverGraph, long serverRevision) {
        revision = serverRevision;
        if (slot == selectedSlot) setGraph(serverGraph, false);
    }

    void applyResult(int resultSlot, boolean accepted, long serverRevision, PrecisionGraph.Diagnostic result) {
        revision = Math.max(revision, serverRevision);
        if (slot == resultSlot) {
            diagnostic = result;
            if (!accepted) showTransient(result);
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
    }

    private void renderSerializedOverlays(GuiGraphicsExtractor graphics) {
        border(graphics, panelX, panelY, panelW, panelH, BORDER);
        if (compactLeft && leftDrawerOpen) {
            graphics.fill(paletteX(), canvasY, paletteX() + paletteWidth(), canvasY + canvasH, SECTION_BG);
            border(graphics, paletteX(), canvasY, paletteWidth(), canvasH, BORDER);
        }
        if (compactRight && rightDrawerOpen) {
            graphics.fill(inspectorX(), canvasY, inspectorX() + inspectorWidth(), canvasY + canvasH, SECTION_BG);
            border(graphics, inspectorX(), canvasY, inspectorWidth(), canvasH, BORDER);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        syncSerializedLayout();
        renderSerializedOverlays(graphics);
        renderTopBar(graphics, mouseX, mouseY);
        renderRails(graphics, mouseX, mouseY);
        if (paletteVisible()) renderPalette(graphics, mouseX, mouseY);
        if (search.visible) search.extractRenderState(graphics, mouseX, mouseY, partialTick);
        renderCanvas(graphics, mouseX, mouseY);
        if (inspectorVisible()) renderInspector(graphics, mouseX, mouseY);
        renderStatus(graphics);
        renderQuickInsert(graphics, mouseX, mouseY);
    }

    private void renderTopBar(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        var x = panelX + 4;
        if (panelW >= 620) {
            smallText(graphics, title.getString(), x, panelY + 6, TEXT, 92);
            x += 96;
        }
        for (var index = 0; index < 4; index++) {
            var label = Component.translatable("screen.academy.precision_operation.slot", index + 1);
            button(graphics, x, panelY + 2, 38, 16, label, mouseX, mouseY, index == slot, true);
            x += 40;
        }
        var toolsX = panelX + panelW - TOOL_LABELS.length * (TOOL_SIZE + 2) - 2;
        for (var index = 0; index < TOOL_LABELS.length; index++) {
            var disabled = index == 6 && !graph.validate().valid();
            iconButton(graphics, toolsX, panelY + 3, TOOL_GLYPHS[index], mouseX, mouseY, disabled);
            if (inside(mouseX, mouseY, toolsX, panelY + 3, TOOL_SIZE, TOOL_SIZE)) {
                graphics.setTooltipForNextFrame(Component.translatable(
                        "screen.academy.precision_operation." + TOOL_LABELS[index]), mouseX, mouseY);
            }
            toolsX += TOOL_SIZE + 2;
        }
    }

    private void renderRails(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (compactLeft) {
            button(graphics, leftX + 1, canvasY + 2, 16, 16, Component.literal("N"),
                    mouseX, mouseY, leftDrawerOpen, false);
        }
        if (compactRight) {
            button(graphics, rightX + 1, canvasY + 2, 16, 16, Component.literal("I"),
                    mouseX, mouseY, rightDrawerOpen, false);
        }
    }

    private void renderPalette(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        var x = paletteX();
        var w = paletteWidth();
        smallText(graphics, Component.translatable("screen.academy.precision_operation.nodes").getString(),
                x + 4, canvasY + 4, DIM, w - 8);
        var tabY = canvasY + 16;
        var tabW = Math.max(16, (w - 8) / PrecisionGraph.NodeGroup.values().length);
        for (var index = 0; index < PrecisionGraph.NodeGroup.values().length; index++) {
            var group = PrecisionGraph.NodeGroup.values()[index];
            button(graphics, x + 4 + index * tabW, tabY, tabW - 1, 12,
                    Component.literal(groupGlyph(group)), mouseX, mouseY, group == selectedGroup, false);
            if (inside(mouseX, mouseY, x + 4 + index * tabW, tabY, tabW - 1, 12)) {
                graphics.setTooltipForNextFrame(Component.translatable(groupKey(group)), mouseX, mouseY);
            }
        }
        var kinds = visibleKinds();
        var listY = canvasY + 44;
        var listBottom = canvasY + canvasH - 3;
        var visibleRows = Math.max(1, (listBottom - listY) / ROW_H);
        paletteScroll = Math.clamp(paletteScroll, 0, Math.max(0, kinds.size() - visibleRows));
        for (var row = 0; row < visibleRows && paletteScroll + row < kinds.size(); row++) {
            var kind = kinds.get(paletteScroll + row);
            var y = listY + row * ROW_H;
            var hover = inside(mouseX, mouseY, x + 3, y, w - 6, ROW_H - 1);
            graphics.fill(x + 3, y, x + w - 3, y + ROW_H - 1,
                    hover ? 0x664FA6C4 : 0x22FFFFFF);
            graphics.fill(x + 3, y, x + 6, y + ROW_H - 1, categoryColor(kind.category()));
            smallText(graphics, groupGlyph(kind.group()), x + 8, y + 3,
                    categoryColor(kind.category()), 8);
            smallText(graphics, nodeLabel(kind).getString(), x + 17, y + 3, TEXT, w - 22);
            if (hover) {
                graphics.setComponentTooltipForNextFrame(font, List.of(
                        nodeLabel(kind),
                        Component.translatable(nodeDescriptionKey(kind)).withColor(DIM)
                ), mouseX, mouseY);
            }
        }
        if (kinds.size() > visibleRows) {
            var trackH = listBottom - listY;
            var thumbH = Math.max(8, trackH * visibleRows / kinds.size());
            var maxScroll = kinds.size() - visibleRows;
            var thumbY = listY + (trackH - thumbH) * paletteScroll / Math.max(1, maxScroll);
            graphics.fill(x + w - 3, listY, x + w - 1, listBottom, 0x442D3E48);
            graphics.fill(x + w - 3, thumbY, x + w - 1, thumbY + thumbH, ACCENT);
        }
    }

    private void renderCanvas(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.enableScissor(canvasX, canvasY, canvasX + canvasW, canvasY + canvasH);
        var pose = graphics.pose();
        pose.pushMatrix();
        pose.translate((float) (canvasX + panX), (float) (canvasY + panY));
        pose.scale((float) zoom, (float) zoom);
        for (var edge : graph.edges()) renderEdge(graphics, edge);
        for (var node : graph.nodes()) renderNode(graphics, node, mouseX, mouseY);
        pose.popMatrix();
        renderConnectionPreview(graphics, mouseX, mouseY);
        renderPortTooltip(graphics, mouseX, mouseY);
        graphics.disableScissor();
        smallText(graphics, Component.literal(Math.round(zoom * 100.0) + "%").getString(),
                canvasX + 3, canvasY + canvasH - 10, DIM, 40);
    }

    private void renderEdge(GuiGraphicsExtractor graphics, PrecisionGraph.Edge edge) {
        var from = node(edge.fromNode());
        var to = node(edge.toNode());
        if (from == null || to == null) return;
        var x1 = (int) Math.round(from.x() + NODE_W);
        var y1 = (int) Math.round(from.y() + outputOffsetY(edge.fromPort()));
        var x2 = (int) Math.round(to.x());
        var y2 = (int) Math.round(to.y() + inputOffsetY(edge.toPort()));
        var type = from.kind().outputDefinitions().get(edge.fromPort()).type();
        orthogonalLine(graphics, x1, y1, x2, y2, portColor(type));
    }

    private void renderNode(GuiGraphicsExtractor graphics, PrecisionGraph.Node node, int mouseX, int mouseY) {
        var x = (int) Math.round(node.x());
        var y = (int) Math.round(node.y());
        var h = nodeHeight(node);
        var selected = node.id() == selectedNode;
        graphics.fill(x, y, x + NODE_W, y + h, selected ? 0xF02A4552 : 0xF0243039);
        border(graphics, x, y, NODE_W, h, selected ? ACCENT : 0xAA66818D);
        graphics.fill(x, y, x + NODE_W, y + NODE_HEADER_H, categoryColor(node.kind().category()));
        smallText(graphics, groupGlyph(node.kind().group()), x + 3, y + 2, 0xFF081015, 8);
        smallText(graphics, nodeLabel(node.kind()).getString(), x + 12, y + 2, 0xFF081015, NODE_W - 15);
        for (var port = 0; port < node.kind().inputDefinitions().size(); port++) {
            var definition = node.kind().inputDefinitions().get(port);
            var color = highlightedPort(node.id(), port, true, definition.type())
                    ? 0xFFFFFFFF : portColor(definition.type());
            renderPort(graphics, x, y + inputOffsetY(port), color,
                    definition.type() == PrecisionGraph.PortType.FLOW
                            && !endpointConnected(new Endpoint(node.id(), port, true, definition.type())));
        }
        for (var port = 0; port < node.kind().outputDefinitions().size(); port++) {
            var definition = node.kind().outputDefinitions().get(port);
            var color = highlightedPort(node.id(), port, false, definition.type())
                    ? 0xFFFFFFFF : portColor(definition.type());
            renderPort(graphics, x + NODE_W, y + outputOffsetY(port), color,
                    definition.type() == PrecisionGraph.PortType.FLOW
                            && !endpointConnected(new Endpoint(node.id(), port, false, definition.type())));
        }
        if (insideNode(mouseX, mouseY, node)) {
            var lines = new ArrayList<Component>();
            lines.add(nodeLabel(node.kind()));
            lines.add(Component.translatable(nodeDescriptionKey(node.kind())).withColor(DIM));
            graphics.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
        }
    }

    private static void renderPort(
            GuiGraphicsExtractor graphics,
            int centerX,
            int centerY,
            int color,
            boolean openEnd
    ) {
        graphics.fill(centerX - 3, centerY - 3, centerX + 3, centerY + 3, color);
        if (openEnd) {
            graphics.fill(centerX - 1, centerY - 1, centerX + 2, centerY + 2, 0xFF182229);
        }
    }

    private void renderConnectionPreview(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (connection == null) return;
        var anchor = endpointScreen(connection.endpoint);
        var target = snappedEndpoint(mouseX, mouseY, connection.endpoint.type, connection.endpoint.input);
        var endX = target == null ? mouseX : endpointScreen(target).x;
        var endY = target == null ? mouseY : endpointScreen(target).y;
        var color = target != null && connectionCreatesCycle(connection.endpoint, target)
                ? ERROR : portColor(connection.endpoint.type);
        orthogonalLine(graphics, anchor.x, anchor.y, endX, endY, color);
    }

    private void renderPortTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        var endpoint = endpointAt(mouseX, mouseY, null, null);
        if (endpoint == null || endpoint.type != PrecisionGraph.PortType.FLOW) return;
        graphics.setComponentTooltipForNextFrame(font, List.of(
                flowPortLabel(endpoint),
                Component.translatable("screen.academy.precision_operation.flow_open_chain_hint").withColor(DIM)
        ), mouseX, mouseY);
    }

    private void renderInspector(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        var x = inspectorX();
        var w = inspectorWidth();
        smallText(graphics, Component.translatable("screen.academy.precision_operation.inspector").getString(),
                x + 5, canvasY + 5, DIM, w - 10);
        var selected = node(selectedNode);
        if (selected == null) {
            smallText(graphics, Component.translatable("screen.academy.precision_operation.no_selection").getString(),
                    x + 5, canvasY + 22, DIM, w - 10);
            return;
        }
        smallText(graphics, nodeLabel(selected.kind()).getString(), x + 5, canvasY + 22, TEXT, w - 10);
        var description = Component.translatable(nodeDescriptionKey(selected.kind()));
        var descriptionHeight = smallWrappedText(graphics, description, x + 5, canvasY + 36, DIM, w - 10);
        var parameterY = canvasY + Math.min(92, Math.max(58, descriptionHeight + 40));
        renderParameterEditor(graphics, selected, x + 5, parameterY, w - 10, mouseX, mouseY);
        var inputsY = parameterY + 30;
        smallText(graphics, Component.translatable("screen.academy.precision_operation.ports").getString(),
                x + 5, inputsY, DIM, w - 10);
        var y = inputsY + 11;
        for (var index = 0; index < selected.kind().inputDefinitions().size(); index++) {
            var port = selected.kind().inputDefinitions().get(index);
            var endpoint = new Endpoint(selected.id(), index, true, port.type());
            smallText(graphics, "< " + portLabel(endpoint, port).getString(),
                    x + 7, y, portColor(port.type()), w - 12);
            y += 9;
        }
        for (var index = 0; index < selected.kind().outputDefinitions().size(); index++) {
            var port = selected.kind().outputDefinitions().get(index);
            var endpoint = new Endpoint(selected.id(), index, false, port.type());
            smallText(graphics, "> " + portLabel(endpoint, port).getString(),
                    x + 7, y, portColor(port.type()), w - 12);
            y += 9;
        }
    }

    private void renderParameterEditor(
            GuiGraphicsExtractor graphics,
            PrecisionGraph.Node node,
            int x,
            int y,
            int width,
            int mouseX,
            int mouseY
    ) {
        var kind = node.kind().parameterKind();
        if (kind == PrecisionGraph.ParameterKind.NONE) return;
        smallText(graphics, Component.translatable("screen.academy.precision_operation.parameter",
                formatParameter(node)).getString(), x, y, TEXT, width);
        if (kind == PrecisionGraph.ParameterKind.RANGE
                || kind == PrecisionGraph.ParameterKind.HEALTH_PERCENT) {
            var min = 1.0;
            var max = kind == PrecisionGraph.ParameterKind.RANGE ? 32.0 : 100.0;
            var trackY = y + 13;
            graphics.fill(x, trackY, x + width, trackY + 2, 0xFF40535D);
            var knob = x + (int) Math.round((node.parameter() - min) / (max - min) * (width - 4));
            graphics.fill(knob, trackY - 2, knob + 4, trackY + 4, ACCENT);
        } else {
            iconButton(graphics, x, y + 11, "<", mouseX, mouseY, false);
            iconButton(graphics, x + 18, y + 11, ">", mouseX, mouseY, false);
        }
    }

    private void renderStatus(GuiGraphicsExtractor graphics) {
        var shown = System.currentTimeMillis() < transientUntil ? transientDiagnostic
                : diagnostic != PrecisionGraph.Diagnostic.OK ? diagnostic : graph.validate().diagnostic();
        smallText(graphics, Component.translatable(shown.translationKey()).getString(),
                panelX + 4, panelY + panelH - 11,
                shown == PrecisionGraph.Diagnostic.OK ? 0xFF7FD8A0 : ERROR, panelW - 8);
    }

    private void renderQuickInsert(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (quickInsert == null) return;
        var rows = Math.min(12, quickInsert.kinds.size());
        var w = 112;
        var h = rows * ROW_H + 4;
        var x = Math.clamp(quickInsert.x, panelX + 2, panelX + panelW - w - 2);
        var y = Math.clamp(quickInsert.y, canvasY + 2, canvasY + canvasH - h - 2);
        graphics.fill(x, y, x + w, y + h, 0xFA17232B);
        border(graphics, x, y, w, h, BORDER);
        for (var row = 0; row < rows; row++) {
            var kind = quickInsert.kinds.get(row);
            var rowY = y + 2 + row * ROW_H;
            var hover = inside(mouseX, mouseY, x + 2, rowY, w - 4, ROW_H - 1);
            if (hover) graphics.fill(x + 2, rowY, x + w - 2, rowY + ROW_H - 1, 0x664FA6C4);
            smallText(graphics, nodeLabel(kind).getString(), x + 5, rowY + 3, TEXT, w - 10);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        syncSerializedLayout();
        var x = event.x();
        var y = event.y();
        if (event.button() == 0) {
            if (handleQuickInsertClick(x, y) || handleTopBarClick(x, y) || handleRailClick(x, y)
                    || handleInspectorClick(x, y) || handlePaletteClick(x, y)) return true;
            if (spaceDown && inside(x, y, canvasX, canvasY, canvasW, canvasH)) {
                panning = true;
                return true;
            }
            if (handleCanvasClick(x, y)) return true;
        }
        if ((event.button() == 1 || event.button() == 2)
                && inside(x, y, canvasX, canvasY, canvasW, canvasH)) {
            var endpoint = endpointAt(x, y, null, null);
            if (event.button() == 1 && endpoint != null) {
                disconnect(endpoint);
            } else {
                panning = true;
            }
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (connection != null) return true;
        if (draggingNode != null) {
            var selected = node(draggingNode);
            if (selected != null) replaceNode(new PrecisionGraph.Node(
                    selected.id(), selected.kind(), selected.parameter(),
                    selected.x() + dragX / zoom, selected.y() + dragY / zoom
            ), false);
            return true;
        }
        if (panning) {
            panX += dragX;
            panY += dragY;
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (connection != null && event.button() == 0) {
            finishConnection(event.x(), event.y());
            draggingNode = null;
            return true;
        }
        if (draggingNode != null) changed();
        draggingNode = null;
        panning = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (paletteVisible() && inside(mouseX, mouseY, paletteX(), canvasY, paletteWidth(), canvasH)) {
            paletteScroll = Math.max(0, paletteScroll - (int) Math.signum(scrollY));
            return true;
        }
        if (inside(mouseX, mouseY, canvasX, canvasY, canvasW, canvasH)) {
            zoomAt(mouseX, mouseY, zoom + Math.signum(scrollY) * 0.1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_SPACE) {
            spaceDown = true;
            return true;
        }
        if (event.key() == InputConstants.KEY_ESCAPE && (connection != null || quickInsert != null)) {
            connection = null;
            quickInsert = null;
            return true;
        }
        if (event.key() == InputConstants.KEY_DELETE) {
            deleteSelected();
            return true;
        }
        if ((event.modifiers() & InputConstants.MOD_CONTROL) != 0) {
            if (event.key() == InputConstants.KEY_C) {
                copySelected();
                return true;
            }
            if (event.key() == InputConstants.KEY_Z) {
                undo();
                return true;
            }
            if (event.key() == InputConstants.KEY_Y) {
                redo();
                return true;
            }
            if (event.key() == InputConstants.KEY_S) {
                save();
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        if (event.key() == InputConstants.KEY_SPACE) {
            spaceDown = false;
            panning = false;
            return true;
        }
        return super.keyReleased(event);
    }

    @Override
    public void onClose() {
        PrecisionOperationClient.updateLocal(slot, graph);
        PrecisionOperationClient.closed(this);
        super.onClose();
    }

    @Override
    public String debugLayoutId() {
        return serializedLayoutId;
    }

    @Override
    public FrameLayoutWidget debugLayoutRoot() {
        return serializedLayout;
    }

    private boolean handleTopBarClick(double mouseX, double mouseY) {
        var x = panelX + 4 + (panelW >= 620 ? 96 : 0);
        for (var index = 0; index < 4; index++) {
            if (inside(mouseX, mouseY, x, panelY + 2, 38, 16)) {
                PrecisionOperationClient.updateLocal(slot, graph);
                slot = index;
                PrecisionOperationClient.selectSlot(slot);
                setGraph(PrecisionOperationClient.graph(slot), false);
                revision = PrecisionOperationClient.revision();
                fitCanvas(true);
                return true;
            }
            x += 40;
        }
        var toolsX = panelX + panelW - TOOL_LABELS.length * (TOOL_SIZE + 2) - 2;
        for (var index = 0; index < TOOL_LABELS.length; index++) {
            if (inside(mouseX, mouseY, toolsX, panelY + 3, TOOL_SIZE, TOOL_SIZE)) {
                switch (index) {
                    case 0 -> deleteSelected();
                    case 1 -> copySelected();
                    case 2 -> undo();
                    case 3 -> redo();
                    case 4 -> autoLayout();
                    case 5 -> fitCanvas(false);
                    case 6 -> save();
                    case 7 -> setGraph(PrecisionOperationClient.serverGraph(slot), true);
                    default -> {
                    }
                }
                return true;
            }
            toolsX += TOOL_SIZE + 2;
        }
        return false;
    }

    private boolean handleRailClick(double mouseX, double mouseY) {
        if (compactLeft && inside(mouseX, mouseY, leftX + 1, canvasY + 2, 16, 16)) {
            leftDrawerOpen = !leftDrawerOpen;
            if (leftDrawerOpen) rightDrawerOpen = false;
            updateSearchBounds();
            return true;
        }
        if (compactRight && inside(mouseX, mouseY, rightX + 1, canvasY + 2, 16, 16)) {
            rightDrawerOpen = !rightDrawerOpen;
            if (rightDrawerOpen) leftDrawerOpen = false;
            updateSearchBounds();
            return true;
        }
        return false;
    }

    private boolean handlePaletteClick(double mouseX, double mouseY) {
        if (!paletteVisible()) return false;
        var x = paletteX();
        var w = paletteWidth();
        var tabY = canvasY + 16;
        var tabW = Math.max(16, (w - 8) / PrecisionGraph.NodeGroup.values().length);
        for (var index = 0; index < PrecisionGraph.NodeGroup.values().length; index++) {
            if (inside(mouseX, mouseY, x + 4 + index * tabW, tabY, tabW - 1, 12)) {
                selectedGroup = PrecisionGraph.NodeGroup.values()[index];
                paletteScroll = 0;
                return true;
            }
        }
        var listY = canvasY + 44;
        if (!inside(mouseX, mouseY, x + 3, listY, w - 6, canvasH - 47)) return false;
        var row = (int) ((mouseY - listY) / ROW_H);
        var kinds = visibleKinds();
        var index = paletteScroll + row;
        if (index >= 0 && index < kinds.size()) {
            addNode(kinds.get(index), screenToGraphX(canvasX + canvasW / 2.0),
                    screenToGraphY(canvasY + canvasH / 2.0), true);
            return true;
        }
        return false;
    }

    private boolean handleInspectorClick(double mouseX, double mouseY) {
        if (!inspectorVisible()) return false;
        var selected = node(selectedNode);
        if (selected == null || selected.kind().parameterKind() == PrecisionGraph.ParameterKind.NONE) return false;
        var x = inspectorX() + 5;
        var w = inspectorWidth() - 10;
        var description = Component.translatable(nodeDescriptionKey(selected.kind()));
        var descriptionHeight = (int) Math.ceil(font.wordWrapHeight(
                description, (int) (w / SMALL_TEXT_SCALE)) * SMALL_TEXT_SCALE);
        var y = canvasY + Math.min(92, Math.max(58, descriptionHeight + 40));
        var parameterKind = selected.kind().parameterKind();
        if ((parameterKind == PrecisionGraph.ParameterKind.RANGE
                || parameterKind == PrecisionGraph.ParameterKind.HEALTH_PERCENT)
                && inside(mouseX, mouseY, x, y + 9, w, 12)) {
            var max = parameterKind == PrecisionGraph.ParameterKind.RANGE ? 32.0 : 100.0;
            var value = 1.0 + Math.clamp((mouseX - x) / Math.max(1.0, w), 0.0, 1.0) * (max - 1.0);
            setParameter(selected, Math.rint(value));
            return true;
        }
        if (inside(mouseX, mouseY, x, y + 11, TOOL_SIZE, TOOL_SIZE)) {
            adjustParameter(selected, -1);
            return true;
        }
        if (inside(mouseX, mouseY, x + 18, y + 11, TOOL_SIZE, TOOL_SIZE)) {
            adjustParameter(selected, 1);
            return true;
        }
        return false;
    }

    private boolean handleCanvasClick(double mouseX, double mouseY) {
        if (!inside(mouseX, mouseY, canvasX, canvasY, canvasW, canvasH)) return false;
        quickInsert = null;
        var endpoint = endpointAt(mouseX, mouseY, null, null);
        if (endpoint != null) {
            if (connection != null && compatible(connection.endpoint, endpoint)) {
                connect(connection.endpoint, endpoint);
                connection = null;
            } else {
                connection = new ConnectionDrag(endpoint, mouseX, mouseY);
                selectedNode = endpoint.nodeId;
            }
            return true;
        }
        for (var node : reversed(graph.nodes())) {
            if (insideHeader(mouseX, mouseY, node)) {
                selectedNode = node.id();
                pushUndo();
                draggingNode = node.id();
                return true;
            }
            if (insideNode(mouseX, mouseY, node)) {
                selectedNode = node.id();
                connection = null;
                return true;
            }
        }
        selectedNode = -1;
        connection = null;
        return true;
    }

    private void finishConnection(double mouseX, double mouseY) {
        if (connection == null) return;
        var moved = Math.hypot(mouseX - connection.startX, mouseY - connection.startY) > 3.0;
        var target = snappedEndpoint(mouseX, mouseY, connection.endpoint.type, connection.endpoint.input);
        if (target != null && compatible(connection.endpoint, target)) {
            connect(connection.endpoint, target);
            connection = null;
            return;
        }
        if (moved) {
            var candidates = compatibleKinds(connection.endpoint).stream().limit(12).toList();
            if (!candidates.isEmpty()) quickInsert = new QuickInsert((int) mouseX, (int) mouseY, candidates, connection.endpoint);
            connection = null;
        }
    }

    private boolean handleQuickInsertClick(double mouseX, double mouseY) {
        if (quickInsert == null) return false;
        var rows = Math.min(12, quickInsert.kinds.size());
        var w = 112;
        var h = rows * ROW_H + 4;
        var x = Math.clamp(quickInsert.x, panelX + 2, panelX + panelW - w - 2);
        var y = Math.clamp(quickInsert.y, canvasY + 2, canvasY + canvasH - h - 2);
        if (!inside(mouseX, mouseY, x, y, w, h)) {
            quickInsert = null;
            return false;
        }
        var row = (int) ((mouseY - y - 2) / ROW_H);
        if (row >= 0 && row < rows) {
            var kind = quickInsert.kinds.get(row);
            var anchor = quickInsert.anchor;
            var node = addNode(kind, screenToGraphX(quickInsert.x), screenToGraphY(quickInsert.y), false);
            if (node != null) {
                var endpoint = firstCompatibleEndpoint(node, anchor);
                if (endpoint != null) connect(anchor, endpoint);
            }
            quickInsert = null;
            return true;
        }
        return true;
    }

    private void connect(Endpoint first, Endpoint second) {
        var output = first.input ? second : first;
        var input = first.input ? first : second;
        if (output.input || !input.input || output.type != input.type || output.nodeId == input.nodeId) {
            showTransient(PrecisionGraph.Diagnostic.TYPE_MISMATCH);
            return;
        }
        var candidate = graphWithConnection(output, input);
        var result = candidate.validate();
        if (result.diagnostic() == PrecisionGraph.Diagnostic.FLOW_CYCLE
                || result.diagnostic() == PrecisionGraph.Diagnostic.CYCLE) {
            showTransient(result.diagnostic());
            return;
        }
        pushUndo();
        graph = candidate;
        changed();
    }

    private PrecisionGraph graphWithConnection(Endpoint output, Endpoint input) {
        var edges = new ArrayList<>(graph.edges());
        edges.removeIf(edge -> edge.toNode() == input.nodeId && edge.toPort() == input.port);
        if (output.type == PrecisionGraph.PortType.FLOW) {
            edges.removeIf(edge -> edge.fromNode() == output.nodeId && edge.fromPort() == output.port);
        }
        edges.add(new PrecisionGraph.Edge(output.nodeId, output.port, input.nodeId, input.port));
        return new PrecisionGraph(graph.nodes(), edges);
    }

    private void disconnect(Endpoint endpoint) {
        var filtered = graph.edges().stream().filter(edge -> endpoint.input
                ? edge.toNode() != endpoint.nodeId || edge.toPort() != endpoint.port
                : edge.fromNode() != endpoint.nodeId || edge.fromPort() != endpoint.port).toList();
        if (filtered.size() == graph.edges().size()) return;
        pushUndo();
        graph = new PrecisionGraph(graph.nodes(), filtered);
        changed();
    }

    private PrecisionGraph.Node addNode(PrecisionGraph.NodeKind kind, double x, double y, boolean autoChain) {
        if (graph.nodes().size() >= PrecisionGraph.MAX_NODES) {
            showTransient(PrecisionGraph.Diagnostic.TOO_MANY_NODES);
            return null;
        }
        var id = graph.nodes().stream().mapToInt(PrecisionGraph.Node::id).max().orElse(-1) + 1;
        var node = new PrecisionGraph.Node(id, kind, kind.defaultParameter(), x, y);
        pushUndo();
        var nodes = new ArrayList<>(graph.nodes());
        nodes.add(node);
        var edges = new ArrayList<>(graph.edges());
        if (autoChain && kind.isAction()) {
            tailAction().ifPresent(tail -> edges.add(new PrecisionGraph.Edge(
                    tail.id(), tail.kind().flowOutputPort(), node.id(), kind.flowInputPort())));
        }
        graph = new PrecisionGraph(nodes, edges);
        selectedNode = id;
        changed();
        return node;
    }

    private java.util.Optional<PrecisionGraph.Node> tailAction() {
        var sources = graph.edges().stream()
                .filter(edge -> {
                    var source = node(edge.fromNode());
                    return source != null && source.kind().isAction()
                            && source.kind().outputDefinitions().get(edge.fromPort()).type() == PrecisionGraph.PortType.FLOW;
                })
                .map(PrecisionGraph.Edge::fromNode)
                .collect(java.util.stream.Collectors.toSet());
        return graph.nodes().stream().filter(node -> node.kind().isAction())
                .filter(node -> !sources.contains(node.id()))
                .min(Comparator.comparingInt(PrecisionGraph.Node::id));
    }

    private void deleteSelected() {
        var selected = node(selectedNode);
        if (selected == null) return;
        pushUndo();
        PrecisionGraph.Edge before = null;
        PrecisionGraph.Edge after = null;
        if (selected.kind().isAction()) {
            for (var edge : graph.edges()) {
                if (edge.toNode() == selected.id() && edge.toPort() == selected.kind().flowInputPort()) before = edge;
                if (edge.fromNode() == selected.id() && edge.fromPort() == selected.kind().flowOutputPort()) after = edge;
            }
        }
        var edges = new ArrayList<>(graph.edges().stream().filter(edge ->
                edge.fromNode() != selected.id() && edge.toNode() != selected.id()).toList());
        if (before != null && after != null) {
            var previous = node(before.fromNode());
            var next = node(after.toNode());
            if (previous != null && next != null) edges.add(new PrecisionGraph.Edge(
                    previous.id(), previous.kind().flowOutputPort(), next.id(), next.kind().flowInputPort()));
        }
        graph = new PrecisionGraph(
                graph.nodes().stream().filter(node -> node.id() != selected.id()).toList(), edges);
        selectedNode = -1;
        changed();
    }

    private void copySelected() {
        var selected = node(selectedNode);
        if (selected == null) return;
        addNode(selected.kind(), selected.x() + 18, selected.y() + 18, selected.kind().isAction());
        var added = node(selectedNode);
        if (added != null && added.parameter() != selected.parameter()) {
            replaceNode(new PrecisionGraph.Node(added.id(), added.kind(), selected.parameter(), added.x(), added.y()), true);
        }
    }

    private void autoLayout() {
        if (graph.nodes().isEmpty()) return;
        pushUndo();
        var incoming = new HashMap<Integer, List<Integer>>();
        for (var edge : graph.edges()) {
            var from = node(edge.fromNode());
            if (from == null || from.kind().isAction()) continue;
            incoming.computeIfAbsent(edge.toNode(), _ -> new ArrayList<>()).add(edge.fromNode());
        }
        var layers = new HashMap<Integer, Integer>();
        for (var pass = 0; pass < graph.nodes().size(); pass++) {
            for (var node : graph.nodes()) {
                if (node.kind().isAction()) continue;
                var layer = incoming.getOrDefault(node.id(), List.of()).stream()
                        .mapToInt(id -> layers.getOrDefault(id, 0) + 1).max().orElse(0);
                layers.put(node.id(), layer);
            }
        }
        var rows = new HashMap<Integer, Integer>();
        var replacement = new ArrayList<PrecisionGraph.Node>();
        for (var node : graph.nodes().stream().filter(node -> !node.kind().isAction())
                .sorted(Comparator.comparingInt(node -> layers.getOrDefault(node.id(), 0))).toList()) {
            var layer = layers.getOrDefault(node.id(), 0);
            var row = rows.merge(layer, 1, Integer::sum) - 1;
            replacement.add(new PrecisionGraph.Node(node.id(), node.kind(), node.parameter(),
                    8 + layer * 100.0, 8 + row * 42.0));
        }
        var actions = orderedActions();
        for (var index = 0; index < actions.size(); index++) {
            var node = actions.get(index);
            replacement.add(new PrecisionGraph.Node(node.id(), node.kind(), node.parameter(),
                    8 + (layers.values().stream().mapToInt(Integer::intValue).max().orElse(0) + 1) * 100.0,
                    8 + index * 50.0));
        }
        graph = new PrecisionGraph(replacement, graph.edges());
        changed();
        fitCanvas(false);
    }

    private List<PrecisionGraph.Node> orderedActions() {
        var validation = graph.validate();
        if (validation.valid()) {
            var byId = graph.nodes().stream().collect(java.util.stream.Collectors.toMap(
                    PrecisionGraph.Node::id, node -> node));
            return validation.actionOrder().stream().map(byId::get).toList();
        }
        return graph.nodes().stream().filter(node -> node.kind().isAction())
                .sorted(Comparator.comparingInt(PrecisionGraph.Node::id)).toList();
    }

    private void fitCanvas(boolean initial) {
        if (graph.nodes().isEmpty()) {
            zoom = 1.0;
            panX = 0.0;
            panY = 0.0;
            return;
        }
        var minX = graph.nodes().stream().mapToDouble(PrecisionGraph.Node::x).min().orElse(0.0);
        var minY = graph.nodes().stream().mapToDouble(PrecisionGraph.Node::y).min().orElse(0.0);
        var maxX = graph.nodes().stream().mapToDouble(node -> node.x() + NODE_W).max().orElse(NODE_W);
        var maxY = graph.nodes().stream().mapToDouble(node -> node.y() + nodeHeight(node)).max().orElse(MIN_NODE_H);
        var fit = Math.min((canvasW - 20.0) / Math.max(1.0, maxX - minX),
                (canvasH - 20.0) / Math.max(1.0, maxY - minY));
        zoom = Math.clamp(fit, initial ? 0.65 : MIN_ZOOM, initial ? 1.0 : MAX_ZOOM);
        panX = (canvasW - (minX + maxX) * zoom) / 2.0;
        panY = (canvasH - (minY + maxY) * zoom) / 2.0;
    }

    private void zoomAt(double mouseX, double mouseY, double requested) {
        var view = PrecisionEditorGeometry.zoomAt(
                mouseX, mouseY, canvasX, canvasY, panX, panY, zoom, requested);
        panX = view.panX();
        panY = view.panY();
        zoom = view.zoom();
    }

    private void adjustParameter(PrecisionGraph.Node node, int direction) {
        var kind = node.kind().parameterKind();
        var max = switch (kind) {
            case COUNT -> 8;
            case CAPABILITY -> org.academy.api.common.entitycontrol.ControlCapability.values().length - 1;
            case SORT_DIRECTION -> 1;
            case ENTITY_TYPE -> 3;
            default -> Integer.MAX_VALUE;
        };
        var min = kind == PrecisionGraph.ParameterKind.COUNT ? 1 : 0;
        var value = node.parameter() + direction;
        if (kind == PrecisionGraph.ParameterKind.CAPABILITY
                || kind == PrecisionGraph.ParameterKind.SORT_DIRECTION
                || kind == PrecisionGraph.ParameterKind.ENTITY_TYPE) {
            if (value < min) value = max;
            if (value > max) value = min;
        }
        setParameter(node, value);
    }

    private void setParameter(PrecisionGraph.Node node, double value) {
        if (!node.kind().isParameterValid(value)) return;
        pushUndo();
        replaceNode(new PrecisionGraph.Node(node.id(), node.kind(), value, node.x(), node.y()), true);
    }

    private void undo() {
        if (undo.isEmpty()) return;
        redo.push(graph);
        graph = undo.pop();
        changed();
    }

    private void redo() {
        if (redo.isEmpty()) return;
        undo.push(graph);
        graph = redo.pop();
        changed();
    }

    private void save() {
        var validation = graph.validate();
        if (!validation.valid()) {
            showTransient(validation.diagnostic());
            return;
        }
        PrecisionOperationClient.save(slot, graph, revision);
    }

    private void pushUndo() {
        undo.push(graph);
        while (undo.size() > 64) undo.removeLast();
        redo.clear();
    }

    private void setGraph(PrecisionGraph next, boolean recordUndo) {
        if (recordUndo) pushUndo();
        graph = next == null ? PrecisionGraph.EMPTY : next;
        selectedNode = -1;
        connection = null;
        quickInsert = null;
        changed();
    }

    private void replaceNode(PrecisionGraph.Node replacement, boolean notify) {
        graph = new PrecisionGraph(graph.nodes().stream().map(node ->
                node.id() == replacement.id() ? replacement : node).toList(), graph.edges());
        if (notify) changed();
    }

    private void changed() {
        diagnostic = graph.validate().diagnostic();
        PrecisionOperationClient.updateLocal(slot, graph);
    }

    private List<PrecisionGraph.NodeKind> visibleKinds() {
        var query = search == null ? "" : search.getValue().strip().toLowerCase(Locale.ROOT);
        return java.util.Arrays.stream(PrecisionGraph.NodeKind.values()).filter(kind -> {
            if (query.isEmpty()) return kind.group() == selectedGroup;
            var label = nodeLabel(kind).getString().toLowerCase(Locale.ROOT);
            var description = Component.translatable(nodeDescriptionKey(kind)).getString().toLowerCase(Locale.ROOT);
            var group = Component.translatable(groupKey(kind.group())).getString().toLowerCase(Locale.ROOT);
            return label.contains(query) || description.contains(query) || group.contains(query)
                    || kind.name().toLowerCase(Locale.ROOT).contains(query);
        }).toList();
    }

    private List<PrecisionGraph.NodeKind> compatibleKinds(Endpoint anchor) {
        return java.util.Arrays.stream(PrecisionGraph.NodeKind.values())
                .filter(kind -> anchor.input
                        ? kind.outputDefinitions().stream().anyMatch(port -> port.type() == anchor.type)
                        : kind.inputDefinitions().stream().anyMatch(port -> port.type() == anchor.type))
                .toList();
    }

    private Endpoint firstCompatibleEndpoint(PrecisionGraph.Node node, Endpoint anchor) {
        if (anchor.input) {
            for (var port = 0; port < node.kind().outputDefinitions().size(); port++) {
                if (node.kind().outputDefinitions().get(port).type() == anchor.type) {
                    return new Endpoint(node.id(), port, false, anchor.type);
                }
            }
        } else {
            for (var port = 0; port < node.kind().inputDefinitions().size(); port++) {
                if (node.kind().inputDefinitions().get(port).type() == anchor.type) {
                    return new Endpoint(node.id(), port, true, anchor.type);
                }
            }
        }
        return null;
    }

    private Endpoint endpointAt(double mouseX, double mouseY, PrecisionGraph.PortType type, Boolean input) {
        Endpoint closest = null;
        var best = Double.MAX_VALUE;
        for (var node : graph.nodes()) {
            if (input == null || input) {
                for (var port = 0; port < node.kind().inputDefinitions().size(); port++) {
                    var definition = node.kind().inputDefinitions().get(port);
                    if (type != null && definition.type() != type) continue;
                    var endpoint = new Endpoint(node.id(), port, true, definition.type());
                    var point = endpointScreen(endpoint);
                    var distance = Math.hypot(mouseX - point.x, mouseY - point.y);
                    if (distance <= PORT_HIT / 2.0 && distance < best) {
                        closest = endpoint;
                        best = distance;
                    }
                }
            }
            if (input == null || !input) {
                for (var port = 0; port < node.kind().outputDefinitions().size(); port++) {
                    var definition = node.kind().outputDefinitions().get(port);
                    if (type != null && definition.type() != type) continue;
                    var endpoint = new Endpoint(node.id(), port, false, definition.type());
                    var point = endpointScreen(endpoint);
                    var distance = Math.hypot(mouseX - point.x, mouseY - point.y);
                    if (distance <= PORT_HIT / 2.0 && distance < best) {
                        closest = endpoint;
                        best = distance;
                    }
                }
            }
        }
        return closest;
    }

    private Endpoint snappedEndpoint(double mouseX, double mouseY, PrecisionGraph.PortType type, boolean sourceInput) {
        Endpoint closest = null;
        var best = Double.MAX_VALUE;
        for (var node : graph.nodes()) {
            var definitions = sourceInput ? node.kind().outputDefinitions() : node.kind().inputDefinitions();
            for (var port = 0; port < definitions.size(); port++) {
                var definition = definitions.get(port);
                if (definition.type() != type) continue;
                var endpoint = new Endpoint(node.id(), port, !sourceInput, type);
                var point = endpointScreen(endpoint);
                var distance = Math.hypot(mouseX - point.x, mouseY - point.y);
                if (distance <= SNAP_DISTANCE && distance < best) {
                    closest = endpoint;
                    best = distance;
                }
            }
        }
        return closest;
    }

    private boolean compatible(Endpoint first, Endpoint second) {
        return first.nodeId != second.nodeId && first.input != second.input && first.type == second.type;
    }

    private boolean connectionCreatesCycle(Endpoint first, Endpoint second) {
        if (!compatible(first, second)) return false;
        var output = first.input ? second : first;
        var input = first.input ? first : second;
        var result = graphWithConnection(output, input).validate().diagnostic();
        return result == PrecisionGraph.Diagnostic.FLOW_CYCLE || result == PrecisionGraph.Diagnostic.CYCLE;
    }

    private boolean endpointConnected(Endpoint endpoint) {
        return graph.edges().stream().anyMatch(edge -> endpoint.input
                ? edge.toNode() == endpoint.nodeId && edge.toPort() == endpoint.port
                : edge.fromNode() == endpoint.nodeId && edge.fromPort() == endpoint.port);
    }

    private Component portLabel(Endpoint endpoint, PrecisionGraph.PortDefinition definition) {
        return definition.type() == PrecisionGraph.PortType.FLOW
                ? flowPortLabel(endpoint)
                : Component.translatable(portKey(definition.key()));
    }

    private Component flowPortLabel(Endpoint endpoint) {
        var position = graph.flowPosition(endpoint.nodeId);
        var suffix = endpoint.input
                ? position.isOpenInput() ? "flow_input_open" : "flow_input"
                : position.isOpenOutput() ? "flow_output_open" : "flow_output";
        return Component.translatable(portKey(suffix));
    }

    private boolean highlightedPort(int nodeId, int port, boolean input, PrecisionGraph.PortType type) {
        if (connection == null || connection.endpoint.input == input || connection.endpoint.type != type) return false;
        return connection.endpoint.nodeId != nodeId;
    }

    private ScreenPoint endpointScreen(Endpoint endpoint) {
        var node = node(endpoint.nodeId);
        if (node == null) return new ScreenPoint(0, 0);
        var x = endpoint.input ? node.x() : node.x() + NODE_W;
        var y = node.y() + (endpoint.input ? inputOffsetY(endpoint.port) : outputOffsetY(endpoint.port));
        return new ScreenPoint(
                (int) Math.round(canvasX + panX + x * zoom),
                (int) Math.round(canvasY + panY + y * zoom)
        );
    }

    private boolean insideNode(double mouseX, double mouseY, PrecisionGraph.Node node) {
        var x = canvasX + panX + node.x() * zoom;
        var y = canvasY + panY + node.y() * zoom;
        return inside(mouseX, mouseY, x, y, NODE_W * zoom, nodeHeight(node) * zoom);
    }

    private boolean insideHeader(double mouseX, double mouseY, PrecisionGraph.Node node) {
        var x = canvasX + panX + node.x() * zoom;
        var y = canvasY + panY + node.y() * zoom;
        return inside(mouseX, mouseY, x, y, NODE_W * zoom, NODE_HEADER_H * zoom);
    }

    private int nodeHeight(PrecisionGraph.Node node) {
        return Math.max(MIN_NODE_H, NODE_HEADER_H
                + Math.max(node.kind().inputDefinitions().size(), node.kind().outputDefinitions().size()) * PORT_ROW_H + 3);
    }

    private static int inputOffsetY(int port) {
        return NODE_HEADER_H + 4 + port * PORT_ROW_H;
    }

    private static int outputOffsetY(int port) {
        return NODE_HEADER_H + 4 + port * PORT_ROW_H;
    }

    private double screenToGraphX(double screenX) {
        return PrecisionEditorGeometry.screenToGraph(screenX, canvasX, panX, zoom);
    }

    private double screenToGraphY(double screenY) {
        return PrecisionEditorGeometry.screenToGraph(screenY, canvasY, panY, zoom);
    }

    private int paletteX() {
        return compactLeft ? leftX + RAIL_W + 2 : leftX;
    }

    private int paletteWidth() {
        return compactLeft ? 104 : leftW;
    }

    private boolean paletteVisible() {
        return !compactLeft || leftDrawerOpen;
    }

    private int inspectorX() {
        return compactRight ? rightX - 128 - 2 : rightX;
    }

    private int inspectorWidth() {
        return compactRight ? 128 : rightW;
    }

    private boolean inspectorVisible() {
        return !compactRight || rightDrawerOpen;
    }

    private void updateSearchBounds() {
        if (search == null) return;
        search.visible = paletteVisible();
        search.setX(paletteX() + 4);
        search.setY(panelY + TOP_H + 25);
        search.setWidth(paletteWidth() - 8);
    }

    private String formatParameter(PrecisionGraph.Node node) {
        return switch (node.kind().parameterKind()) {
            case NONE -> "-";
            case RANGE -> Math.round(node.parameter()) + " m";
            case COUNT -> Long.toString(Math.round(node.parameter()));
            case HEALTH_PERCENT -> Math.round(node.parameter()) + "%";
            case SORT_DIRECTION -> Component.translatable(node.parameter() == 0.0
                    ? "screen.academy.precision_operation.value.near_first"
                    : "screen.academy.precision_operation.value.far_first").getString();
            case ENTITY_TYPE -> Component.translatable(
                    "screen.academy.precision_operation.value.entity_type." + (int) node.parameter()).getString();
            case CAPABILITY -> Component.translatable(
                    "screen.academy.precision_operation.value.capability." + (int) node.parameter()).getString();
        };
    }

    private PrecisionGraph.Node node(int id) {
        return graph.nodes().stream().filter(node -> node.id() == id).findFirst().orElse(null);
    }

    private Component nodeLabel(PrecisionGraph.NodeKind kind) {
        return Component.translatable("screen.academy.precision_operation.node."
                + kind.name().toLowerCase(Locale.ROOT));
    }

    private static String nodeDescriptionKey(PrecisionGraph.NodeKind kind) {
        return "screen.academy.precision_operation.node."
                + kind.name().toLowerCase(Locale.ROOT) + ".description";
    }

    private static String portKey(String key) {
        return "screen.academy.precision_operation.port." + key;
    }

    private static String groupKey(PrecisionGraph.NodeGroup group) {
        return "screen.academy.precision_operation.group." + group.name().toLowerCase(Locale.ROOT);
    }

    private static String groupGlyph(PrecisionGraph.NodeGroup group) {
        return switch (group) {
            case TARGET -> "T";
            case COLLECTION -> "S";
            case FILTER -> "F";
            case MENTAL_ACTION -> "M";
            case CONTROL_ACTION -> "C";
        };
    }

    private void showTransient(PrecisionGraph.Diagnostic shown) {
        transientDiagnostic = shown;
        transientUntil = System.currentTimeMillis() + 1800L;
    }

    private void smallText(
            GuiGraphicsExtractor graphics,
            String value,
            int x,
            int y,
            int color,
            int maxWidth
    ) {
        var clipped = font.plainSubstrByWidth(value, Math.max(1, (int) (maxWidth / SMALL_TEXT_SCALE)));
        var pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(SMALL_TEXT_SCALE, SMALL_TEXT_SCALE);
        graphics.text(font, clipped, 0, 0, color, false);
        pose.popMatrix();
    }

    private int smallWrappedText(
            GuiGraphicsExtractor graphics,
            Component value,
            int x,
            int y,
            int color,
            int maxWidth
    ) {
        var lines = font.split(value, Math.max(1, (int) (maxWidth / SMALL_TEXT_SCALE)));
        var pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(SMALL_TEXT_SCALE, SMALL_TEXT_SCALE);
        for (var index = 0; index < lines.size(); index++) {
            graphics.text(font, lines.get(index), 0, index * 9, color, false);
        }
        pose.popMatrix();
        return (int) Math.ceil(lines.size() * 9 * SMALL_TEXT_SCALE);
    }

    private void button(
            GuiGraphicsExtractor graphics,
            int x,
            int y,
            int width,
            int height,
            Component label,
            int mouseX,
            int mouseY,
            boolean selected,
            boolean small
    ) {
        var hover = inside(mouseX, mouseY, x, y, width, height);
        graphics.fill(x, y, x + width, y + height,
                selected ? 0xAA2E6D83 : hover ? 0x664FA6C4 : 0x22FFFFFF);
        border(graphics, x, y, width, height, selected || hover ? ACCENT : 0x5566818D);
        if (small) {
            smallText(graphics, label.getString(), x + 3, y + 5, TEXT, width - 6);
        } else {
            graphics.centeredText(font, font.plainSubstrByWidth(label.getString(), width - 3),
                    x + width / 2, y + Math.max(1, (height - 8) / 2), TEXT);
        }
    }

    private void iconButton(
            GuiGraphicsExtractor graphics,
            int x,
            int y,
            String glyph,
            int mouseX,
            int mouseY,
            boolean disabled
    ) {
        var hover = !disabled && inside(mouseX, mouseY, x, y, TOOL_SIZE, TOOL_SIZE);
        graphics.fill(x, y, x + TOOL_SIZE, y + TOOL_SIZE, disabled ? 0x112F3D45
                : hover ? 0x664FA6C4 : 0x223F5560);
        border(graphics, x, y, TOOL_SIZE, TOOL_SIZE, hover ? ACCENT : 0x5566818D);
        smallText(graphics, glyph, x + 4, y + 4, disabled ? 0x556F7C82 : TEXT, 8);
    }

    private static int categoryColor(PrecisionGraph.NodeCategory category) {
        return switch (category) {
            case SOURCE -> 0xFF5BA4C9;
            case COLLECTION -> 0xFFB69B62;
            case FILTER -> 0xFF78B77A;
            case ACTION -> 0xFFD89258;
            case CONTROL -> 0xFFC87686;
        };
    }

    private static int portColor(PrecisionGraph.PortType type) {
        return switch (type) {
            case ENTITY -> 0xFFFFCF70;
            case ENTITY_SET -> 0xFF75D7A7;
            case FLOW -> 0xFFDB82E8;
        };
    }

    private static void orthogonalLine(
            GuiGraphicsExtractor graphics,
            int x1,
            int y1,
            int x2,
            int y2,
            int color
    ) {
        var mid = (x1 + x2) / 2;
        line(graphics, x1, y1, mid, y1, color);
        line(graphics, mid, y1, mid, y2, color);
        line(graphics, mid, y2, x2, y2, color);
    }

    private static void line(GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2, int color) {
        graphics.fill(Math.min(x1, x2), Math.min(y1, y2), Math.max(x1, x2) + 1, Math.max(y1, y2) + 1, color);
    }

    private static void border(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }

    private static boolean inside(double x, double y, double left, double top, double width, double height) {
        return x >= left && x < left + width && y >= top && y < top + height;
    }

    private static Rect rect(Widget widget) {
        return new Rect(
                Math.round(widget.getAbsoluteX()),
                Math.round(widget.getAbsoluteY()),
                Math.round(widget.getWidth()),
                Math.round(widget.getHeight())
        );
    }

    private static <T> List<T> reversed(List<T> values) {
        var result = new ArrayList<>(values);
        java.util.Collections.reverse(result);
        return result;
    }

    private record Endpoint(int nodeId, int port, boolean input, PrecisionGraph.PortType type) {
    }

    private record ConnectionDrag(Endpoint endpoint, double startX, double startY) {
    }

    private record QuickInsert(int x, int y, List<PrecisionGraph.NodeKind> kinds, Endpoint anchor) {
    }

    private record ScreenPoint(int x, int y) {
    }

    private record Rect(int x, int y, int width, int height) {
    }
}
