package org.academy.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Pose;
import org.academy.internal.client.renderer.entity.layers.SkillEffectsLayer;
import org.academy.internal.common.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerRenderer.class)
public abstract class MixinPlayerRenderer {
    @Shadow
    protected abstract void setupRotations(AbstractClientPlayer entityLiving, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTicks);

    @Shadow protected abstract void scale(AbstractClientPlayer livingEntity, PoseStack poseStack, float partialTickTime);

    @Unique
    public PlayerRenderer academyCraft$instance;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        academyCraft$instance = (PlayerRenderer) (Object) this;
        SkillEffectsLayer.INSTANCE = new SkillEffectsLayer(academyCraft$instance);
    }

    @Inject(method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("HEAD"))
    private void render(AbstractClientPlayer entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        if (SkillEffectsLayer.INSTANCE != null) {
            if (!entity.isSpectator()) {
                poseStack.pushPose();
                float f = Mth.rotLerp(partialTicks, entity.yBodyRotO, entity.yBodyRot);
                if (entity.hasPose(Pose.SLEEPING)) {
                    Direction direction = entity.getBedOrientation();
                    if (direction != null) {
                        float f4 = entity.getEyeHeight(Pose.STANDING) - 0.1F;
                        poseStack.translate((float)(-direction.getStepX()) * f4, 0.0F, (float)(-direction.getStepZ()) * f4);
                    }
                }
                setupRotations(entity, poseStack, entity.tickCount + partialTicks, f, partialTicks);
                poseStack.scale(-1.0F, -1.0F, 1.0F);
                scale(entity, poseStack, partialTicks);
                poseStack.translate(0.0F, -1.501F, 0.0F);
                SkillEffectsLayer.INSTANCE.render(poseStack, buffer, packedLight, entity, 0, 0, partialTicks, 0, 0, 0);
                poseStack.popPose();
            }
        }
    }

    @Inject(method = "getArmPose", at = @At("HEAD"), cancellable = true)
    private static void getArmPose(AbstractClientPlayer player, InteractionHand hand, CallbackInfoReturnable<HumanoidModel.ArmPose> cir) {
        var itemstack = player.getItemInHand(hand);
        if (itemstack.getItem() == Items.IMAGIPHASE_DOWSING_ROD) {
            cir.setReturnValue(HumanoidModel.ArmPose.CROSSBOW_HOLD);
        }
    }
}