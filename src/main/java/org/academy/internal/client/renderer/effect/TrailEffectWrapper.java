package org.academy.internal.client.renderer.effect;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.academy.api.client.render.effect.TrailRenderer;
import org.academy.api.client.renderer.EffectRenderer;

import java.util.ArrayList;
import java.util.List;

import static org.academy.api.client.Render.RenderTypes.POS_COLOR_TRANGLES_BLOOM;

public final class TrailEffectWrapper implements EffectRenderer {
    public static final TrailEffectWrapper INSTANCE = new TrailEffectWrapper();
    private final List<TrailRenderer> activeTrails = new ArrayList<>();

    private TrailEffectWrapper() {}

    public TrailRenderer createTrail(float maxAge, float width, float r, float g, float b) {
        var trail = new TrailRenderer(maxAge, width);
        trail.setColor(r, g, b);
        activeTrails.add(trail);
        return trail;
    }

    @Override
    public void render(PoseStack poseStack, SubmitNodeCollector submitNodeCollector,
                       int packedLight, AvatarRenderState renderState, float yRot, float xRot) {
        if (activeTrails.isEmpty()) return;

        var deltaTime = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaTicks();
        var camera = Minecraft.getInstance().gameRenderer.mainCamera();

        for (var it = activeTrails.iterator(); it.hasNext(); ) {
            var trail = it.next();
            trail.update(deltaTime);
            if (trail.isEmpty()) {
                it.remove();
                continue;
            }
            submitNodeCollector.submitCustomGeometry(poseStack, POS_COLOR_TRANGLES_BLOOM,
                    (pose, vc) -> trail.render(poseStack, vc, camera, trail.getR(), trail.getG(), trail.getB()));
        }
    }

    @Override
    public void renderFirstPerson(PoseStack poseStack, SubmitNodeCollector nodeCollector,
                                   LocalPlayer player, int packedLight, float partialTick) {
        if (activeTrails.isEmpty()) return;

        var deltaTime = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaTicks();
        var camera = Minecraft.getInstance().gameRenderer.mainCamera();

        for (var it = activeTrails.iterator(); it.hasNext(); ) {
            var trail = it.next();
            trail.update(deltaTime);
            if (trail.isEmpty()) {
                it.remove();
                continue;
            }
            nodeCollector.submitCustomGeometry(poseStack, POS_COLOR_TRANGLES_BLOOM,
                    (pose, vc) -> trail.render(poseStack, vc, camera, trail.getR(), trail.getG(), trail.getB()));
        }
    }
}
