package org.academy.internal.client.render.vfx;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.academy.api.client.renderer.EffectRenderer;

public final class WingFirstPersonBridge implements EffectRenderer {
    public static final WingFirstPersonBridge INSTANCE = new WingFirstPersonBridge();

    private WingFirstPersonBridge() {
    }

    @Override
    public void render(PoseStack poseStack, SubmitNodeCollector collector, int packedLight,
                       AvatarRenderState renderState, float yRot, float xRot) {
        // Third-person wings are rendered by WingVfx through the Vfx pipeline.
    }

    @Override
    public void renderFirstPerson(PoseStack poseStack, SubmitNodeCollector collector,
                                  LocalPlayer player, int packedLight, float partialTick) {
        WingVfx.submitFirstPerson(poseStack, collector, player, packedLight, partialTick);
    }

    @Override
    public boolean renderFirstPersonWhenHudHidden() {
        return WingVfx.renderFirstPersonWhenHudHidden();
    }
}
