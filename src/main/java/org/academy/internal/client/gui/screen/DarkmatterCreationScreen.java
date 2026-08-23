package org.academy.internal.client.gui.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.academy.api.client.gui.drawable.ColorDrawable;
import org.academy.api.client.gui.drawable.StateListDrawable;
import org.academy.api.client.gui.layout.Gravity;
import org.academy.api.client.gui.layout.SizeMode;
import org.academy.api.client.gui.screen.UiScreen;
import org.academy.api.client.gui.widget.BlendQuadWidget;
import org.academy.api.client.gui.widget.ButtonWidget;
import org.academy.api.client.gui.widget.FillWidget;
import org.academy.api.client.gui.widget.FrameLayoutWidget;
import org.academy.api.client.gui.widget.LabelWidget;
import org.academy.api.client.gui.widget.SeekBarWidget;
import org.academy.api.client.gui.widget.TextBoxWidget;
import org.academy.api.client.gui.widget.Widget;
import org.academy.api.common.ability.darkmatter.DarkmatterCreatureRegistries;
import org.academy.internal.common.ability.darkmatter.creature.DarkmatterCreatureBlueprint;
import org.academy.internal.common.ability.darkmatter.skills.lv4.DarkmatterCreation;
import org.academy.internal.common.world.entity.EntityTypes;
import org.academy.internal.common.world.entity.ability.DarkmatterBeetle;
import org.misaka.MisakaNetworkClient;

import java.util.ArrayList;
import java.util.List;

/** Four-slot creature editor implemented exclusively with the Academy widget/event tree. */
public final class DarkmatterCreationScreen extends UiScreen {
    private static final int PANEL_W = 540;
    private static final int PANEL_H = 320;
    private static final int ACCENT = 0xFF55C8E8;
    private static final int CONTROL = 0x45101820;
    private static final int CONTROL_HOVER = 0x70465A64;
    private static final int CONTROL_ACTIVE = 0x9855C8E8;
    private static final int DANGER = 0xA0502028;
    private static final String[] HEADS = {
            DarkmatterCreatureRegistries.HEAD_JAW.toString(),
            DarkmatterCreatureRegistries.HEAD_CANNON.toString(),
            DarkmatterCreatureRegistries.HEAD_HOMING.toString()};
    private static final String[] TORSOS = {
            DarkmatterCreatureRegistries.TORSO_WALK.toString(),
            DarkmatterCreatureRegistries.TORSO_FLY.toString(),
            DarkmatterCreatureRegistries.TORSO_SWIM.toString()};
    private static final String[] LIMBS = {
            DarkmatterCreatureRegistries.LIMBS_GUARD.toString(),
            DarkmatterCreatureRegistries.LIMBS_MINER.toString(),
            DarkmatterCreatureRegistries.LIMBS_CARRIER.toString()};
    private static final String[] ADDITIONAL = {
            DarkmatterCreatureRegistries.ADDITIONAL_NONE.toString(),
            DarkmatterCreatureRegistries.ADDITIONAL_CARAPACE.toString(),
            DarkmatterCreatureRegistries.ADDITIONAL_SENSOR.toString(),
            DarkmatterCreatureRegistries.ADDITIONAL_WEAPON.toString()};
    private static final String[] MODULES = {
            DarkmatterCreatureRegistries.MODULE_GUARD.toString(),
            DarkmatterCreatureRegistries.MODULE_FOCUS.toString(),
            DarkmatterCreatureRegistries.MODULE_PICKUP.toString(),
            DarkmatterCreatureRegistries.MODULE_EXCAVATION.toString(),
            DarkmatterCreatureRegistries.MODULE_SCOUT.toString(),
            DarkmatterCreatureRegistries.MODULE_SELF_REPAIR.toString(),
            DarkmatterCreatureRegistries.MODULE_FORMATION.toString()};

