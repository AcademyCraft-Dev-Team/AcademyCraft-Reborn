package org.academy.api.client.renderer.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.academy.AcademyCraft;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.resource.TextureResources;
import org.academy.api.client.util.RenderUtil;
import org.academy.api.common.ability.AbilityCategory;
import org.academy.api.common.util.MathUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

public class HUDManager {
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

    private HUDManager() {
    }

    public static void init() {
        initialized = true;
    }

    public static void registerHUDRenderer(HUDRenderer renderer) {
        if (!initialized) HUD_RENDERERS.add(renderer);
    }

    public static void render(GuiGraphics guiGraphics, float partialTick) {
        targetAlpha = AbilitySystemClient.isActiveHUD() ? 1.0f : 0.0f;

        float factor = MathUtil.animationFactor(MathUtil.PI, Minecraft.getInstance().getDeltaFrameTime());
        currentAlpha = MathUtil.lerpStartEndFactor(currentAlpha, targetAlpha, factor);

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
        guiGraphics.bufferSource().endBatch(currentIconRenderType);

        RenderSystem.setShaderColor(originColor[0], originColor[1], originColor[2], originColor[3]);
    }

    @SuppressWarnings({"UnnecessaryLocalVariable", "DuplicatedCode"})
    public static void renderAbilityIcon(GuiGraphics guiGraphics) {
        final AbilityCategory abilityCategory = AbilitySystemClient.getCategory();
        currentIconRenderType = ABILITY_ICON.apply(abilityCategory);
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
        smoothProgress = MathUtil.lerpStartEndFactor(smoothProgress, progress, MathUtil.animationFactor(MathUtil.PI / 2, Minecraft.getInstance().getDeltaFrameTime()));
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
}