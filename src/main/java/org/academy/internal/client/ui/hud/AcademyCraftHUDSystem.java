package org.academy.internal.client.ui.hud;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.academy.AbilitySystemClient;
import org.academy.AcademyCraft;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static net.minecraft.client.renderer.RenderStateShard.POSITION_TEX_SHADER;
import static net.minecraft.client.renderer.RenderStateShard.TRANSLUCENT_TRANSPARENCY;

public class AcademyCraftHUDSystem {
    public static final RenderType.CompositeRenderType COMPUTING_POWER_BAR = RenderType.create(
            "computing_power_bar",
            DefaultVertexFormat.POSITION_TEX,
            VertexFormat.Mode.QUADS,
            16,
            false,
            true,
            RenderType.CompositeState.builder()
                    .setTextureState(new RenderStateShard.TextureStateShard(
                            new ResourceLocation(AcademyCraft.MOD_ID, "textures/gui/computing_power_bar.png"),
                            false,
                            false
                    ))
                    .setShaderState(POSITION_TEX_SHADER)
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
                            new ResourceLocation(AcademyCraft.MOD_ID, "textures/gui/computing_power_bar_background.png"),
                            false,
                            false
                    ))
                    .setShaderState(POSITION_TEX_SHADER)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .createCompositeState(false)
    );

    public static final Supplier<Float> SCALE_FACTOR = () -> 1.0f;
    public static final float DEFAULT_SCALA = 0.2F;

    public static final AtomicReference<Float> smoothProgress = new AtomicReference<>(0.0f);

    public static void init() {
        HudRenderCallback.EVENT.register(AcademyCraftHUDSystem::render);
    }

    public static void render(GuiGraphics guiGraphics, float partialTicks) {
        AcademyCraftHUDSystem.renderComputingPowerBarBackground(guiGraphics);
        AcademyCraftHUDSystem.renderComputingPowerBar(guiGraphics);
    }

    @SuppressWarnings("UnnecessaryLocalVariable")
    public static void renderComputingPowerBar(GuiGraphics guiGraphics) {
        final VertexConsumer vertexConsumer = guiGraphics.bufferSource().getBuffer(COMPUTING_POWER_BAR);

        smoothProgress.updateAndGet(oldVal -> oldVal + (AbilitySystemClient.getComputingPower() - oldVal) * 0.025f);
        final float progress = smoothProgress.get();

        final float userScale = SCALE_FACTOR.get();
        final float scale = DEFAULT_SCALA * userScale;

        final int imageWidth = 946;
        final int imageHeight = 147;
        final int imageLeftSafeZoneLength = 20;
        final int imageBarLength = 730;

        final float width = imageWidth * scale;
        final float height = imageHeight * scale;
        final float leftSafeZoneWidth = imageLeftSafeZoneLength * scale;
        final float barLength = imageBarLength * scale;

        final float sin = (float) Math.sin(Math.toRadians(55));
        final float barWidthOffset = barLength * (1.0f - progress);
        final float leftTopOffset = barWidthOffset + leftSafeZoneWidth;
        final float leftBottomOffset = (leftTopOffset + (height * sin));

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

        // Left Top
        vertexConsumer.vertex(leftTopX, leftTopY, z).uv(leftTopUv, 0).endVertex();
        // Left Bottom
        vertexConsumer.vertex(leftBottomX, leftBottomY, z).uv(leftBottomUv, 1).endVertex();
        // Right Bottom
        vertexConsumer.vertex(rightBottomX, rightBottomY, z).uv(1, 1).endVertex();
        // Right Top
        vertexConsumer.vertex(rightTopX, rightTopY, z).uv(1, 0).endVertex();
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