    private static DarkmatterCreation.EditorSnapshotPacket latest;
    private DarkmatterCreation.EditorSnapshotPacket snapshot;
    private DarkmatterCreatureBlueprint editing;
    private int selectedSlot;
    private Tab tab = Tab.BLUEPRINT;
    private boolean dirty;
    private int rosterPage;
    private int widgetCounter;
    private TextBoxWidget nameBox;
    private FrameLayoutWidget panel;
    private DarkmatterBeetle previewEntity;
    private int panelWidth = PANEL_W;
    private int panelHeight = PANEL_H;
    private int panelX;
    private int panelY;
    private boolean compact;
    private String summonStatus;
    private final List<ModuleHoverTarget> moduleHoverTargets = new ArrayList<>();

    public DarkmatterCreationScreen() {
        super(Component.translatable("screen.academy.darkmatter_creation.title"));
        snapshot = latest;
        if (snapshot == null) {
            var defaults = new ArrayList<DarkmatterCreatureBlueprint>();
            for (var slot = 0; slot < 4; slot++) {
                defaults.add(DarkmatterCreatureBlueprint.defaultFor(slot, 4));
            }
            snapshot = new DarkmatterCreation.EditorSnapshotPacket(0, 4, 0, defaults, List.of());
        }
        selectedSlot = snapshot.selectedSlot;
        editing = blueprint(selectedSlot);
    }

    public static void acceptSnapshot(DarkmatterCreation.EditorSnapshotPacket packet) {
        latest = packet;
        Minecraft.getInstance().execute(() -> {
            if (Minecraft.getInstance().gui.screen() instanceof DarkmatterCreationScreen screen) {
                screen.applySnapshot(packet);
            }
        });
    }

    public static void acceptRosterDelta(DarkmatterCreation.RosterDeltaPacket packet) {
        Minecraft.getInstance().execute(() -> {
            var current = latest;
            if (current == null || packet.baseRevision != current.revision
                    || packet.revision <= packet.baseRevision) {
                MisakaNetworkClient.send(DarkmatterCreation.EditorRequestPacket.INSTANCE);
                return;
            }
            var next = new DarkmatterCreation.EditorSnapshotPacket(packet.revision,
                    current.abilityLevel, current.selectedSlot, current.blueprints, packet.roster);
            latest = next;
            if (Minecraft.getInstance().gui.screen() instanceof DarkmatterCreationScreen screen) {
                screen.applySnapshot(next);
            }
        });
    }

    public static void acceptSummonResult(DarkmatterCreation.SummonResultPacket packet) {
        Minecraft.getInstance().execute(() -> {
            var minecraft = Minecraft.getInstance();
            var message = Component.translatable(packet.result.translationKey());
            if (minecraft.player != null) minecraft.player.sendOverlayMessage(message);
            if (minecraft.gui.screen() instanceof DarkmatterCreationScreen screen) {
                screen.summonStatus = message.getString();
                if (packet.result == DarkmatterCreation.SummonResult.SUMMONED) {
                    screen.tab = Tab.SUMMONED;
                    screen.rosterPage = 0;
                }
                screen.rebuild();
            }
        });
    }

    private void applySnapshot(DarkmatterCreation.EditorSnapshotPacket packet) {
        snapshot = packet;
        if (!dirty) {
            selectedSlot = packet.selectedSlot;
            editing = blueprint(selectedSlot);
            rebuild();
        } else if (tab == Tab.SUMMONED) {
            rebuild();
        }
    }

    private void rebuild() {
        clearWidgets();
        init();
    }

    @Override
    protected void onInit() {
        widgetCounter = 0;
        moduleHoverTargets.clear();
        nameBox = null;
        panelWidth = Math.min(PANEL_W, Math.max(300, width - 12));
        panelHeight = Math.min(PANEL_H, Math.max(220, height - 12));
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;
        compact = panelWidth < 500 || panelHeight < 285;

        panel = new FrameLayoutWidget();
        panel.setLayoutParams(new FrameLayoutWidget.LayoutParams()
                .size(panelWidth, panelHeight).gravity(Gravity.CENTER));
        var background = new BlendQuadWidget();
        background.setAlpha(0.43f);
        background.setDrawLine(false);
        background.setLayoutParams(new FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT));
        panel.addChild("background", background);
        addRule(8, 0, panelWidth - 16, 1, 0xE6FFFFFF);
        addRule(12, 57, panelWidth - 24, 1, 0x60FFFFFF);

