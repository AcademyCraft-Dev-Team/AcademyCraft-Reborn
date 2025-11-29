package org.academy.api.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;

@FunctionalInterface
public interface EffectRenderer {
    void render(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int packedLight, AvatarRenderState renderState, float yRot, float xRot);

    default void renderFirstPerson(PoseStack poseStack, SubmitNodeCollector nodeCollector, LocalPlayer player, int packedLight, float partialTick) {

    }
}