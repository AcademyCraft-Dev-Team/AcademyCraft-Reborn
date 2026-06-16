package org.academy.internal.client.renderer.effect;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.academy.api.client.renderer.EffectRenderer;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.Deque;

import static org.academy.api.client.Render.RenderTypes.POS_COLOR_QUADS_BLOOM;

public final class AfterimageEffectWrapper implements EffectRenderer {
    public static final AfterimageEffectWrapper INSTANCE = new AfterimageEffectWrapper();

    private static final int MAX_SNAPSHOTS = 14;
    private static final float LIFETIME = 2.0f;
    private static final float CAPTURE_INTERVAL = 0.06f;
    private static final float BODY_HEIGHT = 1.8f;
    private static final float BODY_WIDTH = 0.3f;
    private static final float HEAD_SIZE = 0.25f;
    private static final float LIMB_WIDTH = 0.15f;
    private static final float ARM_LENGTH = 0.55f;
    private static final float LEG_LENGTH = 0.65f;

    private final Deque<Snapshot> snapshots = new ArrayDeque<>();
    private float captureAccumulator;
    private boolean active;
    private float deactivateTimer;

    private AfterimageEffectWrapper() {}

    public void setActive(boolean active) {
        this.active = active;
        if (!active) {
            deactivateTimer = LIFETIME;
        }
    }

    public void captureAt(float x, float y, float z) {
        snapshots.addFirst(new Snapshot(x, y, z, 0, 0, 0, 0, false, 1.0f));
        while (snapshots.size() > MAX_SNAPSHOTS) {
            snapshots.removeLast();
        }
    }

    public void captureAt(float x, float y, float z, AvatarRenderState renderState, float yRot, float xRot) {
        snapshots.addFirst(new Snapshot(
                x, y, z, yRot, xRot,
                renderState.walkAnimationPos,
                renderState.walkAnimationSpeed,
                renderState.isCrouching,
                renderState.isBaby ? 0.5f : 1.0f
        ));
        while (snapshots.size() > MAX_SNAPSHOTS) {
            snapshots.removeLast();
        }
    }

    @Override
    public void render(PoseStack poseStack, SubmitNodeCollector submitNodeCollector,
                       int packedLight, AvatarRenderState renderState, float yRot, float xRot) {
        var deltaTime = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaTicks();

        for (var it = snapshots.iterator(); it.hasNext(); ) {
            var s = it.next();
            s.age += deltaTime;
            if (s.age > LIFETIME) {
                it.remove();
            }
        }

        if (active) {
            captureAccumulator += deltaTime;
            if (captureAccumulator >= CAPTURE_INTERVAL) {
                captureAccumulator -= CAPTURE_INTERVAL;
                captureAt(
                        (float) renderState.x, (float) renderState.y, (float) renderState.z,
                        renderState, yRot, xRot
                );
            }
        } else if (deactivateTimer > 0) {
            deactivateTimer -= deltaTime;
        }

        if (snapshots.isEmpty()) return;

        var camera = Minecraft.getInstance().gameRenderer.mainCamera();
        submitNodeCollector.submitCustomGeometry(poseStack, POS_COLOR_QUADS_BLOOM,
                (pose, vc) -> renderSnapshots(poseStack, vc, camera));
    }

    private void renderSnapshots(PoseStack poseStack, VertexConsumer vc, Camera camera) {
        var camPos = camera.position();
        var count = snapshots.size();

        var i = 0;
        for (var s : snapshots) {
            var life = s.age / LIFETIME;
            var alpha = easeOutCubic(1.0f - life) * 0.55f;
            if (alpha <= 0.01f) continue;

            // Color gradient: newest cyan -> mid blue -> oldest purple
            var r = lerp(0.2f, 0.6f, life) + 0.1f * (1 - life);
            var g = lerp(0.5f, 0.2f, life);
            var b = lerp(1.0f, 0.7f, life);
            var a = alpha;

            drawSilhouette(poseStack.last().pose(), vc, s, camPos, r, g, b, a, count - i - 1);
            i++;
        }
    }

