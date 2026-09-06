package org.academy.internal.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.academy.api.common.util.MathUtil;
import org.academy.internal.client.renderer.entity.state.RelaySatelliteRenderState;
import org.academy.internal.common.world.entity.misaka.RelaySatelliteEntity;
import org.academy.internal.common.world.item.Items;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import static org.academy.api.client.render.Render.RenderTypes.POS_COLOR_QUADS_ADDITIVE;

public final class RelaySatelliteRenderer extends EntityRenderer<RelaySatelliteEntity, RelaySatelliteRenderState> {
    private final ItemModelResolver itemModelResolver;

    public RelaySatelliteRenderer(EntityRendererProvider.Context context) {
        super(context);
        itemModelResolver = context.getItemModelResolver();
    }

    @Override
    public RelaySatelliteRenderState createRenderState() {
        return new RelaySatelliteRenderState();
    }

    @Override
    public void extractRenderState(RelaySatelliteEntity entity, RelaySatelliteRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.spin = (entity.tickCount + partialTick) * 4.0f;
        state.crashing = entity.isCrashing();
        state.launching = entity.isLaunching();
        var motion = entity.getDeltaMovement();
        if (state.launching && motion.lengthSqr() < 1.0e-6) {
            state.trailVelocity.set(0.0f, 0.2f, 0.0f);
        } else {
            state.trailVelocity.set((float) motion.x, (float) motion.y, (float) motion.z);
        }
        var stack = new ItemStack(entity.isHyper()
                ? Items.HYPER_NETWORK_RELAY_SATELLITE.get()
                : Items.NETWORK_RELAY_SATELLITE.get());
        state.extractItemGroupRenderState(entity, stack, itemModelResolver);
    }

    @Override
    public void submit(
            RelaySatelliteRenderState state,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            CameraRenderState camera
    ) {
        if (state.crashing) {
            submitTrail(state, poseStack, collector, 1.0f, 0.45f, 0.08f, 1.0f, 0.75f, 0.2f, 1.0f, 0.95f, 0.55f);
        } else if (state.launching) {
            submitTrail(state, poseStack, collector, 0.55f, 0.85f, 1.0f, 0.75f, 0.95f, 1.0f, 1.0f, 1.0f, 0.85f);
        }
        poseStack.pushPose();
        poseStack.translate(0.0, 0.25, 0.0);
        if (state.crashing || state.launching) {
            alignToTrailVelocity(poseStack, state.trailVelocity);
            poseStack.mulPose(Axis.ZP.rotationDegrees(state.spin * (state.launching ? 1.2f : 2.5f)));
            poseStack.scale(1.6f, 1.6f, 1.6f);
        } else {
            poseStack.mulPose(Axis.YP.rotationDegrees(state.spin));
            poseStack.scale(1.75f, 1.75f, 1.75f);
        }
        ItemEntityRenderer.submitMultipleFromCount(
                poseStack, collector, state.lightCoords, state, MathUtil.RANDOM_SOURCE
        );
        poseStack.popPose();
    }

    private static void alignToTrailVelocity(PoseStack poseStack, Vector3f velocity) {
        var speedSqr = velocity.lengthSquared();
        if (speedSqr < 1.0e-6f) {
            poseStack.mulPose(Axis.XP.rotationDegrees(75.0f));
            return;
        }
        var speed = Mth.sqrt(speedSqr);
        var yaw = (float) (Mth.atan2(velocity.x, velocity.z) * Mth.RAD_TO_DEG);
        var pitch = (float) (Mth.atan2(-velocity.y, Mth.sqrt(velocity.x * velocity.x + velocity.z * velocity.z))
                * Mth.RAD_TO_DEG);
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(pitch));
        var stretch = Mth.clamp(1.0f + speed * 0.35f, 1.15f, 2.8f);
        poseStack.scale(1.0f, stretch, 1.0f);
    }

    private static void submitTrail(
            RelaySatelliteRenderState state,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            float r0, float g0, float b0,
            float r1, float g1, float b1,
            float r2, float g2, float b2
    ) {
        var velocity = state.trailVelocity;
        var speedSqr = velocity.lengthSquared();
        if (speedSqr < 1.0e-6f) {
            return;
        }
        var speed = Mth.sqrt(speedSqr);
        var ux = -velocity.x / speed;
        var uy = -velocity.y / speed;
        var uz = -velocity.z / speed;
        var trailLen = Mth.clamp(2.2f + speed * 2.5f, 2.5f, 9.0f);
        var half = Mth.clamp(0.16f + speed * 0.04f, 0.16f, 0.32f);

        var side = new Vector3f(uy, -ux, 0.0f);
        if (side.lengthSquared() < 1.0e-4f) {
            side.set(0.0f, uz, -uy);
        }
        side.normalize().mul(half);
        var up = new Vector3f(ux, uy, uz).cross(side.x, side.y, side.z, new Vector3f()).normalize().mul(half);

        poseStack.pushPose();
        poseStack.translate(0.0, 0.25, 0.0);
        collector.submitCustomGeometry(poseStack, POS_COLOR_QUADS_ADDITIVE, (pose, consumer) -> {
            var matrix = pose.pose();
            renderTrailQuad(matrix, consumer, ux, uy, uz, trailLen, side, r0, g0, b0, 0.5f);
            renderTrailQuad(matrix, consumer, ux, uy, uz, trailLen, up, r1, g1, b1, 0.38f);
            renderTrailQuad(matrix, consumer, ux, uy, uz, trailLen * 0.55f, side.mul(0.55f, new Vector3f()),
                    r2, g2, b2, 0.65f);
        });
        poseStack.popPose();
    }

    private static void renderTrailQuad(
            Matrix4f matrix,
            VertexConsumer consumer,
            float ux,
            float uy,
            float uz,
            float trailLen,
            Vector3f halfWidth,
            float red,
            float green,
            float blue,
            float alpha
    ) {
        var hx = halfWidth.x;
        var hy = halfWidth.y;
        var hz = halfWidth.z;
        var tailX = ux * trailLen;
        var tailY = uy * trailLen;
        var tailZ = uz * trailLen;

        consumer.addVertex(matrix, -hx, -hy, -hz).setColor(red, green, blue, alpha);
        consumer.addVertex(matrix, hx, hy, hz).setColor(red, green, blue, alpha);
        consumer.addVertex(matrix, tailX + hx * 0.15f, tailY + hy * 0.15f, tailZ + hz * 0.15f)
                .setColor(red, green, blue, 0.0f);
        consumer.addVertex(matrix, tailX - hx * 0.15f, tailY - hy * 0.15f, tailZ - hz * 0.15f)
                .setColor(red, green, blue, 0.0f);
    }
}
