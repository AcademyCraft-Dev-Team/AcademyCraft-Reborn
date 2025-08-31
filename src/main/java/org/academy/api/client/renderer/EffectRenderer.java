package org.academy.api.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import org.jetbrains.annotations.NotNull;

@FunctionalInterface
public interface EffectRenderer {
    void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, PlayerRenderState renderState, float yRot, float xRot);
}