        var title = label(Component.translatable("screen.academy.darkmatter_creation.title").getString(),
                13, 9);
        title.setRed(((ACCENT >> 16) & 0xFF) / 255.0f);
        title.setGreen(((ACCENT >> 8) & 0xFF) / 255.0f);
        title.setBlue((ACCENT & 0xFF) / 255.0f);
        getRoot().addChild("darkmatter_creation", panel);

        for (var slot = 0; slot < 4; slot++) {
            var value = slot;
            addButton(panelWidth - 182 + slot * 40, 8, 34, 18,
                    Integer.toString(slot + 1), () -> selectSlot(value), slot == selectedSlot, false);
        }
        var tabs = Tab.values();
        var tabGap = 3;
        var tabWidth = (panelWidth - 24 - tabGap * (tabs.length - 1)) / tabs.length;
        for (var i = 0; i < tabs.length; i++) {
            var value = tabs[i];
            addButton(12 + i * (tabWidth + tabGap), 32, tabWidth, 19,
                    Component.translatable(value.key).getString(), () -> {
                        commitName();
                        tab = value;
                        rebuild();
                    }, tab == value, false);
        }

        switch (tab) {
            case BLUEPRINT -> buildBlueprintTab();
            case PARTS -> buildPartsTab();
            case PHASE -> buildPhaseTab();
            case MODULES -> buildModulesTab();
            case SUMMONED -> buildSummonedTab();
        }
        if (tab != Tab.SUMMONED) {
            addButton(panelWidth - 210, panelHeight - 29, 92, 20,
                    tr("screen.academy.darkmatter_creation.save"), this::save, false, false);
            addButton(panelWidth - 112, panelHeight - 29, 100, 20,
                    tr("screen.academy.darkmatter_creation.summon"), this::saveAndSummon, false, false);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                   float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        extractCreaturePreview(graphics, mouseX, mouseY);
        extractModuleTooltip(graphics, mouseX, mouseY);
    }

    private void extractCreaturePreview(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (compact || tab == Tab.MODULES || tab == Tab.SUMMONED
                || Minecraft.getInstance().level == null) return;
        if (previewEntity == null || previewEntity.level() != Minecraft.getInstance().level) {
            previewEntity = new DarkmatterBeetle(EntityTypes.DARKMATTER_BEETLE.get(),
                    Minecraft.getInstance().level);
            // InventoryScreen's living-entity preview now resolves held-item models with the
            // entity id as part of the model seed. This preview is deliberately not inserted
            // into the client level, so give it a stable, non-world id before extraction.
            previewEntity.setId(Integer.MIN_VALUE + 1);
        }
        previewEntity.applyBlueprint(editing, selectedSlot, snapshot.abilityLevel, 0, false);
        InventoryScreen.extractEntityInInventoryFollowsMouse(graphics,
                panelX + panelWidth - 184, panelY + 66,
                panelX + panelWidth - 18, panelY + panelHeight - 42,
                62, 0.0f, mouseX, mouseY, previewEntity);
    }