    private void drawSilhouette(Matrix4f mat, VertexConsumer vc, Snapshot s,
                                 net.minecraft.world.phys.Vec3 camPos,
                                 float r, float g, float b, float a, int index) {
        var dx = (float) (s.x - camPos.x);
        var dy = (float) (s.y - camPos.y);
        var dz = (float) (s.z - camPos.z);
        var scale = s.bodyScale;
        var eyeY = dy + 1.52f * scale;

        // Limb animation
        var walkDist = s.limbSwing * s.limbSpeed * 0.6f;
        var leftArmSwing = (float) Math.sin(walkDist * Math.PI) * 0.5f;
        var rightArmSwing = (float) Math.sin(walkDist * Math.PI + Math.PI) * 0.5f;
        var leftLegSwing = (float) Math.sin(walkDist * Math.PI + Math.PI) * 0.5f;
        var rightLegSwing = (float) Math.sin(walkDist * Math.PI) * 0.5f;

        if (s.crouching) {
            eyeY -= 0.2f * scale;
            leftLegSwing *= 0.3f;
            rightLegSwing *= 0.3f;
        }

        // Head (billboarded box)
        var headCY = eyeY + 0.08f * scale;
        var headHw = HEAD_SIZE * scale;
        var headHh = 0.2f * scale;
        drawBillboardBox(mat, vc, dx, headCY - headHh, dz, dx, headCY + headHh, dz,
                headHw, r, g, b, a);

        // Torso
        var torsoTop = headCY - headHh;
        var torsoBot = dy + 0.7f * scale;
        var torsoHw = BODY_WIDTH * scale;
        drawBillboardBox(mat, vc, dx, torsoBot, dz, dx, torsoTop, dz,
                torsoHw, r * 0.9f, g * 0.9f, b * 0.9f, a * 0.85f);

        // Left arm
        var shoulderY = torsoTop - 0.05f * scale;
        var shoulderX = -torsoHw - LIMB_WIDTH * 0.5f * scale;
        var elbowY = shoulderY - ARM_LENGTH * 0.5f * scale;
        var handY = shoulderY - ARM_LENGTH * scale;
        var midArmX = shoulderX + (float) Math.sin(leftArmSwing) * 0.15f * scale;
        var armEndX = shoulderX + (float) Math.sin(leftArmSwing * 1.4f) * 0.2f * scale;
        drawLimb(mat, vc, dx + shoulderX, shoulderY, dz,
                dx + midArmX, elbowY, dz,
                dx + armEndX, handY, dz,
                LIMB_WIDTH * scale, r, g, b, a * 0.8f);

        // Right arm
        shoulderX = torsoHw + LIMB_WIDTH * 0.5f * scale;
        midArmX = shoulderX + (float) Math.sin(rightArmSwing) * 0.15f * scale;
        armEndX = shoulderX + (float) Math.sin(rightArmSwing * 1.4f) * 0.2f * scale;
        drawLimb(mat, vc, dx + shoulderX, shoulderY, dz,
                dx + midArmX, elbowY, dz,
                dx + armEndX, handY, dz,
                LIMB_WIDTH * scale, r, g, b, a * 0.8f);

        // Left leg
        var hipY = torsoBot;
        var hipX = -torsoHw * 0.5f;
        var kneeY = hipY - LEG_LENGTH * 0.5f * scale;
        var footY = dy + 0.05f * scale;
        var midLegX = hipX + (float) Math.sin(leftLegSwing) * 0.12f * scale;
        var legEndX = hipX + (float) Math.sin(leftLegSwing * 1.4f) * 0.18f * scale;
        drawLimb(mat, vc, dx + hipX, hipY, dz,
                dx + midLegX, kneeY, dz,
                dx + legEndX, footY, dz,
                LIMB_WIDTH * 1.1f * scale, r, g, b, a * 0.75f);

        // Right leg
        hipX = torsoHw * 0.5f;
        midLegX = hipX + (float) Math.sin(rightLegSwing) * 0.12f * scale;
        legEndX = hipX + (float) Math.sin(rightLegSwing * 1.4f) * 0.18f * scale;
        drawLimb(mat, vc, dx + hipX, hipY, dz,
                dx + midLegX, kneeY, dz,
                dx + legEndX, footY, dz,
                LIMB_WIDTH * 1.1f * scale, r, g, b, a * 0.75f);
    }

    private void drawBillboardBox(Matrix4f mat, VertexConsumer vc,
                                   float x0, float y0, float z0,
                                   float x1, float y1, float z1,
                                   float hw, float r, float g, float b, float a) {
        var cx = (x0 + x1) * 0.5f;
        var cz = (z0 + z1) * 0.5f;
        // Billboard: render 4 quads forming a box always facing camera-relative axes
        renderQuadX(mat, vc, cx - hw, y0, cz, cx + hw, y1, cz, r, g, b, a);
        renderQuadX(mat, vc, cx + hw, y0, cz, cx - hw, y1, cz, r, g, b, a);
        renderQuadZ(mat, vc, cx, y0, cz - hw, cx, y1, cz + hw, r, g, b, a);
        renderQuadZ(mat, vc, cx, y0, cz + hw, cx, y1, cz - hw, r, g, b, a);
    }

