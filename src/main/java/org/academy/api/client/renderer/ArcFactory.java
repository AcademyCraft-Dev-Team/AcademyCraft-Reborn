package org.academy.api.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import org.academy.api.client.render.post.PostEffect;
import org.academy.api.common.util.MathUtil;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.academy.api.client.Render.RenderTypes;
import static org.academy.api.common.util.MathUtil.EPSILON;

public final class ArcFactory {
    private static final float THICKNESS_VARIATION = 0.4f;
    private static final float MIN_THICKNESS_FACTOR = 0.1f;

    static {
        PostEffect.addFixedBuffer(RenderTypes.ARC);
    }

    public static void render(PoseStack ps, SubmitNodeCollector submitNodeCollector, ArcRenderData data) {
        render(ps, submitNodeCollector, data, 1, 1, 1, 1);
    }

    public static void render(PoseStack ps, SubmitNodeCollector submitNodeCollector, ArcRenderData data, float r, float g, float b, float a) {
        submitNodeCollector.submitCustomGeometry(ps, RenderTypes.ARC, (pose, vc) -> renderRecursive(pose, vc, data, r, g, b, a));
    }

    private static void renderRecursive(PoseStack.Pose pose, VertexConsumer vc, ArcRenderData data, float r, float g, float b, float a) {
        for (var quad : data.quads) {
            vc.addVertex(pose, quad.v1.pos.x(), quad.v1.pos.y(), quad.v1.pos.z()).setUv(quad.v1.u, quad.v1.v).setColor(r, g, b, a);
            vc.addVertex(pose, quad.v2.pos.x(), quad.v2.pos.y(), quad.v2.pos.z()).setUv(quad.v2.u, quad.v2.v).setColor(r, g, b, a);
            vc.addVertex(pose, quad.v3.pos.x(), quad.v3.pos.y(), quad.v3.pos.z()).setUv(quad.v3.u, quad.v3.v).setColor(r, g, b, a);
            vc.addVertex(pose, quad.v4.pos.x(), quad.v4.pos.y(), quad.v4.pos.z()).setUv(quad.v4.u, quad.v4.v).setColor(r, g, b, a);
        }

        for (var branch : data.branches) {
            renderRecursive(pose, vc, branch, r, g, b, a);
        }
    }

    public static class Vertex {
        public final Vector3f pos;
        public final float u, v;

        Vertex(Vector3f pos, float u, float v) {
            this.pos = pos;
            this.u = u;
            this.v = v;
        }
    }

    public static class Quad {
        public Vertex v1, v2, v3, v4;
    }

    public static class ArcRenderData {
        public final List<Quad> quads = new ArrayList<>();
        public final List<ArcRenderData> branches = new ArrayList<>();
    }

    public static class Generator {
        public static ArcRenderData generate(ArcStyle style) {
            var data = new ArcRenderData();
            var rnd = new Random(style.seed);
            generateRecursive(data, rnd, style, 0);
            return data;
        }

