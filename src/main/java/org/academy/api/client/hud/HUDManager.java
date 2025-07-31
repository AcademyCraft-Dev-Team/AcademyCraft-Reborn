package org.academy.api.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.gui.widget.*;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.render.MatrixStack;
import org.academy.api.client.render.RenderTypes;
import org.academy.api.client.resource.TextureResources;
import org.academy.api.client.util.ClientUtil;
import org.academy.api.client.util.RenderUtil;
import org.academy.api.common.ability.AbilityCategory;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.util.MathUtil;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.academy.AcademyCraft.getResourceLocation;

public final class HUDManager {
    private static boolean initialized = false;
    private static final List<HUDRenderer> HUD_RENDERERS = new ArrayList<>();
    public static final RenderType CP_BAR_VALUE =
            RenderUtil.getPositionColorTexRenderTypeFull(
                    "cp_bar_value",
                    TextureResources.CP_BAR_VALUE,
                    false);
    public static final RenderType CP_BAR =
            RenderUtil.getPositionColorTexRenderTypeFull(
                    "cp_bar",
                    TextureResources.CP_BAR,
                    false);
    public static final RenderType CP_BAR_BACKGROUND =
            RenderUtil.getPositionColorTexRenderTypeFull(
                    "cp_bar_background",
                    TextureResources.CP_BAR_BACKGROUND,
                    false);
    public static final Function<AbilityCategory, RenderType> ABILITY_ICON = abilityCategory -> {
        var key = abilityCategory.getKey();
        return RenderUtil.getPositionColorTexRenderTypeFull("ability_icon", getResourceLocation(key.getNamespace(),
                "textures/ability/" + key.getPath() + "/icon_overlay.png"
        ), false);
    };
    public static final Supplier<Float> SCALE_FACTOR = () -> 1.0f;
    public static final float DEFAULT_SCALA = 0.2F;
    public static final int CP_BAR_WIDTH = 964;
    public static final int CP_BAR_HEIGHT = 147;
    public static final int CP_BAR_CONSUMABLE_WIDTH = 743;
    public static final int CP_BAR_LEFT_SAFE_ZONE = 46;
    public static final int CP_BAR_RIGHT_SAFE_ZONE = 34;
    public static final int CP_BAR_TOP_SAFE_ZONE = 30;
    public static final float CP_BAR_ANGLE = 45F;
    public static final float CP_BAR_TANGENT = (float) Math.tan(Math.toRadians(CP_BAR_ANGLE));
    public static final int ABILITY_ICON_WIDTH = 64;
    public static final int ABILITY_ICON_HEIGHT = 64;
    public static final int ABILITY_ICON_RIGHT_SAFE_ZONE = 10;
    public static final int ABILITY_ICON_TOP_SAFE_ZONE = 10;
    public static float targetAlpha;
    public static float currentAlpha;
    public static float smoothProgress;
    private static final List<SkillWidget> skillWidgets = new ArrayList<>();
    private static int selectedSkillIndex = 0;
    private static final float SKILL_LIST_RIGHT_MARGIN = 5f;
    private static final float DEFAULT_SKILL_WIDGET_WIDTH = 80f;
    private static final float DEFAULT_SKILL_WIDGET_HEIGHT = 20f;
    private static final float SKILL_WIDGET_SPACING = 2f;
    private static final float SELECTED_SKILL_WIDTH_MULTIPLIER = 1.25f;
    private static final float SELECTED_SKILL_HEIGHT_MULTIPLIER = 1.25f;
    public static final String KEY_SKILL_UP = "hud_skill_up";
    public static final String KEY_SKILL_DOWN = "hud_skill_down";
    private static ImageWidget cpBarBackgroundWidget;
    private static ImageWidget cpBarWidget;
    private static QuadVertexWidget cpBarValueWidget;
    private static ImageWidget abilityIconWidget;

    private HUDManager() {
    }

    public static void init() {
        if (initialized) return;
        initialized = true;

        InputSystem.addKeyBinding(KEY_SKILL_UP, new InputSystem.InputPair(
                InputSystem.InputType.KEYBOARD,
                new InputSystem.KeyInfo(new LinkedHashSet<>(Set.of(GLFW.GLFW_KEY_UP)), GLFW.GLFW_RELEASE, new LinkedHashSet<>())
        ), HUDManager::selectPreviousSkill);

        InputSystem.addKeyBinding(KEY_SKILL_DOWN, new InputSystem.InputPair(
                InputSystem.InputType.KEYBOARD,
                new InputSystem.KeyInfo(new LinkedHashSet<>(Set.of(GLFW.GLFW_KEY_DOWN)), GLFW.GLFW_RELEASE, new LinkedHashSet<>())
        ), HUDManager::selectNextSkill);

        final var scale = DEFAULT_SCALA * SCALE_FACTOR.get();
        final var bgWidth = 946 * scale;
        final var bgHeight = 147 * scale;
        cpBarBackgroundWidget = new ImageWidget(0, 0, bgWidth, bgHeight, CP_BAR_BACKGROUND);
        cpBarWidget = new ImageWidget(0, 0, bgWidth, bgHeight, CP_BAR);
        cpBarValueWidget = new QuadVertexWidget(0, 0, 0, 0, CP_BAR_VALUE);

        final var iconWidth = ABILITY_ICON_WIDTH * scale;
        final var iconHeight = ABILITY_ICON_HEIGHT * scale;
        abilityIconWidget = new ImageWidget(0, 0, iconWidth, iconHeight, null);

        NeoForge.EVENT_BUS.register(HUDManager.class);
    }

