package org.academy.internal.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.api.client.renderer.CylinderRenderer;
import org.academy.api.client.util.VertexUtil;
import org.academy.internal.client.renderer.blockentity.state.EnergyLaserTowerRenderState;
import org.academy.internal.common.world.entity.misaka.RelaySatelliteEntity;
import org.academy.internal.common.world.item.Items;
import org.academy.internal.common.world.level.block.EnergyLaserTowerBlock;
import org.academy.internal.common.world.level.block.entity.EnergyLaserTowerBlockEntity;
import org.academy.internal.server.misaka.MisakaRelayOrbits;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;
import net.minecraft.util.LightCoordsUtil;

import static org.academy.api.client.render.Render.RenderTypes.POS_COLOR_QUADS_ADDITIVE;
import static org.academy.api.client.render.Render.RenderTypes.POS_COLOR_QUADS_NO_DEPTH_WRITE;

/** Skyward / satellite-aimed power beam, plus occasional sky-layer orbit item model. */
public final class EnergyLaserTowerRenderer
        implements BlockEntityRenderer<EnergyLaserTowerBlockEntity, EnergyLaserTowerRenderState> {
    public static final EnergyLaserTowerRenderer INSTANCE = new EnergyLaserTowerRenderer();
    private static final float[][] CYLINDER = VertexUtil.Cylinder.getCylinderVertexBuffer(0, 1, 0.5f, 12, true);
    private static final double DEFAULT_BEAM_HEIGHT = 64.0;

    private EnergyLaserTowerRenderer() {
    }

    @Override
    public EnergyLaserTowerRenderState createRenderState() {
        return new EnergyLaserTowerRenderState();
    }

    @Override
    public void extractRenderState(
            EnergyLaserTowerBlockEntity blockEntity,
            EnergyLaserTowerRenderState renderState,
            float partialTick,
            Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress
    ) {
        BlockEntityRenderer.super.extractRenderState(
                blockEntity, renderState, partialTick, cameraPosition, breakProgress
        );
        renderState.beamActive = false;
        renderState.showSkySatellite = false;
        renderState.skySatelliteItem.clear();
        if (!blockEntity.isMain()) {
            return;
        }
        renderState.beamActive = blockEntity.isBeamActive() && blockEntity.getEnergyStored() > 0;
        renderState.beamEndRelative = new Vec3(0.5, EnergyLaserTowerBlock.HEIGHT + DEFAULT_BEAM_HEIGHT, 0.5);

        var level = Minecraft.getInstance().level;
        var origin = Vec3.atLowerCornerOf(blockEntity.getBlockPos());
        var targetId = blockEntity.getBeamTargetEntityUuid();
        if (renderState.beamActive && targetId != null && level != null) {
            var entity = level.getEntity(targetId);
            if (entity != null && !entity.isRemoved()) {
                renderState.beamEndRelative = entity.getPosition(partialTick).subtract(origin);
            }
        }

        if (blockEntity.isOrbiting() && level != null) {
            long time = level.getGameTime();
            int seed = blockEntity.getOrbitAngleSeed();
            var slot = MisakaRelayOrbits.orbitSlotWorld(
                    blockEntity.getBlockPos(),
                    blockEntity.getOrbitVisualY(),
                    time,
                    seed
            );
            var relative = slot.subtract(origin);
            if (renderState.beamActive && targetId == null) {
                renderState.beamEndRelative = relative;
            }
            renderState.orbitHyper = blockEntity.isOrbitHyper();
            renderState.skySatelliteRelative = relative;
            renderState.skySatelliteSpin = (time + partialTick) * 4.0f;

            var player = Minecraft.getInstance().player;
            boolean near = player != null
                    && player.distanceToSqr(slot.x, player.getY(), slot.z)
                    <= MisakaRelayOrbits.SKY_VIEW_RANGE * MisakaRelayOrbits.SKY_VIEW_RANGE;
            boolean lookingUp = player != null && player.getXRot() < -25.0f;
            boolean window = MisakaRelayOrbits.isSkyModelVisible(time, seed);
            if (near && lookingUp && window) {
                renderState.showSkySatellite = true;
                var stack = new ItemStack(renderState.orbitHyper
                        ? Items.HYPER_NETWORK_RELAY_SATELLITE.get()
                        : Items.NETWORK_RELAY_SATELLITE.get());
                Minecraft.getInstance().getItemModelResolver().updateForTopItem(
                        renderState.skySatelliteItem,
                        stack,
                        ItemDisplayContext.GROUND,
                        level,
                        null,
                        seed
                );
            }
        }
    }

    @Override
    public void submit(
            EnergyLaserTowerRenderState renderState,
            PoseStack poseStack,
            SubmitNodeCollector nodeCollector,
            CameraRenderState cameraRenderState
    ) {
        if (renderState.beamActive) {
            submitBeam(renderState, poseStack, nodeCollector);
        }
        if (renderState.showSkySatellite && !renderState.skySatelliteItem.isEmpty()) {
            submitSkySatellite(renderState, poseStack, nodeCollector);
        }
    }

    private static void submitBeam(
            EnergyLaserTowerRenderState renderState,
            PoseStack poseStack,
            SubmitNodeCollector nodeCollector
    ) {
        var start = new Vec3(0.5, EnergyLaserTowerBlock.HEIGHT, 0.5);
        var end = renderState.beamEndRelative;
        var delta = end.subtract(start);
        double length = delta.length();
        if (!(length > 0.05) || !Double.isFinite(length)) {
            return;
        }
        var direction = delta.normalize();
        poseStack.pushPose();
        poseStack.translate(start.x, start.y, start.z);
        poseStack.mulPose(new Quaternionf().rotationTo(
                new Vector3f(0.0f, 1.0f, 0.0f),
                new Vector3f((float) direction.x, (float) direction.y, (float) direction.z)
        ));
        submitLayer(nodeCollector, poseStack, (float) length, 0.12f, POS_COLOR_QUADS_NO_DEPTH_WRITE, 0.35f, 0.85f, 1.0f, 0.55f);
        submitLayer(nodeCollector, poseStack, (float) length, 0.045f, POS_COLOR_QUADS_ADDITIVE, 0.85f, 0.95f, 1.0f, 0.9f);
        poseStack.popPose();
    }

    private static void submitSkySatellite(
            EnergyLaserTowerRenderState renderState,
            PoseStack poseStack,
            SubmitNodeCollector nodeCollector
    ) {
        var rel = renderState.skySatelliteRelative;
        // Shrunk sky item model: smaller than launch/crash entities, grows slightly with range.
        float scale = (float) Mth.clamp(0.35 + rel.length() * 0.0015, 0.45, 0.95);
        poseStack.pushPose();
        poseStack.translate(rel.x, rel.y, rel.z);
        poseStack.mulPose(Axis.YP.rotationDegrees(renderState.skySatelliteSpin));
        poseStack.scale(scale, scale, scale);
        renderState.skySatelliteItem.submit(
                poseStack,
                nodeCollector,
                LightCoordsUtil.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY,
                0
        );
        poseStack.popPose();
    }

    private static void submitLayer(
            SubmitNodeCollector nodeCollector,
            PoseStack poseStack,
            float length,
            float radius,
            RenderType renderType,
            float red,
            float green,
            float blue,
            float alpha
    ) {
        poseStack.pushPose();
        poseStack.scale(radius, length, radius);
        nodeCollector.submitCustomGeometry(
                poseStack,
                renderType,
                (pose, consumer) -> renderCylinder(pose.pose(), consumer, red, green, blue, alpha)
        );
        poseStack.popPose();
    }

    private static void renderCylinder(
            Matrix4f matrix,
            VertexConsumer consumer,
            float red,
            float green,
            float blue,
            float alpha
    ) {
        CylinderRenderer.renderCylinder(matrix, consumer, CYLINDER, red, green, blue, alpha);
    }

    @Override
    public AABB getRenderBoundingBox(EnergyLaserTowerBlockEntity blockEntity) {
        if (!blockEntity.isMain()) {
            return new AABB(blockEntity.getBlockPos());
        }
        var pos = blockEntity.getBlockPos();
        double top = Math.max(
                pos.getY() + EnergyLaserTowerBlock.HEIGHT + 128,
                blockEntity.getOrbitVisualY() + 8.0
        );
        double radius = RelaySatelliteEntity.ORBIT_RADIUS + 8.0;
        if (blockEntity.isBeamActive() || blockEntity.isOrbiting()) {
            return new AABB(
                    pos.getX() + 0.5 - radius,
                    pos.getY(),
                    pos.getZ() + 0.5 - radius,
                    pos.getX() + 0.5 + radius,
                    top,
                    pos.getZ() + 0.5 + radius
            );
        }
        return new AABB(
                pos.getX(),
                pos.getY(),
                pos.getZ(),
                pos.getX() + 1,
                pos.getY() + EnergyLaserTowerBlock.HEIGHT,
                pos.getZ() + 1
        );
    }
}
