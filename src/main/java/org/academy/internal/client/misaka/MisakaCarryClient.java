package org.academy.internal.client.misaka;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import org.academy.internal.common.world.entity.misaka.MisakaSisterEntity;

/**
 * Client helpers for Misaka carry (player holding a sister via passenger mount).
 */
public final class MisakaCarryClient {
    private MisakaCarryClient() {
    }

    public static boolean isCarryingSister(int entityId) {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            return false;
        }
        var entity = level.getEntity(entityId);
        return entity instanceof Player player && isCarryingSister(player);
    }

    public static boolean isCarryingSister(Player player) {
        if (player == null) {
            return false;
        }
        for (var passenger : player.getPassengers()) {
            if (passenger instanceof MisakaSisterEntity) {
                return true;
            }
        }
        return false;
    }

    /** Prefer carry arms unless the player is actively using an item. */
    public static boolean shouldApplyCarryArmPose(Player player) {
        return isCarryingSister(player) && !player.isUsingItem();
    }

    public static boolean shouldApplyCarryArmPose(int entityId) {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            return false;
        }
        var entity = level.getEntity(entityId);
        return entity instanceof Player player && shouldApplyCarryArmPose(player);
    }

    /** Third-person arms wrapping an object held against the chest. */
    public static void applyCarryArmPose(HumanoidModel<?> model) {
        model.rightArm.xRot = -1.05F;
        model.rightArm.yRot = -0.35F;
        model.rightArm.zRot = 0.12F;

        model.leftArm.xRot = -1.05F;
        model.leftArm.yRot = 0.35F;
        model.leftArm.zRot = -0.12F;
    }

    /**
     * First-person PoseStack for a hug: arms lower, inward, and slightly forward of the camera.
     * Ends with the same skin-orientation offsets vanilla {@code renderPlayerArm} uses.
     */
    public static void applyFirstPersonCarryArm(PoseStack poseStack, HumanoidArm arm, float inverseArmHeight) {
        boolean right = arm != HumanoidArm.LEFT;
        float invert = right ? 1.0F : -1.0F;
        poseStack.translate(invert * 0.22F, -0.42F + inverseArmHeight * -0.35F, -0.48F);
        poseStack.mulPose(Axis.YP.rotationDegrees(invert * 18.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(48.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(invert * -28.0F));
        poseStack.translate(invert * -1.0F, 3.6F, 3.5F);
        poseStack.mulPose(Axis.ZP.rotationDegrees(invert * 120.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(200.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(invert * -135.0F));
        poseStack.translate(invert * 5.6F, 0.0F, 0.0F);
    }
}
