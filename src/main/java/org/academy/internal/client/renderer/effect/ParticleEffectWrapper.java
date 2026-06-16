package org.academy.internal.client.renderer.effect;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.academy.api.client.render.effect.ParticleEmitter;
import org.academy.api.client.renderer.EffectRenderer;

import java.util.ArrayList;
import java.util.List;

import static org.academy.api.client.Render.RenderTypes.POS_COLOR_QUADS_BLOOM;

public final class ParticleEffectWrapper implements EffectRenderer {
    public static final ParticleEffectWrapper INSTANCE = new ParticleEffectWrapper();
    private final List<ParticleEmitter> emitters = new ArrayList<>();

    private ParticleEffectWrapper() {}

    public ParticleEmitter createEmitter(float x, float y, float z) {
        var emitter = new ParticleEmitter();
        emitter.setPosition(x, y, z);
        emitter.setActive(true);
        emitters.add(emitter);
        return emitter;
    }

    @Override
    public void render(PoseStack poseStack, SubmitNodeCollector submitNodeCollector,
                       int packedLight, AvatarRenderState renderState, float yRot, float xRot) {
        if (emitters.isEmpty()) return;

        var deltaTime = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaTicks();
        var camera = Minecraft.getInstance().gameRenderer.mainCamera();

        for (var it = emitters.iterator(); it.hasNext(); ) {
            var emitter = it.next();
            emitter.update(deltaTime);
            if (!emitter.isActive()) {
                it.remove();
                continue;
            }
            submitNodeCollector.submitCustomGeometry(poseStack, POS_COLOR_QUADS_BLOOM,
                    (pose, vc) -> emitter.render(poseStack, vc, camera));
        }
    }

    @Override
    public void renderFirstPerson(PoseStack poseStack, SubmitNodeCollector nodeCollector,
                                   LocalPlayer player, int packedLight, float partialTick) {
        if (emitters.isEmpty()) return;

        var camera = Minecraft.getInstance().gameRenderer.mainCamera();

        for (var it = emitters.iterator(); it.hasNext(); ) {
            var emitter = it.next();
            if (!emitter.isActive()) {
                it.remove();
                continue;
            }
            nodeCollector.submitCustomGeometry(poseStack, POS_COLOR_QUADS_BLOOM,
                    (pose, vc) -> emitter.render(poseStack, vc, camera));
        }
    }
}
