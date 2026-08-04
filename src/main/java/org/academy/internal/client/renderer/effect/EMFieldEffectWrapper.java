package org.academy.internal.client.renderer.effect;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.academy.api.client.render.effect.EMFieldRenderer;
import org.academy.api.client.renderer.EffectRenderer;
import org.academy.api.client.vanilla.RenderLoopEvent;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static org.academy.api.client.render.Render.RenderTypes.POS_COLOR_QUADS_BLOOM;

public final class EMFieldEffectWrapper implements EffectRenderer {
    public static final EMFieldEffectWrapper INSTANCE = new EMFieldEffectWrapper();
    private static final float DEFAULT_LIFETIME = 4.0f;
    private static final int MAX_ACTIVE_FIELDS = 8;
    private static final int MAX_LINES_PER_FIELD = 24;
    private static final int MAX_TOTAL_LINES = 96;
    private static final double MAX_LINE_LENGTH_SQR = 128.0 * 128.0;

    private final List<EMFieldRenderer> activeFields = new ArrayList<>();
    private final Map<EMFieldRenderer, Float> fieldLifetimes = new IdentityHashMap<>();
    private boolean submittedThisFrame;

    private EMFieldEffectWrapper() {
        NeoForge.EVENT_BUS.register(this);
    }

    public EMFieldRenderer createField() {
        return createField(DEFAULT_LIFETIME);
    }

    public EMFieldRenderer createField(float lifetime) {
        while (activeFields.size() >= MAX_ACTIVE_FIELDS) {
            removeField(activeFields.getFirst());
        }
        var field = new EMFieldRenderer();
        field.setActive(true);
        activeFields.add(field);
        fieldLifetimes.put(field, Math.clamp(lifetime, 1.0f, 20.0f));
        return field;
    }

    public void addFieldLine(Vec3 from, Vec3 to, float r, float g, float b,
                             float thickness, float alpha, float waviness) {
        var field = getWritableField(from, to);
        if (field == null) return;
        var line = field.addFieldLine();
        line.setPoints(from.toVector3f(), to.toVector3f())
                .setColor(r, g, b)
                .setThickness(thickness)
                .setAlpha(alpha)
                .setWaviness(waviness, 16);
    }

    public void addFieldLine(Vec3 from, Vec3 to, float r, float g, float b,
                             float thickness, float alpha, float waviness, int segments) {
        var field = getWritableField(from, to);
        if (field == null) return;
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
        var field = getWritableField(from, to);
        if (field == null) return;
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

    private EMFieldRenderer getWritableField(Vec3 from, Vec3 to) {
        if (!isFinite(from) || !isFinite(to)) return null;
        var distanceSqr = from.distanceToSqr(to);
        if (!Double.isFinite(distanceSqr) || distanceSqr > MAX_LINE_LENGTH_SQR) return null;
        if (getTotalLineCount() >= MAX_TOTAL_LINES) return null;
        if (activeFields.isEmpty() || activeFields.getLast().getFieldLineCount() >= MAX_LINES_PER_FIELD) {
            createField();
        }
        return activeFields.getLast();
    }

    private int getTotalLineCount() {
        var total = 0;
        for (var field : activeFields) {
            total += field.getFieldLineCount();
        }
        return total;
    }

    private static boolean isFinite(Vec3 value) {
        return Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }

    private void removeField(EMFieldRenderer field) {
        field.clearFieldLines();
        activeFields.remove(field);
        fieldLifetimes.remove(field);
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Post event) {
        if (Minecraft.getInstance().level == null) {
            clearLines();
            return;
        }
        for (var it = activeFields.iterator(); it.hasNext(); ) {
            var field = it.next();
            field.update(1.0f);

            var remaining = fieldLifetimes.getOrDefault(field, 0f) - 1.0f;
            if (remaining <= 0) {
                field.clearFieldLines();
                fieldLifetimes.remove(field);
                it.remove();
            } else {
                fieldLifetimes.put(field, remaining);
            }
        }
    }

    @SubscribeEvent
    public void onRenderLoop(RenderLoopEvent event) {
        submittedThisFrame = false;
    }

    @Override
    public void render(PoseStack poseStack, SubmitNodeCollector submitNodeCollector,
                       int packedLight, AvatarRenderState renderState, float yRot, float xRot) {
        if (activeFields.isEmpty() || submittedThisFrame) return;
        submittedThisFrame = true;

        var camera = Minecraft.getInstance().gameRenderer.mainCamera();
        var worldPoseStack = new PoseStack();

        for (var field : activeFields) {
            submitNodeCollector.submitCustomGeometry(worldPoseStack, POS_COLOR_QUADS_BLOOM,
                    (pose, vc) -> field.render(worldPoseStack, camera, 0.0f));
        }
    }

    @Override
    public void renderFirstPerson(PoseStack poseStack, SubmitNodeCollector nodeCollector,
                                  LocalPlayer player, int packedLight, float partialTick) {
        if (activeFields.isEmpty() || submittedThisFrame) return;
        submittedThisFrame = true;

        var camera = Minecraft.getInstance().gameRenderer.mainCamera();
        var worldPoseStack = new PoseStack();

        for (var field : activeFields) {
            nodeCollector.submitCustomGeometry(worldPoseStack, POS_COLOR_QUADS_BLOOM,
                    (pose, vc) -> field.render(worldPoseStack, camera, partialTick));
        }
    }
}
