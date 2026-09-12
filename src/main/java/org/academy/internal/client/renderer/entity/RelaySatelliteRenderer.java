package org.academy.internal.client.renderer.entity;

import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import org.academy.AcademyCraft;
import org.academy.internal.client.renderer.RelayPlatformGeo;
import org.academy.internal.common.world.entity.misaka.RelaySatelliteEntity;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import static org.academy.api.client.render.Render.RenderTypes.POS_COLOR_QUADS_ADDITIVE;

public final class RelaySatelliteRenderer
        extends GeoEntityRenderer<RelaySatelliteEntity, EntityRenderState> {
    private static final DataTicket<Float> SPIN = DataTicket.create("academy_relay_sat_spin", Float.class);
    private static final DataTicket<Boolean> CRASHING = DataTicket.create("academy_relay_sat_crash", Boolean.class);
    private static final DataTicket<Boolean> LAUNCHING = DataTicket.create("academy_relay_sat_launch", Boolean.class);
    private static final DataTicket<Vector3f> TRAIL_VEL = DataTicket.create("academy_relay_sat_trail", Vector3f.class);

    public RelaySatelliteRenderer(EntityRendererProvider.Context context) {
        super(context, RelayPlatformGeo.entityModel(AcademyCraft.academy("relay_satellite")));
        withScale(0.85F);
        this.shadowRadius = 0.6f;
    }

    @Override
    public void addRenderData(
            RelaySatelliteEntity animatable,
            @Nullable Void relatedObject,
            EntityRenderState renderState,
            float partialTick
    ) {
        super.addRenderData(animatable, relatedObject, renderState, partialTick);
        var geo = asGeo(renderState);
        geo.addGeckolibData(SPIN, (animatable.tickCount + partialTick) * 4.0f);
        geo.addGeckolibData(CRASHING, animatable.isCrashing());
        geo.addGeckolibData(LAUNCHING, animatable.isLaunching());
        var motion = animatable.getDeltaMovement();
        var trail = new Vector3f();
        if (animatable.isLaunching() && motion.lengthSqr() < 1.0e-6) {
            trail.set(0.0f, 0.2f, 0.0f);
        } else {
            trail.set((float) motion.x, (float) motion.y, (float) motion.z);
        }
        geo.addGeckolibData(TRAIL_VEL, trail);
    }

    @Override
    public void submit(
            EntityRenderState state,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            CameraRenderState camera
    ) {
        var geo = asGeo(state);
        boolean crashing = Boolean.TRUE.equals(geo.getOrDefaultGeckolibData(CRASHING, false));
        boolean launching = Boolean.TRUE.equals(geo.getOrDefaultGeckolibData(LAUNCHING, false));
        float spin = geo.getOrDefaultGeckolibData(SPIN, 0.0f);
        Vector3f trail = geo.getOrDefaultGeckolibData(TRAIL_VEL, new Vector3f());

        if (crashing) {
            submitTrail(poseStack, collector, trail, 1.0f, 0.45f, 0.08f, 1.0f, 0.75f, 0.2f, 1.0f, 0.95f, 0.55f);
        } else if (launching) {
            submitTrail(poseStack, collector, trail, 0.55f, 0.85f, 1.0f, 0.75f, 0.95f, 1.0f, 1.0f, 1.0f, 0.85f);
        }
        poseStack.pushPose();
        poseStack.translate(0.0, 0.15, 0.0);
        if (launching) {
            // Geo sat is Y-up (same as on the pad). Keep upright during ascent — do not tip +Z into velocity.
            poseStack.mulPose(Axis.YP.rotationDegrees(spin));
        } else if (crashing) {
            // Model +Y is the antenna/"head". Point it along dive velocity (head-down).
            alignHeadAlongVelocity(poseStack, trail);
            poseStack.mulPose(Axis.YP.rotationDegrees(spin * 2.5f));
        } else {
            poseStack.mulPose(Axis.YP.rotationDegrees(spin));
        }
        super.submit(state, poseStack, collector, camera);
        poseStack.popPose();
    }

    /** Maps local +Y (head) onto the velocity direction. */
    private static void alignHeadAlongVelocity(PoseStack poseStack, Vector3f velocity) {
        var speedSqr = velocity.lengthSquared();
        var target = new Vector3f();
        if (speedSqr < 1.0e-6f) {
            target.set(0.0f, -1.0f, 0.0f);
        } else {
            target.set(velocity).normalize();
        }
        var rotation = new Quaternionf().rotationTo(new Vector3f(0.0f, 1.0f, 0.0f), target);
        poseStack.mulPose(rotation);
    }

    private static void submitTrail(
            PoseStack poseStack,
            SubmitNodeCollector collector,
            Vector3f velocity,
            float r0, float g0, float b0,
            float r1, float g1, float b1,
            float r2, float g2, float b2
    ) {
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

    private static GeoRenderState asGeo(EntityRenderState state) {
        return (GeoRenderState) (Object) state;
    }
}
