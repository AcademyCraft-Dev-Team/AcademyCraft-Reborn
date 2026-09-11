package org.academy.internal.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.academy.api.client.renderer.OrientedCylinderBeam;
import org.academy.api.common.util.MathUtil;
import org.academy.internal.client.renderer.entity.state.OrbitalStrikeProxyRenderState;
import org.academy.internal.common.world.entity.misaka.OrbitalStrikeProxyEntity;
import org.academy.internal.common.world.item.Items;

import static org.academy.api.client.render.Render.RenderTypes.POS_COLOR_QUADS_ADDITIVE;
import static org.academy.api.client.render.Render.RenderTypes.POS_COLOR_QUADS_NO_DEPTH_WRITE;

public final class OrbitalStrikeProxyRenderer
        extends EntityRenderer<OrbitalStrikeProxyEntity, OrbitalStrikeProxyRenderState> {
    private final ItemModelResolver itemModelResolver;

    public OrbitalStrikeProxyRenderer(EntityRendererProvider.Context context) {
        super(context);
        itemModelResolver = context.getItemModelResolver();
    }

    @Override
    public OrbitalStrikeProxyRenderState createRenderState() {
        return new OrbitalStrikeProxyRenderState();
    }

    @Override
    public void extractRenderState(
            OrbitalStrikeProxyEntity entity,
            OrbitalStrikeProxyRenderState state,
            float partialTick
    ) {
        super.extractRenderState(entity, state, partialTick);
        state.firing = entity.isFiring();
        state.hyper = entity.isHyper();
        state.impact = entity.getImpact();
        state.spin = (entity.tickCount + partialTick) * 4.0f;
        var stack = new ItemStack(state.hyper
                ? Items.HYPER_NETWORK_RELAY_SATELLITE.get()
                : Items.NETWORK_RELAY_SATELLITE.get());
        state.extractItemGroupRenderState(entity, stack, itemModelResolver);
        var impact = state.impact;
        state.beamEndRelative.set(
                (float) (impact.getX() + 0.5 - entity.getX()),
                (float) (impact.getY() + 0.1 - entity.getY()),
                (float) (impact.getZ() + 0.5 - entity.getZ())
        );
    }

    @Override
    public void submit(
            OrbitalStrikeProxyRenderState state,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            CameraRenderState camera
    ) {
        if (state.firing) {
            submitBeam(state, poseStack, collector);
        }
        poseStack.pushPose();
        poseStack.translate(0.0, 0.2, 0.0);
        poseStack.mulPose(Axis.YP.rotationDegrees(state.spin));
        float scale = state.firing ? 1.35f : 1.1f;
        poseStack.scale(scale, scale, scale);
        ItemEntityRenderer.submitMultipleFromCount(
                poseStack, collector, state.lightCoords, state, MathUtil.RANDOM_SOURCE
        );
        poseStack.popPose();
    }

    private static void submitBeam(
            OrbitalStrikeProxyRenderState state,
            PoseStack poseStack,
            SubmitNodeCollector collector
    ) {
        var start = Vec3.ZERO;
        var end = new Vec3(state.beamEndRelative.x, state.beamEndRelative.y, state.beamEndRelative.z);
        var delta = end.subtract(start);
        double length = delta.length();
        if (!(length > 0.5) || !Double.isFinite(length)) {
            return;
        }
        // Full length to impact — do not apply uplink MAX_BEAM_TRACK_RANGE.
        if (!OrientedCylinderBeam.preparePose(poseStack, start, delta, (float) length)) {
            return;
        }
        OrientedCylinderBeam.submitLayer(
                collector, poseStack, (float) length, 0.55f,
                POS_COLOR_QUADS_NO_DEPTH_WRITE, 1.0f, 0.35f, 0.1f, 0.65f
        );
        OrientedCylinderBeam.submitLayer(
                collector, poseStack, (float) length, 0.22f,
                POS_COLOR_QUADS_ADDITIVE, 1.0f, 0.75f, 0.25f, 0.95f
        );
        poseStack.popPose();
    }
}