    public static void registerHUDRenderer(HUDRenderer renderer) {
        if (!initialized) HUD_RENDERERS.add(renderer);
    }

    private static void updateSkillWidgetsList() {
        var learnedSkills = AbilitySystemClient.LEARNED_SKILLS;
        if (skillWidgets.size() == learnedSkills.size()) {
            var match = true;
            var tempList = new ArrayList<>(learnedSkills);
            for (var i = 0; i < skillWidgets.size(); i++) {
                if (!skillWidgets.get(i).skill.equals(tempList.get(i))) {
                    match = false;
                    break;
                }
            }
            if (match) return;
        }
        populateSkillWidgets();
    }

    private static void populateSkillWidgets() {
        skillWidgets.clear();

        var sortedSkills = new ArrayList<>(AbilitySystemClient.LEARNED_SKILLS);

        for (var skill : sortedSkills) {
            var window = Minecraft.getInstance().getWindow();
            var widget = new SkillWidget(window.getGuiScaledWidth(), window.getGuiScaledHeight() / 2f, DEFAULT_SKILL_WIDGET_WIDTH, DEFAULT_SKILL_WIDGET_HEIGHT, skill);
            skillWidgets.add(widget);
        }

        if (!skillWidgets.isEmpty()) {
            if (selectedSkillIndex >= skillWidgets.size()) {
                selectedSkillIndex = 0;
            }
        } else {
            selectedSkillIndex = 0;
        }
    }

    public static void selectNextSkill() {
        if (skillWidgets.isEmpty() || !AbilitySystemClient.isActiveHUD()) return;
        var screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        var baseSkillWidgetX = screenWidth - DEFAULT_SKILL_WIDGET_WIDTH - SKILL_LIST_RIGHT_MARGIN;
        skillWidgets.get(selectedSkillIndex).setSelected(false, baseSkillWidgetX);
        selectedSkillIndex = (selectedSkillIndex + 1) % skillWidgets.size();
        skillWidgets.get(selectedSkillIndex).setSelected(true, baseSkillWidgetX);
        ClientUtil.playDownSound();
    }

    public static void selectPreviousSkill() {
        if (skillWidgets.isEmpty() || !AbilitySystemClient.isActiveHUD()) return;
        var screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        var baseSkillWidgetX = screenWidth - DEFAULT_SKILL_WIDGET_WIDTH - SKILL_LIST_RIGHT_MARGIN;
        skillWidgets.get(selectedSkillIndex).setSelected(false, baseSkillWidgetX);
        selectedSkillIndex = (selectedSkillIndex - 1 + skillWidgets.size()) % skillWidgets.size();
        skillWidgets.get(selectedSkillIndex).setSelected(true, baseSkillWidgetX);
        ClientUtil.playDownSound();
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event) {
        if (!initialized) return;

        if (AbilitySystemClient.isActiveHUD()) {
            updateSkillWidgetsList();
        }
    }

