package org.academy.internal.client.renderer.effect;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.academy.api.client.render.effect.SpatialDistortionRenderer;
import org.academy.api.client.renderer.EffectRenderer;

import java.util.ArrayList;
import java.util.List;

import static org.academy.api.client.Render.RenderTypes.POS_COLOR_QUADS_BLOOM;

public final class DistortionEffectWrapper implements EffectRenderer {
    public static final DistortionEffectWrapper INSTANCE = new DistortionEffectWrapper();
    private final List<SpatialDistortionRenderer> activeEffects = new ArrayList<>();

    private DistortionEffectWrapper() {}

    public void trigger(float cx, float cy, float cz, float lifetime, float intensity,
                         float cr, float cg, float cb, float ca,
                         float er, float eg, float eb, float ea) {
        var fx = new SpatialDistortionRenderer();
        fx.setColors(cr, cg, cb, ca, er, eg, eb, ea);
        fx.trigger(cx, cy, cz, lifetime);
        activeEffects.add(fx);
    }

    @Override
    public void render(PoseStack poseStack, SubmitNodeCollector submitNodeCollector,
                       int packedLight, AvatarRenderState renderState, float yRot, float xRot) {
        if (activeEffects.isEmpty()) return;

        var deltaTime = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaTicks();
        var camera = Minecraft.getInstance().gameRenderer.mainCamera();

        for (var it = activeEffects.iterator(); it.hasNext(); ) {
            var fx = it.next();
            fx.update(deltaTime);
            if (!fx.isActive()) {
                it.remove();
                continue;
            }
            submitNodeCollector.submitCustomGeometry(poseStack, POS_COLOR_QUADS_BLOOM,
                    (pose, vc) -> fx.render(poseStack, vc, camera));
        }
    }

    @Override
    public void renderFirstPerson(PoseStack poseStack, SubmitNodeCollector nodeCollector,
                                   LocalPlayer player, int packedLight, float partialTick) {
        if (activeEffects.isEmpty()) return;

        var camera = Minecraft.getInstance().gameRenderer.mainCamera();

        for (var it = activeEffects.iterator(); it.hasNext(); ) {
            var fx = it.next();
            if (!fx.isActive()) {
                it.remove();
                continue;
            }
            nodeCollector.submitCustomGeometry(poseStack, POS_COLOR_QUADS_BLOOM,
                    (pose, vc) -> fx.render(poseStack, vc, camera));
        }
    }
}
