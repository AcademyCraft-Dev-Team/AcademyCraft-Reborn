package org.academy.internal.client.ui.hud;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.AbilityCategory;
import org.academy.api.common.util.MathUtil;

import java.util.function.Function;
import java.util.function.Supplier;

import static net.minecraft.client.renderer.RenderStateShard.*;

public class AcademyCraftHUDSystem {
    public static final RenderType.CompositeRenderType COMPUTING_POWER_BAR = RenderType.create(
            "computing_power_bar",
            DefaultVertexFormat.POSITION_COLOR_TEX,
            VertexFormat.Mode.QUADS,
            16,
            false,
            true,
            RenderType.CompositeState.builder()
                    .setTextureState(new RenderStateShard.TextureStateShard(
                            new ResourceLocation(AcademyCraft.MOD_ID, "textures/ui/hud/computing_power_bar.png"),
                            false,
                            false
                    ))
                    .setShaderState(POSITION_COLOR_TEX_SHADER)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .createCompositeState(false)
    );

    public static final RenderType.CompositeRenderType COMPUTING_POWER_BAR_BACKGROUND = RenderType.create(
            "computing_power_bar_background",
            DefaultVertexFormat.POSITION_TEX,
            VertexFormat.Mode.QUADS,
            16,
            false,
            true,
            RenderType.CompositeState.builder()
                    .setTextureState(new RenderStateShard.TextureStateShard(
                            new ResourceLocation(AcademyCraft.MOD_ID,
                                    "textures/ui/hud/computing_power_bar_background.png"
                            ),
                            false,
                            false
                    ))
                    .setShaderState(POSITION_TEX_SHADER)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .createCompositeState(false)
    );

    public static final Function<AbilityCategory, RenderType> ABILITY_ICON = abilityCategory ->
            RenderType.create(
                    "ability_icon",
                    DefaultVertexFormat.POSITION_COLOR_TEX,
                    VertexFormat.Mode.QUADS,
                    16,
                    false,
                    true,
                    RenderType.CompositeState.builder()
                            .setTextureState(new RenderStateShard.TextureStateShard(
                                    new ResourceLocation(AcademyCraft.MOD_ID,
                                            "textures/ui/hud/ability/" + abilityCategory.name + "/icon_overlay.png"
                                    ),
                                    false,
                                    false
                            ))
                            .setShaderState(POSITION_COLOR_TEX_SHADER)
                            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                            .createCompositeState(false)
            );

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

    public static float smoothProgress;

    public static void render(GuiGraphics guiGraphics, float partialTicks) {
        if (AbilitySystemClient.isActiveHUD()) {
            AcademyCraftHUDSystem.renderComputingPowerBarBackground(guiGraphics);
            AcademyCraftHUDSystem.renderComputingPowerBar(guiGraphics);
            AcademyCraftHUDSystem.renderAbilityIcon(guiGraphics);
        }
    }

    public static void renderAbilityIcon(GuiGraphics guiGraphics) {
        final AbilityCategory abilityCategory = AbilitySystemClient.getCategory();
        if (abilityCategory == null) return;
        final VertexConsumer vertexConsumer = guiGraphics.bufferSource().getBuffer(ABILITY_ICON.apply(abilityCategory));
        final float scale = DEFAULT_SCALA * SCALE_FACTOR.get();

        final float width = ABILITY_ICON_WIDTH * scale;
        final float height = ABILITY_ICON_HEIGHT * scale;
        final float rightSafeZone = (COMPUTING_POWER_BAR_RIGHT_SAFE_ZONE + ABILITY_ICON_RIGHT_SAFE_ZONE) * scale;
        final float topSafeZone = (COMPUTING_POWER_BAR_TOP_SAFE_ZONE + ABILITY_ICON_TOP_SAFE_ZONE) * scale;

        final float z = 8;

        final float rightTopX = guiGraphics.guiWidth() - rightSafeZone;
        final float rightTopY = topSafeZone;

        final float rightBottomX = rightTopX;
        final float rightBottomY = rightTopY + height;

        final float leftTopX = rightTopX - width;
        final float leftTopY = rightTopY;

        final float leftBottomX = rightBottomX - width;
        final float leftBottomY = rightBottomY;
        // Left Top
        vertexConsumer.vertex(leftTopX, leftTopY, z).color(255, 255, 255, 255).uv(0, 0).endVertex();
        // Left Bottom
        vertexConsumer.vertex(leftBottomX, leftBottomY, z).color(255, 255, 255, 255).uv(0, 1).endVertex();
        // Right Bottom
        vertexConsumer.vertex(rightBottomX, rightBottomY, z).color(255, 255, 255, 255).uv(1, 1).endVertex();
        // Right Top
        vertexConsumer.vertex(rightTopX, rightTopY, z).color(255, 255, 255, 255).uv(1, 0).endVertex();
    }

    @SuppressWarnings("UnnecessaryLocalVariable")
    public static void renderComputingPowerBar(GuiGraphics guiGraphics) {
        final VertexConsumer vertexConsumer = guiGraphics.bufferSource().getBuffer(COMPUTING_POWER_BAR);
        final float computingPower = AbilitySystemClient.getComputingPower();
        final float maximumComputingPower = AbilitySystemClient.getMaximumComputingPower();
        final float progress;
        if (computingPower != 0 && maximumComputingPower != 0) {
            progress = computingPower / maximumComputingPower;
        } else {
            progress = 0;
        }
        smoothProgress = MathUtil.lerp(smoothProgress, progress, 0.125f);
        final float scale = DEFAULT_SCALA * SCALE_FACTOR.get();

        final float width = COMPUTING_POWER_BAR_WIDTH * scale;
        final float height = COMPUTING_POWER_BAR_HEIGHT * scale;
        final float leftSafeZoneWidth = (COMPUTING_POWER_BAR_LEFT_SAFE_ZONE - (COMPUTING_POWER_BAR_TOP_SAFE_ZONE / COMPUTING_POWER_BAR_TANGENT)) * scale;
        final float barLength = COMPUTING_POWER_BAR_CONSUMABLE_WIDTH * scale;

        final float barWidthOffset = barLength * (1.0f - smoothProgress);
        final float leftTopOffset = barWidthOffset + leftSafeZoneWidth;
        final float leftBottomOffset = leftTopOffset + (height / COMPUTING_POWER_BAR_TANGENT);

        final float z = 4;

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

        // Left Top
        vertexConsumer.vertex(leftTopX, leftTopY, z).color(255, 255, 255, 255).uv(leftTopUv, 0).endVertex();
        // Left Bottom
        vertexConsumer.vertex(leftBottomX, leftBottomY, z).color(255, 255, 255, 255).uv(leftBottomUv, 1).endVertex();
        // Right Bottom
        vertexConsumer.vertex(rightBottomX, rightBottomY, z).color(255, 255, 255, 255).uv(1, 1).endVertex();
        // Right Top
        vertexConsumer.vertex(rightTopX, rightTopY, z).color(255, 255, 255, 255).uv(1, 0).endVertex();
    }

    @SuppressWarnings("UnnecessaryLocalVariable")
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
        // Left Top
        vertexConsumer.vertex(leftTopX, leftTopY, z).uv(0, 0).endVertex();
        // Left Bottom
        vertexConsumer.vertex(leftBottomX, leftBottomY, z).uv(0, 1).endVertex();
        // Right Bottom
        vertexConsumer.vertex(rightBottomX, rightBottomY, z).uv(1, 1).endVertex();
        // Right Top
        vertexConsumer.vertex(rightTopX, rightTopY, z).uv(1, 0).endVertex();
    }

    private AcademyCraftHUDSystem() {
    }
}