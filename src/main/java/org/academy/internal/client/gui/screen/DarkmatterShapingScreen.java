package org.academy.internal.client.gui.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.gui.drawable.ColorDrawable;
import org.academy.api.client.gui.drawable.StateListDrawable;
import org.academy.api.client.gui.layout.Gravity;
import org.academy.api.client.gui.layout.Orientation;
import org.academy.api.client.gui.layout.SizeMode;
import org.academy.api.client.gui.screen.UiScreen;
import org.academy.api.client.gui.widget.*;
import org.academy.api.common.ability.darkmatter.*;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.darkmatter.skills.lv1.DarkmatterShaping;

import java.util.*;

/**
 * Standalone editor for native dark-matter equipment and material blueprints.
 */
public final class DarkmatterShapingScreen extends UiScreen {
    private static final int PANEL_W = 392;
    private static final int PANEL_H = 210;
    private static final int ACCENT = 0xFF7680DE;

    private final Map<String, Integer> modifiers = new LinkedHashMap<>();
    private final Map<DarkmatterShape, ButtonWidget> shapeButtons = new LinkedHashMap<>();
    private final List<Widget> phaseWidgets = new ArrayList<>();
    private final List<Widget> blockWidgets = new ArrayList<>();
    private DarkmatterShape selectedShape = DarkmatterShape.TOOL;
    private int alphaPercent = 50;
    private LinearLayoutWidget modifierContent;
    private LabelWidget alphaLabel;
    private LabelWidget betaLabel;
    private LabelWidget budgetLabel;
    private LabelWidget costLabel;
    private LabelWidget parametersLabel;
    private LabelWidget selectionLabel;
    private LabelWidget statusLabel;
    private LabelWidget blockHardnessLabel;
    private LabelWidget blockResistanceLabel;
    private LabelWidget blockGravityLabel;
    private ButtonWidget createButton;
    private float blockHardness = DarkmatterBlockProfile.DEFAULT.hardness();
    private float blockResistance = DarkmatterBlockProfile.DEFAULT.explosionResistance();
    private boolean blockGravity = DarkmatterBlockProfile.DEFAULT.gravity();
    private boolean requestPending;

    public DarkmatterShapingScreen() {
        super(Component.translatable("screen.academy.darkmatter_shaping.title"));
        DarkmatterModifiers.bootstrap();
    }

    @Override
    protected void onInit() {
        var panel = new FrameLayoutWidget();
        panel.setLayoutParams(new FrameLayoutWidget.LayoutParams()
                .size(PANEL_W, PANEL_H).gravity(Gravity.CENTER));
        var background = new BlendQuadWidget();
        background.setAlpha(0.48f);
        background.setDrawLine(false);
        background.setLayoutParams(new FrameLayoutWidget.LayoutParams()
                .sizeMode(SizeMode.MATCH_PARENT));
        panel.addChild("background", background);
        panel.addChild("top_rule", rule(PANEL_W - 12, 0xE0FFFFFF,
                Gravity.TOP_LEFT, 6, 0));
        panel.addChild("header_rule", rule(PANEL_W - 20, 0x80FFFFFF,
                Gravity.TOP_LEFT, 10, 25));
        panel.addChild("bottom_rule", rule(PANEL_W - 12, 0x70FFFFFF,
                Gravity.BOTTOM_LEFT, 6, 0));
        panel.addChild("left_separator", verticalRule(0x55FFFFFF, 98, 30, PANEL_H - 40));
        panel.addChild("right_separator", verticalRule(0x55FFFFFF, 246, 30, PANEL_H - 40));

        var title = label("screen.academy.darkmatter_shaping.title", 8.0f);
        title.setLayoutParams(new FrameLayoutWidget.LayoutParams()
                .gravity(Gravity.TOP_LEFT).margin(12, 8, 0, 0));
        panel.addChild("title", title);
        getRoot().addChild("darkmatter_shaping", panel);

        buildShapeList(panel);
        buildPhaseEditor(panel);
        buildModifierList(panel);
        buildFooter(panel);
        refreshAll();
    }

