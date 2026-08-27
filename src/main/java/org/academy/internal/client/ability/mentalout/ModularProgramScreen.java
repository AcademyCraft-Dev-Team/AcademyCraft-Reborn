package org.academy.internal.client.ability.mentalout;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.academy.AcademyCraft;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.gui.layout.Gravity;
import org.academy.api.client.gui.layout.SizeMode;
import org.academy.api.client.gui.environment.UiEnvironment;
import org.academy.api.client.gui.render.RenderContext;
import org.academy.api.client.gui.screen.UiScreen;
import org.academy.api.client.gui.widget.AbstractWidget;
import org.academy.api.client.gui.widget.EmptyWidget;
import org.academy.api.client.gui.widget.FrameLayoutWidget;
import org.academy.api.client.gui.widget.LabelWidget;
import org.academy.api.client.gui.widget.TextBoxWidget;
import org.academy.api.client.gui.widget.Widget;
import org.academy.api.common.ability.program.*;
import org.academy.internal.client.ability.program.ProgramConfigurationOptions;
import org.academy.internal.client.gui.SerializedUiLayout;
import org.academy.internal.client.gui.debug.SerializedUiDebugHost;
import org.academy.internal.client.ability.program.ProgramConfigurationOptions;
import org.academy.internal.client.ability.program.ProgramClipboardCodec;
import org.academy.internal.common.ability.ProficiencyPolicy;
import org.academy.internal.common.ability.AbilityCategoryNames;
import org.academy.internal.common.ability.ProficiencyPolicy;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.mentalout.precision.PrecisionGraph;
import org.academy.internal.common.ability.mentalout.precision.PrecisionOperationManager;
import org.academy.internal.common.ability.program.AbilityProgramDefinition;
import org.academy.internal.common.ability.program.AbilityProgramDefinitions;
import org.academy.internal.common.ability.program.CommonProgramNodeIds;
import org.academy.internal.common.ability.program.PrecisionProgramNodeIds;
import org.academy.internal.common.ability.program.ProgramEditorDocument;
import org.academy.internal.common.ability.program.ProgramEditorNodeCatalog;
import org.academy.internal.common.ability.program.ProgramPowerScale;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Named-port editor used by Precision Operation and backed directly by {@link AbilityProgram}.
 */
public final class ModularProgramScreen extends UiScreen implements SerializedUiDebugHost {
    private static final int NODE_W = 88;
    private static final int NODE_HEADER_H = 11;
    private static final int PORT_ROW_H = 8;
    private static final int NODE_CONFIGURATION_ROW_H = 7;
    private static final int CONFIGURATION_ROW_H = 27;
    private static final int MIN_NODE_H = 26;
    private static final double MIN_ZOOM = 0.5;
    private static final double MAX_ZOOM = 1.6;
    private static final int PANEL_BACKGROUND = 0x10000000;
    private static final int SECTION_BACKGROUND = 0x14000000;
    private static final int CANVAS_BACKGROUND = 0x28000000;
    private static final int CONTROL_BACKGROUND = 0x16000000;
    private static final int INPUT_BACKGROUND = 0x28000000;
    private static final int ROW_BACKGROUND = 0x14FFFFFF;
    private static final int HOVER_BACKGROUND = 0x2AFFFFFF;
    private static final int SELECTED_BACKGROUND = 0x30FFFFFF;
    private static final int NODE_BACKGROUND = 0xB00A0A0A;
    private static final int NODE_SELECTED_BACKGROUND = 0xC00D1720;
    private static final int NODE_HEADER = 0x24FFFFFF;
    private static final int POPUP_BACKGROUND = 0xE0101010;
    private static final int BORDER = 0xD9FFFFFF;
    private static final int BORDER_MUTED = 0x54FFFFFF;
    private static final int DIVIDER = 0x80FFFFFF;
    private static final int GRID_MINOR = 0x0FFFFFFF;
    private static final int GRID_MAJOR = 0x20FFFFFF;
    private static final int SELECTION_BACKGROUND = 0x20FFFFFF;
    private static final double SELECTION_DRAG_THRESHOLD = 3.0;
    private static final int DEFAULT_ACCENT = 0xFF1177D6;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int DIM = 0xBFFFFFFF;
    private static final int NODE_SECONDARY_TEXT = 0xE6FFFFFF;
    private static final int DISABLED = 0x33FFFFFF;
    private static final int ERROR = 0xFFFF5A66;
    private static final int ERROR_BACKGROUND = 0xB030090D;
    private static final int TOP_H = 20;
    private static final int RAIL_W = 18;
    private static final int ROW_H = 14;
    private static final int TOOL_SIZE = 14;
    private static final int PORT_HIT = 14;
    private static final int SNAP_DISTANCE = 10;
    private static final int PALETTE_TAB_OFFSET_Y = 16;
    private static final int PALETTE_SEARCH_OFFSET_Y = 31;
    private static final int PALETTE_LIST_OFFSET_Y = 49;
    private static final String[] TOOL_LABELS = {
            "delete", "copy", "paste", "undo", "redo", "auto_layout", "fit",
            "export", "import", "save", "restore"
    };
    private static final String[] TOOL_GLYPHS = {
            "X", "C", "P", "<", ">", "A", "F", "E", "I", "S", "R"
    };

    private final ArrayDeque<AbilityProgram> undo = new ArrayDeque<>();
    private final ArrayDeque<AbilityProgram> redo = new ArrayDeque<>();
    private final AbilityProgramDefinition definition;
    private final ProgramEditorNodeCatalog catalog;
    private final ModularProgramEditorSession session;
    private final Set<Identifier> capabilities;
    private final int accentColor;
    private ProgramEditorDocument document;
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
    private TextBoxWidget search;
    private final Map<String, TextBoxWidget> configurationInputs = new HashMap<>();
    private final Map<String, Boolean> configurationInputValidity = new HashMap<>();
    private int configurationNode = -1;
    private boolean updatingConfigurationInput;
    private String draggingPowerField;
    private int draggingPowerNode = -1;
    private ProgramEditorNodeCatalog.Group selectedGroup = ProgramEditorNodeCatalog.Group.TARGET;
    private int paletteScroll;
    private int selectedNode = -1;
    private final Set<Integer> selectedNodes = new LinkedHashSet<>();
    private Integer draggingNode;
    private SelectionDrag selectionDrag;
    private boolean panning;
    private boolean spaceDown;
    private double panX;
    private double panY;
    private double zoom = 1.0;
    private boolean initialView = true;
    private ConnectionDrag connection;
    private QuickInsert quickInsert;
    private PrecisionGraph.Diagnostic serverDiagnostic = PrecisionGraph.Diagnostic.OK;
    private int serverDiagnosticNode = -1;
    private PrecisionGraph.Diagnostic transientDiagnostic = PrecisionGraph.Diagnostic.OK;
    private long transientUntil;
    private Widget panelLayout;
    private Widget paletteLayout;
    private Widget canvasLayout;
    private Widget inspectorLayout;
    private FrameLayoutWidget serializedLayout;
    private String serializedLayoutId;
    private EditorSurface editorSurface;
    private FrameLayoutWidget inputLayer;
    private TooltipSurface tooltipSurface;

    public ModularProgramScreen(ModularProgramEditorSession session) {
        super(session.title());
        this.session = Objects.requireNonNull(session, "session");
        if (session.slotCount() < 1) {
            throw new IllegalArgumentException("Program editor session needs at least one slot");
        }
        this.slot = Math.clamp(session.selectedSlot(), 0, session.slotCount() - 1);
        var program = session.editableProgram(this.slot);
        accentColor = categoryAccent(program.category());
        definition = AbilityProgramDefinitions.require(program.category());
        catalog = definition.editorCatalog();
        capabilities = Set.copyOf(session.capabilities());
        document = document(program);
        revision = session.revision();
    }

