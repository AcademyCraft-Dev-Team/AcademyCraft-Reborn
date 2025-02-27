package org.academy;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.academy.api.client.command.CommandManager;
import org.academy.api.client.ui.AcademyCraftHUDSystem;
import org.academy.api.common.ability.AbilityCategory;
import org.academy.api.common.ability.Skill;

import java.util.concurrent.TimeUnit;

import static net.minecraft.client.renderer.RenderStateShard.POSITION_TEX_SHADER;
import static net.minecraft.client.renderer.RenderStateShard.TRANSLUCENT_TRANSPARENCY;

public final class AbilitySystemClient {
    public static void init() {
        ClientLifecycleEvents.CLIENT_STARTED.register(minecraft -> {
            for (AbilityCategory abilityCategory : AbilitySystem.abilityCategoryMap.values()) {
                abilityCategory.initClient();
                for (Skill skill : abilityCategory.skillList) {
                    skill.initClient();
                }
            }
            CommandManager.registerCommands();

            AcademyCraft.executorService.scheduleAtFixedRate(() -> {
                if (minecraft.level != null) {
                    if (AcademyCraftHUDSystem.cp >= 1f) {
                        AcademyCraftHUDSystem.cp = 0.0f;
                    }
                    AcademyCraftHUDSystem.cp += 0.0025f;
                }
            }, 0, 50, TimeUnit.MILLISECONDS);
        });

        HudRenderCallback.EVENT.register(new HudRenderCallback() {
            public static final RenderType.CompositeRenderType CP = RenderType.create(
                    "cp",
                    DefaultVertexFormat.POSITION_TEX,
                    VertexFormat.Mode.QUADS,
                    16,
                    false,
                    true,
                    RenderType.CompositeState.builder()
                            .setTextureState(new RenderStateShard.TextureStateShard(
                                    new ResourceLocation(AcademyCraft.MOD_ID, "textures/gui/cp.png"),
                                    false,
                                    false
                            ))
                            .setShaderState(POSITION_TEX_SHADER)
                            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                            .createCompositeState(false)
            );
            private float smoothProgress = 0.0f;

            @Override
            public void onHudRender(GuiGraphics guiGraphics, float partialTicks) {
                final VertexConsumer vertexConsumer = guiGraphics.bufferSource().getBuffer(CP);

                final float targetProgress = AcademyCraftHUDSystem.cp;

                smoothProgress += (targetProgress - smoothProgress) * 0.025f;

                final float progress = smoothProgress;

                final float userScale = 1.0f;
                final float defaultScale = 0.2f;

                final float scale = defaultScale * userScale;

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
                final float rightBottomY = 0 + height;

                final float leftTopX = rightTopX - width + leftTopOffset;
                final float leftTopY = 0;

                final float leftBottomX = rightTopX - width + leftBottomOffset;
                final float leftBottomY = 0 + height;

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
        });
    }
}