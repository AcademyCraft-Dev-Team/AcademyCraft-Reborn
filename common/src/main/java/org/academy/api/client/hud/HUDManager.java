package org.academy.api.client.hud;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import org.academy.AcademyCraft;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.gui.widget.AutoScaleLabelWidget;
import org.academy.api.client.gui.widget.ColorFillWidget;
import org.academy.api.client.gui.widget.ImageWidget;
import org.academy.api.client.gui.widget.PanelWidget;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.renderer.RenderTypes;
import org.academy.api.client.resource.TextureResources;
import org.academy.api.client.util.ClientUtil;
import org.academy.api.client.util.RenderUtil;
import org.academy.api.client.vanilla.ClientTickEvent;
import org.academy.api.common.ability.AbilityCategory;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.util.MathUtil;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

public final class HUDManager {
    private static boolean initialized = false;
    private static final List<HUDRenderer> HUD_RENDERERS = new ArrayList<>();
    public static final RenderType COMPUTING_POWER_BAR =
            RenderUtil.getPositionTexRenderType(
                    "computing_power_bar",
                    TextureResources.TEXTURE_COMPUTING_POWER_BAR,
                    false);
    public static final RenderType COMPUTING_POWER_BAR_BACKGROUND =
            RenderUtil.getPositionTexRenderType(
                    "computing_power_bar_background",
                    TextureResources.TEXTURE_COMPUTING_POWER_BAR_BACKGROUND,
                    false);
    public static final Function<AbilityCategory, RenderType> ABILITY_ICON = abilityCategory ->
            RenderUtil.getPositionTexRenderType("ability_icon", new ResourceLocation(AcademyCraft.MOD_ID,
                    "textures/ability/" + abilityCategory.name + "/icon_overlay.png"
            ), false);
    public static final Supplier<Float> SCALE_FACTOR = () -> 1.0f;
    public static final float DEFAULT_SCALA = 0.2F;
    public static final int COMPUTING_POWER_BAR_WIDTH = 964;
    public static final int COMPUTING_POWER_BAR_HEIGHT = 147;
    public static final int COMPUTING_POWER_BAR_CONSUMABLE_WIDTH = 743;
    public static final int COMPUTING_POWER_BAR_LEFT_SAFE_ZONE = 46;
    public static final int COMPUTING_POWER_BAR_RIGHT_SAFE_ZONE = 34;
    public static final int COMPUTING_POWER_BAR_TOP_SAFE_ZONE = 30;
    public static final float COMPUTING_POWER_BAR_ANGLE = 50F;
    public static final float COMPUTING_POWER_BAR_TANGENT = (float) Math.tan(Math.toRadians(COMPUTING_POWER_BAR_ANGLE));
    public static final int ABILITY_ICON_WIDTH = 64;
    public static final int ABILITY_ICON_HEIGHT = 64;
    public static final int ABILITY_ICON_RIGHT_SAFE_ZONE = 10;
    public static final int ABILITY_ICON_TOP_SAFE_ZONE = 10;
    public static float targetAlpha;
    public static float currentAlpha;
    public static float smoothProgress;
    private static RenderType currentIconRenderType;

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

