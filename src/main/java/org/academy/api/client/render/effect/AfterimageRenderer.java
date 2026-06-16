package org.academy.api.client.render.effect;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;

import java.util.ArrayDeque;
import java.util.Deque;

public final class AfterimageRenderer {
    private final Deque<AfterimageSnapshot> snapshots = new ArrayDeque<>();
    private float captureInterval;
    private float captureAccumulator;
    private int maxSnapshots;
    private float lifetime;
    private boolean active;

    public AfterimageRenderer(float captureInterval, int maxSnapshots, float lifetime) {
        this.captureInterval = captureInterval;
        this.maxSnapshots = maxSnapshots;
        this.lifetime = lifetime;
    }

    public void setActive(boolean active) {
        this.active = active;
        if (!active) snapshots.clear();
    }

    public void update(float deltaTime) {
        for (var s : snapshots) {
            s.age += deltaTime;
        }
        while (!snapshots.isEmpty() && snapshots.getLast().age > lifetime) {
            snapshots.removeLast();
        }

        if (active) {
            captureAccumulator += deltaTime;
        }
    }

    public boolean shouldCapture() {
        if (!active) return false;
        if (captureAccumulator >= captureInterval) {
            captureAccumulator -= captureInterval;
            return true;
        }
        return false;
    }

    public void capture(AvatarRenderState renderState, float yRot, float xRot) {
        if (snapshots.size() >= maxSnapshots) {
            snapshots.removeLast();
        }
        snapshots.addFirst(new AfterimageSnapshot(renderState, yRot, xRot, 0, lifetime));
    }

    public void render(PoseStack poseStack, VertexConsumer vc, Camera camera,
                       float r, float g, float b) {
        // Afterimage rendering is handled by the caller (SkillEffectsLayer)
        // who has access to the player model renderer.
        // This class provides the snapshot data.
    }

    public Deque<AfterimageSnapshot> getSnapshots() {
        return snapshots;
    }

    public float getLifetime() {
        return lifetime;
    }

    public boolean hasSnapshots() {
        return !snapshots.isEmpty();
    }

    public static final class AfterimageSnapshot {
        public final AvatarRenderState renderState;
        public final float yRot, xRot;
        final float snapshotLifetime;
        float age;

        AfterimageSnapshot(AvatarRenderState renderState, float yRot, float xRot, float age, float lifetime) {
            this.renderState = renderState;
            this.yRot = yRot;
            this.xRot = xRot;
            this.age = age;
            snapshotLifetime = lifetime;
        }

        public float getAlpha() {
            var life = snapshotLifetime > 0 ? age / snapshotLifetime : 1.0f;
            return Math.clamp(1.0f - life, 0.0f, 1.0f);
        }
    }
}
