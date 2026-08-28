package org.academy.internal.client.animation;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import org.academy.internal.common.world.level.block.AbilityDeveloperSleep;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import static org.academy.AcademyCraft.academy;

public final class AbilityDeveloperSleepClient {
    public static final ContextKey<PodPose> CONTEXT_KEY =
            new ContextKey<>(academy("ability_developer_sleep_pose"));
    private static final double SLEEPING_HEIGHT = 0.6875;

    private AbilityDeveloperSleepClient() {
    }

    public static void extract(Avatar avatar, AvatarRenderState renderState) {
        renderState.setRenderData(CONTEXT_KEY, sample(avatar, renderState.partialTick));
    }

    public static void applyModelTransform(AvatarRenderState renderState, PoseStack poseStack) {
        var podPose = renderState.getRenderData(CONTEXT_KEY);
        if (podPose == null || podPose.angleDegrees() <= 0.0f) return;

        var pivotX = podPose.mainPosition().getX() - podPose.sleepingPosition().getX();
        var pivotZ = podPose.mainPosition().getZ() - podPose.sleepingPosition().getZ();
        poseStack.translate(pivotX, 0.0, pivotZ);
        poseStack.mulPose(rotation(podPose));
        poseStack.translate(-pivotX, 0.0, -pivotZ);
    }

    @Nullable
    public static CameraAdjustment cameraAdjustment(Camera camera, float partialTick) {
        if (!(camera.entity() instanceof LivingEntity livingEntity)) return null;
        var podPose = sample(livingEntity, partialTick);
        if (podPose == null || podPose.angleDegrees() <= 0.0f) return null;

        var pivot = Vec3.atBottomCenterOf(podPose.mainPosition()).add(0.0, SLEEPING_HEIGHT, 0.0);
        var rotatedOffset = camera.position().subtract(pivot).toVector3f();
        rotation(podPose).transform(rotatedOffset);
        return new CameraAdjustment(
                pivot.add(rotatedOffset.x, rotatedOffset.y, rotatedOffset.z),
                podPose.angleDegrees()
        );
    }

    @Nullable
    private static PodPose sample(LivingEntity sleeper, float partialTick) {
        if (!sleeper.isSleeping()) return null;
        var level = sleeper.level();
        if (level == null) return null;
        var sleepingPosition = sleeper.getSleepingPos().orElse(null);
        if (sleepingPosition == null) return null;

        var developer = AbilityDeveloperSleep.getDeveloperAt(level, sleepingPosition);
        if (developer == null) return null;
        var facing = developer.getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING);
        return new PodPose(
                developer.getBlockPos(),
                sleepingPosition,
                facing,
                developer.getPodRotationDegrees(partialTick)
        );
    }

    private static Quaternionf rotation(PodPose podPose) {
        Direction axis = podPose.facing().getCounterClockWise();
        return new Quaternionf().rotationAxis(
                podPose.angleDegrees() * ((float) Math.PI / 180.0f),
                axis.getStepX(),
                0.0f,
                axis.getStepZ()
        );
    }

    public record PodPose(
            BlockPos mainPosition,
            BlockPos sleepingPosition,
            Direction facing,
            float angleDegrees
    ) {
    }

    public record CameraAdjustment(Vec3 position, float pitchOffset) {
    }
}