        private static void generateRecursive(ArcRenderData parentData, Random rnd, ArcStyle style, int depth) {
            float startX = style.start.x, startY = style.start.y, startZ = style.start.z;
            float endX = style.end.x, endY = style.end.y, endZ = style.end.z;
            var thickness = style.thickness;
            var segments = style.segments;

            var distSq = style.start.distanceSquared(style.end);
            if (depth >= style.maxBranchDepth || distSq < EPSILON * EPSILON) {
                return;
            }

            var deltaX = endX - startX;
            var deltaY = endY - startY;
            var deltaZ = endZ - startZ;

            var prevPosX = startX;
            var prevPosY = startY;
            var prevPosZ = startZ;
            float prevLX = 0, prevLY = 0, prevLZ = 0, prevRX = 0, prevRY = 0, prevRZ = 0;
            var baseHalfThickness = thickness * 0.5f;

            for (var i = 1; i <= segments; ++i) {
                var t = (float) i / segments;
                var currentMidpointX = startX + deltaX * t;
                var currentMidpointY = startY + deltaY * t;
                var currentMidpointZ = startZ + deltaZ * t;

                float displacementDirX, displacementDirY, displacementDirZ;
                var deltaLenSq = deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ;
                if (deltaLenSq > EPSILON) {
                    var invDeltaLen = 1.0f / (float) Math.sqrt(deltaLenSq);
                    var dirX = deltaX * invDeltaLen;
                    var dirY = deltaY * invDeltaLen;
                    var dirZ = deltaZ * invDeltaLen;

                    var upX = 0.0f;
                    var upY = 1.0f;
                    if (Math.abs(dirY) > 1.0f - EPSILON) {
                        upX = 1.0f;
                        upY = 0.0f;
                    }

                    var sideX = dirY * 0 - dirZ * upY;
                    var sideY = dirZ * upX - dirX * 0;
                    var sideZ = dirX * upY - dirY * upX;
                    var invSideLen = 1.0f / (float) Math.sqrt(sideX * sideX + sideY * sideY + sideZ * sideZ);
                    sideX *= invSideLen;
                    sideY *= invSideLen;
                    sideZ *= invSideLen;

                    var renderUpX = sideY * dirZ - sideZ * dirY;
                    var renderUpY = sideZ * dirX - sideX * dirZ;
                    var renderUpZ = sideX * dirY - sideY * dirX;

                    var angle = rnd.nextFloat() * MathUtil.TWO_PI;
                    var cosAngle = (float) Math.cos(angle);
                    var sinAngle = (float) Math.sin(angle);
                    displacementDirX = sideX * cosAngle + renderUpX * sinAngle;
                    displacementDirY = sideY * cosAngle + renderUpY * sinAngle;
                    displacementDirZ = sideZ * cosAngle + renderUpZ * sinAngle;
                } else {
                    displacementDirX = rnd.nextFloat() - 0.5f;
                    displacementDirY = rnd.nextFloat() - 0.5f;
                    displacementDirZ = rnd.nextFloat() - 0.5f;
                }

                var falloff = 1.0f - (float) Math.pow(2.0 * t - 1.0, 2);
                var displacementMagnitude = baseHalfThickness * style.displacement * falloff * (rnd.nextFloat() * 0.8f + 0.2f);
                var currentPosX = currentMidpointX + displacementDirX * displacementMagnitude;
                var currentPosY = currentMidpointY + displacementDirY * displacementMagnitude;
                var currentPosZ = currentMidpointZ + displacementDirZ * displacementMagnitude;

                var segDeltaX = currentPosX - prevPosX;
                var segDeltaY = currentPosY - prevPosY;
                var segDeltaZ = currentPosZ - prevPosZ;

                var invSegLen = 1.0f / (float) Math.sqrt(segDeltaX * segDeltaX + segDeltaY * segDeltaY + segDeltaZ * segDeltaZ);
                var segDirX = segDeltaX * invSegLen;
                var segDirY = segDeltaY * invSegLen;
                var segDirZ = segDeltaZ * invSegLen;

                var segUpX = 0.0f;
                var segUpY = 1.0f;
                if (Math.abs(segDirY) > 1.0f - EPSILON) {
                    segUpX = 1.0f;
                    segUpY = 0.0f;
                }

                var segSideX = segDirY * 0 - segDirZ * segUpY;
                var segSideY = segDirZ * segUpX - segDirX * 0;
                var segSideZ = segDirX * segUpY - segDirY * segUpX;
                var invSegSideLen = 1.0f / (float) Math.sqrt(segSideX * segSideX + segSideY * segSideY + segSideZ * segSideZ);
                segSideX *= invSegSideLen;
                segSideY *= invSegSideLen;
                segSideZ *= invSegSideLen;

                var currentHalfThickness = baseHalfThickness * (1.0f + THICKNESS_VARIATION * (rnd.nextFloat() * 2.0f - 1.0f));
                currentHalfThickness = Math.max(baseHalfThickness * MIN_THICKNESS_FACTOR, currentHalfThickness);

                var currentLX = currentPosX - segSideX * currentHalfThickness;
                var currentLY = currentPosY - segSideY * currentHalfThickness;
                var currentLZ = currentPosZ - segSideZ * currentHalfThickness;
                var currentRX = currentPosX + segSideX * currentHalfThickness;
                var currentRY = currentPosY + segSideY * currentHalfThickness;
                var currentRZ = currentPosZ + segSideZ * currentHalfThickness;

                if (i == 1) {
                    prevLX = prevPosX - segSideX * currentHalfThickness;
                    prevLY = prevPosY - segSideY * currentHalfThickness;
                    prevLZ = prevPosZ - segSideZ * currentHalfThickness;
                    prevRX = prevPosX + segSideX * currentHalfThickness;
                    prevRY = prevPosY + segSideY * currentHalfThickness;
                    prevRZ = prevPosZ + segSideZ * currentHalfThickness;
                }

                var u0 = (float) (i - 1) / segments;
                var u1 = (float) i / segments;

                var quad = new Quad();
                quad.v1 = new Vertex(new Vector3f(prevLX, prevLY, prevLZ), u0, 0);
                quad.v2 = new Vertex(new Vector3f(prevRX, prevRY, prevRZ), u0, 1);
                quad.v3 = new Vertex(new Vector3f(currentRX, currentRY, currentRZ), u1, 1);
                quad.v4 = new Vertex(new Vector3f(currentLX, currentLY, currentLZ), u1, 0);
                parentData.quads.add(quad);

                if (depth == 0 && i > 1 && i < segments - 1 && rnd.nextFloat() < style.branchChance) {
                    var remainingDist = (float) Math.sqrt((endX - currentPosX) * (endX - currentPosX) + (endY - currentPosY) * (endY - currentPosY) + (endZ - currentPosZ) * (endZ - currentPosZ));
                    var branchLength = remainingDist * (0.3f + rnd.nextFloat() * 0.4f);
                    var angleOffset = (rnd.nextFloat() - 0.5f) * Math.PI * 1.2f;

                    var cosY = (float) Math.cos(angleOffset);
                    var sinY = (float) Math.sin(angleOffset);
                    var branchDirX = segDirX * cosY - segDirZ * sinY;
                    var branchDirZ = segDirX * sinY + segDirZ * cosY;

                    var branchEndX = currentPosX + branchDirX * branchLength;
                    var branchEndY = currentPosY + segDirY * branchLength;
                    var branchEndZ = currentPosZ + branchDirZ * branchLength;

                    var branchData = new ArcRenderData();
                    parentData.branches.add(branchData);

                    var branchStyle = new ArcStyle();
                    branchStyle.start = new Vector3f(currentPosX, currentPosY, currentPosZ);
                    branchStyle.end = new Vector3f(branchEndX, branchEndY, branchEndZ);
                    branchStyle.thickness = thickness * style.branchThicknessFactor;
                    branchStyle.segments = (int) (segments * style.branchSegmentsFactor);
                    branchStyle.seed = rnd.nextLong();
                    branchStyle.displacement = style.displacement;
                    branchStyle.branchChance = 0;
                    branchStyle.maxBranchDepth = style.maxBranchDepth;

                    generateRecursive(branchData, rnd, branchStyle, depth + 1);
                }

                prevPosX = currentPosX;
                prevPosY = currentPosY;
                prevPosZ = currentPosZ;
                prevLX = currentLX;
                prevLY = currentLY;
                prevLZ = currentLZ;
                prevRX = currentRX;
                prevRY = currentRY;
                prevRZ = currentRZ;
            }
        }
    }
}