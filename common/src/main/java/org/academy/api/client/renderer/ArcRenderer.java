package org.academy.api.client.renderer;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.academy.AcademyCraft;
import org.academy.api.common.util.MathUtil;

import java.util.Random;

import static net.minecraft.client.renderer.RenderStateShard.ITEM_ENTITY_TARGET;
import static org.academy.AcademyCraft.getResourceLocation;
import static org.academy.api.client.util.RenderStateUtil.*;

public final class ArcRenderer {
    public static final ResourceLocation ARC_TEXTURE = getResourceLocation(
            "textures/ability/electromaster/skill/arc_generate/effect/line_segment.png");
    public static final RenderType ARC_RENDER_TYPE = new RenderType.CompositeRenderType(
            "arc_render_type",
            DefaultVertexFormat.POSITION_TEX,
            VertexFormat.Mode.QUADS,
            256,
            false,
            true,
            RenderType.CompositeState.builder()
                    .setTextureState(new RenderStateShard.TextureStateShard(
                            ARC_TEXTURE,
                            false,
                            false
                    ))
                    .setShaderState(POSITION_TEX_SHADER)
                    .setCullState(NO_CULL)
                    .setOutputState(ITEM_ENTITY_TARGET)
                    .setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .createCompositeState(false)
    );

    private static final float THICKNESS_VARIATION = 0.4f;
    private static final float MIN_THICKNESS_FACTOR = 0.1f;
    private static final double EPSILON = 1e-6;

    public static void renderArc(PoseStack ps, MultiBufferSource mbs, long seed,
                                 float sx, float sy, float sz, float ex, float ey, float ez,
                                 float thickness, int segments) {
        var rnd = new Random(seed);
        renderArcRecursive(ps, mbs, rnd, sx, sy, sz, ex, ey, ez, thickness, segments, 0.15f, 0);
    }

    private static void renderArcRecursive(PoseStack ps, MultiBufferSource mbs, Random rnd,
                                           float startX, float startY, float startZ,
                                           float endX, float endY, float endZ,
                                           float thickness, int segments,
                                           float branchChance, int depth) {
        var distSq = (endX - startX) * (endX - startX) + (endY - startY) * (endY - startY) + (endZ - startZ) * (endZ - startZ);
        if (depth > 1 || distSq < EPSILON * EPSILON) {
            return;
        }

        var vc = mbs.getBuffer(ARC_RENDER_TYPE);
        var matrix = ps.last().pose();

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
            var displacementMagnitude = baseHalfThickness * 5.0f * falloff * (rnd.nextFloat() * 0.8f + 0.2f);
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

            vc.vertex(matrix, prevLX, prevLY, prevLZ).uv(u0, 0).endVertex();
            vc.vertex(matrix, prevRX, prevRY, prevRZ).uv(u0, 1).endVertex();
            vc.vertex(matrix, currentRX, currentRY, currentRZ).uv(u1, 1).endVertex();
            vc.vertex(matrix, currentLX, currentLY, currentLZ).uv(u1, 0).endVertex();

            if (depth == 0 && i > 1 && i < segments - 1 && rnd.nextFloat() < branchChance) {
                var branchLength = (float) Math.sqrt((endX - currentPosX) * (endX - currentPosX) + (endY - currentPosY) * (endY - currentPosY) + (endZ - currentPosZ) * (endZ - currentPosZ)) * (0.3f + rnd.nextFloat() * 0.4f);
                var angleOffset = (rnd.nextFloat() - 0.5f) * Math.PI * 1.2f;

                var cosY = (float) Math.cos(angleOffset);
                var sinY = (float) Math.sin(angleOffset);
                var branchDirX = segDirX * cosY - segDirZ * sinY;
                var branchDirZ = segDirX * sinY + segDirZ * cosY;

                var branchEndX = currentPosX + branchDirX * branchLength;
                var branchEndY = currentPosY + segDirY * branchLength;
                var branchEndZ = currentPosZ + branchDirZ * branchLength;

                renderArcRecursive(ps, mbs, rnd, currentPosX, currentPosY, currentPosZ, branchEndX, branchEndY, branchEndZ, thickness * 0.6f, (int) (segments * 0.6f), 0, depth + 1);
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