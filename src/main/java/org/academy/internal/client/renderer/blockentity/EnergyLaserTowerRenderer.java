package org.academy.internal.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.api.client.renderer.OrientedCylinderBeam;
import org.academy.internal.client.renderer.blockentity.state.EnergyLaserTowerRenderState;
import org.academy.internal.common.world.entity.misaka.RelaySatelliteEntity;
import org.academy.internal.common.world.level.block.EnergyLaserTowerBlock;
import org.academy.internal.common.world.level.block.entity.EnergyLaserTowerBlockEntity;
import org.academy.internal.server.misaka.MisakaRelayOrbits;
import org.jspecify.annotations.Nullable;

import static org.academy.api.client.render.Render.RenderTypes.POS_COLOR_QUADS_ADDITIVE;
import static org.academy.api.client.render.Render.RenderTypes.POS_COLOR_QUADS_NO_DEPTH_WRITE;

/**
 * Skyward / satellite-aimed power beam only.
 * In-orbit sky model is drawn by {@code MisakaOrbitSkyClient} under the satellite slot
 * (independent of standing at this tower; no orbit entity).
 */
public final class EnergyLaserTowerRenderer
        implements BlockEntityRenderer<EnergyLaserTowerBlockEntity, EnergyLaserTowerRenderState> {
    public static final EnergyLaserTowerRenderer INSTANCE = new EnergyLaserTowerRenderer();
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
        if (!blockEntity.isMain()) {
            return;
        }
        renderState.beamActive = blockEntity.isBeamActive() && blockEntity.getEnergyStored() > 0;
        renderState.beamEndRelative = new Vec3(0.5, EnergyLaserTowerBlock.HEIGHT + DEFAULT_BEAM_HEIGHT, 0.5);
        if (!renderState.beamActive) {
            return;
        }

        var level = Minecraft.getInstance().level;
        if (level == null || !blockEntity.isOrbiting()) {
            return;
        }
        Vec3 tip;
        if (blockEntity.isStrikeAiming()) {
            tip = blockEntity.getStrikeAim();
        } else {
            double orbitY = blockEntity.getOrbitVisualY();
            if (!(orbitY > 1.0)) {
                orbitY = MisakaRelayOrbits.visualOrbitY(level, MisakaRelayOrbits.DEFAULT_ORBIT_HEIGHT);
            }
            tip = MisakaRelayOrbits.orbitSlotWorld(
                    blockEntity.getBlockPos(),
                    orbitY,
                    level.getGameTime(),
                    blockEntity.getOrbitAngleSeed()
            );
        }
        if (!MisakaRelayOrbits.isAboveLaserHorizon(blockEntity.getBlockPos(), tip)) {
            renderState.beamActive = false;
            return;
        }
        var relative = tip.subtract(Vec3.atLowerCornerOf(blockEntity.getBlockPos()));
        renderState.beamEndRelative = clampBeamEnd(relative);
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
    }

    private static Vec3 clampBeamEnd(Vec3 relativeEnd) {
        var start = new Vec3(0.5, EnergyLaserTowerBlock.HEIGHT, 0.5);
        var delta = relativeEnd.subtract(start);
        double length = delta.length();
        double max = MisakaRelayOrbits.MAX_BEAM_TRACK_RANGE;
        if (!(length > max) || !Double.isFinite(length) || length <= 1.0e-6) {
            return relativeEnd;
        }
        return start.add(delta.scale(max / length));
    }

    private static void submitBeam(
            EnergyLaserTowerRenderState renderState,
            PoseStack poseStack,
            SubmitNodeCollector nodeCollector
    ) {
        var start = new Vec3(0.5, EnergyLaserTowerBlock.HEIGHT, 0.5);
        var end = renderState.beamEndRelative;
        if (end.y < start.y) {
            return;
        }
        var delta = end.subtract(start);
        double length = delta.length();
        if (!(length > 0.05) || !Double.isFinite(length)) {
            return;
        }
        if (!OrientedCylinderBeam.preparePose(poseStack, start, delta, (float) length)) {
            return;
        }
        OrientedCylinderBeam.submitLayer(
                nodeCollector, poseStack, (float) length, 0.12f,
                POS_COLOR_QUADS_NO_DEPTH_WRITE, 0.35f, 0.85f, 1.0f, 0.55f
        );
        OrientedCylinderBeam.submitLayer(
                nodeCollector, poseStack, (float) length, 0.045f,
                POS_COLOR_QUADS_ADDITIVE, 0.85f, 0.95f, 1.0f, 0.9f
        );
        poseStack.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(EnergyLaserTowerBlockEntity blockEntity) {
        if (!blockEntity.isMain()) {
            return new AABB(blockEntity.getBlockPos());
        }
        var pos = blockEntity.getBlockPos();
        if (!blockEntity.isBeamActive()) {
            return new AABB(
                    pos.getX(),
                    pos.getY(),
                    pos.getZ(),
                    pos.getX() + 1,
                    pos.getY() + EnergyLaserTowerBlock.HEIGHT,
                    pos.getZ() + 1
            );
        }
        double orbitY = blockEntity.getOrbitVisualY();
        if (!(orbitY > 1.0)) {
            var level = blockEntity.getLevel();
            orbitY = level != null
                    ? MisakaRelayOrbits.visualOrbitY(level, MisakaRelayOrbits.DEFAULT_ORBIT_HEIGHT)
                    : pos.getY() + 304.0;
        }
        double top = Math.max(pos.getY() + EnergyLaserTowerBlock.HEIGHT + 128, orbitY + 8.0);
        double radius = RelaySatelliteEntity.ORBIT_RADIUS + 8.0;
        double minX = pos.getX() + 0.5 - radius;
        double minZ = pos.getZ() + 0.5 - radius;
        double maxX = pos.getX() + 0.5 + radius;
        double maxZ = pos.getZ() + 0.5 + radius;
        if (blockEntity.isStrikeAiming()) {
            var aim = blockEntity.getStrikeAim();
            minX = Math.min(minX, aim.x - 8.0);
            minZ = Math.min(minZ, aim.z - 8.0);
            maxX = Math.max(maxX, aim.x + 8.0);
            maxZ = Math.max(maxZ, aim.z + 8.0);
            top = Math.max(top, aim.y + 8.0);
        }
        return new AABB(minX, pos.getY(), minZ, maxX, top, maxZ);
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    @Override
    public int getViewDistance() {
        return Math.max(
                Minecraft.getInstance().options.getEffectiveRenderDistance() * 16,
                128
        );
    }
}
