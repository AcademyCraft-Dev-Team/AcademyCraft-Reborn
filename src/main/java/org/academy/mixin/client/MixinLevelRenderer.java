package org.academy.mixin.client;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import org.academy.api.client.render.post.BloomEffect;
import org.academy.api.client.render.post.PostEffect;
import org.academy.api.client.renderer.CylinderRenderer;
import org.academy.internal.client.model.WindGenBaseModel;
import org.academy.internal.common.world.level.block.Blocks;
import org.academy.internal.common.world.level.block.MultiBlock;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class MixinLevelRenderer {
    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelTargetBundle;clear()V"))
    private void renderLevel(GraphicsResourceAllocator graphicsResourceAllocator, DeltaTracker deltaTracker, boolean renderBlockOutline, Camera camera, Matrix4f frustumMatrix, Matrix4f projectionMatrix, GpuBufferSlice fogBuffer, Vector4f fogColor, boolean renderSky, CallbackInfo ci) {
        PostEffect.pre();
        BloomEffect.process();
        PostEffect.post();
    }

    @Inject(method = "renderHitOutline", at = @At("HEAD"), cancellable = true)
    private void renderHitOutline(PoseStack poseStack, VertexConsumer buffer, Entity entity, double camX, double camY, double camZ, BlockPos pos, BlockState state, int color, CallbackInfo ci) {
        if (state.getBlock() == Blocks.WIND_GEN_PILLAR.get() || (state.getBlock() == Blocks.WIND_GEN_BASE.get() && state.getValue(MultiBlock.TYPE) == MultiBlock.MultiBlockType.SUBJECT)) {
            poseStack.pushPose();
            poseStack.translate(pos.getX() - camX, pos.getY() - camY, pos.getZ() - camZ);
            poseStack.translate(0.5f, 0, 0.5f);
            poseStack.rotateAround(Axis.YN.rotationDegrees(22.5f), 0, 0, 0);
            CylinderRenderer.renderCylinderWireframe(poseStack, buffer, WindGenBaseModel.PILLAR_OUTLINE_VERTEX_BUFFER, ARGB.red(color), ARGB.green(color), ARGB.blue(color), ARGB.alpha(color));
            poseStack.popPose();
            ci.cancel();
        }
    }
}