    public static void render(MatrixStack stack, MultiBufferSource.BufferSource bufferSource, float partialTick) {
        targetAlpha = AbilitySystemClient.isActiveHUD() ? 1.0f : 0.0f;

        var animFactor = ClientUtil.animationFactor(MathUtil.PI / 2);
        currentAlpha = MathUtil.lerpStartEndFactor(currentAlpha, targetAlpha, animFactor);

        for (var renderer : HUD_RENDERERS) {
            renderer.render(stack, bufferSource, partialTick);
        }

        var screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        cpBarBackgroundWidget.setX(screenWidth - cpBarBackgroundWidget.getWidth());
        cpBarBackgroundWidget.setY(0);
        cpBarBackgroundWidget.setAlpha(currentAlpha);
        cpBarBackgroundWidget.render(stack, bufferSource, 0, 0, partialTick);

        var targetR = 1.0f;
        var targetG = 174 / 255.0f;
        var targetB = 68 / 255.0f;

        var finalR = MathUtil.lerpFactorStartEnd(targetR, 1.0f, currentAlpha);
        var finalG = MathUtil.lerpStartEndFactor(targetG, 1.0f, currentAlpha);
        var finalB = MathUtil.lerpFactorStartEnd(targetB, 1.0f, currentAlpha);

        cpBarWidget.setX(screenWidth - cpBarWidget.getWidth());
        cpBarWidget.setY(0);
        cpBarWidget.red = finalR;
        cpBarWidget.green = finalG;
        cpBarWidget.blue = finalB;
        cpBarWidget.setAlpha(currentAlpha);
        cpBarWidget.render(stack, bufferSource, 0, 0, partialTick);

        final var computingPower = AbilitySystemClient.getComputingPower();
        final var maximumComputingPower = AbilitySystemClient.getMaximumComputingPower();
        final var progress = (computingPower != 0 && maximumComputingPower != 0) ? (computingPower / maximumComputingPower) : 0;
        smoothProgress = MathUtil.lerpStartEndFactor(smoothProgress, progress, ClientUtil.animationFactor(MathUtil.PI / 2));
        if (Float.isNaN(smoothProgress)) {
            smoothProgress = 0f;
        }

        final var scale = DEFAULT_SCALA * SCALE_FACTOR.get();
        final var width = CP_BAR_WIDTH * scale;
        final var height = CP_BAR_HEIGHT * scale;
        final var leftSafeZoneWidth = (CP_BAR_LEFT_SAFE_ZONE - (CP_BAR_TOP_SAFE_ZONE / CP_BAR_TANGENT)) * scale;
        final var barLength = CP_BAR_CONSUMABLE_WIDTH * scale;
        final var barWidthOffset = barLength * (1.0f - smoothProgress);
        final var leftTopOffset = barWidthOffset + leftSafeZoneWidth;
        final var leftBottomOffset = leftTopOffset + (height / CP_BAR_TANGENT);
        final var rightTopX = (float) screenWidth;
        final var leftTopX = rightTopX - width + leftTopOffset;
        final var leftBottomX = rightTopX - width + leftBottomOffset;
        final var leftTopUv = 1 - ((width - leftTopOffset) / width);
        final var leftBottomUv = 1 - ((width - leftBottomOffset) / width);

        cpBarValueWidget.setVertex(0, leftTopX, 0, 0, leftTopUv, 0);
        cpBarValueWidget.setVertex(1, leftBottomX, height, 0, leftBottomUv, 1);
        cpBarValueWidget.setVertex(2, rightTopX, height, 0, 1, 1);
        cpBarValueWidget.setVertex(3, rightTopX, 0, 0, 1, 0);
        cpBarValueWidget.red = finalR;
        cpBarValueWidget.green = finalG;
        cpBarValueWidget.blue = finalB;
        cpBarValueWidget.setAlpha(currentAlpha);
        cpBarValueWidget.render(stack, bufferSource, 0, 0, partialTick);

        final var rightSafeZone = (CP_BAR_RIGHT_SAFE_ZONE + ABILITY_ICON_RIGHT_SAFE_ZONE) * scale;
        final var topSafeZone = (CP_BAR_TOP_SAFE_ZONE + ABILITY_ICON_TOP_SAFE_ZONE) * scale;
        final var abilityCategory = AbilitySystemClient.getCategory();
        abilityIconWidget.renderType = ABILITY_ICON.apply(abilityCategory);
        if (abilityIconWidget.renderType != null) {
            abilityIconWidget.setX(screenWidth - rightSafeZone - abilityIconWidget.getWidth());
            abilityIconWidget.setY(topSafeZone);
            abilityIconWidget.setAlpha(currentAlpha);
            abilityIconWidget.render(stack, bufferSource, 0, 0, partialTick);
        }

        if (!skillWidgets.isEmpty()) {
            var screenHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();
            var baseSkillWidgetX = screenWidth - DEFAULT_SKILL_WIDGET_WIDTH - SKILL_LIST_RIGHT_MARGIN;

            for (var i = 0; i < skillWidgets.size(); i++) {
                skillWidgets.get(i).setSelected(i == selectedSkillIndex, baseSkillWidgetX);
            }

            var totalListTargetHeight = 0f;
            for (var i = 0; i < skillWidgets.size(); i++) {
                var widget = skillWidgets.get(i);
                totalListTargetHeight += widget.targetHeight;
                if (i < skillWidgets.size() - 1) {
                    totalListTargetHeight += SKILL_WIDGET_SPACING;
                }
            }

            var currentLayoutY = (screenHeight - totalListTargetHeight) / 2f;

            for (var i = 0; i < skillWidgets.size(); i++) {
                var widget = skillWidgets.get(i);
                var selected = i == selectedSkillIndex;
                widget.originalY = currentLayoutY;
                widget.setSelected(selected, baseSkillWidgetX);
                widget.setAlpha((selected ? 1 : 0.65f) * currentAlpha);
                widget.render(stack, bufferSource, 0, 0, partialTick);
                currentLayoutY += widget.targetHeight + SKILL_WIDGET_SPACING;
            }
        }
    }

