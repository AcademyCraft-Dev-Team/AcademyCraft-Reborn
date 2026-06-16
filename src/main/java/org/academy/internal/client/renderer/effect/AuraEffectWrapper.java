package org.academy.internal.client.renderer.effect;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.academy.api.client.render.effect.AuraRenderer;
import org.academy.api.client.renderer.EffectRenderer;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static org.academy.api.client.Render.RenderTypes.POS_COLOR_QUADS_BLOOM;

public final class AuraEffectWrapper implements EffectRenderer {
    public static final AuraEffectWrapper INSTANCE = new AuraEffectWrapper();
    private final List<AuraRenderer> activeAuras = new ArrayList<>();
    private final Map<AuraRenderer, Float> lifetimes = new IdentityHashMap<>();
    private final Map<AuraRenderer, float[]> positions = new IdentityHashMap<>();
    private final Map<AuraRenderer, Float> radii = new IdentityHashMap<>();

    private AuraEffectWrapper() {}

    public void triggerSphere(float cx, float cy, float cz, float radius,
                              float ir, float ig, float ib, float ia,
                              float or, float og, float ob, float oa,
                              float lifetime) {
        var aura = new AuraRenderer();
        var layer = aura.addLayer();
        layer.setInnerColor(ir, ig, ib, ia);
        layer.setOuterColor(or, og, ob, oa);
        aura.setActive(true);
        aura.setRimEnabled(true, 0.4f);
        aura.setHaloEnabled(true, 1.15f, 0.15f);
        activeAuras.add(aura);
        lifetimes.put(aura, lifetime);
        positions.put(aura, new float[]{cx, cy, cz});
        radii.put(aura, radius);
    }

    @Override
    public void render(PoseStack poseStack, SubmitNodeCollector submitNodeCollector,
                       int packedLight, AvatarRenderState renderState, float yRot, float xRot) {
        if (activeAuras.isEmpty()) return;

        var deltaTime = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaTicks();
        var camera = Minecraft.getInstance().gameRenderer.mainCamera();
        var camPos = camera.position();

        for (var it = activeAuras.iterator(); it.hasNext(); ) {
            var aura = it.next();
            aura.update(deltaTime);

            var remaining = lifetimes.getOrDefault(aura, 0f) - deltaTime;
            if (remaining <= 0) {
                lifetimes.remove(aura);
                positions.remove(aura);
                radii.remove(aura);
                it.remove();
                continue;
            }
            lifetimes.put(aura, remaining);

            var pos = positions.get(aura);
            var radius = radii.getOrDefault(aura, 1.5f);
            var relX = pos[0] - (float) camPos.x;
            var relY = pos[1] - (float) camPos.y;
            var relZ = pos[2] - (float) camPos.z;

            submitNodeCollector.submitCustomGeometry(poseStack, POS_COLOR_QUADS_BLOOM,
                    (pose, vc) -> aura.renderSphere(poseStack, vc,
                            relX, relY, relZ, radius, 32, 64));
        }
    }

    @Override
    public void renderFirstPerson(PoseStack poseStack, SubmitNodeCollector nodeCollector,
                                   LocalPlayer player, int packedLight, float partialTick) {
        if (activeAuras.isEmpty()) return;

        // Use partialTick for smooth first-person animation
        var camera = Minecraft.getInstance().gameRenderer.mainCamera();
        var camPos = camera.position();

        for (AuraRenderer aura : activeAuras) {
            if (!lifetimes.containsKey(aura)) continue;

            var pos = positions.get(aura);
            if (pos == null) continue;
            var radius = radii.getOrDefault(aura, 1.5f);
            var relX = pos[0] - (float) camPos.x;
            var relY = pos[1] - (float) camPos.y;
            var relZ = pos[2] - (float) camPos.z;

            nodeCollector.submitCustomGeometry(poseStack, POS_COLOR_QUADS_BLOOM,
                    (pose, vc) -> aura.renderSphere(poseStack, vc,
                            relX, relY, relZ, radius, 32, 64));
        }
    }
}
