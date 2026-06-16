package org.academy.internal.client.renderer.effect;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.phys.Vec3;
import org.academy.api.client.render.effect.EMFieldRenderer;
import org.academy.api.client.renderer.EffectRenderer;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static org.academy.api.client.Render.RenderTypes.POS_COLOR_QUADS_BLOOM;

public final class EMFieldEffectWrapper implements EffectRenderer {
    public static final EMFieldEffectWrapper INSTANCE = new EMFieldEffectWrapper();
    private static final float DEFAULT_LIFETIME = 4.0f;

    private final List<EMFieldRenderer> activeFields = new ArrayList<>();
    private final Map<EMFieldRenderer, Float> fieldLifetimes = new IdentityHashMap<>();

    private EMFieldEffectWrapper() {}

    public EMFieldRenderer createField() {
        return createField(DEFAULT_LIFETIME);
    }

    public EMFieldRenderer createField(float lifetime) {
        var field = new EMFieldRenderer();
        field.setActive(true);
        activeFields.add(field);
        fieldLifetimes.put(field, lifetime);
        return field;
    }

    public void addFieldLine(Vec3 from, Vec3 to, float r, float g, float b,
                             float thickness, float alpha, float waviness) {
        if (activeFields.isEmpty()) {
            createField();
        }
        var field = activeFields.getLast();
        var line = field.addFieldLine();
        line.setPoints(from.toVector3f(), to.toVector3f())
                .setColor(r, g, b)
                .setThickness(thickness)
                .setAlpha(alpha)
                .setWaviness(waviness, 16);
    }

    public void addFieldLine(Vec3 from, Vec3 to, float r, float g, float b,
                             float thickness, float alpha, float waviness, int segments) {
        if (activeFields.isEmpty()) {
            createField();
        }
        var field = activeFields.getLast();
        var line = field.addFieldLine();
        line.setPoints(from.toVector3f(), to.toVector3f())
                .setColor(r, g, b)
                .setThickness(thickness)
                .setAlpha(alpha)
                .setWaviness(waviness, segments);
    }

    public void addFieldLineWithBranches(Vec3 from, Vec3 to, float r, float g, float b,
                                          float thickness, float alpha, float waviness,
                                          int segments, int branchCount) {
        if (activeFields.isEmpty()) {
            createField();
        }
        var field = activeFields.getLast();
        var line = field.addFieldLine();
        line.setPoints(from.toVector3f(), to.toVector3f())
                .setColor(r, g, b)
                .setThickness(thickness)
                .setAlpha(alpha)
                .setWaviness(waviness, segments)
                .setBranchCount(branchCount);
    }

    public void clearLines() {
        for (var field : activeFields) {
            field.clearFieldLines();
        }
        activeFields.clear();
        fieldLifetimes.clear();
    }

    public void ensureActive() {
        if (activeFields.isEmpty()) {
            createField();
        }
    }

    @Override
    public void render(PoseStack poseStack, SubmitNodeCollector submitNodeCollector,
                       int packedLight, AvatarRenderState renderState, float yRot, float xRot) {
        if (activeFields.isEmpty()) return;

        var deltaTime = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaTicks();
        var camera = Minecraft.getInstance().gameRenderer.mainCamera();

        for (var it = activeFields.iterator(); it.hasNext(); ) {
            var field = it.next();
            field.update(deltaTime);

            var remaining = fieldLifetimes.getOrDefault(field, 0f) - deltaTime;
            if (remaining <= 0) {
                field.clearFieldLines();
                fieldLifetimes.remove(field);
                it.remove();
                continue;
            }
            fieldLifetimes.put(field, remaining);

            submitNodeCollector.submitCustomGeometry(poseStack, POS_COLOR_QUADS_BLOOM,
                    (pose, vc) -> field.render(poseStack, camera, deltaTime));
        }
    }

    @Override
    public void renderFirstPerson(PoseStack poseStack, SubmitNodeCollector nodeCollector,
                                   LocalPlayer player, int packedLight, float partialTick) {
        if (activeFields.isEmpty()) return;

        var camera = Minecraft.getInstance().gameRenderer.mainCamera();

        for (var field : activeFields) {
            nodeCollector.submitCustomGeometry(poseStack, POS_COLOR_QUADS_BLOOM,
                    (pose, vc) -> field.render(poseStack, camera, partialTick));
        }
    }
}