    private void buildShapeList(FrameLayoutWidget panel) {
        var section = label("screen.academy.darkmatter_shaping.shapes", 7.0f);
        section.setAlpha(0.72f);
        section.setLayoutParams(new FrameLayoutWidget.LayoutParams()
                .gravity(Gravity.TOP_LEFT).margin(10, 31, 0, 0));
        panel.addChild("shape_header", section);

        var scroll = new ScrollPanelWidget();
        scroll.setScrollSpeed(18.0f);
        scroll.setLayoutParams(new FrameLayoutWidget.LayoutParams()
                .size(82, 140).gravity(Gravity.TOP_LEFT).margin(10, 43, 0, 0));
        var content = new LinearLayoutWidget();
        content.setOrientation(Orientation.VERTICAL);
        content.setSpacing(2.0f);
        content.setLayoutParams(new LinearLayoutWidget.LayoutParams()
                .width(77).heightMode(SizeMode.WRAP_CONTENT));
        var index = 0;
        for (var shape : DarkmatterShape.values()) {
            var unlocked = shape.isUnlockedAt(currentAbilityLevel());
            var name = Component.translatable(shape.translationKey()).getString();
            var button = textButton(unlocked ? name : Component.translatable(
                    "screen.academy.darkmatter_shaping.locked.entry", name,
                    shape.requiredAbilityLevel()).getString(), 77, 18);
            button.setOnClickListener(_ -> selectShape(shape));
            button.setEnabled(unlocked);
            button.setAlpha(unlocked ? 1.0f : 0.34f);
            button.setTooltipText((unlocked
                    ? Component.translatable("screen.academy.darkmatter_shaping.shape.tooltip",
                    shape.baseMatterCost())
                    : Component.translatable("screen.academy.darkmatter_shaping.shape.tooltip.locked",
                    shape.baseMatterCost(), shape.requiredAbilityLevel())).getString());
            shapeButtons.put(shape, button);
            content.addChild("shape_" + index++, button);
        }
        scroll.setContent(content);
        panel.addChild("shape_scroll", scroll);
        var bar = new ScrollBarWidget(scroll, Orientation.VERTICAL);
        bar.setShowBackground(true);
        bar.setTrackColor(0x28000000);
        bar.setThumbColor(0xB0FFFFFF);
        bar.setLayoutParams(new FrameLayoutWidget.LayoutParams()
                .size(4, 140).gravity(Gravity.TOP_LEFT).margin(91, 43, 0, 0));
        panel.addChild("shape_scrollbar", bar);
    }

