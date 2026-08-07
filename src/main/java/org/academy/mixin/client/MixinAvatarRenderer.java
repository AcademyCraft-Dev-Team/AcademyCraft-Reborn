package org.academy.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.academy.api.client.vanilla.AvatarRendererContext;
import org.academy.internal.common.attachment.AttachmentTypes;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@SuppressWarnings("AddedMixinMembersNamePattern")
@Mixin(AvatarRenderer.class)
public abstract class MixinAvatarRenderer implements AvatarRendererContext {
    @Unique
    @Nullable
    private Matrix4f modelRootMatrix;

    @Inject(method = "setupRotations*", at = @At("RETURN"))
    private void captureModelRootMatrix(AvatarRenderState state, PoseStack poseStack, float bodyRot, float entityScale, CallbackInfo ci) {
        var localPlayer = Minecraft.getInstance().player;
        if (localPlayer == null || state.id != localPlayer.getId()) return;
        var matrix = new Matrix4f(poseStack.last().pose());
        matrix.scale(-1.0f, -1.0f, 1.0f);
        matrix.scale(0.9375f);
        matrix.translate(0.0f, -1.501f, 0.0f);
        modelRootMatrix = matrix;
    }

    @Inject(
            method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
            at = @At("RETURN")
    )
    private void hideMagneticWeaponInMainHand(
            Avatar avatar, AvatarRenderState state, float partialTick, CallbackInfo ci
    ) {
        var data = avatar.getData(AttachmentTypes.MAGNETIC_WEAPON_DATA.get());
        if (!data.active() || !data.hideMainHand()) return;
        if (avatar.getMainArm() == HumanoidArm.RIGHT) {
            state.rightHandItemState.clear();
            state.rightHandItemStack = ItemStack.EMPTY;
        } else {
            state.leftHandItemState.clear();
            state.leftHandItemStack = ItemStack.EMPTY;
        }
    }

    @Override
    public @Nullable Matrix4f takeModelRootMatrix() {
        var matrix = modelRootMatrix;
        modelRootMatrix = null;
        return matrix;
    }
}