    public static final class SkillWidget extends PanelWidget {
        private final Skill skill;
        private final FillWidget back;
        private final AutoScaleLabelWidget label;
        private final ImageWidget icon;

        public float originalX, originalY, originalWidth, originalHeight;
        private float targetX;
        public float targetY;
        public float targetWidth;
        public float targetHeight;
        private float currentWidgetAlpha = 0.0f;

        public SkillWidget(float initialXOffset, float initialYOffset, float width, float height, Skill newSkill) {
            super(initialXOffset, initialYOffset, width, height);
            skill = newSkill;
            originalX = 0;
            originalY = initialYOffset;
            targetY = initialYOffset;
            originalWidth = width;
            originalHeight = height;

            targetX = 0;
            targetWidth = width;
            targetHeight = height;

            var padding = 2f;
            var iconSize = height - padding * 2;

            label = new AutoScaleLabelWidget(skill.getTranslatedName(), padding, 0, width - iconSize - padding * 3, false);
            label.scale = 0.7f;
            label.setCentered(false);

            icon = new ImageWidget(width - padding - iconSize, padding, iconSize, iconSize, getSkillIconRenderType(skill));
            icon.widthScale = 0.8f;
            icon.heightScale = 0.8f;

            back = new FillWidget(0, 0, width, height, 0x60000000);

            addChild("back", back);
            addChild("label", label);
            addChild("icon", icon);
        }

        private RenderType getSkillIconRenderType(Skill skillInstance) {
            for (var infos : AbilitySystemClient.SKILL_INFOS.values()) {
                for (var info : infos) {
                    if (info.skill().equals(skillInstance)) {
                        return RenderUtil.getPositionColorTexRenderTypeFull("skill_icon_hud_" + skillInstance.getKey().getPath().toLowerCase().replace(" ", "_"), info.texture(), false);
                    }
                }
            }
            return RenderTypes.ICON_BOX;
        }

        @Override
        public void render(MatrixStack stack, MultiBufferSource.BufferSource bufferSource, double mouseX, double mouseY, float partialTick) {
            var animFactor = ClientUtil.animationFactor(MathUtil.PI / 2);
            currentWidgetAlpha = MathUtil.lerpStartEndFactor(currentWidgetAlpha, getAbsoluteAlpha(), animFactor);

            setX(MathUtil.lerpStartEndFactor(getX(), targetX, animFactor));
            setY(MathUtil.lerpStartEndFactor(getY(), targetY, animFactor));
            setWidth(MathUtil.lerpStartEndFactor(getWidth(), targetWidth, animFactor));
            setHeight(MathUtil.lerpStartEndFactor(getHeight(), targetHeight, animFactor));

            var finalBackAlpha = (int) (0x70 * currentWidgetAlpha);
            back.color = (back.color & 0x00FFFFFF) | (finalBackAlpha << 24);

            var finalLabelAlpha = (int) (0xFF * currentWidgetAlpha);
            label.color = (label.color & 0x00FFFFFF) | (finalLabelAlpha << 24);
            icon.setAlpha(currentWidgetAlpha);

            back.setWidth(getWidth());
            back.setHeight(getHeight());

            var currentHeightRatio = getHeight() / originalHeight;
            var padding = 2f * currentHeightRatio;
            var iconSize = getHeight() - padding * 2;

            label.setX(padding);
            label.setWidth(getWidth() - iconSize - padding * 3);
            label.setY((getHeight() - (label.getHeight() * label.scale * currentHeightRatio)) / 2f);

            icon.setX(getWidth() - padding - iconSize);
            icon.setY(padding);
            icon.setWidth(iconSize);
            icon.setHeight(iconSize);

            super.render(stack, bufferSource, mouseX, mouseY, partialTick);
        }

        public void setSelected(boolean selected, float currentFrameBaseX) {
            originalX = currentFrameBaseX;

            float newTargetWidth;
            float newTargetHeight;
            float newTargetX;

            if (selected) {
                newTargetWidth = originalWidth * SELECTED_SKILL_WIDTH_MULTIPLIER;
                newTargetHeight = originalHeight * SELECTED_SKILL_HEIGHT_MULTIPLIER;
                newTargetX = originalX - (newTargetWidth - originalWidth);
            } else {
                newTargetWidth = originalWidth;
                newTargetHeight = originalHeight;
                newTargetX = originalX;
            }

            targetX = newTargetX;
            targetY = originalY;
            targetWidth = newTargetWidth;
            targetHeight = newTargetHeight;
        }
    }
}