    private void buildPhaseEditor(FrameLayoutWidget panel) {
        var section = label("screen.academy.darkmatter_shaping.phase", 7.0f);
        section.setAlpha(0.72f);
        section.setLayoutParams(new FrameLayoutWidget.LayoutParams()
                .gravity(Gravity.TOP_LEFT).margin(108, 31, 0, 0));
        panel.addChild("phase_header", section);

        selectionLabel = new LabelWidget("");
        selectionLabel.setBaseFontSize(8.0f);
        selectionLabel.setLayoutParams(new FrameLayoutWidget.LayoutParams()
                .size(128, 12).gravity(Gravity.TOP_LEFT).margin(108, 46, 0, 0));
        panel.addChild("selection", selectionLabel);

        alphaLabel = new LabelWidget("");
        alphaLabel.setBaseFontSize(7.0f);
        alphaLabel.setLayoutParams(new FrameLayoutWidget.LayoutParams()
                .size(60, 10).gravity(Gravity.TOP_LEFT).margin(108, 67, 0, 0));
        panel.addChild("alpha", alphaLabel);
        phaseWidgets.add(alphaLabel);
        betaLabel = new LabelWidget("");
        betaLabel.setBaseFontSize(7.0f);
        betaLabel.setLayoutParams(new FrameLayoutWidget.LayoutParams()
                .size(60, 10).gravity(Gravity.TOP_RIGHT).margin(0, 67, 157, 0));
        panel.addChild("beta", betaLabel);
        phaseWidgets.add(betaLabel);

        var slider = new SeekBarWidget();
        slider.setMin(0.0f);
        slider.setMax(100.0f);
        slider.setProgress(alphaPercent);
        slider.setKeyProgressIncrement(1);
        slider.setBarColors(0x402A2A2A, ACCENT);
        slider.setTooltipText(Component.translatable(
                "screen.academy.darkmatter_shaping.phase_hint").getString());
        slider.setLayoutParams(new FrameLayoutWidget.LayoutParams()
                .size(128, 6).gravity(Gravity.TOP_LEFT).margin(108, 81, 0, 0));
        slider.setOnSeekBarChangeListener(new SeekBarWidget.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBarWidget seekBar, float progress, boolean fromUser) {
                alphaPercent = Math.round(progress);
                refreshSummary();
            }

            @Override
            public void onStartTrackingTouch(SeekBarWidget seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBarWidget seekBar) {
            }
        });
        panel.addChild("phase_slider", slider);
        phaseWidgets.add(slider);

        budgetLabel = new LabelWidget("");
        budgetLabel.setBaseFontSize(7.0f);
        budgetLabel.setLayoutParams(new FrameLayoutWidget.LayoutParams()
                .size(128, 10).gravity(Gravity.TOP_LEFT).margin(108, 100, 0, 0));
        panel.addChild("budget", budgetLabel);
        phaseWidgets.add(budgetLabel);
        costLabel = new LabelWidget("");
        costLabel.setBaseFontSize(7.0f);
        costLabel.setAlpha(0.72f);
        costLabel.setLayoutParams(new FrameLayoutWidget.LayoutParams()
                .size(128, 10).gravity(Gravity.TOP_LEFT).margin(108, 114, 0, 0));
        panel.addChild("cost", costLabel);

        parametersLabel = new LabelWidget("");
        parametersLabel.setBaseFontSize(6.25f);
        parametersLabel.setAlpha(0.82f);
        parametersLabel.setLayoutParams(new FrameLayoutWidget.LayoutParams()
                .size(128, 48).gravity(Gravity.TOP_LEFT).margin(108, 130, 0, 0));
        panel.addChild("parameters", parametersLabel);

        buildBlockEditor(panel);
    }

    private void buildBlockEditor(FrameLayoutWidget panel) {
        blockHardnessLabel = new LabelWidget("");
        blockHardnessLabel.setBaseFontSize(7.0f);
        blockHardnessLabel.setLayoutParams(new FrameLayoutWidget.LayoutParams()
                .size(128, 10).gravity(Gravity.TOP_LEFT).margin(108, 67, 0, 0));
        panel.addChild("block_hardness_label", blockHardnessLabel);
        blockWidgets.add(blockHardnessLabel);

        var hardnessSlider = new SeekBarWidget();
        hardnessSlider.setMin(DarkmatterBlockProfile.MIN_HARDNESS);
        hardnessSlider.setMax(DarkmatterBlockProfile.MAX_HARDNESS);
        hardnessSlider.setProgress(blockHardness);
        hardnessSlider.setKeyProgressIncrement(1);
        hardnessSlider.setBarColors(0x402A2A2A, ACCENT);
        hardnessSlider.setLayoutParams(new FrameLayoutWidget.LayoutParams()
                .size(128, 6).gravity(Gravity.TOP_LEFT).margin(108, 80, 0, 0));
        hardnessSlider.setOnSeekBarChangeListener(new SeekBarWidget.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBarWidget seekBar, float progress, boolean fromUser) {
                blockHardness = Math.round(progress * 2.0f) / 2.0f;
                refreshSummary();
            }

            @Override
            public void onStartTrackingTouch(SeekBarWidget seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBarWidget seekBar) {
            }
        });
        panel.addChild("block_hardness_slider", hardnessSlider);
        blockWidgets.add(hardnessSlider);

        blockResistanceLabel = new LabelWidget("");
        blockResistanceLabel.setBaseFontSize(7.0f);
        blockResistanceLabel.setLayoutParams(new FrameLayoutWidget.LayoutParams()
                .size(128, 10).gravity(Gravity.TOP_LEFT).margin(108, 92, 0, 0));
        panel.addChild("block_resistance_label", blockResistanceLabel);
        blockWidgets.add(blockResistanceLabel);

        var resistanceSlider = new SeekBarWidget();
        resistanceSlider.setMin(DarkmatterBlockProfile.MIN_EXPLOSION_RESISTANCE);
        resistanceSlider.setMax(DarkmatterBlockProfile.MAX_EXPLOSION_RESISTANCE);
        resistanceSlider.setProgress(blockResistance);
        resistanceSlider.setKeyProgressIncrement(10);
        resistanceSlider.setBarColors(0x402A2A2A, ACCENT);
        resistanceSlider.setLayoutParams(new FrameLayoutWidget.LayoutParams()
                .size(128, 6).gravity(Gravity.TOP_LEFT).margin(108, 105, 0, 0));
        resistanceSlider.setOnSeekBarChangeListener(new SeekBarWidget.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBarWidget seekBar, float progress, boolean fromUser) {
                blockResistance = Math.round(progress / 10.0f) * 10.0f;
                refreshSummary();
            }

            @Override
            public void onStartTrackingTouch(SeekBarWidget seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBarWidget seekBar) {
            }
        });
        panel.addChild("block_resistance_slider", resistanceSlider);
        blockWidgets.add(resistanceSlider);

        blockGravityLabel = new LabelWidget("");
        blockGravityLabel.setBaseFontSize(7.0f);
        blockGravityLabel.setLayoutParams(new FrameLayoutWidget.LayoutParams()
                .size(96, 12).gravity(Gravity.TOP_LEFT).margin(108, 132, 0, 0));
        panel.addChild("block_gravity_label", blockGravityLabel);
        blockWidgets.add(blockGravityLabel);

        var gravityToggle = new ToggleButtonWidget();
        gravityToggle.updateChecked(blockGravity);
        gravityToggle.updateTrackColors(0x50FFFFFF, ACCENT);
        gravityToggle.setLayoutParams(new FrameLayoutWidget.LayoutParams()
                .size(28, 10).gravity(Gravity.TOP_LEFT).margin(208, 130, 0, 0));
        gravityToggle.updateOnCheckedChangeListener((_, checked) -> {
            blockGravity = checked;
            refreshSummary();
        });
        panel.addChild("block_gravity_toggle", gravityToggle);
        blockWidgets.add(gravityToggle);
    }

    private void buildModifierList(FrameLayoutWidget panel) {
        var section = label("screen.academy.darkmatter_shaping.modifiers", 7.0f);
        section.setAlpha(0.72f);
        section.setLayoutParams(new FrameLayoutWidget.LayoutParams()
                .gravity(Gravity.TOP_LEFT).margin(256, 31, 0, 0));
        panel.addChild("modifier_header", section);
        var scroll = new ScrollPanelWidget();
        scroll.setScrollSpeed(18.0f);
        scroll.setLayoutParams(new FrameLayoutWidget.LayoutParams()
                .size(119, 140).gravity(Gravity.TOP_LEFT).margin(256, 43, 0, 0));
        modifierContent = new LinearLayoutWidget();
        modifierContent.setOrientation(Orientation.VERTICAL);
        modifierContent.setSpacing(2.0f);
        modifierContent.setLayoutParams(new LinearLayoutWidget.LayoutParams()
                .width(114).heightMode(SizeMode.WRAP_CONTENT));
        scroll.setContent(modifierContent);
        panel.addChild("modifier_scroll", scroll);
        var bar = new ScrollBarWidget(scroll, Orientation.VERTICAL);
        bar.setShowBackground(true);
        bar.setTrackColor(0x28000000);
        bar.setThumbColor(0xB0FFFFFF);
        bar.setLayoutParams(new FrameLayoutWidget.LayoutParams()
                .size(4, 140).gravity(Gravity.TOP_LEFT).margin(378, 43, 0, 0));
        panel.addChild("modifier_scrollbar", bar);
    }

    private void buildFooter(FrameLayoutWidget panel) {
        statusLabel = new LabelWidget("");
        statusLabel.setBaseFontSize(6.5f);
        statusLabel.setAlpha(0.75f);
        statusLabel.setLayoutParams(new FrameLayoutWidget.LayoutParams()
                .size(128, 10).gravity(Gravity.BOTTOM_LEFT).margin(256, 0, 0, 12));
        panel.addChild("status", statusLabel);
        createButton = textButton(Component.translatable(
                "screen.academy.darkmatter_shaping.create").getString(), 128, 18);
        createButton.setOnClickListener(_ -> submit());
        createButton.setLayoutParams(new FrameLayoutWidget.LayoutParams()
                .size(128, 18).gravity(Gravity.BOTTOM_LEFT).margin(108, 0, 0, 8));
        panel.addChild("create", createButton);
    }

    private void selectShape(DarkmatterShape shape) {
        if (!shape.isUnlockedAt(currentAbilityLevel())) {
            showLevelRequirement(shape.requiredAbilityLevel());
            return;
        }
        selectedShape = shape;
        modifiers.entrySet().removeIf(entry -> DarkmatterShapingRegistries
                .modifier(entry.getKey()).map(type -> !type.supports(shape)).orElse(true));
        refreshAll();
    }

    private void refreshAll() {
        shapeButtons.forEach((shape, button) -> {
            var unlocked = shape.isUnlockedAt(currentAbilityLevel());
            button.setSelected(unlocked && shape == selectedShape);
            button.setEnabled(unlocked);
            button.setAlpha(unlocked ? 1.0f : 0.34f);
        });
        rebuildModifierRows();
        refreshSummary();
    }

    private void rebuildModifierRows() {
        modifierContent.clearChildren();
        var index = 0;
        for (var type : DarkmatterShapingRegistries.modifiers()) {
            if (!type.supports(selectedShape)) continue;
            modifierContent.addChild("modifier_" + index++, modifierRow(type));
        }
        if (index == 0) {
            var empty = new LabelWidget(Component.translatable(
                    "screen.academy.darkmatter_shaping.modifiers.none").getString());
            empty.setBaseFontSize(6.5f);
            empty.setAlpha(0.6f);
            empty.setLayoutParams(new LinearLayoutWidget.LayoutParams().size(114, 18));
            modifierContent.addChild("modifier_empty", empty);
        }
    }

    private FrameLayoutWidget modifierRow(DarkmatterModifierType type) {
        var row = new FrameLayoutWidget();
        row.setLayoutParams(new LinearLayoutWidget.LayoutParams().size(114, 18));
        var fill = new FillWidget(0x28000000);
        fill.setLayoutParams(new FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT));
        row.addChild("fill", fill);
        var minus = textButton("−", 16, 16);
        minus.setLayoutParams(new FrameLayoutWidget.LayoutParams()
                .size(16, 16).gravity(Gravity.CENTER_LEFT).marginLeft(1));
        minus.setOnClickListener(_ -> changeModifier(type, -1));
        row.addChild("minus", minus);
        var level = modifiers.getOrDefault(type.id(), 0);
        var name = Component.translatable(type.nameKey()).getString();
        var unlocked = type.isUnlockedAt(currentAbilityLevel());
        var displayName = unlocked ? name : Component.translatable(
                "screen.academy.darkmatter_shaping.locked.entry", name,
                type.requiredAbilityLevel()).getString();
        var text = new LabelWidget(displayName + (level > 0 ? "  " + level : ""));
        text.setBaseFontSize(6.5f);
        text.setTooltipText((unlocked
                ? Component.translatable("screen.academy.darkmatter_shaping.modifier.tooltip",
                Component.translatable(type.descriptionKey()))
                : Component.translatable("screen.academy.darkmatter_shaping.modifier.tooltip.locked",
                Component.translatable(type.descriptionKey()), type.requiredAbilityLevel())).getString());
        text.setAlpha(unlocked ? (level > 0 ? 1.0f : 0.68f) : 0.34f);
        text.setLayoutParams(new FrameLayoutWidget.LayoutParams()
                .size(78, 16).gravity(Gravity.CENTER));
        row.addChild("name", text);
        var plus = textButton("+", 16, 16);
        plus.setLayoutParams(new FrameLayoutWidget.LayoutParams()
                .size(16, 16).gravity(Gravity.CENTER_RIGHT).marginRight(1));
        plus.setOnClickListener(_ -> changeModifier(type, 1));
        minus.setEnabled(unlocked);
        plus.setEnabled(unlocked);
        minus.setAlpha(unlocked ? 1.0f : 0.34f);
        plus.setAlpha(unlocked ? 1.0f : 0.34f);
        row.addChild("plus", plus);
        return row;
    }

    private void changeModifier(DarkmatterModifierType type, int delta) {
        if (!type.isUnlockedAt(currentAbilityLevel())) {
            showLevelRequirement(type.requiredAbilityLevel());
            return;
        }
        var current = modifiers.getOrDefault(type.id(), 0);
        var next = Math.clamp(current + delta, 0, type.maxLevel());
        if (next > 0) {
            for (var conflict : type.conflicts()) modifiers.remove(conflict);
            var candidate = new LinkedHashMap<>(modifiers);
            candidate.put(type.id(), next);
            if (!clientValidation(candidate).valid()) return;
            modifiers.put(type.id(), next);
        } else {
            modifiers.remove(type.id());
        }
        rebuildModifierRows();
        refreshSummary();
    }

    private DarkmatterShaping.Server.ModifierValidation clientValidation(
            Map<String, Integer> candidate) {
        return DarkmatterShaping.Server.validateModifiers(selectedShape, candidate,
                Math.clamp(AbilitySystemClient.getDarkmatterLevel(), 1, 5),
                AbilitySystemClient.getSkillProficiencyMilestone(
                        Skills.DARKMATTER_SHAPING.get()));
    }

    private void refreshSummary() {
        var validation = clientValidation(modifiers);
        var blockMode = selectedShape == DarkmatterShape.BLOCK;
        setGroupVisible(phaseWidgets, !blockMode);
        setGroupVisible(blockWidgets, blockMode);
        parametersLabel.setLayoutParams(new FrameLayoutWidget.LayoutParams()
                .size(128, blockMode ? 30 : 48).gravity(Gravity.TOP_LEFT)
                .margin(108, blockMode ? 148 : 130, 0, 0));
        selectionLabel.setText(Component.translatable(selectedShape.translationKey()).getString());
        alphaLabel.setText(Component.translatable(
                "screen.academy.darkmatter_shaping.alpha", alphaPercent).getString());
        betaLabel.setText(Component.translatable(
                "screen.academy.darkmatter_shaping.beta", 100 - alphaPercent).getString());
        budgetLabel.setText(Component.translatable(
                "screen.academy.darkmatter_shaping.budget",
                validation.usedPoints(), validation.budget()).getString());
        var milestone = AbilitySystemClient.getSkillProficiencyMilestone(
                Skills.DARKMATTER_SHAPING.get());
        var rawCost = selectedShape.baseMatterCost() + validation.usedPoints() * 0.5f;
        var cost = rawCost * (milestone >= 1 ? 0.9f : 1.0f);
        costLabel.setText(Component.translatable(
                "screen.academy.darkmatter_shaping.cost", cost).getString());
        blockHardnessLabel.setText(Component.translatable(
                "screen.academy.darkmatter_shaping.block.hardness",
                decimal(blockHardness)).getString());
        blockResistanceLabel.setText(Component.translatable(
                "screen.academy.darkmatter_shaping.block.explosion_resistance",
                decimal(blockResistance)).getString());
        blockGravityLabel.setText(Component.translatable(
                "screen.academy.darkmatter_shaping.block.gravity",
                Component.translatable(blockGravity
                        ? "screen.academy.darkmatter_shaping.block.gravity.enabled"
                        : "screen.academy.darkmatter_shaping.block.gravity.disabled")).getString());
        parametersLabel.setText(parameterText(previewProfile()));
        if (createButton != null) {
            createButton.setEnabled(!requestPending && validation.valid());
            createButton.setAlpha(!requestPending && validation.valid() ? 1.0f : 0.34f);
        }
        if (!requestPending) {
            var requiredLevel = firstLockedRequirement();
            if (requiredLevel > 0) showLevelRequirement(requiredLevel);
            else if (!validation.valid()) statusLabel.setText(Component.translatable(
                    DarkmatterShaping.Result.INVALID_PROFILE.translationKey()).getString());
            else statusLabel.setText("");
        }
    }

    private DarkmatterShapingProfile previewProfile() {
        var level = Math.clamp(AbilitySystemClient.getDarkmatterLevel(), 1, 5);
        var total = level * 50;
        var alpha = Math.round(total * alphaPercent / 100.0f);
        return new DarkmatterShapingProfile(level, alpha, total - alpha, modifiers);
    }

    private String parameterText(DarkmatterShapingProfile profile) {
        var alpha = profile.alphaPower();
        var beta = profile.betaPower();
        var penetration = DarkmatterShaping.Server.penetration(selectedShape, beta) * 100.0f;
        return switch (selectedShape) {
            case TOOL -> Component.translatable(
                    "screen.academy.darkmatter_shaping.parameters.tool",
                    DarkmatterShaping.Server.toolEfficiency(alpha),
                    DarkmatterShaping.Server.toolFortune(beta),
                    decimal(DarkmatterShaping.Server.directDamage(selectedShape, alpha)),
                    decimal(penetration)).getString();
            case SPEAR -> Component.translatable(
                    "screen.academy.darkmatter_shaping.parameters.spear",
                    decimal(DarkmatterShaping.Server.spearDamage(alpha)),
                    decimal(DarkmatterShaping.Server.spearRange(alpha)),
                    decimal(DarkmatterShaping.Server.spearSpeed(beta)),
                    decimal(penetration)).getString();
            case SWORD, TRIDENT -> Component.translatable(
                    "screen.academy.darkmatter_shaping.parameters.weapon",
                    decimal(DarkmatterShaping.Server.directDamage(selectedShape, alpha)),
                    decimal(penetration)).getString();
            case MACE -> Component.translatable(
                    "screen.academy.darkmatter_shaping.parameters.mace",
                    decimal(DarkmatterShaping.Server.phaseDamageBonus(alpha)),
                    decimal(penetration)).getString();
            case BOW, CROSSBOW, ARROW -> Component.translatable(
                    "screen.academy.darkmatter_shaping.parameters.ranged",
                    decimal(DarkmatterShaping.Server.phaseDamageBonus(alpha)),
                    decimal(penetration),
                    selectedShape == DarkmatterShape.CROSSBOW ? 8 : 15).getString();
            case ARMOR -> Component.translatable(
                    "screen.academy.darkmatter_shaping.parameters.armor",
                    decimal(DarkmatterShaping.Server.armorReduction(alpha) * 100.0f),
                    DarkmatterShaping.Server.armorWeaknessTicks(beta)).getString();
            case COATING -> Component.translatable(
                    "screen.academy.darkmatter_shaping.parameters.coating",
                    decimal(DarkmatterShaping.Server.phaseDamageBonus(alpha)),
                    DarkmatterShaping.Server.toolEfficiency(alpha),
                    DarkmatterShaping.Server.toolFortune(beta),
                    decimal(DarkmatterShaping.Server.penetration(
                            DarkmatterShape.TOOL, beta) * 100.0f)).getString();
            case BLOCK -> Component.translatable(
                    "screen.academy.darkmatter_shaping.parameters.block",
                    decimal(blockHardness), decimal(blockResistance)).getString();
        };
    }

    private DarkmatterBlockProfile blockProfile() {
        return new DarkmatterBlockProfile(blockHardness, blockResistance, blockGravity);
    }

    private static void setGroupVisible(List<Widget> widgets, boolean visible) {
        for (var widget : widgets) {
            widget.setVisibility(visible ? Widget.Visibility.VISIBLE : Widget.Visibility.GONE);
            widget.setEnabled(visible);
        }
    }

    private static String decimal(float value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private void submit() {
        if (requestPending) return;
        var validation = clientValidation(modifiers);
        if (!validation.valid()) return;
        requestPending = true;
        statusLabel.setText(Component.translatable(
                "screen.academy.darkmatter_shaping.status.waiting").getString());
        if (createButton != null) {
            createButton.setEnabled(false);
            createButton.setAlpha(0.34f);
        }
        DarkmatterShaping.Client.shape(selectedShape, alphaPercent, modifiers, blockProfile());
    }

    private int firstLockedRequirement() {
        if (!selectedShape.isUnlockedAt(currentAbilityLevel())) {
            return selectedShape.requiredAbilityLevel();
        }
        for (var entry : modifiers.entrySet()) {
            if (entry.getValue() <= 0) continue;
            var type = DarkmatterShapingRegistries.modifier(entry.getKey()).orElse(null);
            if (type != null && !type.isUnlockedAt(currentAbilityLevel())) {
                return type.requiredAbilityLevel();
            }
        }
        return 0;
    }

    private void showLevelRequirement(int requiredLevel) {
        if (statusLabel != null) statusLabel.setText(Component.translatable(
                "screen.academy.darkmatter_shaping.locked.level", requiredLevel).getString());
    }

    private static int currentAbilityLevel() {
        return Math.clamp(AbilitySystemClient.getDarkmatterLevel(), 1, 5);
    }

    public void acceptServerResult(DarkmatterShaping.Result result) {
        requestPending = false;
        if (result == DarkmatterShaping.Result.SHAPED) {
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.sendOverlayMessage(
                        Component.translatable(result.translationKey()));
            }
            Minecraft.getInstance().gui.setScreen(null);
            return;
        }
        refreshSummary();
        statusLabel.setText(Component.translatable(result.translationKey()).getString());
    }

    private static LabelWidget label(String key, float size) {
        var label = new LabelWidget(Component.translatable(key).getString());
        label.setBaseFontSize(size);
        return label;
    }

    private static FillWidget rule(int width, int color, int gravity, int x, int y) {
        var widget = new FillWidget(color);
        widget.setLayoutParams(new FrameLayoutWidget.LayoutParams()
                .size(width, 1).gravity(gravity).margin(x, y, 0, 0));
        return widget;
    }

    private static FillWidget verticalRule(int color, int x, int y, int height) {
        var widget = new FillWidget(color);
        widget.setLayoutParams(new FrameLayoutWidget.LayoutParams()
                .size(1, height).gravity(Gravity.TOP_LEFT).margin(x, y, 0, 0));
        return widget;
    }

    private static ButtonWidget textButton(String text, int width, int height) {
        var button = new ButtonWidget();
        button.setLayoutParams(new FrameLayoutWidget.LayoutParams().size(width, height));
        var states = new StateListDrawable();
        states.setDefault(new ColorDrawable(0x28101010));
        states.addState(Widget.SELECTED, new ColorDrawable(0x707680DE));
        states.addState(Widget.HOVERED, new ColorDrawable(0x50FFFFFF));
        states.addState(Widget.PRESSED, new ColorDrawable(0x907680DE));
        button.setBackground(states);
        var label = new LabelWidget(text);
        label.setBaseFontSize(7.0f);
        label.setLayoutParams(new FrameLayoutWidget.LayoutParams()
                .sizeMode(SizeMode.MATCH_PARENT).gravity(Gravity.CENTER));
        button.addChild("label", label);
        return button;
    }
}
