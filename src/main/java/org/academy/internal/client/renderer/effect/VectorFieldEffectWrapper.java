package org.academy.internal.client.renderer.effect;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.academy.api.client.render.effect.VectorFieldRenderer;
import org.academy.api.client.renderer.EffectRenderer;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static org.academy.api.client.Render.RenderTypes.POS_COLOR_QUADS_BLOOM;

public final class VectorFieldEffectWrapper implements EffectRenderer {
    public static final VectorFieldEffectWrapper INSTANCE = new VectorFieldEffectWrapper();
    private final List<VectorFieldRenderer> activeFields = new ArrayList<>();
    private final Map<VectorFieldRenderer, Float> lifetimes = new IdentityHashMap<>();
    private final Map<VectorFieldRenderer, float[]> centers = new IdentityHashMap<>();

    private VectorFieldEffectWrapper() {
    }

    public void trigger(float cx, float cy, float cz, int gridX, int gridZ, float spacing,
                        float r, float g, float b, float lifetime) {
        var field = new VectorFieldRenderer(gridX, gridZ, spacing);
        field.setColor(r, g, b);
        field.setAllDirections((x, z) -> {
            var dx = (x - gridX / 2f) * 0.3f;
            var dz = (z - gridZ / 2f) * 0.3f;
            return new Vector3f(dx, 1.0f, dz).normalize();
        });
        activeFields.add(field);
        lifetimes.put(field, lifetime);
        centers.put(field, new float[]{cx, cy, cz});
    }

    @Override
    public void render(PoseStack poseStack, SubmitNodeCollector submitNodeCollector,
                       int packedLight, AvatarRenderState renderState, float yRot, float xRot) {
        if (activeFields.isEmpty()) return;

        var deltaTime = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaTicks();
        var camera = Minecraft.getInstance().gameRenderer.mainCamera();
        var camPos = camera.position();

        for (var it = activeFields.iterator(); it.hasNext(); ) {
            var field = it.next();
            field.update(deltaTime);

            var remaining = lifetimes.getOrDefault(field, 0f) - deltaTime;
            if (remaining <= 0) {
                lifetimes.remove(field);
                centers.remove(field);
                it.remove();
                continue;
            }
            lifetimes.put(field, remaining);

            var center = centers.get(field);
            var relX = center[0] - (float) camPos.x;
            var relY = center[1] - (float) camPos.y;
            var relZ = center[2] - (float) camPos.z;

            submitNodeCollector.submitCustomGeometry(poseStack, POS_COLOR_QUADS_BLOOM,
                    (pose, vc) -> field.render(poseStack, vc, camera,
                            relX, relY, relZ, 1.0f));
        }
    }

    @Override
    public void renderFirstPerson(PoseStack poseStack, SubmitNodeCollector nodeCollector,
                                  LocalPlayer player, int packedLight, float partialTick) {
        if (activeFields.isEmpty()) return;

        var camera = Minecraft.getInstance().gameRenderer.mainCamera();
        var camPos = camera.position();

        for (var field : activeFields) {
            if (!lifetimes.containsKey(field)) continue;
            var center = centers.get(field);
            if (center == null) continue;
            var relX = center[0] - (float) camPos.x;
            var relY = center[1] - (float) camPos.y;
            var relZ = center[2] - (float) camPos.z;

            nodeCollector.submitCustomGeometry(poseStack, POS_COLOR_QUADS_BLOOM,
                    (pose, vc) -> field.render(poseStack, vc, camera,
                            relX, relY, relZ, 1.0f));
        }
    }
}