    private void drawLimb(Matrix4f mat, VertexConsumer vc,
                           float sx, float sy, float sz,
                           float mx, float my, float mz,
                           float ex, float ey, float ez,
                           float width, float r, float g, float b, float a) {
        // Shoulder to elbow segment
        drawBillboardSegment(mat, vc, sx, sy, sz, mx, my, mz, width, r, g, b, a);
        // Elbow to hand segment
        drawBillboardSegment(mat, vc, mx, my, mz, ex, ey, ez, width * 0.85f, r, g, b, a);
    }

    private void drawBillboardSegment(Matrix4f mat, VertexConsumer vc,
                                       float x0, float y0, float z0,
                                       float x1, float y1, float z1,
                                       float width, float r, float g, float b, float a) {
        var lenX = x1 - x0;
        var lenY = y1 - y0;
        var lenZ = z1 - z0;
        var segLen = (float) Math.sqrt(lenX * lenX + lenY * lenY + lenZ * lenZ);
        if (segLen < 0.001f) return;

        // Compute perpendicular directions for billboarding
        var nx = lenX / segLen;
        var nz = lenZ / segLen;
        // Cross with Y axis to get horizontal perpendicular
        var perpX = -nz * width;
        var perpZ = nx * width;

        vc.addVertex(mat, x0 - perpX, y0, z0 - perpZ).setColor(r, g, b, a);
        vc.addVertex(mat, x0 + perpX, y0, z0 + perpZ).setColor(r, g, b, a);
        vc.addVertex(mat, x1 + perpX, y1, z1 + perpZ).setColor(r, g, b, a * 0.7f);
        vc.addVertex(mat, x1 - perpX, y1, z1 - perpZ).setColor(r, g, b, a * 0.7f);

        vc.addVertex(mat, x0 + perpX, y0, z0 + perpZ).setColor(r, g, b, a);
        vc.addVertex(mat, x0 - perpX, y0, z0 - perpZ).setColor(r, g, b, a);
        vc.addVertex(mat, x1 - perpX, y1, z1 - perpZ).setColor(r, g, b, a * 0.7f);
        vc.addVertex(mat, x1 + perpX, y1, z1 + perpZ).setColor(r, g, b, a * 0.7f);
    }

    private void renderQuadX(Matrix4f mat, VertexConsumer vc,
                              float x0, float y0, float z0,
                              float x1, float y1, float z1,
                              float r, float g, float b, float a) {
        vc.addVertex(mat, x0, y0, z0).setColor(r, g, b, a);
        vc.addVertex(mat, x0, y1, z0).setColor(r, g, b, a);
        vc.addVertex(mat, x1, y1, z1).setColor(r, g, b, a);
        vc.addVertex(mat, x1, y0, z1).setColor(r, g, b, a);
    }

    private void renderQuadZ(Matrix4f mat, VertexConsumer vc,
                              float x0, float y0, float z0,
                              float x1, float y1, float z1,
                              float r, float g, float b, float a) {
        vc.addVertex(mat, x0, y0, z0).setColor(r, g, b, a);
        vc.addVertex(mat, x1, y0, z1).setColor(r, g, b, a);
        vc.addVertex(mat, x1, y1, z1).setColor(r, g, b, a);
        vc.addVertex(mat, x0, y1, z0).setColor(r, g, b, a);
    }

    public boolean shouldCapture() {
        if (!active) return false;
        if (captureAccumulator >= CAPTURE_INTERVAL) {
            captureAccumulator -= CAPTURE_INTERVAL;
            return true;
        }
        return false;
    }

    private static float easeOutCubic(float t) {
        return 1.0f - (1.0f - t) * (1.0f - t) * (1.0f - t);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    @Override
    public void renderFirstPerson(PoseStack poseStack, SubmitNodeCollector nodeCollector,
                                   LocalPlayer player, int packedLight, float partialTick) {
        // Afterimages are not visible in first person
    }

    private static class Snapshot {
        final float x, y, z;
        final float yRot, xRot;
        final float limbSwing;
        final float limbSpeed;
        final boolean crouching;
        final float bodyScale;
        float age;

        Snapshot(float x, float y, float z, float yRot, float xRot,
                 float limbSwing, float limbSpeed, boolean crouching, float bodyScale) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.yRot = yRot;
            this.xRot = xRot;
            this.limbSwing = limbSwing;
            this.limbSpeed = limbSpeed;
            this.crouching = crouching;
            this.bodyScale = bodyScale;
        }
    }
}