        AcademyCraft.EVENT_BUS.register(HUDManager.class);
    }

    public static void registerHUDRenderer(HUDRenderer renderer) {
        if (!initialized) HUD_RENDERERS.add(renderer);
    }

    private static void updateSkillWidgetsList() {
        Set<Skill> learnedSkills = AbilitySystemClient.LEARNED_SKILLS;
        if (skillWidgets.size() == learnedSkills.size()) {
            boolean match = true;
            List<Skill> tempList = new ArrayList<>(learnedSkills);
            tempList.sort(Comparator.comparing(skill -> skill.name));
            for (int i = 0; i < skillWidgets.size(); i++) {
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

        List<Skill> sortedSkills = new ArrayList<>(AbilitySystemClient.LEARNED_SKILLS);
        sortedSkills.sort(Comparator.comparing(skill -> skill.name));

        for (Skill skill : sortedSkills) {
            Window window = Minecraft.getInstance().getWindow();
            SkillWidget widget = new SkillWidget(window.getGuiScaledWidth(), window.getGuiScaledHeight() / 2f, DEFAULT_SKILL_WIDGET_WIDTH, DEFAULT_SKILL_WIDGET_HEIGHT, skill);
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
        float screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        float baseSkillWidgetX = screenWidth - DEFAULT_SKILL_WIDGET_WIDTH - SKILL_LIST_RIGHT_MARGIN;
        skillWidgets.get(selectedSkillIndex).setSelected(false, baseSkillWidgetX);
        selectedSkillIndex = (selectedSkillIndex + 1) % skillWidgets.size();
        skillWidgets.get(selectedSkillIndex).setSelected(true, baseSkillWidgetX);
        ClientUtil.playDownSound();
    }

    public static void selectPreviousSkill() {
        if (skillWidgets.isEmpty() || !AbilitySystemClient.isActiveHUD()) return;
        float screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        float baseSkillWidgetX = screenWidth - DEFAULT_SKILL_WIDGET_WIDTH - SKILL_LIST_RIGHT_MARGIN;
        skillWidgets.get(selectedSkillIndex).setSelected(false, baseSkillWidgetX);
        selectedSkillIndex = (selectedSkillIndex - 1 + skillWidgets.size()) % skillWidgets.size();
        skillWidgets.get(selectedSkillIndex).setSelected(true, baseSkillWidgetX);
        ClientUtil.playDownSound();
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent event) {
        if (!initialized) return;

        if (AbilitySystemClient.isActiveHUD()) {
            updateSkillWidgetsList();
        }
    }

    public static void render(GuiGraphics guiGraphics, float partialTick) {
        targetAlpha = AbilitySystemClient.isActiveHUD() ? 1.0f : 0.0f;

        float animFactor = ClientUtil.animationFactor(MathUtil.PI / 2);
        currentAlpha = MathUtil.lerpStartEndFactor(currentAlpha, targetAlpha, animFactor);

        float[] originColor = RenderSystem.getShaderColor().clone();

        for (HUDRenderer renderer : HUD_RENDERERS) {
            renderer.render(guiGraphics, partialTick);
        }

        RenderSystem.setShaderColor(originColor[0], originColor[1], originColor[2], currentAlpha * originColor[3]);

        HUDManager.renderComputingPowerBarBackground(guiGraphics);
        guiGraphics.bufferSource().endBatch(COMPUTING_POWER_BAR_BACKGROUND);

        float targetR = 1.0f;
        float targetG = 174 / 255.0f;
        float targetB = 68 / 255.0f;

        float finalR = targetR + currentAlpha * (originColor[0] - targetR);
        float finalG = targetG + currentAlpha * (originColor[1] - targetG);
        float finalB = targetB + currentAlpha * (originColor[2] - targetB);
        float finalA = currentAlpha * originColor[3];

        RenderSystem.setShaderColor(finalR, finalG, finalB, finalA);
        HUDManager.renderComputingPowerBar(guiGraphics, partialTick);
        guiGraphics.bufferSource().endBatch(COMPUTING_POWER_BAR);

        RenderSystem.setShaderColor(originColor[0], originColor[1], originColor[2], currentAlpha * originColor[3]);
        HUDManager.renderAbilityIcon(guiGraphics);
        if (currentIconRenderType != null) {
            guiGraphics.bufferSource().endBatch(currentIconRenderType);
        }

        if (!skillWidgets.isEmpty()) {
            float screenWidth = guiGraphics.guiWidth();
            float screenHeight = guiGraphics.guiHeight();
            float baseSkillWidgetX = screenWidth - DEFAULT_SKILL_WIDGET_WIDTH - SKILL_LIST_RIGHT_MARGIN;

            for (int i = 0; i < skillWidgets.size(); i++) {
                skillWidgets.get(i).setSelected(i == selectedSkillIndex, baseSkillWidgetX);
            }

            float totalListTargetHeight = 0;
            for (int i = 0; i < skillWidgets.size(); i++) {
                SkillWidget widget = skillWidgets.get(i);
                totalListTargetHeight += widget.targetHeight;
                if (i < skillWidgets.size() - 1) {
                    totalListTargetHeight += SKILL_WIDGET_SPACING;
                }
            }

            float currentLayoutY = (screenHeight - totalListTargetHeight) / 2f;

            for (int i = 0; i < skillWidgets.size(); i++) {
                SkillWidget widget = skillWidgets.get(i);

                boolean selected = i == selectedSkillIndex;
                widget.originalY = currentLayoutY;
                widget.setSelected(selected, baseSkillWidgetX);

                widget.setAlpha((selected ? 1 : 0.65f) * currentAlpha);
                widget.render(guiGraphics, 0, 0, partialTick);

                currentLayoutY += widget.targetHeight + SKILL_WIDGET_SPACING;
            }
        }

        RenderSystem.setShaderColor(originColor[0], originColor[1], originColor[2], originColor[3]);
    }

    @SuppressWarnings({"UnnecessaryLocalVariable", "DuplicatedCode"})
    public static void renderAbilityIcon(GuiGraphics guiGraphics) {
        final AbilityCategory abilityCategory = AbilitySystemClient.getCategory();
        currentIconRenderType = ABILITY_ICON.apply(abilityCategory);
        if (currentIconRenderType == null) return;

        final VertexConsumer vertexConsumer = guiGraphics.bufferSource().getBuffer(currentIconRenderType);
        final float scale = DEFAULT_SCALA * SCALE_FACTOR.get();

        final float width = ABILITY_ICON_WIDTH * scale;
        final float height = ABILITY_ICON_HEIGHT * scale;
        final float rightSafeZone = (COMPUTING_POWER_BAR_RIGHT_SAFE_ZONE + ABILITY_ICON_RIGHT_SAFE_ZONE) * scale;
        final float topSafeZone = (COMPUTING_POWER_BAR_TOP_SAFE_ZONE + ABILITY_ICON_TOP_SAFE_ZONE) * scale;

        final float z = 0;

        final float rightTopX = guiGraphics.guiWidth() - rightSafeZone;
        final float rightTopY = topSafeZone;

        final float rightBottomX = rightTopX;
        final float rightBottomY = rightTopY + height;

        final float leftTopX = rightTopX - width;
        final float leftTopY = rightTopY;

        final float leftBottomX = rightBottomX - width;
        final float leftBottomY = rightBottomY;
        vertexConsumer.vertex(leftTopX, leftTopY, z).uv(0, 0).endVertex();
        vertexConsumer.vertex(leftBottomX, leftBottomY, z).uv(0, 1).endVertex();
        vertexConsumer.vertex(rightBottomX, rightBottomY, z).uv(1, 1).endVertex();
        vertexConsumer.vertex(rightTopX, rightTopY, z).uv(1, 0).endVertex();
    }

    @SuppressWarnings("UnnecessaryLocalVariable")
    public static void renderComputingPowerBar(GuiGraphics guiGraphics, float partialTick) {
        final VertexConsumer vertexConsumer = guiGraphics.bufferSource().getBuffer(COMPUTING_POWER_BAR);
        final float computingPower = AbilitySystemClient.getComputingPower();
        final float maximumComputingPower = AbilitySystemClient.getMaximumComputingPower();
        final float progress;
        if (computingPower != 0 && maximumComputingPower != 0) {
            progress = computingPower / maximumComputingPower;
        } else {
            progress = 0;
        }
        smoothProgress = MathUtil.lerpStartEndFactor(smoothProgress, progress, ClientUtil.animationFactor(MathUtil.PI / 2));
        if (Float.isNaN(smoothProgress)) {
            smoothProgress = 0f;
        }
        final float scale = DEFAULT_SCALA * SCALE_FACTOR.get();

        final float width = COMPUTING_POWER_BAR_WIDTH * scale;
        final float height = COMPUTING_POWER_BAR_HEIGHT * scale;
        final float leftSafeZoneWidth = (COMPUTING_POWER_BAR_LEFT_SAFE_ZONE - (COMPUTING_POWER_BAR_TOP_SAFE_ZONE / COMPUTING_POWER_BAR_TANGENT)) * scale;
        final float barLength = COMPUTING_POWER_BAR_CONSUMABLE_WIDTH * scale;

        final float barWidthOffset = barLength * (1.0f - smoothProgress);
        final float leftTopOffset = barWidthOffset + leftSafeZoneWidth;
        final float leftBottomOffset = leftTopOffset + (height / COMPUTING_POWER_BAR_TANGENT);

        final float z = 0;

        final float rightTopX = guiGraphics.guiWidth();
        final float rightTopY = 0;

        final float rightBottomX = guiGraphics.guiWidth();
        final float rightBottomY = height;

        final float leftTopX = rightTopX - width + leftTopOffset;
        final float leftTopY = 0;

        final float leftBottomX = rightTopX - width + leftBottomOffset;

        final float leftBottomY = height;

        final float leftTopUv = 1 - ((width - leftTopOffset) / width);
        final float leftBottomUv = 1 - ((width - leftBottomOffset) / width);

        vertexConsumer.vertex(leftTopX, leftTopY, z).uv(leftTopUv, 0).endVertex();
        vertexConsumer.vertex(leftBottomX, leftBottomY, z).uv(leftBottomUv, 1).endVertex();
        vertexConsumer.vertex(rightBottomX, rightBottomY, z).uv(1, 1).endVertex();
        vertexConsumer.vertex(rightTopX, rightTopY, z).uv(1, 0).endVertex();
    }

    @SuppressWarnings({"UnnecessaryLocalVariable", "DuplicatedCode"})
    public static void renderComputingPowerBarBackground(GuiGraphics guiGraphics) {
        final VertexConsumer vertexConsumer = guiGraphics.bufferSource().getBuffer(COMPUTING_POWER_BAR_BACKGROUND);
        final int imageWidth = 946;
        final int imageHeight = 147;

        final float userScale = SCALE_FACTOR.get();
        final float scale = DEFAULT_SCALA * userScale;

        final float width = imageWidth * scale;
        final float height = imageHeight * scale;

        final float z = 0;

        final float rightTopX = guiGraphics.guiWidth();
        final float rightTopY = 0;

        final float rightBottomX = guiGraphics.guiWidth();
        final float rightBottomY = height;

        final float leftTopX = rightTopX - width;
        final float leftTopY = 0;

        final float leftBottomX = rightBottomX - width;
        final float leftBottomY = height;
        vertexConsumer.vertex(leftTopX, leftTopY, z).uv(0, 0).endVertex();
        vertexConsumer.vertex(leftBottomX, leftBottomY, z).uv(0, 1).endVertex();
        vertexConsumer.vertex(rightBottomX, rightBottomY, z).uv(1, 1).endVertex();
        vertexConsumer.vertex(rightTopX, rightTopY, z).uv(1, 0).endVertex();
    }

    public static final class SkillWidget extends PanelWidget {
        private final Skill skill;
        private final ColorFillWidget back;
        private final AutoScaleLabelWidget label;
        private final ImageWidget icon;

        private boolean isSelected = false;
        public float originalX, originalY, originalWidth, originalHeight;
        private float targetX;
        public float targetY;
        public float targetWidth;
        public float targetHeight;
        private float currentWidgetAlpha = 0.0f;
        private float targetWidgetAlpha = 0.0f;

        public SkillWidget(float initialXOffset, float initialYOffset, float width, float height, Skill skill) {
            super(initialXOffset, initialYOffset, width, height);
            this.skill = skill;
            this.originalX = 0;
            this.originalY = initialYOffset;
            this.targetY = initialYOffset;
            this.originalWidth = width;
            this.originalHeight = height;

            this.targetX = 0;
            this.targetWidth = width;
            this.targetHeight = height;

            float padding = 2f;
            float iconSize = height - padding * 2;

            this.label = new AutoScaleLabelWidget(skill.name, padding, 0, width - iconSize - padding * 3, false);
            this.label.scale = 0.7f;
            this.label.setCentered(false);

            this.icon = new ImageWidget(width - padding - iconSize, padding, iconSize, iconSize, getSkillIconRenderType(skill));
            this.icon.widthScale = 0.8f;
            this.icon.heightScale = 0.8f;

            this.back = new ColorFillWidget(0, 0, width, height, 0x60000000);

            addChild("back", this.back);
            addChild("label", this.label);
            addChild("icon", this.icon);
        }

        private RenderType getSkillIconRenderType(Skill skillInstance) {
            for (List<AbilitySystemClient.SkillInfo> infos : AbilitySystemClient.SKILL_INFOS.values()) {
                for (AbilitySystemClient.SkillInfo info : infos) {
                    if (info.skill().equals(skillInstance)) {
                        return RenderUtil.getPositionColorTexRenderTypeFull("skill_icon_hud_" + skillInstance.name.toLowerCase().replace(" ", "_"), info.texture(), false);
                    }
                }
            }
            return RenderTypes.RENDER_TYPE_ICON_BOX;
        }

        public void setAlpha(float globalAlpha) {
            this.targetWidgetAlpha = globalAlpha;
        }

        @Override
        public void render(GuiGraphics guiGraphics, double mouseX, double mouseY, float partialTick) {
            float animFactor = ClientUtil.animationFactor(MathUtil.PI / 2);
            this.currentWidgetAlpha = MathUtil.lerpStartEndFactor(this.currentWidgetAlpha, this.targetWidgetAlpha, animFactor);

            this.setX(MathUtil.lerpStartEndFactor(this.getX(), targetX, animFactor));
            this.setY(MathUtil.lerpStartEndFactor(this.getY(), targetY, animFactor));
            this.setWidth(MathUtil.lerpStartEndFactor(this.getWidth(), targetWidth, animFactor));
            this.setHeight(MathUtil.lerpStartEndFactor(this.getHeight(), targetHeight, animFactor));

            int finalBackAlpha = (int) (0x70 * this.currentWidgetAlpha);
            this.back.color = (this.back.color & 0x00FFFFFF) | (finalBackAlpha << 24);

            int finalLabelAlpha = (int) (0xFF * this.currentWidgetAlpha);
            this.label.color = (this.label.color & 0x00FFFFFF) | (finalLabelAlpha << 24);
            this.icon.alpha = this.currentWidgetAlpha;

            this.back.setWidth(this.getWidth());
            this.back.setHeight(this.getHeight());

            float currentHeightRatio = this.getHeight() / originalHeight;
            float padding = 2f * currentHeightRatio;
            float iconSize = this.getHeight() - padding * 2;

            this.label.setX(padding);
            this.label.setWidth(this.getWidth() - iconSize - padding * 3);
            this.label.setY((this.getHeight() - (this.label.getHeight() * this.label.scale * currentHeightRatio)) / 2f);

            this.icon.setX(this.getWidth() - padding - iconSize);
            this.icon.setY(padding);
            this.icon.setWidth(iconSize);
            this.icon.setHeight(iconSize);

            super.render(guiGraphics, mouseX, mouseY, partialTick);
        }

        public void setSelected(boolean selected, float currentFrameBaseX) {
            this.originalX = currentFrameBaseX;

            boolean selectionChanged = (this.isSelected != selected);
            this.isSelected = selected;

            float newTargetWidth;
            float newTargetHeight;
            float newTargetX;
            float newTargetY;

            if (selected) {
                newTargetWidth = originalWidth * SELECTED_SKILL_WIDTH_MULTIPLIER;
                newTargetHeight = originalHeight * SELECTED_SKILL_HEIGHT_MULTIPLIER;
                newTargetX = this.originalX - (newTargetWidth - originalWidth);
            } else {
                newTargetWidth = originalWidth;
                newTargetHeight = originalHeight;
                newTargetX = this.originalX;
            }
            newTargetY = this.originalY;

            if (selectionChanged ||
                    Math.abs(this.targetX - newTargetX) > 0.001f ||
                    Math.abs(this.targetY - newTargetY) > 0.001f ||
                    Math.abs(this.targetWidth - newTargetWidth) > 0.001f ||
                    Math.abs(this.targetHeight - newTargetHeight) > 0.001f) {

                this.targetX = newTargetX;
                this.targetY = newTargetY;
                this.targetWidth = newTargetWidth;
                this.targetHeight = newTargetHeight;
            }
        }
    }
}