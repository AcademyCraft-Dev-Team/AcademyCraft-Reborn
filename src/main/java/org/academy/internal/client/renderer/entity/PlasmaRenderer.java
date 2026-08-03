package org.academy.internal.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import org.academy.api.client.compatibility.IrisCompat;
import org.academy.api.client.render.Render;
import org.academy.api.client.renderer.BallRenderer;
import org.academy.api.client.util.VertexUtil;
import org.academy.api.common.util.ImprovedNoise;
import org.academy.internal.client.renderer.entity.state.PlasmaRenderState;
import org.academy.internal.common.world.entity.skill.Plasma;
import org.joml.Quaternionf;

import static org.academy.api.client.render.Render.RenderTypes.PLASMA_CLOUD;
import static org.academy.internal.client.renderer.effect.StormWingEffectRenderer.renderRing;

public class PlasmaRenderer extends EntityRenderer<Plasma, PlasmaRenderState> {
    private static final int LAYER_COUNT = 28;
    private static final int LAYER_SEGMENTS = 12;
    private static final float[][][] LAYER_VERTICES =
            VertexUtil.Ring.getVerticalVertexBuffer(1.0f, 1.0f, LAYER_SEGMENTS);
    private static final float[][] CORE_VERTICES =
            VertexUtil.Ball.getIcosphereVertexBuffer(1.0f, 2, true);

    public PlasmaRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void submit(PlasmaRenderState state, PoseStack poseStack,
                       SubmitNodeCollector collector, CameraRenderState cameraRenderState) {
        if (IrisCompat.isShadowRendererActive()) return;
        var progress = state.launched ? 1.0f : state.gatherProgress;
        if (progress <= 0.0f) return;
        var easedProgress = (float) Mth.smoothstep(Mth.clamp(progress, 0.0f, 1.0f));
        submitAtmosphericLayers(poseStack, collector, state.ageInTicks, easedProgress);
        submitCore(poseStack, collector, state.ageInTicks, easedProgress);
    }

    private static void submitAtmosphericLayers(PoseStack poseStack, SubmitNodeCollector collector,
                                                float time, float progress) {
        collector.submitCustomGeometry(poseStack, PLASMA_CLOUD, (pose, consumer) -> {
            var cloudPose = new PoseStack();
            cloudPose.last().set(pose);

            for (var i = 0; i < LAYER_COUNT; i++) {
                var normalized = i / (float) (LAYER_COUNT - 1);
                var centered = normalized * 2.0f - 1.0f;
                var timeValue = time * 0.035f;
                var displacementX = (float) ImprovedNoise.noise(i * 0.31, timeValue, 10.0) * 1.7f * progress;
                var displacementZ = (float) ImprovedNoise.noise(i * 0.31, timeValue, 20.0) * 1.7f * progress;
                var verticalJitter = (float) ImprovedNoise.noise(i * 0.67, timeValue, 30.0) * 0.28f;
                var radiusNoise = (float) ImprovedNoise.noise(i * 0.47, timeValue, 40.0) * 0.22f;
                var widthNoise = (float) ImprovedNoise.noise(i * 0.73, timeValue, 50.0) * 0.28f;
                var profile = 0.32f + Mth.square(Math.abs(centered)) * 0.68f;
                var radius = (4.5f + 15.5f * profile) * (0.25f + progress * 0.75f);
                radius *= 1.0f + radiusNoise;
                var width = Math.max(0.22f, (0.75f + profile * 1.65f) * (1.0f + widthNoise));
                var y = 9.0f - normalized * 20.0f + verticalJitter;
                var rotation = time * (0.012f + normalized * 0.006f) + i * 0.71f;
                var tiltX = (float) ImprovedNoise.noise(i * 0.41, timeValue, 60.0) * 0.09f;
                var tiltZ = (float) ImprovedNoise.noise(i * 0.41, timeValue, 70.0) * 0.09f;

                cloudPose.pushPose();
                cloudPose.translate(displacementX, y, displacementZ);
                cloudPose.mulPose(new Quaternionf().rotateY(rotation).rotateX(tiltX).rotateZ(tiltZ));
                cloudPose.scale(radius, width, radius);
                renderRing(
                        cloudPose.last().pose(), consumer, LAYER_SEGMENTS, LAYER_VERTICES,
                        1.0f, 0.82f, 1.0f, (0.20f + progress * 0.42f) * (0.75f + profile * 0.25f)
                );
                cloudPose.popPose();
            }
        });
    }

    private static void submitCore(PoseStack poseStack, SubmitNodeCollector collector,
                                   float time, float progress) {
        var pulse = 0.5f + 0.5f * Mth.sin(time * 0.34f);
        var innerRadius = (0.9f + progress * 2.7f) * (0.96f + pulse * 0.08f);
        var glowRadius = innerRadius * 1.45f;

        poseStack.pushPose();
        poseStack.scale(glowRadius, glowRadius, glowRadius);
        collector.submitCustomGeometry(
                poseStack,
                Render.RenderTypes.POS_COLOR_TRANGLES_BLOOM_ADDITIVE,
                (pose, consumer) -> BallRenderer.renderBall(
                        pose, consumer, CORE_VERTICES,
                        0.72f, 0.18f, 1.0f, 0.42f,
                        LightCoordsUtil.FULL_BRIGHT, 0
                )
        );
        poseStack.popPose();

        poseStack.pushPose();
        poseStack.scale(innerRadius, innerRadius, innerRadius);
        collector.submitCustomGeometry(
                poseStack,
                Render.RenderTypes.POS_COLOR_TRANGLES,
                (pose, consumer) -> BallRenderer.renderBall(
                        pose, consumer, CORE_VERTICES,
                        1.0f, 0.92f, 1.0f, 0.98f,
                        LightCoordsUtil.FULL_BRIGHT, 0
                )
        );
        poseStack.popPose();
    }

    @Override
    public PlasmaRenderState createRenderState() {
        return new PlasmaRenderState();
    }

    @Override
    public void extractRenderState(Plasma entity, PlasmaRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.gatherProgress = entity.getGatherProgress();
        state.launched = entity.isLaunched();
    }

    @Override
    protected boolean affectedByCulling(Plasma entity) {
        return false;
    }
}