    private void extractModuleTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (tab != Tab.MODULES) return;
        for (var target : moduleHoverTargets) {
            if (mouseX < panelX + target.x || mouseX >= panelX + target.x + target.width
                    || mouseY < panelY + target.y || mouseY >= panelY + target.y + target.height) {
                continue;
            }
            graphics.setComponentTooltipForNextFrame(font, List.of(
                    Component.literal(moduleName(target.module)),
                    Component.literal(moduleDescription(target.module)).withColor(0xFF9AA4AA)
            ), mouseX, mouseY);
            return;
        }
    }

    private void buildBlueprintTab() {
        var inputY = compact ? 70 : 82;
        nameBox = new TextBoxWidget(32);
        nameBox.setText(editing.name());
        nameBox.setPlaceholder(tr("screen.academy.darkmatter_creation.name"));
        nameBox.setClearWhenEnter(false);
        nameBox.setWhenEnter(_ -> commitName());
        nameBox.setOnFocusLost(this::commitName);
        nameBox.setBackground(new ColorDrawable(CONTROL));
        nameBox.setLayoutParams(new FrameLayoutWidget.LayoutParams()
                .size(compact ? panelWidth - 52 : 238, 20)
                .gravity(Gravity.TOP_LEFT).margin(26, inputY, 0, 0).paddingLeft(5));
        panel.addChild("name", nameBox);

        var investmentY = compact ? 104 : 124;
        addButton(26, investmentY, 76, 20, "− 5 MP", () -> changeInvestment(-5), false, false);
        addButton(188, investmentY, 76, 20, "+ 5 MP", () -> changeInvestment(5), false, false);
        addLabel("screen.academy.darkmatter_creation.investment", 114, investmentY + 6,
                "  " + editing.investment() + " MP");
        var strength = editing.effectiveInvestment(0) / 5.0;
        addLabelLiteral(tr("screen.academy.darkmatter_creation.stats",
                String.format("%.0f", 8 + 2 * strength),
                String.format("%.1f", 2 + .4 * strength),
                String.format("%.1f", Math.min(20, .5 * strength)),
                String.format("%.3f", .20 + .004 * strength)),
                26, compact ? 137 : 170);
        addLabelLiteral(tr("screen.academy.darkmatter_creation.module_usage",
                        editing.moduleCost(), editing.moduleBudget()),
                26, compact ? 157 : 191);
        var errors = editing.validate(snapshot.abilityLevel);
        addLabelLiteral(fit(summonStatus != null ? summonStatus : errors.isEmpty()
                        ? tr("screen.academy.darkmatter_creation.valid")
                        : tr("screen.academy.darkmatter_creation.invalid",
                        errors.stream().map(this::validationError).toList().stream()
                                .collect(java.util.stream.Collectors.joining(", "))),
                        compact ? panelWidth - 52 : 306),
                26, compact ? 177 : 220);
    }

    private void buildPartsTab() {
        var firstY = compact ? 68 : 78;
        var gap = compact ? 28 : 35;
        addCycleButton(24, firstY, "screen.academy.darkmatter_creation.part.head",
                editing.head(), HEADS, value -> replaceParts(
                value, editing.torso(), editing.limbs(), editing.additional()));
        addCycleButton(24, firstY + gap, "screen.academy.darkmatter_creation.part.torso",
                editing.torso(), TORSOS, value -> replaceParts(
                editing.head(), value, editing.limbs(), editing.additional()));
        addCycleButton(24, firstY + gap * 2, "screen.academy.darkmatter_creation.part.limbs",
                editing.limbs(), LIMBS, value -> replaceParts(
                editing.head(), editing.torso(), value, editing.additional()));
        addCycleButton(24, firstY + gap * 3, "screen.academy.darkmatter_creation.part.additional",
                editing.additional(), ADDITIONAL,
                value -> replaceParts(editing.head(), editing.torso(), editing.limbs(), value));
    }

    private void buildPhaseTab() {
        var firstY = compact ? 68 : 75;
        var gap = compact ? 31 : 40;
        addPhaseSlider(24, firstY, tr("screen.academy.darkmatter_creation.part.head"),
                editing.headAlpha(), 0);
        addPhaseSlider(24, firstY + gap, tr("screen.academy.darkmatter_creation.part.torso"),
                editing.torsoAlpha(), 1);
        addPhaseSlider(24, firstY + gap * 2, tr("screen.academy.darkmatter_creation.part.limbs"),
                editing.limbsAlpha(), 2);
        addPhaseSlider(24, firstY + gap * 3,
                tr("screen.academy.darkmatter_creation.part.additional"),
                editing.additionalAlpha(), 3);
        addLabelLiteral(tr("screen.academy.darkmatter_creation.phase_pool",
                snapshot.abilityLevel * 50), 24, compact ? 192 : 238);
    }

    private void buildModulesTab() {
        var columns = compact ? 3 : 2;
        var columnWidth = (panelWidth - 48 - (columns - 1) * 8) / columns;
        var rowGap = compact ? 28 : 35;
        for (var i = 0; i < MODULES.length; i++) {
            var module = MODULES[i];
            var enabled = editing.modules().contains(module);
            var x = 24 + (i % columns) * (columnWidth + 8);
            var y = 73 + (i / columns) * rowGap;
            addButton(x, y, columnWidth, 21,
                    fit(tr("screen.academy.darkmatter_creation.module_entry",
                            moduleName(module), moduleCost(module)), columnWidth - 8),
                    () -> toggleModule(module), enabled, false);
            moduleHoverTargets.add(new ModuleHoverTarget(x, y, columnWidth, 21, module));
        }
        addLabelLiteral(tr("screen.academy.darkmatter_creation.module_budget",
                        editing.moduleCost(), editing.moduleBudget()),
                24, compact ? panelHeight - 58 : 222);
    }

    private void buildSummonedTab() {
        var roster = snapshot.roster;
        var rowsPerPage = compact ? Math.max(1, Math.min(4, (panelHeight - 110) / 32)) : 6;
        var from = Math.min(roster.size(), rosterPage * rowsPerPage);
        var to = Math.min(roster.size(), from + rowsPerPage);
        if (roster.isEmpty()) addLabel("screen.academy.darkmatter_creation.empty", 24, 86, "");
        for (var i = from; i < to; i++) {
            var entry = roster.get(i);
            var rowY = 69 + (i - from) * 32;
            addRule(20, rowY + 25, panelWidth - 40, 1, 0x28FFFFFF);
            var dimension = dimensionName(entry.dimension());
            var position = entry.loaded()
                    ? (entry.distance() < 0 ? dimension
                    : tr("screen.academy.darkmatter_creation.roster.distance",
                    String.format("%.1f", entry.distance())))
                    : tr("screen.academy.darkmatter_creation.roster.unloaded", dimension);
            var rowText = tr("screen.academy.darkmatter_creation.roster.row",
                    entry.name(), Math.round(entry.health()), Math.round(entry.maxHealth()),
                    position, entry.investment(), entry.slot() + 1);
            addLabelLiteral(font.plainSubstrByWidth(rowText, panelWidth - 150), 24, rowY + 6);
            addButton(panelWidth - 108, rowY, 82, 20,
                    tr("screen.academy.darkmatter_creation.dismantle"),
                    () -> MisakaNetworkClient.send(
                            new DarkmatterCreation.DismantlePacket(false, entry.uuid())),
                    false, true);
        }
        var controlsY = panelHeight - 29;
        addButton(24, controlsY, 30, 18, "<", () -> {
            rosterPage = Math.max(0, rosterPage - 1);
            rebuild();
        }, false, false);
        addButton(58, controlsY, 30, 18, ">", () -> {
            rosterPage = Math.min(Math.max(0, (roster.size() - 1) / rowsPerPage), rosterPage + 1);
            rebuild();
        }, false, false);
        addButton(panelWidth / 2 - 66, controlsY, 132, 18,
                tr("screen.academy.darkmatter_creation.dismantle_all"),
                () -> MisakaNetworkClient.send(new DarkmatterCreation.DismantlePacket(true, null)),
                false, true);
    }

    private void addCycleButton(int x, int y, String labelKey, String current, String[] values,
                                java.util.function.Consumer<String> setter) {
        addButton(x, y, compact ? panelWidth - 48 : 300, 24,
                tr(labelKey) + "  ·  " + partName(current), () -> {
                    setter.accept(values[(indexOf(values, current) + 1) % values.length]);
                    dirty = true;
                    rebuild();
                }, false, false);
        if (!compact) {
            addLabelLiteral(fit(partDescription(current), 292), x + 4, y + 25);
        }
    }

    private void addPhaseSlider(int x, int y, String text, int alpha, int part) {
        var total = Math.max(1, snapshot.abilityLevel * 50);
        var label = addLabelLiteral(phaseLabel(text, alpha, total), x, y);
        var slider = new SeekBarWidget();
        slider.setMin(0);
        slider.setMax(total);
        slider.setBarColors(0x50101820, ACCENT);
        slider.setLayoutParams(new FrameLayoutWidget.LayoutParams()
                .size(compact ? panelWidth - 48 : 300, 7)
                .gravity(Gravity.TOP_LEFT).margin(x, y + 13, 0, 0));
        slider.setOnSeekBarChangeListener(new SeekBarWidget.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBarWidget seekBar, float progress, boolean fromUser) {
                if (!fromUser) return;
                var points = Math.round(progress);
                label.setText(phaseLabel(text, points, total));
                updatePartPhase(part, points);
            }

            @Override
            public void onStartTrackingTouch(SeekBarWidget seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBarWidget seekBar) {
            }
        });
        slider.setProgress(alpha);
        panel.addChild("phase_" + widgetCounter++, slider);
    }

    private void updatePartPhase(int part, int points) {
        editing = switch (part) {
            case 0 -> copy(editing.head(), editing.torso(), editing.limbs(), editing.additional(),
                    points, editing.torsoAlpha(), editing.limbsAlpha(), editing.additionalAlpha(), editing.modules());
            case 1 -> copy(editing.head(), editing.torso(), editing.limbs(), editing.additional(),
                    editing.headAlpha(), points, editing.limbsAlpha(), editing.additionalAlpha(), editing.modules());
            case 2 -> copy(editing.head(), editing.torso(), editing.limbs(), editing.additional(),
                    editing.headAlpha(), editing.torsoAlpha(), points, editing.additionalAlpha(), editing.modules());
            default -> copy(editing.head(), editing.torso(), editing.limbs(), editing.additional(),
                    editing.headAlpha(), editing.torsoAlpha(), editing.limbsAlpha(), points, editing.modules());
        };
        dirty = true;
    }

    private static String phaseLabel(String label, int alpha, int total) {
        var alphaPercent = Math.round(alpha * 100.0f / total);
        return tr("screen.academy.darkmatter_creation.phase_value",
                label, alphaPercent, 100 - alphaPercent);
    }

    private ButtonWidget addButton(int x, int y, int width, int height, String text,
                                   Runnable action, boolean selected, boolean danger) {
        var button = new ButtonWidget();
        button.setLayoutParams(new FrameLayoutWidget.LayoutParams().size(width, height)
                .gravity(Gravity.TOP_LEFT).margin(x, y, 0, 0));
        var background = new StateListDrawable();
        background.setDefault(new ColorDrawable(danger ? 0x60402028 : CONTROL));
        background.addState(Widget.SELECTED, new ColorDrawable(CONTROL_ACTIVE));
        background.addState(Widget.HOVERED, new ColorDrawable(danger ? DANGER : CONTROL_HOVER));
        background.addState(Widget.PRESSED, new ColorDrawable(danger ? 0xD0783038 : CONTROL_ACTIVE));
        button.setBackground(background);
        button.setSelected(selected);
        button.setOnClickListener(_ -> action.run());
        var rail = new FillWidget(selected ? ACCENT : 0x55FFFFFF);
        rail.setLayoutParams(new FrameLayoutWidget.LayoutParams()
                .size(selected ? 2 : 1, Math.max(1, height - 6))
                .gravity(Gravity.CENTER_LEFT).marginLeft(2));
        button.addChild("rail", rail);
        var label = new LabelWidget(text);
        label.setBaseFontSize(7.5f);
        label.setLayoutParams(new FrameLayoutWidget.LayoutParams()
                .sizeMode(SizeMode.MATCH_PARENT).gravity(Gravity.CENTER));
        button.addChild("label", label);
        panel.addChild("button_" + widgetCounter++, button);
        return button;
    }

    private void addRule(int x, int y, int width, int height, int color) {
        var rule = new FillWidget(color);
        rule.setLayoutParams(new FrameLayoutWidget.LayoutParams().size(width, height)
                .gravity(Gravity.TOP_LEFT).margin(x, y, 0, 0));
        panel.addChild("rule_" + widgetCounter++, rule);
    }

    private LabelWidget label(String value, int x, int y) {
        var label = new LabelWidget(value);
        label.setBaseFontSize(8.0f);
        label.setLayoutParams(new FrameLayoutWidget.LayoutParams()
                .gravity(Gravity.TOP_LEFT).margin(x, y, 0, 0));
        panel.addChild("label_" + widgetCounter++, label);
        return label;
    }

    private void addLabel(String key, int x, int y, String suffix) {
        addLabelLiteral(tr(key) + suffix, x, y);
    }

    private LabelWidget addLabelLiteral(String value, int x, int y) {
        return label(value, x, y);
    }

    private static String tr(String key) {
        return Component.translatable(key).getString();
    }

    private static String tr(String key, Object... arguments) {
        return Component.translatable(key, arguments).getString();
    }

    private String fit(String value, int width) {
        return font.plainSubstrByWidth(value, Math.max(1, width));
    }

    private static String partName(String raw) {
        var id = Identifier.tryParse(raw);
        if (id == null) return shortId(raw);
        return DarkmatterCreatureRegistries.part(id)
                .map(type -> localizedOrFallback(type.translationKey(), shortId(raw)))
                .orElseGet(() -> shortId(raw));
    }

    private static String partDescription(String raw) {
        var id = Identifier.tryParse(raw);
        if (id == null) return tr("screen.academy.darkmatter_creation.missing_part", shortId(raw));
        return DarkmatterCreatureRegistries.part(id)
                .map(type -> localizedOrFallback(type.descriptionTranslationKey(),
                        tr("screen.academy.darkmatter_creation.missing_description")))
                .orElseGet(() -> tr("screen.academy.darkmatter_creation.missing_part", shortId(raw)));
    }

    private static String moduleName(String raw) {
        var id = Identifier.tryParse(raw);
        if (id == null) return shortId(raw);
        return DarkmatterCreatureRegistries.module(id)
                .map(type -> localizedOrFallback(type.translationKey(), shortId(raw)))
                .orElseGet(() -> shortId(raw));
    }

    private static String moduleDescription(String raw) {
        var id = Identifier.tryParse(raw);
        if (id == null) return tr("screen.academy.darkmatter_creation.missing_part", shortId(raw));
        return DarkmatterCreatureRegistries.module(id)
                .map(type -> localizedOrFallback(type.descriptionTranslationKey(),
                        tr("screen.academy.darkmatter_creation.missing_description")))
                .orElseGet(() -> tr("screen.academy.darkmatter_creation.error.module", shortId(raw)));
    }

    private static int moduleCost(String raw) {
        var id = Identifier.tryParse(raw);
        return id == null ? 0 : DarkmatterCreatureRegistries.module(id)
                .map(type -> type.budgetCost()).orElse(0);
    }

    private static String localizedOrFallback(String key, String fallback) {
        var localized = I18n.get(key);
        return localized.equals(key) ? fallback : localized;
    }

    private static String dimensionName(String raw) {
        var id = Identifier.tryParse(raw);
        if (id == null) return raw;
        return localizedOrFallback("dimension." + id.getNamespace() + "." + id.getPath(), raw);
    }

    private String validationError(String error) {
        if (error.startsWith("module_budget:")) {
            var values = error.substring("module_budget:".length()).split("/", 2);
            return tr("screen.academy.darkmatter_creation.error.module_budget",
                    values.length > 0 ? values[0] : "?", values.length > 1 ? values[1] : "?");
        }
        if (error.startsWith("module:")) {
            return tr("screen.academy.darkmatter_creation.error.module",
                    moduleName(error.substring("module:".length())));
        }
        var key = "screen.academy.darkmatter_creation.error." + error;
        return localizedOrFallback(key, error);
    }

    private void selectSlot(int slot) {
        commitName();
        selectedSlot = Math.clamp(slot, 0, 3);
        editing = blueprint(selectedSlot);
        dirty = false;
        summonStatus = null;
        rebuild();
    }

    private DarkmatterCreatureBlueprint blueprint(int slot) {
        if (snapshot.blueprints.size() > slot) return snapshot.blueprints.get(slot).copy();
        return DarkmatterCreatureBlueprint.defaultFor(slot, snapshot.abilityLevel);
    }

    private void commitName() {
        if (nameBox != null && !nameBox.getText().equals(editing.name())) {
            editing = new DarkmatterCreatureBlueprint(nameBox.getText(), editing.investment(),
                    editing.head(), editing.torso(), editing.limbs(), editing.additional(),
                    editing.headAlpha(), editing.torsoAlpha(), editing.limbsAlpha(),
                    editing.additionalAlpha(), editing.modules());
            dirty = true;
        }
    }

    private void changeInvestment(int delta) {
        commitName();
        var value = Math.clamp(editing.investment() + delta, 5, snapshot.abilityLevel * 25);
        editing = new DarkmatterCreatureBlueprint(editing.name(), value, editing.head(),
                editing.torso(), editing.limbs(), editing.additional(), editing.headAlpha(),
                editing.torsoAlpha(), editing.limbsAlpha(), editing.additionalAlpha(), editing.modules());
        dirty = true;
        summonStatus = null;
        rebuild();
    }

    private void replaceParts(String head, String torso, String limbs, String additional) {
        editing = copy(head, torso, limbs, additional, editing.headAlpha(), editing.torsoAlpha(),
                editing.limbsAlpha(), editing.additionalAlpha(), editing.modules());
    }

    private void toggleModule(String module) {
        var values = new ArrayList<>(editing.modules());
        if (!values.remove(module)) values.add(module);
        editing = copy(editing.head(), editing.torso(), editing.limbs(), editing.additional(),
                editing.headAlpha(), editing.torsoAlpha(), editing.limbsAlpha(),
                editing.additionalAlpha(), values);
        dirty = true;
        rebuild();
    }

    private DarkmatterCreatureBlueprint copy(String head, String torso, String limbs, String additional,
                                             int headA, int torsoA, int limbsA, int additionalA,
                                             List<String> modules) {
        return new DarkmatterCreatureBlueprint(editing.name(), editing.investment(), head, torso,
                limbs, additional, headA, torsoA, limbsA, additionalA, modules);
    }

    private void save() {
        commitName();
        if (!editing.validate(snapshot.abilityLevel).isEmpty()) return;
        MisakaNetworkClient.send(new DarkmatterCreation.SaveBlueprintPacket(selectedSlot, editing));
        dirty = false;
    }

    private void saveAndSummon() {
        commitName();
        if (!editing.validate(snapshot.abilityLevel).isEmpty()) return;
        MisakaNetworkClient.send(new DarkmatterCreation.SummonPacket(selectedSlot, editing));
        summonStatus = tr("screen.academy.darkmatter_creation.status.waiting");
        dirty = false;
        rebuild();
    }

    private static int indexOf(String[] values, String value) {
        for (var i = 0; i < values.length; i++) if (values[i].equals(value)) return i;
        return 0;
    }

    private static String shortId(String value) {
        if (value == null) return "missing";
        var split = value.indexOf(':');
        return split >= 0 ? value.substring(split + 1) : value;
    }

    private record ModuleHoverTarget(int x, int y, int width, int height, String module) { }

    private enum Tab {
        BLUEPRINT("screen.academy.darkmatter_creation.tab.blueprint"),
        PARTS("screen.academy.darkmatter_creation.tab.parts"),
        PHASE("screen.academy.darkmatter_creation.tab.phase"),
        MODULES("screen.academy.darkmatter_creation.tab.modules"),
        SUMMONED("screen.academy.darkmatter_creation.tab.summoned");

        private final String key;

        Tab(String key) {
            this.key = key;
        }
    }
}
