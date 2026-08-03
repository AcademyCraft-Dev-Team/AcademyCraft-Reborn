package org.academy.internal.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.RenderShape;
import org.academy.internal.client.renderer.entity.state.DirStrikeBlockFxRenderState;
import org.academy.internal.common.world.entity.skill.DirStrikeBlockFx;

public final class DirStrikeBlockFxRenderer
        extends EntityRenderer<DirStrikeBlockFx, DirStrikeBlockFxRenderState> {
    private static final float RISE_TICKS = 6.0f;

    public DirStrikeBlockFxRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void submit(DirStrikeBlockFxRenderState state, PoseStack poseStack,
                       SubmitNodeCollector collector, CameraRenderState cameraState) {
        if (!state.active || state.movingBlockRenderState.blockState.getRenderShape() != RenderShape.MODEL) return;

        var seed = state.movingBlockRenderState.blockState.getSeed(state.movingBlockRenderState.randomSeedPos);
        var lift = state.motion * state.peakHeight * 1.12f;
        var tilt = state.motion * 12.0f;
        var cornerPitch = (((seed >> 1) & 1L) == 0L ? 1.0f : -1.0f) * state.motion * 4.0f;
        var cornerRoll = (((seed >> 2) & 1L) == 0L ? 1.0f : -1.0f) * state.motion * 3.4f;
        var cornerLift = (0.015f + ((seed >> 3) & 3L) * 0.005f) * state.motion;

        poseStack.pushPose();
        poseStack.translate(0.0, lift + 0.03 + cornerLift, 0.0);
        poseStack.mulPose(Axis.YP.rotationDegrees(state.yRot));
        poseStack.translate(0.0, 0.0, state.motion * 0.1f);
        poseStack.mulPose(Axis.XP.rotationDegrees(-tilt));
        poseStack.mulPose(Axis.ZP.rotationDegrees(tilt * 0.35f));
        poseStack.mulPose(Axis.XP.rotationDegrees(cornerPitch));
        poseStack.mulPose(Axis.ZP.rotationDegrees(cornerRoll));
        poseStack.scale(0.98f, 0.98f, 0.98f);
        poseStack.translate(-0.5, 0.0, -0.5);
        collector.submitMovingBlock(poseStack, state.movingBlockRenderState, state.outlineColor);
        poseStack.popPose();
    }

    @Override
    public DirStrikeBlockFxRenderState createRenderState() {
        return new DirStrikeBlockFxRenderState();
    }

    @Override
    public void extractRenderState(DirStrikeBlockFx entity, DirStrikeBlockFxRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        var pos = entity.blockPosition();
        state.active = entity.isActive(partialTick);
        state.motion = motion(entity, partialTick);
        state.peakHeight = entity.getPeakHeight();
        state.yRot = entity.getYRot();
        state.movingBlockRenderState.randomSeedPos = entity.getStartPos();
        state.movingBlockRenderState.blockPos = pos;
        state.movingBlockRenderState.blockState = entity.getBlockState();
        if (entity.level() instanceof ClientLevel clientLevel) {
            state.movingBlockRenderState.biome = clientLevel.getBiome(pos);
            state.movingBlockRenderState.cardinalLighting = clientLevel.cardinalLighting();
            state.movingBlockRenderState.lightEngine = clientLevel.getLightEngine();
        }
    }

    private static float motion(DirStrikeBlockFx entity, float partialTick) {
        var activeTick = entity.getActiveTick(partialTick);
        var riseTicks = Math.min(RISE_TICKS, Math.max(2.0f, entity.getDuration() * 0.45f));
        var fallTicks = Math.max(6.0f, entity.getDuration() - riseTicks);
        if (activeTick <= riseTicks) {
            var rise = Mth.clamp(activeTick / riseTicks, 0.0f, 1.0f);
            var inverse = 1.0f - rise;
            return 1.0f - inverse * inverse * inverse;
        }
        if (activeTick <= riseTicks + entity.getHoldTicks()) return 1.0f;
        var fall = Mth.clamp(
                (activeTick - riseTicks - entity.getHoldTicks()) / fallTicks, 0.0f, 1.0f);
        return 1.0f - fall * fall;
    }

    @Override
    protected boolean affectedByCulling(DirStrikeBlockFx entity) {
        return false;
    }
}