    @Override
    protected void onInit() {
        var geometry = PrecisionEditorGeometry.layout(width, height);
        panelX = geometry.panelX();
        panelY = geometry.panelY();
        panelW = geometry.panelW();
        panelH = geometry.panelH();
        leftX = geometry.leftX();
        leftW = geometry.leftW();
        rightX = geometry.rightX();
        rightW = geometry.rightW();
        canvasX = geometry.canvasX();
        canvasY = geometry.canvasY();
        canvasW = geometry.canvasW();
        canvasH = geometry.canvasH();
        compactLeft = geometry.compactLeft();
        compactRight = geometry.compactRight();

        var variant = compactLeft ? "compact" : compactRight ? "medium" : "wide";
        serializedLayoutId = "precision_operation_" + variant;
        serializedLayout = SerializedUiLayout.INSTANCE.load(
                AcademyCraft.academy("ui/layout/" + serializedLayoutId + ".json"),
                List.of("panel", "palette", "canvas", "inspector", "title_accent"),
                () -> fallbackLayout(geometry)
        );
        getRoot().addChild("serialized_layout", serializedLayout);
        panelLayout = SerializedUiLayout.INSTANCE.require(serializedLayout, "panel");
        paletteLayout = SerializedUiLayout.INSTANCE.require(serializedLayout, "palette");
        canvasLayout = SerializedUiLayout.INSTANCE.require(serializedLayout, "canvas");
        inspectorLayout = SerializedUiLayout.INSTANCE.require(serializedLayout, "inspector");

        editorSurface = new EditorSurface();
        editorSurface.setCoverAllPrev(true);
        editorSurface.setLayoutParams(new FrameLayoutWidget.LayoutParams()
                .sizeMode(SizeMode.MATCH_PARENT));
        getRoot().addChild("editor_surface", editorSurface);

        inputLayer = new FrameLayoutWidget();
        inputLayer.setCoverAllPrev(true);
        inputLayer.setLayoutParams(new FrameLayoutWidget.LayoutParams()
                .sizeMode(SizeMode.MATCH_PARENT));
        getRoot().addChild("input_layer", inputLayer);

        search = new TextBoxWidget(64);
        search.setBaseFontSize(ProgramUiGraphics.BODY_FONT_SIZE);
        search.setPlaceholder(Component.translatable(
                "screen.academy.precision_operation.search").getString());
        search.setBackground(null);
        search.setCoverAllPrev(true);
        setTextColor(search, TEXT);
        setTextBoxBounds(search, paletteX() + 4, canvasY + PALETTE_SEARCH_OFFSET_Y,
                paletteWidth() - 8, 15);
        search.setVisibility(paletteVisible() ? Widget.Visibility.VISIBLE : Widget.Visibility.GONE);
        inputLayer.addChild("search_input", search);

        tooltipSurface = new TooltipSurface();
        tooltipSurface.setCoverAllPrev(true);
        tooltipSurface.setLayoutParams(new FrameLayoutWidget.LayoutParams()
                .sizeMode(SizeMode.MATCH_PARENT));
        getRoot().addChild("tooltip_surface", tooltipSurface);

        clearConfigurationInputs();
        if (initialView) {
            initialView = false;
            if (!nodes().isEmpty()) fitCanvas(true);
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
        slot.setLayoutParams(new FrameLayoutWidget.LayoutParams()
                .size(width, height).margin(x, y, 0, 0));
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

    public void applyServerState(int selectedSlot, AbilityProgram serverProgram, long serverRevision) {
        revision = serverRevision;
        if (slot != selectedSlot) return;
        setProgram(serverProgram, false);
    }

    public void applyProgramResult(
            int resultSlot,
            long serverRevision,
            ProgramDiagnosticCode diagnostic,
            int nodeId,
            boolean clearDiagnostic
    ) {
        revision = Math.max(revision, serverRevision);
        if (slot != resultSlot) return;
        if (diagnostic != null) {
            serverDiagnostic = mapDiagnostic(diagnostic);
            serverDiagnosticNode = nodeId;
            showTransient(serverDiagnostic);
        } else if (clearDiagnostic) {
            serverDiagnostic = PrecisionGraph.Diagnostic.OK;
            serverDiagnosticNode = -1;
        }
    }

    void applyResult(
            int resultSlot,
            PrecisionOperationManager.FeedbackType type,
            long serverRevision,
            PrecisionGraph.Diagnostic result,
            int nodeId,
            int port
    ) {
        revision = Math.max(revision, serverRevision);
        if (slot != resultSlot) return;
        if (type == PrecisionOperationManager.FeedbackType.ERROR) {
            serverDiagnostic = result;
            serverDiagnosticNode = nodeId;
            showTransient(result);
        } else if (type == PrecisionOperationManager.FeedbackType.STARTED
                || type == PrecisionOperationManager.FeedbackType.COMPLETED
                && session.diagnostic(slot) == PrecisionGraph.Diagnostic.OK) {
            serverDiagnostic = PrecisionGraph.Diagnostic.OK;
            serverDiagnosticNode = -1;
        }
    }

    @Override
    public void extractRenderState(
            GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        syncSerializedLayout();
        syncConfigurationInputs();
        if (editorSurface != null) editorSurface.invalidate();
        if (tooltipSurface != null) tooltipSurface.invalidate();
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private final class EditorSurface extends AbstractWidget {
        @Override
        protected void renderInternal(RenderContext context) {
            super.renderInternal(context);
            syncSerializedLayout();
            var minecraft = Minecraft.getInstance();
            var window = minecraft.getWindow();
            var mouseX = (int) Math.round(minecraft.mouseHandler.getScaledXPos(window));
            var mouseY = (int) Math.round(minecraft.mouseHandler.getScaledYPos(window));
            var graphics = new ProgramUiGraphics(context);
            renderStructure(graphics);
            renderTopBar(graphics, mouseX, mouseY);
            renderCanvas(graphics, mouseX, mouseY);
            renderDrawers(graphics);
            renderRails(graphics, mouseX, mouseY);
            if (paletteVisible()) renderPalette(graphics, mouseX, mouseY);
            if (inspectorVisible()) renderInspector(graphics, mouseX, mouseY);
            renderStatus(graphics);
            renderQuickInsert(graphics, mouseX, mouseY);
        }
    }

    private final class TooltipSurface extends AbstractWidget {
        @Override
        protected void renderInternal(RenderContext context) {
            super.renderInternal(context);
            var minecraft = Minecraft.getInstance();
            var window = minecraft.getWindow();
            var mouseX = (int) Math.round(minecraft.mouseHandler.getScaledXPos(window));
            var mouseY = (int) Math.round(minecraft.mouseHandler.getScaledYPos(window));
            renderTooltip(new ProgramUiGraphics(context), mouseX, mouseY);
        }
    }

    private void renderStructure(ProgramUiGraphics graphics) {
        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, PANEL_BACKGROUND);
        renderInstrumentFrame(graphics, panelX, panelY, panelW, panelH);
        graphics.fill(panelX + 7, panelY + TOP_H, panelX + panelW - 7, panelY + TOP_H + 1, DIVIDER);
        graphics.fill(panelX + 7, panelY + TOP_H, panelX + 31, panelY + TOP_H + 1, accentColor);
        graphics.fill(canvasX, canvasY, canvasX + canvasW, canvasY + canvasH, CANVAS_BACKGROUND);
        border(graphics, canvasX, canvasY, canvasW, canvasH, BORDER_MUTED);
        if (!compactLeft) renderSection(graphics, paletteX(), canvasY, paletteWidth(), canvasH);
        if (!compactRight) renderSection(graphics, inspectorX(), canvasY, inspectorWidth(), canvasH);
    }

    private void renderDrawers(ProgramUiGraphics graphics) {
        if (compactLeft && leftDrawerOpen) {
            renderSection(graphics, paletteX(), canvasY, paletteWidth(), canvasH);
        }
        if (compactRight && rightDrawerOpen) {
            renderSection(graphics, inspectorX(), canvasY, inspectorWidth(), canvasH);
        }
    }

    private void renderTopBar(ProgramUiGraphics graphics, int mouseX, int mouseY) {
        var x = panelX + 4;
        if (panelW >= 620) {
            headingText(graphics, title.getString(), panelX + 12, panelY + 5, TEXT, 84);
            x += 96;
        }
        var slotWidth = slotTabWidth(x);
        for (var index = 0; index < session.slotCount(); index++) {
            button(graphics, x, panelY + 2, slotWidth, 16,
                    Component.translatable("screen.academy.precision_operation.slot", index + 1),
                    mouseX, mouseY, index == slot, true);
            x += slotWidth + 2;
        }
        var toolsX = panelX + panelW - TOOL_LABELS.length * (TOOL_SIZE + 2) - 2;
        for (var index = 0; index < TOOL_LABELS.length; index++) {
            var disabled = index == 9 && (!document.validation().valid() || !configurationInputsValid());
            iconButton(graphics, toolsX, panelY + 3, TOOL_GLYPHS[index], mouseX, mouseY, disabled);
            toolsX += TOOL_SIZE + 2;
        }
    }

    private void renderRails(ProgramUiGraphics graphics, int mouseX, int mouseY) {
        if (compactLeft) {
            button(graphics, leftX + 1, canvasY + 2, 16, 16, Component.literal("N"),
                    mouseX, mouseY, leftDrawerOpen, false);
        }
        if (compactRight) {
            button(graphics, rightX + 1, canvasY + 2, 16, 16, Component.literal("I"),
                    mouseX, mouseY, rightDrawerOpen, false);
        }
    }

    private void renderPalette(ProgramUiGraphics graphics, int mouseX, int mouseY) {
        var x = paletteX();
        var width = paletteWidth();
        headingText(graphics, Component.translatable(
                        "screen.academy.precision_operation.nodes").getString(),
                x + 4, canvasY + 3, DIM, width - 8);
        var groups = ProgramEditorNodeCatalog.Group.values();
        var tabY = canvasY + PALETTE_TAB_OFFSET_Y;
        var tabW = Math.max(11, (width - 8) / groups.length);
        for (var index = 0; index < groups.length; index++) {
            var group = groups[index];
            button(graphics, x + 4 + index * tabW, tabY, tabW - 1, 12,
                    Component.literal(groupGlyph(group)), mouseX, mouseY,
                    group == selectedGroup, false);
        }
        renderInput(graphics, Math.round(search.getX()), Math.round(search.getY()),
                Math.round(search.getWidth()), 15, search.isFocused());
        var entries = visibleEntries();
        var listY = canvasY + PALETTE_LIST_OFFSET_Y;
        var listBottom = canvasY + canvasH - 3;
        var visibleRows = Math.max(1, (listBottom - listY) / ROW_H);
        paletteScroll = Math.clamp(paletteScroll, 0, Math.max(0, entries.size() - visibleRows));
        for (var row = 0; row < visibleRows && paletteScroll + row < entries.size(); row++) {
            var entry = entries.get(paletteScroll + row);
            var y = listY + row * ROW_H;
            var hover = inside(mouseX, mouseY, x + 3, y, width - 6, ROW_H - 1);
            graphics.fill(x + 3, y, x + width - 3, y + ROW_H - 1,
                    hover ? HOVER_BACKGROUND : ROW_BACKGROUND);
            graphics.fill(x + 3, y, x + 5, y + ROW_H - 1, groupColor(entry.group()));
            smallText(graphics, groupGlyph(entry.group()), x + 8, y + 3,
                    groupColor(entry.group()), 8);
            var categoryRestricted = entry.categoryRestricted();
            smallText(graphics, nodeLabel(entry).getString(), x + 17, y + 3, TEXT,
                    categoryRestricted ? width - 36 : width - 22);
            if (categoryRestricted) {
                graphics.fill(x + width - 17, y + 2, x + width - 16, y + ROW_H - 3, DIVIDER);
                smallText(graphics, categoryGlyph(entry), x + width - 13, y + 3,
                        accentColor, 8);
            }
        }
        if (entries.size() > visibleRows) {
            var trackH = listBottom - listY;
            var thumbH = Math.max(8, trackH * visibleRows / entries.size());
            var thumbY = listY + (trackH - thumbH) * paletteScroll
                    / Math.max(1, entries.size() - visibleRows);
            graphics.fill(x + width - 3, listY, x + width - 1, listBottom, CONTROL_BACKGROUND);
            graphics.fill(x + width - 3, thumbY, x + width - 1, thumbY + thumbH, DIM);
        }
    }

    private void renderCanvas(ProgramUiGraphics graphics, int mouseX, int mouseY) {
        graphics.enableScissor(canvasX, canvasY, canvasX + canvasW, canvasY + canvasH);
        renderCanvasGrid(graphics);
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate((float) (canvasX + panX), (float) (canvasY + panY));
        pose.scale((float) zoom, (float) zoom);
        for (var edge : document.program().graph().edges()) renderEdge(graphics, edge);
        for (var node : nodes()) renderNode(graphics, node);
        pose.popPose();
        renderSelectionDrag(graphics);
        renderConnectionPreview(graphics, mouseX, mouseY);
        graphics.disableScissor();
        smallText(graphics, Math.round(zoom * 100.0) + "%",
                canvasX + 3, canvasY + canvasH - 10, DIM, 40);
    }

    private void renderEdge(ProgramUiGraphics graphics, ProgramGraph.Edge edge) {
        var from = node(edge.from().nodeId());
        var to = node(edge.to().nodeId());
        if (from == null || to == null) return;
        var fromIndex = portIndex(from.schema.outputs(), edge.from().port());
        var toIndex = portIndex(to.schema.inputs(), edge.to().port());
        if (fromIndex < 0 || toIndex < 0) return;
        var type = from.schema.outputs().get(fromIndex).type();
        orthogonalLine(graphics,
                (int) Math.round(from.x + NODE_W),
                (int) Math.round(from.y + portOffsetY(from, fromIndex)),
                (int) Math.round(to.x),
                (int) Math.round(to.y + portOffsetY(to, toIndex)),
                portColor(type));
    }

    private void renderNode(ProgramUiGraphics graphics, NodeView node) {
        var x = (int) Math.round(node.x);
        var y = (int) Math.round(node.y);
        var height = nodeHeight(node);
        var localError = firstDiagnostic();
        var errorNode = serverDiagnostic != PrecisionGraph.Diagnostic.OK
                ? serverDiagnosticNode : localError == null ? -1 : localError.nodeId();
        var hasError = node.id() == errorNode;
        var selected = selectedNodes.contains(node.id());
        graphics.fill(x, y, x + NODE_W, y + height,
                hasError ? ERROR_BACKGROUND : selected ? NODE_SELECTED_BACKGROUND : NODE_BACKGROUND);
        border(graphics, x, y, NODE_W, height,
                hasError ? ERROR : selected ? accentColor : BORDER_MUTED);
        graphics.fill(x, y, x + NODE_W, y + NODE_HEADER_H, hasError ? ERROR : NODE_HEADER);
        if (!hasError) graphics.fill(x, y, x + 2, y + NODE_HEADER_H, groupColor(node.entry.group()));
        smallText(graphics, groupGlyph(node.entry.group()), x + 3, y + 2, TEXT, 8);
        var categoryRestricted = node.entry.categoryRestricted();
        var rightInset = hasError ? 15 : 3;
        if (categoryRestricted) {
            var badgeX = x + NODE_W - rightInset - 8;
            graphics.fill(badgeX - 2, y + 2, badgeX - 1, y + NODE_HEADER_H - 2, DIVIDER);
            smallText(graphics, categoryGlyph(node.entry), badgeX + 1, y + 2, accentColor, 7);
            rightInset += 11;
        }
        smallText(graphics, nodeLabel(node.entry).getString(), x + 12, y + 2, TEXT,
                NODE_W - 12 - rightInset);
        if (hasError) smallText(graphics, "!!", x + NODE_W - 12, y + 2, TEXT, 10);
        var configurationY = y + NODE_HEADER_H + 2;
        for (var field : configurationFields(node)) {
            var value = node.source.configuration().getAsJsonObject().get(field);
            smallText(graphics,
                    configurationFieldLabel(field).getString() + ": "
                            + configurationDisplayValue(node, field, value).getString(),
                    x + 4, configurationY, NODE_SECONDARY_TEXT, NODE_W - 8);
            configurationY += NODE_CONFIGURATION_ROW_H;
        }
        for (var index = 0; index < node.schema.inputs().size(); index++) {
            var port = node.schema.inputs().get(index);
            var endpoint = new Endpoint(node.id(), port.name(), true, port.type());
            var color = highlightedPort(endpoint) ? TEXT : portColor(port.type());
            renderPort(graphics, x, y + portOffsetY(node, index), color,
                    port.type().equals(ProgramValueTypes.FLOW) && !endpointConnected(endpoint));
        }
        for (var index = 0; index < node.schema.outputs().size(); index++) {
            var port = node.schema.outputs().get(index);
            var endpoint = new Endpoint(node.id(), port.name(), false, port.type());
            var color = highlightedPort(endpoint) ? TEXT : portColor(port.type());
            renderPort(graphics, x + NODE_W, y + portOffsetY(node, index), color,
                    port.type().equals(ProgramValueTypes.FLOW) && !endpointConnected(endpoint));
        }
    }

    private Component localDiagnostic(NodeView node, ProgramDiagnostic diagnostic) {
        if (diagnostic.code() == ProgramDiagnosticCode.MISSING_INPUT
                && diagnostic.port() != null) {
            return Component.translatable(
                    "screen.academy.program.diagnostic.missing_input",
                    portLabel(node.entry, diagnostic.port())
            ).withColor(ERROR);
        }
        return Component.literal(diagnostic.code().name()).withColor(ERROR);
    }

    private static void renderPort(
            ProgramUiGraphics graphics,
            int centerX,
            int centerY,
            int color,
            boolean openEnd
    ) {
        graphics.fill(centerX - 3, centerY - 3, centerX + 3, centerY + 3, color);
        if (openEnd) graphics.fill(centerX - 1, centerY - 1, centerX + 2, centerY + 2, 0xFF111111);
    }

    private void renderSelectionDrag(ProgramUiGraphics graphics) {
        if (selectionDrag == null) return;
        var bounds = selectionDrag.bounds();
        if (!bounds.exceeds(SELECTION_DRAG_THRESHOLD)) return;
        var left = (int) Math.floor(bounds.left());
        var top = (int) Math.floor(bounds.top());
        var right = (int) Math.ceil(bounds.right());
        var bottom = (int) Math.ceil(bounds.bottom());
        graphics.fill(left, top, right, bottom, SELECTION_BACKGROUND);
        border(graphics, left, top,
                Math.max(1, right - left), Math.max(1, bottom - top), accentColor);
    }

    private void renderConnectionPreview(ProgramUiGraphics graphics, int mouseX, int mouseY) {
        if (connection == null) return;
        var anchor = endpointScreen(connection.endpoint);
        var target = snappedEndpoint(mouseX, mouseY, connection.endpoint);
        var end = target == null ? new ScreenPoint(mouseX, mouseY) : endpointScreen(target);
        var color = target != null && connectionRejected(connection.endpoint, target)
                ? ERROR : portColor(connection.endpoint.type);
        orthogonalLine(graphics, anchor.x, anchor.y, end.x, end.y, color);
    }

    private void renderInspector(ProgramUiGraphics graphics, int mouseX, int mouseY) {
        var x = inspectorX();
        var width = inspectorWidth();
        headingText(graphics, Component.translatable(
                        "screen.academy.precision_operation.inspector").getString(),
                x + 5, canvasY + 4, DIM, width - 10);
        var selected = node(selectedNode);
        if (selected == null) {
            var status = selectedNodes.size() > 1
                    ? Component.translatable(
                            "screen.academy.precision_operation.multi_selection",
                            selectedNodes.size())
                    : Component.translatable("screen.academy.precision_operation.no_selection");
            smallText(graphics, status.getString(),
                    x + 5, canvasY + 22, DIM, width - 10);
            return;
        }
        headingText(graphics, nodeLabel(selected.entry).getString(),
                x + 5, canvasY + 21, TEXT, width - 10);
        if (selected.entry.categoryRestricted()) {
            smallText(graphics, categoryScopeLabel(selected.entry).getString(),
                    x + 5, canvasY + 33, accentColor, width - 10);
        }
        var descriptionOffset = inspectorDescriptionOffset(selected.entry);
        var descriptionHeight = smallWrappedText(graphics, nodeDescription(selected.entry),
                x + 5, canvasY + descriptionOffset, DIM, width - 10);
        var configY = canvasY + inspectorConfigurationOffset(selected.entry, descriptionHeight);
        renderConfigurationEditor(graphics, selected, x + 5, configY, width - 10, mouseX, mouseY);
        var portsY = configY + configurationEditorHeight(selected);
        headingText(graphics, Component.translatable(
                        "screen.academy.precision_operation.ports").getString(),
                x + 5, portsY - 1, DIM, width - 10);
        var y = portsY + 11;
        for (var port : selected.schema.inputs()) {
            smallText(graphics, "< " + portLabel(selected.entry, port.name()).getString(),
                    x + 7, y, portColor(port.type()), width - 12);
            y += 9;
        }
        for (var port : selected.schema.outputs()) {
            smallText(graphics, "> " + portLabel(selected.entry, port.name()).getString(),
                    x + 7, y, portColor(port.type()), width - 12);
            y += 9;
        }
    }

    private void renderConfigurationEditor(
            ProgramUiGraphics graphics,
            NodeView node,
            int x,
            int y,
            int width,
            int mouseX,
            int mouseY
    ) {
        var fields = configurationFields(node);
        if (fields.isEmpty()) return;
        for (var index = 0; index < fields.size(); index++) {
            var field = fields.get(index);
            var rowY = y + index * CONFIGURATION_ROW_H;
            smallText(graphics, configurationFieldLabel(field).getString(),
                    x, rowY, TEXT, width);
            var currentValue = node.source.configuration().getAsJsonObject().get(field);
            var options = ProgramConfigurationOptions.options(node.entry, field, currentValue);
            if (ProgramConfigurationOptions.isToggle(field, currentValue)) {
                var valueY = rowY + 11;
                var checked = currentValue.getAsBoolean();
                var hovered = inside(mouseX, mouseY, x, valueY, width, TOOL_SIZE);
                var trackWidth = 20;
                var trackHeight = 10;
                var trackY = valueY + 2;
                graphics.fill(x, trackY, x + trackWidth, trackY + trackHeight,
                        hovered || checked ? TEXT : BORDER_MUTED);
                var thumbX = checked ? x + trackWidth - 9 : x + 1;
                graphics.fill(thumbX, trackY + 1, thumbX + 8, trackY + trackHeight - 1,
                        checked ? 0xFF000000 : TEXT);
                var selected = ProgramConfigurationOptions.selected(options, currentValue);
                smallText(graphics, selected.label().getString(), x + trackWidth + 5,
                        valueY + 4, checked ? TEXT : DIM, width - trackWidth - 5);
            } else if (ProgramConfigurationOptions.isPowerSlider(field, currentValue)) {
                var valueY = rowY + 11;
                var valueWidth = 32;
                var trackWidth = Math.max(16, width - valueWidth - 4);
                var power = Math.clamp(
                        currentValue.getAsFloat(), ProgramPowerScale.MIN, ProgramPowerScale.MAX);
                var progress = (power - ProgramPowerScale.MIN)
                        / (ProgramPowerScale.MAX - ProgramPowerScale.MIN);
                var fillWidth = Math.round(trackWidth * progress);
                graphics.fill(x, valueY + 6, x + trackWidth, valueY + 8, BORDER_MUTED);
                graphics.fill(x, valueY + 6, x + fillWidth, valueY + 8, accentColor);
                var thumbX = x + fillWidth;
                graphics.fill(thumbX - 1, valueY + 3, thumbX + 1, valueY + 11, TEXT);
                smallText(graphics, String.format(Locale.ROOT, "%.2f", power),
                        x + trackWidth + 5, valueY + 3, TEXT, valueWidth - 1);
            } else if (!options.isEmpty()) {
                var valueY = rowY + 11;
                iconButton(graphics, x, valueY, "<", mouseX, mouseY, false);
                iconButton(graphics, x + width - TOOL_SIZE, valueY, ">", mouseX, mouseY, false);
                renderControl(graphics, x + TOOL_SIZE + 2, valueY,
                        width - TOOL_SIZE * 2 - 4, TOOL_SIZE, true, true, false);
                var selected = ProgramConfigurationOptions.selected(options, currentValue);
                smallText(graphics, selected.label().getString(), x + TOOL_SIZE + 5, valueY + 4,
                        TEXT, width - TOOL_SIZE * 2 - 10);
            } else {
                var input = configurationInputs.get(field);
                renderInput(graphics, x, rowY + 11, width, 15,
                        input != null && input.isFocused());
            }
        }
    }

    private void renderStatus(ProgramUiGraphics graphics) {
        var local = firstDiagnostic();
        var shown = System.currentTimeMillis() < transientUntil ? transientDiagnostic
                : serverDiagnostic != PrecisionGraph.Diagnostic.OK ? serverDiagnostic
                : local == null ? PrecisionGraph.Diagnostic.OK : mapDiagnostic(local.code());
        var nodeId = serverDiagnostic != PrecisionGraph.Diagnostic.OK
                ? serverDiagnosticNode : local == null ? -1 : local.nodeId();
        var text = Component.translatable(shown.translationKey()).getString();
        if (nodeId >= 0) text += "  [#" + nodeId + "]";
        smallText(graphics, text, panelX + 4, panelY + panelH - 11,
                shown == PrecisionGraph.Diagnostic.OK ? DIM : ERROR, panelW - 8);
    }

    private void renderQuickInsert(ProgramUiGraphics graphics, int mouseX, int mouseY) {
        if (quickInsert == null) return;
        var rows = Math.min(12, quickInsert.entries.size());
        var width = 122;
        var height = rows * ROW_H + 4;
        var x = Math.clamp(quickInsert.x, panelX + 2, panelX + panelW - width - 2);
        var y = Math.clamp(quickInsert.y, canvasY + 2, canvasY + canvasH - height - 2);
        graphics.fill(x, y, x + width, y + height, POPUP_BACKGROUND);
        border(graphics, x, y, width, height, BORDER);
        for (var row = 0; row < rows; row++) {
            var entry = quickInsert.entries.get(row);
            var rowY = y + 2 + row * ROW_H;
            if (inside(mouseX, mouseY, x + 2, rowY, width - 4, ROW_H - 1)) {
                graphics.fill(x + 2, rowY, x + width - 2, rowY + ROW_H - 1, HOVER_BACKGROUND);
            }
            smallText(graphics, nodeLabel(entry).getString(), x + 5, rowY + 3, TEXT, width - 10);
        }
    }

    private void renderTooltip(ProgramUiGraphics graphics, int mouseX, int mouseY) {
        var tooltip = hoveredTooltip(mouseX, mouseY);
        if (tooltip.isEmpty()) return;
        var expanded = new ArrayList<TooltipLine>();
        var contentWidth = 0.0f;
        for (var line : tooltip) {
            for (var wrapped : ProgramUiGraphics.wrap(
                    line.text(), 176, ProgramUiGraphics.BODY_FONT_SIZE)) {
                expanded.add(new TooltipLine(wrapped, line.color()));
                contentWidth = Math.max(contentWidth,
                        LabelWidget.Companion.getTextWidth(
                                wrapped, ProgramUiGraphics.BODY_FONT_SIZE));
            }
        }
        var tooltipWidth = Math.max(32, Math.round(contentWidth) + 10);
        var tooltipHeight = expanded.size() * 9 + 8;
        var x = mouseX + 10;
        var y = mouseY + 8;
        if (x + tooltipWidth > width - 4) x = mouseX - tooltipWidth - 10;
        if (y + tooltipHeight > height - 4) y = mouseY - tooltipHeight - 8;
        x = Math.clamp(x, 4, Math.max(4, width - tooltipWidth - 4));
        y = Math.clamp(y, 4, Math.max(4, height - tooltipHeight - 4));
        graphics.fill(x, y, x + tooltipWidth, y + tooltipHeight, POPUP_BACKGROUND);
        border(graphics, x, y, tooltipWidth, tooltipHeight, BORDER);
        graphics.fill(x + 1, y + 1, x + 3, y + tooltipHeight - 1, accentColor);
        for (var index = 0; index < expanded.size(); index++) {
            var line = expanded.get(index);
            graphics.text(line.text(), x + 6, y + 4 + index * 9,
                    line.color(), ProgramUiGraphics.BODY_FONT_SIZE, tooltipWidth - 10);
        }
    }

    private List<TooltipLine> hoveredTooltip(int mouseX, int mouseY) {
        var toolsX = panelX + panelW - TOOL_LABELS.length * (TOOL_SIZE + 2) - 2;
        for (var index = 0; index < TOOL_LABELS.length; index++) {
            if (inside(mouseX, mouseY, toolsX, panelY + 3, TOOL_SIZE, TOOL_SIZE)) {
                return List.of(new TooltipLine(Component.translatable(
                        "screen.academy.precision_operation." + TOOL_LABELS[index]).getString(), TEXT));
            }
            toolsX += TOOL_SIZE + 2;
        }
        if (compactLeft && inside(mouseX, mouseY, leftX + 1, canvasY + 2, 16, 16)) {
            return List.of(new TooltipLine(Component.translatable(
                    "screen.academy.precision_operation.nodes").getString(), TEXT));
        }
        if (compactRight && inside(mouseX, mouseY, rightX + 1, canvasY + 2, 16, 16)) {
            return List.of(new TooltipLine(Component.translatable(
                    "screen.academy.precision_operation.inspector").getString(), TEXT));
        }
        if (paletteVisible()) {
            var x = paletteX();
            var paletteWidth = paletteWidth();
            var groups = ProgramEditorNodeCatalog.Group.values();
            var tabY = canvasY + PALETTE_TAB_OFFSET_Y;
            var tabW = Math.max(11, (paletteWidth - 8) / groups.length);
            for (var index = 0; index < groups.length; index++) {
                if (inside(mouseX, mouseY, x + 4 + index * tabW, tabY, tabW - 1, 12)) {
                    return List.of(new TooltipLine(
                            Component.translatable(groupKey(groups[index])).getString(), TEXT));
                }
            }
            var entries = visibleEntries();
            var listY = canvasY + PALETTE_LIST_OFFSET_Y;
            var listBottom = canvasY + canvasH - 3;
            var visibleRows = Math.max(1, (listBottom - listY) / ROW_H);
            for (var row = 0; row < visibleRows && paletteScroll + row < entries.size(); row++) {
                if (!inside(mouseX, mouseY, x + 3, listY + row * ROW_H,
                        paletteWidth - 6, ROW_H - 1)) continue;
                return tooltipLines(entries.get(paletteScroll + row), null);
            }
        }
        if (!inside(mouseX, mouseY, canvasX, canvasY, canvasW, canvasH)) return List.of();
        var endpoint = endpointAt(mouseX, mouseY);
        if (endpoint != null) {
            var owner = node(endpoint.nodeId);
            var label = owner == null
                    ? Component.literal(endpoint.port)
                    : portLabel(owner.entry, endpoint.port);
            var type = endpoint.type.id().getPath().replace("program_type/", "");
            return List.of(
                    new TooltipLine((endpoint.input ? "< " : "> ") + label.getString(), TEXT),
                    new TooltipLine(type, DIM)
            );
        }
        var localError = firstDiagnostic();
        var errorNode = serverDiagnostic != PrecisionGraph.Diagnostic.OK
                ? serverDiagnosticNode : localError == null ? -1 : localError.nodeId();
        for (var node : reversed(nodes())) {
            if (!insideNode(mouseX, mouseY, node)) continue;
            var error = node.id() != errorNode ? null : localError == null
                    ? Component.translatable(serverDiagnostic.translationKey()).getString()
                    : localDiagnostic(node, localError).getString();
            return tooltipLines(node.entry, error);
        }
        return List.of();
    }

    private List<TooltipLine> tooltipLines(
            ProgramEditorNodeCatalog.Entry entry,
            String error
    ) {
        var lines = new ArrayList<TooltipLine>();
        lines.add(new TooltipLine(nodeLabel(entry).getString(), TEXT));
        if (entry.categoryRestricted()) {
            lines.add(new TooltipLine(categoryScopeLabel(entry).getString(), accentColor));
        }
        lines.add(new TooltipLine(nodeDescription(entry).getString(), DIM));
        if (error != null) lines.add(new TooltipLine(error, ERROR));
        return List.copyOf(lines);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        syncSerializedLayout();
        var x = event.x();
        var y = event.y();
        if (event.button() == 0) {
            if (search != null && search.isVisible()
                    && inside(x, y, search.getX(), search.getY(), search.getWidth(), 15)) {
                unfocusConfigurationInputs();
                getRoot().dispatchEvent(org.academy.api.client.gui.event.MouseEvent.Companion.createPressEvent(
                        event.x(), event.y(), event.button()));
                return true;
            }
            var configurationInput = configurationInputAt(x, y);
            if (configurationInput != null) {
                search.setFocused(false);
                unfocusConfigurationInputs();
                getRoot().dispatchEvent(org.academy.api.client.gui.event.MouseEvent.Companion.createPressEvent(
                        event.x(), event.y(), event.button()));
                return true;
            }
            search.setFocused(false);
            unfocusConfigurationInputs();
            if (handleQuickInsertClick(x, y) || handleTopBarClick(x, y)
                    || handleRailClick(x, y) || handleInspectorClick(x, y)
                    || handlePaletteClick(x, y)) return true;
            if (spaceDown && inside(x, y, canvasX, canvasY, canvasW, canvasH)) {
                selectionDrag = null;
                panning = true;
                return true;
            }
            if (handleCanvasClick(x, y)) return true;
        }
        if ((event.button() == 1 || event.button() == 2)
                && inside(x, y, canvasX, canvasY, canvasW, canvasH)) {
            var endpoint = endpointAt(x, y);
            if (event.button() == 1 && endpoint != null) disconnect(endpoint);
            else panning = true;
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (draggingPowerField != null && event.button() == 0) {
            var selected = node(draggingPowerNode);
            if (selected != null) updatePowerSlider(selected, draggingPowerField, event.x());
            return true;
        }
        if (connection != null) return true;
        if (selectionDrag != null && event.button() == 0) {
            updateSelectionDrag(event.x(), event.y());
            return true;
        }
        if (draggingNode != null) {
            var nodesToMove = selectedNodes.contains(draggingNode)
                    ? Set.copyOf(selectedNodes) : Set.of(draggingNode);
            var result = document.translateNodes(nodesToMove, dragX / zoom, dragY / zoom);
            if (result.successful()) install(result.document(), false);
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
        if (draggingPowerField != null && event.button() == 0) {
            draggingPowerField = null;
            draggingPowerNode = -1;
            return true;
        }
        if (connection != null && event.button() == 0) {
            finishConnection(event.x(), event.y());
            draggingNode = null;
            return true;
        }
        if (selectionDrag != null && event.button() == 0) {
            updateSelectionDrag(event.x(), event.y());
            selectionDrag = null;
            return true;
        }
        draggingNode = null;
        panning = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(
            double mouseX,
            double mouseY,
            double scrollX,
            double scrollY
    ) {
        if (paletteVisible()
                && inside(mouseX, mouseY, paletteX(), canvasY, paletteWidth(), canvasH)) {
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
        if (search != null && search.isFocused()) {
            dispatchTextInputKey(event);
            return true;
        }
        var configurationInput = focusedConfigurationInput();
        if (configurationInput != null) {
            dispatchTextInputKey(event);
            return true;
        }
        if (event.key() == InputConstants.KEY_SPACE) {
            spaceDown = true;
            return true;
        }
        if (event.key() == InputConstants.KEY_ESCAPE
                && (connection != null || quickInsert != null || selectionDrag != null)) {
            connection = null;
            quickInsert = null;
            selectionDrag = null;
            return true;
        }
        if (isDeleteKey(event.key())) {
            deleteSelected();
            return true;
        }
        if ((event.modifiers() & InputConstants.MOD_CONTROL) != 0) {
            if (event.key() == InputConstants.KEY_C) {
                if ((event.modifiers() & InputConstants.MOD_SHIFT) != 0) exportProgram();
                else copySelected();
                return true;
            }
            if (event.key() == InputConstants.KEY_V) {
                if ((event.modifiers() & InputConstants.MOD_SHIFT) != 0) importProgram();
                else pasteClipboard();
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

    static boolean isDeleteKey(int key) {
        return key == InputConstants.KEY_DELETE || key == InputConstants.KEY_BACKSPACE;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (search != null && search.isFocused()) {
            getRoot().dispatchEvent(new org.academy.api.client.gui.event.CharTypedEvent(
                    event.codepoint()));
            return true;
        }
        var configurationInput = focusedConfigurationInput();
        if (configurationInput != null) {
            getRoot().dispatchEvent(new org.academy.api.client.gui.event.CharTypedEvent(
                    event.codepoint()));
            return true;
        }
        return super.charTyped(event);
    }

    private void dispatchTextInputKey(KeyEvent event) {
        getRoot().dispatchEvent(new org.academy.api.client.gui.event.KeyEvent(
                org.academy.api.client.gui.event.EventType.KEY_PRESSED,
                event.key(), event.scancode(), event.modifiers()));
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
        session.updateLocalProgram(slot, document.program());
        session.closed(this);
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
        var slotWidth = slotTabWidth(x);
        for (var index = 0; index < session.slotCount(); index++) {
            if (inside(mouseX, mouseY, x, panelY + 2, slotWidth, 16)) {
                session.updateLocalProgram(slot, document.program());
                slot = index;
                session.selectSlot(slot);
                setProgram(session.editableProgram(slot), false);
                revision = session.revision();
                fitCanvas(true);
                return true;
            }
            x += slotWidth + 2;
        }
        var toolsX = panelX + panelW - TOOL_LABELS.length * (TOOL_SIZE + 2) - 2;
        for (var index = 0; index < TOOL_LABELS.length; index++) {
            if (inside(mouseX, mouseY, toolsX, panelY + 3, TOOL_SIZE, TOOL_SIZE)) {
                switch (index) {
                    case 0 -> deleteSelected();
                    case 1 -> copySelected();
                    case 2 -> pasteClipboard();
                    case 3 -> undo();
                    case 4 -> redo();
                    case 5 -> autoLayout();
                    case 6 -> fitCanvas(false);
                    case 7 -> exportProgram();
                    case 8 -> importProgram();
                    case 9 -> save();
                    case 10 -> setProgram(session.restoredProgram(slot), true);
                    default -> {
                    }
                }
                return true;
            }
            toolsX += TOOL_SIZE + 2;
        }
        return false;
    }

    private int slotTabWidth(int startX) {
        var toolsX = panelX + panelW - TOOL_LABELS.length * (TOOL_SIZE + 2) - 2;
        var count = Math.max(1, session.slotCount());
        var available = Math.max(1, toolsX - startX - (count - 1) * 2 - 2);
        return Math.clamp(available / count, 18, 38);
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
        var width = paletteWidth();
        var groups = ProgramEditorNodeCatalog.Group.values();
        var tabY = canvasY + PALETTE_TAB_OFFSET_Y;
        var tabW = Math.max(11, (width - 8) / groups.length);
        for (var index = 0; index < groups.length; index++) {
            if (inside(mouseX, mouseY, x + 4 + index * tabW, tabY, tabW - 1, 12)) {
                selectedGroup = groups[index];
                paletteScroll = 0;
                return true;
            }
        }
        var listY = canvasY + PALETTE_LIST_OFFSET_Y;
        if (!inside(mouseX, mouseY, x + 3, listY, width - 6,
                canvasY + canvasH - 3 - listY)) return false;
        var row = (int) ((mouseY - listY) / ROW_H);
        var entries = visibleEntries();
        var index = paletteScroll + row;
        if (index >= 0 && index < entries.size()) {
            addNode(entries.get(index), screenToGraphX(canvasX + canvasW / 2.0),
                    screenToGraphY(canvasY + canvasH / 2.0));
            return true;
        }
        return false;
    }

    private boolean handleInspectorClick(double mouseX, double mouseY) {
        if (!inspectorVisible()) return false;
        var selected = node(selectedNode);
        if (selected == null) return false;
        var fields = configurationFields(selected);
        if (fields.isEmpty()) return false;
        var width = inspectorWidth() - 10;
        var descriptionHeight = ProgramUiGraphics.wrappedHeight(
                nodeDescription(selected.entry).getString(), width,
                ProgramUiGraphics.BODY_FONT_SIZE, 9.0f);
        var y = canvasY + inspectorConfigurationOffset(selected.entry, descriptionHeight);
        var x = inspectorX() + 5;
        for (var index = 0; index < fields.size(); index++) {
            var field = fields.get(index);
            var currentValue = selected.source.configuration().getAsJsonObject().get(field);
            var options = ProgramConfigurationOptions.options(selected.entry, field, currentValue);
            var valueY = y + index * CONFIGURATION_ROW_H + 11;
            if (ProgramConfigurationOptions.isToggle(field, currentValue)
                    && inside(mouseX, mouseY, x, valueY, width, TOOL_SIZE)) {
                return toggleConfiguration(selected, field, currentValue);
            }
            if (ProgramConfigurationOptions.isPowerSlider(field, currentValue)) {
                var trackWidth = Math.max(16, width - 36);
                if (inside(mouseX, mouseY, x, valueY, trackWidth, TOOL_SIZE)) {
                    pushUndo();
                    draggingPowerField = field;
                    draggingPowerNode = selected.id();
                    updatePowerSlider(selected, field, mouseX);
                    return true;
                }
            }
            if (!options.isEmpty() && inside(mouseX, mouseY, x, valueY, TOOL_SIZE, TOOL_SIZE)) {
                return stepConfiguration(selected, field, currentValue, options, -1);
            }
            if (!options.isEmpty()
                    && inside(mouseX, mouseY, x + width - TOOL_SIZE, valueY, TOOL_SIZE, TOOL_SIZE)) {
                return stepConfiguration(selected, field, currentValue, options, 1);
            }
        }
        return false;
    }

    private boolean toggleConfiguration(
            NodeView selected,
            String field,
            JsonElement currentValue
    ) {
        var object = selected.source.configuration().getAsJsonObject().deepCopy();
        object.addProperty(field, !currentValue.getAsBoolean());
        var result = document.configureNode(selected.id(), object);
        if (!result.successful()) {
            showTransient(PrecisionGraph.Diagnostic.INVALID_PARAMETER);
            return true;
        }
        pushUndo();
        configurationInputValidity.clear();
        install(result.document(), true);
        configurationNode = -1;
        return true;
    }

    private void updatePowerSlider(NodeView selected, String field, double mouseX) {
        var width = inspectorWidth() - 10;
        var trackWidth = Math.max(16, width - 36);
        var trackX = inspectorX() + 5;
        var progress = Math.clamp((mouseX - trackX) / trackWidth, 0.0, 1.0);
        var power = ProgramPowerScale.MIN
                + progress * (ProgramPowerScale.MAX - ProgramPowerScale.MIN);
        power = Math.round(power * 100.0) / 100.0;
        power = Math.clamp(power, ProgramPowerScale.MIN, ProgramPowerScale.MAX);
        var object = selected.source.configuration().getAsJsonObject().deepCopy();
        object.addProperty(field, power);
        var result = document.configureNode(selected.id(), object);
        if (!result.successful()) {
            showTransient(PrecisionGraph.Diagnostic.INVALID_PARAMETER);
            return;
        }
        configurationInputValidity.clear();
        install(result.document(), true);
        configurationNode = -1;
    }

    private boolean stepConfiguration(
            NodeView selected,
            String field,
            JsonElement currentValue,
            List<ProgramConfigurationOptions.Option> options,
            int direction
    ) {
        var object = selected.source.configuration().getAsJsonObject().deepCopy();
        var next = ProgramConfigurationOptions.step(options, currentValue, direction);
        object.add(field, next.value().deepCopy());
        if (selected.entry.id().equals(CommonProgramNodeIds.SCALAR_CONSTANT) && field.equals("type")) {
            object.addProperty("value", next.value().getAsString().equals("boolean") ? "false" : "0");
        }
        var result = document.configureNode(selected.id(), object);
        if (!result.successful()) {
            showTransient(PrecisionGraph.Diagnostic.INVALID_PARAMETER);
            return true;
        }
        pushUndo();
        configurationInputValidity.clear();
        install(result.document(), true);
        configurationNode = -1;
        return true;
    }

    private boolean handleCanvasClick(double mouseX, double mouseY) {
        if (!inside(mouseX, mouseY, canvasX, canvasY, canvasW, canvasH)) return false;
        quickInsert = null;
        var endpoint = endpointAt(mouseX, mouseY);
        if (endpoint != null) {
            if (connection != null && compatible(connection.endpoint, endpoint)) {
                connect(connection.endpoint, endpoint);
                connection = null;
            } else {
                connection = new ConnectionDrag(endpoint, mouseX, mouseY);
                selectNode(endpoint.nodeId);
            }
            return true;
        }
        for (var node : reversed(nodes())) {
            if (insideHeader(mouseX, mouseY, node)) {
                if (!selectedNodes.contains(node.id())) selectNode(node.id());
                pushUndo();
                draggingNode = node.id();
                return true;
            }
            if (insideNode(mouseX, mouseY, node)) {
                selectNode(node.id());
                connection = null;
                return true;
            }
        }
        selectNode(-1);
        connection = null;
        selectionDrag = new SelectionDrag(mouseX, mouseY, mouseX, mouseY);
        return true;
    }

    private void updateSelectionDrag(double mouseX, double mouseY) {
        if (selectionDrag == null) return;
        var clampedX = Math.clamp(mouseX, canvasX, canvasX + canvasW);
        var clampedY = Math.clamp(mouseY, canvasY, canvasY + canvasH);
        selectionDrag = selectionDrag.update(clampedX, clampedY);
        var screenBounds = selectionDrag.bounds();
        if (!screenBounds.exceeds(SELECTION_DRAG_THRESHOLD)) {
            selectNodes(Set.of());
            return;
        }
        var graphBounds = PrecisionEditorGeometry.selectionBounds(
                screenToGraphX(screenBounds.left()),
                screenToGraphY(screenBounds.top()),
                screenToGraphX(screenBounds.right()),
                screenToGraphY(screenBounds.bottom())
        );
        var selected = new LinkedHashSet<Integer>();
        for (var node : nodes()) {
            if (graphBounds.intersects(node.x, node.y, NODE_W, nodeHeight(node))) {
                selected.add(node.id());
            }
        }
        selectNodes(selected);
    }

    private void finishConnection(double mouseX, double mouseY) {
        if (connection == null) return;
        var moved = Math.hypot(mouseX - connection.startX, mouseY - connection.startY) > 3.0;
        var target = snappedEndpoint(mouseX, mouseY, connection.endpoint);
        if (target != null && compatible(connection.endpoint, target)) {
            connect(connection.endpoint, target);
            connection = null;
            return;
        }
        if (moved) {
            var entries = compatibleEntries(connection.endpoint).stream().limit(12).toList();
            if (!entries.isEmpty()) {
                quickInsert = new QuickInsert(
                        (int) mouseX, (int) mouseY, entries, connection.endpoint);
            }
        }
        connection = null;
    }

    private boolean handleQuickInsertClick(double mouseX, double mouseY) {
        if (quickInsert == null) return false;
        var rows = Math.min(12, quickInsert.entries.size());
        var width = 122;
        var height = rows * ROW_H + 4;
        var x = Math.clamp(quickInsert.x, panelX + 2, panelX + panelW - width - 2);
        var y = Math.clamp(quickInsert.y, canvasY + 2, canvasY + canvasH - height - 2);
        if (!inside(mouseX, mouseY, x, y, width, height)) {
            quickInsert = null;
            return false;
        }
        var row = (int) ((mouseY - y - 2) / ROW_H);
        if (row >= 0 && row < rows) {
            var entry = quickInsert.entries.get(row);
            var anchor = quickInsert.anchor;
            var added = addNode(entry,
                    screenToGraphX(quickInsert.x), screenToGraphY(quickInsert.y));
            if (added != null) {
                var endpoint = firstCompatibleEndpoint(added, anchor);
                if (endpoint != null) connect(anchor, endpoint);
            }
            quickInsert = null;
        }
        return true;
    }

    private void connect(Endpoint first, Endpoint second) {
        if (!compatible(first, second)) {
            showTransient(PrecisionGraph.Diagnostic.TYPE_MISMATCH);
            return;
        }
        var output = first.input ? second : first;
        var input = first.input ? first : second;
        var working = document;
        for (var edge : List.copyOf(working.program().graph().edges())) {
            if (edge.to().equals(input.graphEndpoint())) {
                working = working.disconnect(edge.from(), edge.to()).orElseThrow();
            }
        }
        var outputNode = node(output.nodeId);
        var outputDefinition = outputNode == null ? null
                : outputNode.schema.output(output.port).orElse(null);
        if (outputDefinition != null && outputDefinition.maxConnections() == 1) {
            for (var edge : List.copyOf(working.program().graph().edges())) {
                if (edge.from().equals(output.graphEndpoint())) {
                    working = working.disconnect(edge.from(), edge.to()).orElseThrow();
                }
            }
        }
        var result = working.connect(output.graphEndpoint(), input.graphEndpoint());
        if (!result.successful()) {
            showTransient(result.diagnostic());
            return;
        }
        pushUndo();
        install(result.document(), true);
    }

    private void disconnect(Endpoint endpoint) {
        var working = document;
        var changed = false;
        for (var edge : List.copyOf(working.program().graph().edges())) {
            if (endpoint.input && edge.to().equals(endpoint.graphEndpoint())
                    || !endpoint.input && edge.from().equals(endpoint.graphEndpoint())) {
                working = working.disconnect(edge.from(), edge.to()).orElseThrow();
                changed = true;
            }
        }
        if (!changed) return;
        pushUndo();
        install(working, true);
    }

    private NodeView addNode(ProgramEditorNodeCatalog.Entry entry, double x, double y) {
        var existingIds = document.program().graph().nodes().stream()
                .map(ProgramGraph.Node::id).collect(Collectors.toSet());
        var result = document.addNode(entry.id(), x, y);
        if (!result.successful()) {
            showTransient(result.diagnostic());
            return null;
        }
        pushUndo();
        install(result.document(), true);
        var addedId = document.program().graph().nodes().stream()
                .map(ProgramGraph.Node::id).filter(id -> !existingIds.contains(id))
                .findFirst().orElse(-1);
        selectNode(addedId);
        return node(selectedNode);
    }

    private void deleteSelected() {
        if (selectedNodes.isEmpty()) return;
        var result = document.removeNodes(Set.copyOf(selectedNodes));
        if (!result.successful()) {
            showTransient(result.diagnostic());
            return;
        }
        pushUndo();
        install(result.document(), true);
        selectNode(-1);
    }

    private void copySelected() {
        var copyable = selectedNodes.stream()
                .filter(id -> {
                    var selected = node(id);
                    return selected != null
                            && selected.entry.type().role() != ProgramNodeRole.ENTRY;
                }).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (copyable.isEmpty()) return;
        UiEnvironment.get().setClipboard(
                ProgramClipboardCodec.encodeFragment(document.program(), copyable));
    }

    private void pasteClipboard() {
        var fragment = ProgramClipboardCodec.decodeFragment(
                UiEnvironment.get().clipboard(), document.program().category());
        if (fragment == null || fragment.graph().nodes().isEmpty()) {
            showTransient(PrecisionGraph.Diagnostic.MALFORMED);
            return;
        }
        var working = document;
        var idMap = new HashMap<Integer, Integer>();
        var newIds = new LinkedHashSet<Integer>();
        for (var source : fragment.graph().nodes().stream()
                .sorted(Comparator.comparingInt(ProgramGraph.Node::id)).toList()) {
            var position = fragment.editorLayout().nodePositions().get(source.id());
            var x = (position == null ? 0.0 : position.x()) + 18.0;
            var y = (position == null ? 0.0 : position.y()) + 18.0;
            var before = working.program().graph().nodes().stream()
                    .map(ProgramGraph.Node::id).collect(java.util.stream.Collectors.toSet());
            var added = working.addNode(source.type(), x, y);
            if (!added.successful()) {
                showTransient(added.diagnostic());
                return;
            }
            var addedId = added.document().program().graph().nodes().stream()
                    .map(ProgramGraph.Node::id).filter(id -> !before.contains(id))
                    .findFirst().orElse(-1);
            var configured = added.document().configureNode(
                    addedId, source.configuration());
            if (!configured.successful()) {
                showTransient(configured.diagnostic());
                return;
            }
            working = configured.document();
            idMap.put(source.id(), addedId);
            newIds.add(addedId);
        }
        for (var edge : fragment.graph().edges()) {
            var from = idMap.get(edge.from().nodeId());
            var to = idMap.get(edge.to().nodeId());
            if (from == null || to == null) continue;
            var connected = working.connect(
                    new ProgramGraph.Endpoint(from, edge.from().port()),
                    new ProgramGraph.Endpoint(to, edge.to().port()));
            if (!connected.successful()) {
                showTransient(connected.diagnostic());
                return;
            }
            working = connected.document();
        }
        pushUndo();
        install(working, true);
        selectNodes(newIds);
    }

    private void exportProgram() {
        UiEnvironment.get().setClipboard(
                ProgramClipboardCodec.encodeProgram(document.program()));
    }

    private void importProgram() {
        var imported = ProgramClipboardCodec.decodeProgram(
                UiEnvironment.get().clipboard(), document.program().category());
        if (imported == null) {
            showTransient(PrecisionGraph.Diagnostic.MALFORMED);
            return;
        }
        var current = document.program();
        var replacement = new AbilityProgram(
                AbilityProgram.CURRENT_SCHEMA_VERSION,
                current.id(),
                current.name(),
                current.category(),
                imported.graph(),
                imported.editorLayout()
        );
        var importedDocument = document(replacement);
        if (importedDocument.validation().diagnostics().stream().anyMatch(diagnostic ->
                diagnostic.code() == ProgramDiagnosticCode.UNKNOWN_NODE_TYPE
                        || diagnostic.code() == ProgramDiagnosticCode.CATEGORY_MISMATCH
                        || diagnostic.code() == ProgramDiagnosticCode.CAPABILITY_MISSING)) {
            showTransient(PrecisionGraph.Diagnostic.MALFORMED);
            return;
        }
        setProgram(replacement, true);
    }

    private void autoLayout() {
        if (nodes().isEmpty()) return;
        pushUndo();
        var layers = new HashMap<Integer, Integer>();
        for (var pass = 0; pass < nodes().size(); pass++) {
            for (var node : nodes()) {
                if (node.entry.type().role().requiresFlow()) continue;
                var layer = document.program().graph().edges().stream()
                        .filter(edge -> edge.to().nodeId() == node.id())
                        .filter(edge -> {
                            var source = node(edge.from().nodeId());
                            return source != null && source.schema.output(edge.from().port())
                                    .map(port -> !port.type().equals(ProgramValueTypes.FLOW))
                                    .orElse(false);
                        })
                        .mapToInt(edge -> layers.getOrDefault(edge.from().nodeId(), 0) + 1)
                        .max().orElse(0);
                layers.put(node.id(), layer);
            }
        }
        var maxDataLayer = layers.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        var rows = new HashMap<Integer, Integer>();
        var working = document;
        for (var node : nodes().stream()
                .sorted(Comparator.comparingInt(node -> node.entry.type().role().requiresFlow()
                        ? maxDataLayer + 1 : layers.getOrDefault(node.id(), 0)))
                .toList()) {
            var layer = node.entry.type().role().requiresFlow()
                    ? maxDataLayer + 1 : layers.getOrDefault(node.id(), 0);
            var row = rows.merge(layer, 1, Integer::sum) - 1;
            working = working.moveNode(node.id(), 8 + layer * 108.0, 8 + row * 46.0).orElseThrow();
        }
        install(working, true);
        fitCanvas(false);
    }

    private void fitCanvas(boolean initial) {
        var nodes = nodes();
        if (nodes.isEmpty()) {
            zoom = 1.0;
            panX = 0.0;
            panY = 0.0;
            return;
        }
        var minX = nodes.stream().mapToDouble(node -> node.x).min().orElse(0.0);
        var minY = nodes.stream().mapToDouble(node -> node.y).min().orElse(0.0);
        var maxX = nodes.stream().mapToDouble(node -> node.x + NODE_W).max().orElse(NODE_W);
        var maxY = nodes.stream().mapToDouble(node -> node.y + nodeHeight(node))
                .max().orElse(MIN_NODE_H);
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

    private void undo() {
        if (undo.isEmpty()) return;
        redo.push(document.program());
        setProgram(undo.pop(), false);
    }

    private void redo() {
        if (redo.isEmpty()) return;
        undo.push(document.program());
        setProgram(redo.pop(), false);
    }

    private void save() {
        if (!configurationInputsValid()) {
            showTransient(PrecisionGraph.Diagnostic.INVALID_PARAMETER);
            return;
        }
        var validation = document.validation();
        if (!validation.valid()) {
            showTransient(validation.diagnostics().getFirst());
            return;
        }
        var hasAction = document.program().graph().nodes().stream().anyMatch(node -> {
            if (!session.precisionRules()) {
                var type = definition.nodeLookup().find(node.type());
                return type != null && type.role() == ProgramNodeRole.ACTION;
            }
            var kind = PrecisionProgramNodeIds.kind(node.type());
            return kind != null && kind.isAction() && !kind.isConditionalBranch();
        });
        if (session.precisionRules() && !hasAction) {
            showTransient(PrecisionGraph.Diagnostic.EMPTY_PROGRAM);
            return;
        }
        var locked = session.precisionRules()
                && document.program().graph().nodes().stream().anyMatch(node ->
                node.type().equals(CommonProgramNodeIds.BRANCH)
                        || Optional.ofNullable(PrecisionProgramNodeIds.kind(node.type()))
                        .map(PrecisionGraph.NodeKind::isConditionalBranch).orElse(false));
        if (locked && !branchUnlocked()) {
            showTransient(PrecisionGraph.Diagnostic.PROFICIENCY_REQUIRED);
            return;
        }
        session.saveProgram(
                slot,
                document.program().graph().nodes().isEmpty() ? null : document.program(),
                revision
        );
        revision = session.revision();
    }

    private void pushUndo() {
        undo.push(document.program());
        while (undo.size() > 64) undo.removeLast();
        redo.clear();
    }

    private void setProgram(AbilityProgram program, boolean recordUndo) {
        if (recordUndo) pushUndo();
        document = document(program == null ? session.emptyProgram(slot) : program);
        selectNode(-1);
        selectionDrag = null;
        connection = null;
        quickInsert = null;
        configurationInputValidity.clear();
        serverDiagnostic = session.diagnostic(slot);
        serverDiagnosticNode = session.diagnosticNode(slot);
        session.updateLocalProgram(slot, document.program());
    }

    private void install(ProgramEditorDocument next, boolean clearServerDiagnostic) {
        document = next;
        session.updateLocalProgram(slot, document.program());
        if (clearServerDiagnostic) {
            session.clearDiagnostic(slot);
            serverDiagnostic = PrecisionGraph.Diagnostic.OK;
            serverDiagnosticNode = -1;
        }
    }

    private ProgramEditorDocument document(AbilityProgram program) {
        if (program == null) throw new IllegalArgumentException("Editor program cannot be null");
        return new ProgramEditorDocument(program, definition, capabilities);
    }

    private List<ProgramEditorNodeCatalog.Entry> visibleEntries() {
        var query = search == null ? "" : search.getText().strip().toLowerCase(Locale.ROOT);
        var hasEntry = nodes().stream().anyMatch(node ->
                node.entry.type().role() == ProgramNodeRole.ENTRY);
        return catalog.entries().stream()
                .filter(ProgramEditorNodeCatalog.Entry::visible)
                .filter(entry -> !hasEntry || entry.type().role() != ProgramNodeRole.ENTRY)
                .filter(this::entryUnlocked)
                .filter(entry -> {
                    if (query.isEmpty()) return entry.group() == selectedGroup;
                    return nodeLabel(entry).getString().toLowerCase(Locale.ROOT).contains(query)
                            || nodeDescription(entry).getString().toLowerCase(Locale.ROOT).contains(query)
                            || categoryScopeLabel(entry).getString().toLowerCase(Locale.ROOT).contains(query)
                            || entry.id().toString().toLowerCase(Locale.ROOT).contains(query)
                            || Component.translatable(groupKey(entry.group())).getString()
                            .toLowerCase(Locale.ROOT).contains(query);
                })
                .toList();
    }

    private List<ProgramEditorNodeCatalog.Entry> compatibleEntries(Endpoint anchor) {
        return catalog.entries().stream()
                .filter(ProgramEditorNodeCatalog.Entry::visible)
                .filter(entry -> entry.type().role() != ProgramNodeRole.ENTRY)
                .filter(this::entryUnlocked)
                .filter(entry -> anchor.input
                        ? entry.defaultSchema().outputs().stream().anyMatch(port ->
                        ProgramValueTypes.canConnect(port.type(), anchor.type))
                        : entry.defaultSchema().inputs().stream().anyMatch(port ->
                        ProgramValueTypes.canConnect(anchor.type, port.type())))
                .toList();
    }

    private boolean entryUnlocked(ProgramEditorNodeCatalog.Entry entry) {
        if (!capabilities.containsAll(entry.type().scope().requiredCapabilities())) return false;
        if (!session.precisionRules()) return true;
        var kind = entry.metadata(PrecisionGraph.NodeKind.class).orElse(null);
        return branchUnlocked() || !entry.id().equals(CommonProgramNodeIds.BRANCH)
                && (kind == null || !kind.isConditionalBranch());
    }

    private boolean branchUnlocked() {
        return !session.precisionRules() || ProficiencyPolicy.client().enabled()
                && AbilitySystemClient.getSkillProficiencyMilestone(
                Skills.PRECISION_OPERATION.get()) >= 3;
    }

    private Endpoint firstCompatibleEndpoint(NodeView node, Endpoint anchor) {
        var ports = anchor.input ? node.schema.outputs() : node.schema.inputs();
        for (var port : ports) {
            var compatible = anchor.input
                    ? ProgramValueTypes.canConnect(port.type(), anchor.type)
                    : ProgramValueTypes.canConnect(anchor.type, port.type());
            if (compatible) return new Endpoint(
                    node.id(), port.name(), !anchor.input, port.type());
        }
        return null;
    }

    private Endpoint endpointAt(double mouseX, double mouseY) {
        Endpoint closest = null;
        var best = Double.MAX_VALUE;
        for (var node : nodes()) {
            for (var input : new boolean[]{true, false}) {
                var ports = input ? node.schema.inputs() : node.schema.outputs();
                for (var port : ports) {
                    var endpoint = new Endpoint(node.id(), port.name(), input, port.type());
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

    private Endpoint snappedEndpoint(double mouseX, double mouseY, Endpoint source) {
        Endpoint closest = null;
        var best = Double.MAX_VALUE;
        for (var node : nodes()) {
            var ports = source.input ? node.schema.outputs() : node.schema.inputs();
            for (var port : ports) {
                var candidate = new Endpoint(node.id(), port.name(), !source.input, port.type());
                if (!compatible(source, candidate)) continue;
                var point = endpointScreen(candidate);
                var distance = Math.hypot(mouseX - point.x, mouseY - point.y);
                if (distance <= SNAP_DISTANCE && distance < best) {
                    closest = candidate;
                    best = distance;
                }
            }
        }
        return closest;
    }

    private boolean compatible(Endpoint first, Endpoint second) {
        if (first.nodeId == second.nodeId || first.input == second.input) return false;
        var output = first.input ? second : first;
        var input = first.input ? first : second;
        return ProgramValueTypes.canConnect(output.type, input.type);
    }

    private boolean connectionRejected(Endpoint first, Endpoint second) {
        if (!compatible(first, second)) return true;
        var output = first.input ? second : first;
        var input = first.input ? first : second;
        var working = document;
        for (var edge : List.copyOf(working.program().graph().edges())) {
            if (edge.to().equals(input.graphEndpoint())) {
                working = working.disconnect(edge.from(), edge.to()).orElseThrow();
            }
        }
        var result = working.connect(output.graphEndpoint(), input.graphEndpoint());
        return !result.successful()
                && result.diagnostic().code() == ProgramDiagnosticCode.DATA_CYCLE;
    }

    private boolean endpointConnected(Endpoint endpoint) {
        return document.program().graph().edges().stream().anyMatch(edge -> endpoint.input
                ? edge.to().equals(endpoint.graphEndpoint())
                : edge.from().equals(endpoint.graphEndpoint()));
    }

    private boolean highlightedPort(Endpoint endpoint) {
        if (connection == null || connection.endpoint.input == endpoint.input) return false;
        return compatible(connection.endpoint, endpoint);
    }

    private ScreenPoint endpointScreen(Endpoint endpoint) {
        var node = node(endpoint.nodeId);
        if (node == null) return new ScreenPoint(0, 0);
        var ports = endpoint.input ? node.schema.inputs() : node.schema.outputs();
        var index = portIndex(ports, endpoint.port);
        var x = endpoint.input ? node.x : node.x + NODE_W;
        var y = node.y + portOffsetY(node, Math.max(0, index));
        return new ScreenPoint(
                (int) Math.round(canvasX + panX + x * zoom),
                (int) Math.round(canvasY + panY + y * zoom)
        );
    }

    private boolean insideNode(double mouseX, double mouseY, NodeView node) {
        var x = canvasX + panX + node.x * zoom;
        var y = canvasY + panY + node.y * zoom;
        return inside(mouseX, mouseY, x, y, NODE_W * zoom, nodeHeight(node) * zoom);
    }

    private boolean insideHeader(double mouseX, double mouseY, NodeView node) {
        var x = canvasX + panX + node.x * zoom;
        var y = canvasY + panY + node.y * zoom;
        return inside(mouseX, mouseY, x, y, NODE_W * zoom, NODE_HEADER_H * zoom);
    }

    private int nodeHeight(NodeView node) {
        return Math.max(MIN_NODE_H, NODE_HEADER_H
                + nodeConfigurationHeight(node)
                + Math.max(node.schema.inputs().size(), node.schema.outputs().size()) * PORT_ROW_H + 3);
    }

    private static int portOffsetY(NodeView node, int index) {
        return NODE_HEADER_H + nodeConfigurationHeight(node) + 4 + index * PORT_ROW_H;
    }

    private static int nodeConfigurationHeight(NodeView node) {
        var fieldCount = configurationFields(node).size();
        return fieldCount == 0 ? 0 : 2 + fieldCount * NODE_CONFIGURATION_ROW_H;
    }

    private static int configurationEditorHeight(NodeView node) {
        var fieldCount = configurationFields(node).size();
        return fieldCount == 0 ? 0 : fieldCount * CONFIGURATION_ROW_H + 3;
    }

    private static int portIndex(List<ProgramPortDefinition> ports, String name) {
        for (var index = 0; index < ports.size(); index++) {
            if (ports.get(index).name().equals(name)) return index;
        }
        return -1;
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
        search.setVisibility(paletteVisible() ? Widget.Visibility.VISIBLE : Widget.Visibility.GONE);
        if (!search.isVisible()) search.setFocused(false);
        setTextBoxBounds(search, paletteX() + 4, canvasY + PALETTE_SEARCH_OFFSET_Y,
                paletteWidth() - 8, 15);
    }

    private static void setTextBoxBounds(
            TextBoxWidget input,
            int x,
            int y,
            int width,
            int height
    ) {
        if (Math.round(input.getAbsoluteX()) == x
                && Math.round(input.getAbsoluteY()) == y
                && Math.round(input.getWidth()) == width
                && Math.round(input.getHeight()) == height) return;
        input.setLayoutParams(new FrameLayoutWidget.LayoutParams()
                .size(width, height)
                .gravity(Gravity.TOP_LEFT)
                .margin(x, y, 0, 0)
                .padding(4, 3, 2, 2));
    }

    private static void setTextColor(LabelWidget label, int color) {
        label.setRed((color >>> 16 & 0xFF) / 255.0f);
        label.setGreen((color >>> 8 & 0xFF) / 255.0f);
        label.setBlue((color & 0xFF) / 255.0f);
        label.setAlpha((color >>> 24 & 0xFF) / 255.0f);
    }

    private static String configurationInputName(String field) {
        return "configuration_input_" + field;
    }

    private void syncConfigurationInputs() {
        var selected = node(selectedNode);
        var fields = selected == null ? List.<String>of() : configurationFields(selected);
        var visible = inspectorVisible() && selected != null && !fields.isEmpty();
        if (!visible) {
            clearConfigurationInputs();
            return;
        }
        if (configurationNode != selected.id()) {
            clearConfigurationInputs();
            configurationNode = selected.id();
        }
        var width = inspectorWidth() - 10;
        var descriptionHeight = ProgramUiGraphics.wrappedHeight(
                nodeDescription(selected.entry).getString(), width,
                ProgramUiGraphics.BODY_FONT_SIZE, 9.0f);
        var configurationY = canvasY + inspectorConfigurationOffset(selected.entry, descriptionHeight);
        for (var index = 0; index < fields.size(); index++) {
            var field = fields.get(index);
            var currentValue = selected.source.configuration().getAsJsonObject().get(field);
            var options = ProgramConfigurationOptions.options(selected.entry, field, currentValue);
            if (ProgramConfigurationOptions.isPowerSlider(field, currentValue)
                    || !options.isEmpty()) {
                var removed = configurationInputs.remove(field);
                if (removed != null) {
                    removed.setFocused(false);
                    inputLayer.removeChild(configurationInputName(field));
                }
                configurationInputValidity.remove(field);
                continue;
            }
            var input = configurationInputs.computeIfAbsent(field, ignored -> createConfigurationInput(field));
            input.setVisibility(Widget.Visibility.VISIBLE);
            setTextBoxBounds(input, inspectorX() + 5,
                    configurationY + index * CONFIGURATION_ROW_H + 11, width, 15);
            var expected = currentValue.getAsString();
            if (!input.isFocused() && !input.getText().equals(expected)) {
                updatingConfigurationInput = true;
                input.setText(expected);
                updatingConfigurationInput = false;
                configurationInputValidity.put(field, true);
                setTextColor(input, TEXT);
            }
        }
        for (var field : List.copyOf(configurationInputs.keySet())) {
            if (fields.contains(field)) continue;
            configurationInputs.remove(field).setFocused(false);
            inputLayer.removeChild(configurationInputName(field));
            configurationInputValidity.remove(field);
        }
    }

    private TextBoxWidget createConfigurationInput(String field) {
        var input = new TextBoxWidget(field.equals("selectors") ? 512 : 128);
        input.setBaseFontSize(ProgramUiGraphics.BODY_FONT_SIZE);
        input.setBackground(null);
        input.setCoverAllPrev(true);
        setTextColor(input, TEXT);
        input.setOnTextChanged(value -> configurationInputChanged(field, value));
        inputLayer.addChild(configurationInputName(field), input);
        configurationInputValidity.put(field, true);
        return input;
    }

    private void configurationInputChanged(String field, String value) {
        if (updatingConfigurationInput) return;
        var selected = node(configurationNode);
        if (selected == null) return;
        var fields = configurationFields(selected);
        if (!fields.contains(field)) return;
        var object = selected.source.configuration().getAsJsonObject().deepCopy();
        var previous = object.get(field);
        var input = configurationInputs.get(field);
        if (input == null) return;
        final JsonElement replacement;
        try {
            if (previous.isJsonPrimitive() && previous.getAsJsonPrimitive().isBoolean()) {
                if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
                    throw new IllegalArgumentException("Invalid boolean");
                }
                replacement = new JsonPrimitive(Boolean.parseBoolean(value));
            } else if (previous.isJsonPrimitive() && previous.getAsJsonPrimitive().isNumber()) {
                replacement = new JsonPrimitive(new BigDecimal(value));
            } else {
                replacement = new JsonPrimitive(value);
            }
        } catch (RuntimeException exception) {
            configurationInputValidity.put(field, false);
            setTextColor(input, ERROR);
            return;
        }
        object.add(field, replacement);
        var result = document.configureNode(selected.id(), object);
        configurationInputValidity.put(field, result.successful());
        setTextColor(input, result.successful() ? TEXT : ERROR);
        if (!result.successful()) return;
        pushUndo();
        install(result.document(), true);
    }

    private TextBoxWidget configurationInputAt(double x, double y) {
        for (var input : configurationInputs.values()) {
            if (input.isVisible() && inside(x, y,
                    input.getX(), input.getY(), input.getWidth(), input.getHeight())) {
                return input;
            }
        }
        return null;
    }

    private TextBoxWidget focusedConfigurationInput() {
        return configurationInputs.values().stream()
                .filter(TextBoxWidget::isFocused)
                .findFirst()
                .orElse(null);
    }

    private void unfocusConfigurationInputs() {
        configurationInputs.values().forEach(input -> input.setFocused(false));
    }

    private void clearConfigurationInputs() {
        unfocusConfigurationInputs();
        for (var field : List.copyOf(configurationInputs.keySet())) {
            inputLayer.removeChild(configurationInputName(field));
        }
        configurationInputs.clear();
        configurationInputValidity.clear();
        configurationNode = -1;
    }

    private boolean configurationInputsValid() {
        return configurationInputValidity.values().stream().allMatch(Boolean::booleanValue);
    }

    private static Component configurationFieldLabel(String field) {
        return Component.translatable("screen.academy.program.configuration.field." + field);
    }

    private static Component portLabel(ProgramEditorNodeCatalog.Entry entry, String port) {
        if (port.startsWith("value_")) {
            var suffix = port.substring("value_".length());
            if (suffix.chars().allMatch(Character::isDigit)) {
                return Component.translatable(
                        "screen.academy.program.port.collection_builder_value", suffix);
            }
        }
        return Component.translatable(entry.portTranslationKey(port));
    }

    private static Component configurationDisplayValue(
            NodeView node,
            String field,
            JsonElement currentValue
    ) {
        var options = ProgramConfigurationOptions.options(node.entry, field, currentValue);
        if (ProgramConfigurationOptions.isPowerSlider(field, currentValue)) {
            return Component.literal(String.format(
                    Locale.ROOT, "%.2f", currentValue.getAsFloat()));
        }
        return options.isEmpty()
                ? Component.literal(currentValue == null ? "" : currentValue.getAsString())
                : ProgramConfigurationOptions.selected(options, currentValue).label();
    }

    private static List<String> configurationFields(NodeView node) {
        if (!node.source.configuration().isJsonObject()) return List.of();
        return node.source.configuration().getAsJsonObject().keySet().stream().sorted().toList();
    }

    private void selectNode(int nodeId) {
        selectNodes(nodeId >= 0 ? Set.of(nodeId) : Set.of());
    }

    private void selectNodes(Set<Integer> nodeIds) {
        selectedNodes.clear();
        document.program().graph().nodes().stream()
                .map(ProgramGraph.Node::id)
                .filter(nodeIds::contains)
                .forEach(selectedNodes::add);
        selectedNode = selectedNodes.size() == 1
                ? selectedNodes.iterator().next() : -1;
        clearConfigurationInputs();
    }

    private List<NodeView> nodes() {
        return document.program().graph().nodes().stream()
                .map(this::view)
                .filter(Objects::nonNull)
                .toList();
    }

    private NodeView node(int id) {
        var source = document.program().graph().nodes().stream()
                .filter(node -> node.id() == id).findFirst().orElse(null);
        return source == null ? null : view(source);
    }

    private NodeView view(ProgramGraph.Node source) {
        var entry = catalog.entry(source.type());
        var schema = catalog.schema(
                source.type(), source.configuration());
        if (entry == null || schema == null) return null;
        var position = document.program().editorLayout().nodePositions().get(source.id());
        return new NodeView(source, entry, schema,
                position == null ? 0.0 : position.x(), position == null ? 0.0 : position.y());
    }

    private ProgramDiagnostic firstDiagnostic() {
        var diagnostics = document.validation().diagnostics();
        return diagnostics.isEmpty() ? null : diagnostics.getFirst();
    }

    private void showTransient(ProgramDiagnostic diagnostic) {
        if (diagnostic != null) showTransient(mapDiagnostic(diagnostic.code()));
    }

    private void showTransient(PrecisionGraph.Diagnostic diagnostic) {
        transientDiagnostic = diagnostic;
        transientUntil = System.currentTimeMillis() + 1800L;
    }

    private static PrecisionGraph.Diagnostic mapDiagnostic(ProgramDiagnosticCode code) {
        return switch (code) {
            case EMPTY_PROGRAM -> PrecisionGraph.Diagnostic.EMPTY_PROGRAM;
            case TOO_MANY_NODES -> PrecisionGraph.Diagnostic.TOO_MANY_NODES;
            case TOO_MANY_EDGES -> PrecisionGraph.Diagnostic.TOO_MANY_EDGES;
            case DUPLICATE_NODE -> PrecisionGraph.Diagnostic.DUPLICATE_NODE;
            case DUPLICATE_EDGE -> PrecisionGraph.Diagnostic.DUPLICATE_EDGE;
            case UNKNOWN_PORT -> PrecisionGraph.Diagnostic.INVALID_PORT;
            case TYPE_MISMATCH -> PrecisionGraph.Diagnostic.TYPE_MISMATCH;
            case TOO_MANY_CONNECTIONS -> PrecisionGraph.Diagnostic.MULTIPLE_INPUTS;
            case MISSING_INPUT -> PrecisionGraph.Diagnostic.MISSING_INPUT;
            case DATA_CYCLE -> PrecisionGraph.Diagnostic.CYCLE;
            case NO_ENTRY, MULTIPLE_ENTRIES, INVALID_ENTRY, AMBIGUOUS_FLOW -> PrecisionGraph.Diagnostic.INVALID_FLOW;
            case UNREACHABLE_FLOW_NODE -> PrecisionGraph.Diagnostic.DISCONNECTED_FLOW;
            case INVALID_CONFIGURATION -> PrecisionGraph.Diagnostic.INVALID_PARAMETER;
            default -> PrecisionGraph.Diagnostic.MALFORMED;
        };
    }

    private Component nodeLabel(ProgramEditorNodeCatalog.Entry entry) {
        return Component.translatable(entry.translationKey());
    }

    private Component nodeDescription(ProgramEditorNodeCatalog.Entry entry) {
        return Component.translatable(entry.descriptionTranslationKey());
    }

    private static Component categoryScopeLabel(ProgramEditorNodeCatalog.Entry entry) {
        var category = entry.exclusiveCategory().orElse(null);
        if (category == null) {
            return entry.categoryRestricted()
                    ? Component.translatable("screen.academy.program.node_scope.category_restricted")
                    : Component.empty();
        }
        return Component.translatable(
                "screen.academy.program.node_scope.category_specific",
                categoryName(category)
        );
    }

    private static Component categoryName(Identifier category) {
        return Component.translatable("ability_category."
                + category.getNamespace() + "." + category.getPath());
    }

    private static String categoryGlyph(ProgramEditorNodeCatalog.Entry entry) {
        var category = entry.exclusiveCategory().orElse(null);
        if (category == null) return "*";
        var name = categoryName(category).getString().strip();
        if (name.isEmpty()) return "*";
        var end = name.offsetByCodePoints(0, 1);
        return name.substring(0, end).toUpperCase(Locale.ROOT);
    }

    private static int inspectorDescriptionOffset(ProgramEditorNodeCatalog.Entry entry) {
        return entry.categoryRestricted() ? 44 : 36;
    }

    private static int inspectorConfigurationOffset(
            ProgramEditorNodeCatalog.Entry entry,
            int descriptionHeight
    ) {
        var descriptionOffset = inspectorDescriptionOffset(entry);
        return Math.max(entry.categoryRestricted() ? 66 : 58,
                descriptionHeight + descriptionOffset + 4);
    }

    private static String groupKey(ProgramEditorNodeCatalog.Group group) {
        return "screen.academy.precision_operation.program_group."
                + group.name().toLowerCase(Locale.ROOT);
    }

    private static String groupGlyph(ProgramEditorNodeCatalog.Group group) {
        return switch (group) {
            case TARGET -> "T";
            case COLLECTION -> "S";
            case FILTER -> "F";
            case LOGIC -> "L";
            case FLOW -> ">";
            case ACTION -> "A";
            case VALUE -> "V";
        };
    }

    private static int groupColor(ProgramEditorNodeCatalog.Group group) {
        return switch (group) {
            case TARGET, VALUE -> 0xFFFFFFFF;
            case COLLECTION -> 0xD9FFFFFF;
            case FILTER, LOGIC -> 0xBFFFFFFF;
            case FLOW, ACTION -> 0xF2FFFFFF;
        };
    }

    static int portColor(ProgramValueType type) {
        if (type.equals(ProgramValueTypes.FLOW)) return 0xFF4A9FE8;
        if (type.equals(ProgramValueTypes.BOOLEAN)) return 0xFF8D8FF0;
        if (type.equals(ProgramValueTypes.INTEGER)) return 0xFF62C7E8;
        if (type.equals(ProgramValueTypes.BIG_INTEGER)) return 0xFF56AFD5;
        if (type.equals(ProgramValueTypes.FLOAT)) return 0xFF5DD6C5;
        if (type.equals(ProgramValueTypes.IDENTIFIER)) return 0xFF8FB9D2;
        if (type.equals(ProgramValueTypes.DURATION)) return 0xFF78B7D5;
        if (type.equals(ProgramValueTypes.ENTITY_REFERENCE)
                || type.equals(ProgramValueTypes.LIVING_ENTITY_REFERENCE)) return 0xFFB6D8F2;
        if (type.equals(ProgramValueTypes.ENTITY_SET)
                || type.equals(ProgramValueTypes.LIVING_ENTITY_SET)) return 0xFF8CAFCB;
        if (type.equals(ProgramValueTypes.WORLD_POSITION)) return 0xFF73A7F2;
        if (type.equals(ProgramValueTypes.WORLD_POSITION_SET)) return 0xFF5E8BCB;
        if (type.equals(ProgramValueTypes.BLOCK_POSITION)) return 0xFF7F91D8;
        if (type.equals(ProgramValueTypes.BLOCK_POSITION_SET)) return 0xFF6879B7;
        if (type.equals(ProgramValueTypes.CONTROL_DESTINATION)) return 0xFF8886DC;
        if (type.equals(ProgramValueTypes.DIRECTION)) return 0xFF72D0E4;
        if (type.equals(ProgramValueTypes.DIRECTION_SET)) return 0xFF5CAABD;
        if (type.equals(ProgramValueTypes.ACTION_RESULT)) return 0xFF9AACE0;
        return 0xFF8298AA;
    }

    static int categoryAccent(Identifier category) {
        return switch (category.getPath()) {
            case AbilityCategoryNames.ACCELERATOR -> 0xFFD4DCE2;
            case AbilityCategoryNames.MELTDOWNER -> 0xFF59D68A;
            case AbilityCategoryNames.DARKMATTER -> 0xFFF4F6F7;
            case AbilityCategoryNames.AEROMANIP -> 0xFF8EDCF3;
            case AbilityCategoryNames.ELECTROMASTER -> 0xFF328EE8;
            case AbilityCategoryNames.MENTALOUT -> 0xFFFFB83D;
            case AbilityCategoryNames.TELEPORT -> 0xFFA17BE8;
            default -> DEFAULT_ACCENT;
        };
    }

    private void renderCanvasGrid(ProgramUiGraphics graphics) {
        var firstColumn = (int) Math.floor(screenToGraphX(canvasX) / 16.0);
        var lastColumn = (int) Math.ceil(screenToGraphX(canvasX + canvasW) / 16.0);
        for (var column = firstColumn; column <= lastColumn; column++) {
            var x = (int) Math.round(canvasX + panX + column * 16.0 * zoom);
            graphics.fill(x, canvasY, x + 1, canvasY + canvasH,
                    Math.floorMod(column, 4) == 0 ? GRID_MAJOR : GRID_MINOR);
        }
        var firstRow = (int) Math.floor(screenToGraphY(canvasY) / 16.0);
        var lastRow = (int) Math.ceil(screenToGraphY(canvasY + canvasH) / 16.0);
        for (var row = firstRow; row <= lastRow; row++) {
            var y = (int) Math.round(canvasY + panY + row * 16.0 * zoom);
            graphics.fill(canvasX, y, canvasX + canvasW, y + 1,
                    Math.floorMod(row, 4) == 0 ? GRID_MAJOR : GRID_MINOR);
        }
    }

    private void smallText(
            ProgramUiGraphics graphics,
            String value,
            int x,
            int y,
            int color,
            int maxWidth
    ) {
        graphics.text(value, x, y, color, ProgramUiGraphics.BODY_FONT_SIZE, maxWidth);
    }

    private static void headingText(
            ProgramUiGraphics graphics,
            String value,
            int x,
            int y,
            int color,
            int maxWidth
    ) {
        graphics.text(value, x, y, color, ProgramUiGraphics.HEADING_FONT_SIZE, maxWidth);
    }

    private int smallWrappedText(
            ProgramUiGraphics graphics,
            Component value,
            int x,
            int y,
            int color,
            int maxWidth
    ) {
        var lines = ProgramUiGraphics.wrap(
                value.getString(), maxWidth, ProgramUiGraphics.BODY_FONT_SIZE);
        for (var index = 0; index < lines.size(); index++) {
            graphics.text(lines.get(index), x, y + index * 9, color,
                    ProgramUiGraphics.BODY_FONT_SIZE, maxWidth);
        }
        return lines.size() * 9;
    }

    private void button(
            ProgramUiGraphics graphics,
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
        renderControl(graphics, x, y, width, height, true, selected, hover);
        if (small) smallText(graphics, label.getString(), x + 3, y + 5, TEXT, width - 6);
        else graphics.centeredText(label.getString(), x + width / 2.0f,
                y + Math.max(1, (height - 8) / 2.0f), TEXT,
                ProgramUiGraphics.BODY_FONT_SIZE, width - 3);
    }

    private void iconButton(
            ProgramUiGraphics graphics,
            int x,
            int y,
            String glyph,
            int mouseX,
            int mouseY,
            boolean disabled
    ) {
        var hover = !disabled && inside(mouseX, mouseY, x, y, TOOL_SIZE, TOOL_SIZE);
        renderControl(graphics, x, y, TOOL_SIZE, TOOL_SIZE, !disabled, false, hover);
        smallText(graphics, glyph, x + 4, y + 4,
                disabled ? DISABLED : hover ? TEXT : DIM, 8);
    }

    private static void renderInstrumentFrame(
            ProgramUiGraphics graphics,
            int x,
            int y,
            int width,
            int height
    ) {
        graphics.fill(x + 4, y, x + width - 4, y + 1, BORDER);
        graphics.fill(x + 4, y + height - 1, x + width - 4, y + height, BORDER);
        graphics.fill(x, y + 4, x + 1, y + 18, BORDER_MUTED);
        graphics.fill(x, y + height - 18, x + 1, y + height - 4, BORDER_MUTED);
        graphics.fill(x + width - 1, y + 4, x + width, y + 18, BORDER_MUTED);
        graphics.fill(x + width - 1, y + height - 18, x + width, y + height - 4, BORDER_MUTED);
    }

    private static void renderSection(
            ProgramUiGraphics graphics,
            int x,
            int y,
            int width,
            int height
    ) {
        graphics.fill(x, y, x + width, y + height, SECTION_BACKGROUND);
        graphics.fill(x, y, x + width, y + 1, BORDER_MUTED);
        graphics.fill(x, y + height - 1, x + width, y + height, BORDER_MUTED);
    }

    private void renderInput(
            ProgramUiGraphics graphics,
            int x,
            int y,
            int width,
            int height,
            boolean focused
    ) {
        graphics.fill(x, y, x + width, y + height, INPUT_BACKGROUND);
        graphics.fill(x, y + height - 1, x + width, y + height,
                focused ? accentColor : BORDER_MUTED);
        if (focused) graphics.fill(x, y, x + 2, y + height, accentColor);
    }

    private void renderControl(
            ProgramUiGraphics graphics,
            int x,
            int y,
            int width,
            int height,
            boolean enabled,
            boolean selected,
            boolean hovered
    ) {
        var background = !enabled ? 0x0C000000
                : selected ? SELECTED_BACKGROUND : hovered ? HOVER_BACKGROUND : CONTROL_BACKGROUND;
        graphics.fill(x, y, x + width, y + height, background);
        if (selected) {
            graphics.fill(x, y, x + 2, y + height, accentColor);
            graphics.fill(x + 2, y + height - 1, x + width, y + height, accentColor);
        } else if (hovered) {
            graphics.fill(x, y + height - 1, x + width, y + height, TEXT);
        } else {
            graphics.fill(x, y + height - 1, x + width, y + height, BORDER_MUTED);
        }
    }

    private static void orthogonalLine(
            ProgramUiGraphics graphics,
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

    private static void line(
            ProgramUiGraphics graphics,
            int x1,
            int y1,
            int x2,
            int y2,
            int color
    ) {
        graphics.fill(Math.min(x1, x2), Math.min(y1, y2),
                Math.max(x1, x2) + 1, Math.max(y1, y2) + 1, color);
    }

    private static void border(
            ProgramUiGraphics graphics,
            int x,
            int y,
            int width,
            int height,
            int color
    ) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }

    private static boolean inside(
            double x,
            double y,
            double left,
            double top,
            double width,
            double height
    ) {
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
        Collections.reverse(result);
        return result;
    }

    private record TooltipLine(String text, int color) {
    }

    private record NodeView(
            ProgramGraph.Node source,
            ProgramEditorNodeCatalog.Entry entry,
            ProgramNodeSchema schema,
            double x,
            double y
    ) {
        private int id() {
            return source.id();
        }
    }

    private record Endpoint(
            int nodeId,
            String port,
            boolean input,
            ProgramValueType type
    ) {
        private ProgramGraph.Endpoint graphEndpoint() {
            return new ProgramGraph.Endpoint(nodeId, port);
        }
    }

    private record ConnectionDrag(Endpoint endpoint, double startX, double startY) {
    }

    private record SelectionDrag(
            double startX,
            double startY,
            double currentX,
            double currentY
    ) {
        private SelectionDrag update(double x, double y) {
            return new SelectionDrag(startX, startY, x, y);
        }

        private PrecisionEditorGeometry.SelectionBounds bounds() {
            return PrecisionEditorGeometry.selectionBounds(startX, startY, currentX, currentY);
        }
    }

    private record QuickInsert(
            int x,
            int y,
            List<ProgramEditorNodeCatalog.Entry> entries,
            Endpoint anchor
    ) {
    }

    private record ScreenPoint(int x, int y) {
    }

    private record Rect(int x, int y, int width, int height) {
    }
}
