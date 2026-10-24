package org.academy.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.AABB;
import org.academy.api.client.render.vfxgraph.shape.FirstPersonHandSurface;
import org.academy.api.client.render.vfxgraph.shape.ThirdPersonItemSurface;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemStackRenderState.class)
public abstract class MixinItemStackRenderState {
    @Shadow
    ItemDisplayContext displayContext;

    @Shadow
    public int activeLayerCount;

    @Shadow
    public ItemStackRenderState.LayerRenderState[] layers;

    @Inject(method = "submit", at = @At("HEAD"))
    private void academy$captureItem(
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            int lightCoords,
            int overlayCoords,
            int outlineColor,
            CallbackInfo ci
    ) {
        if (displayContext == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                || displayContext == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND) {
            academy$capture(poseStack, true);
        } else if ((displayContext == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                || displayContext == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND)
                && ThirdPersonItemSurface.hasOwner()) {
            academy$capture(poseStack, false);
        }
    }

    private void academy$capture(PoseStack poseStack, boolean firstPerson) {
        if (activeLayerCount <= 0) return;
        var view = firstPerson
                ? Minecraft.getInstance().gameRenderer.mainCamera().getViewRotationMatrix(new Matrix4f())
                : new Matrix4f();
        var leftHand = displayContext.leftHand();
        var arm = leftHand ? HumanoidArm.LEFT : HumanoidArm.RIGHT;
        var base = new Matrix4f(view).mul(poseStack.last().pose());
        var pose = new PoseStack();
        var builtMesh = false;
        for (var i = 0; i < activeLayerCount; i++) {
            var layer = layers[i];
            if (layer.quads.isEmpty()) continue;
            pose.last().set(poseStack.last());
            layer.itemTransform.apply(leftHand, pose.last());
            pose.last().mulPose(layer.localTransform);
            var transform = new Matrix4f(view).mul(pose.last().pose());
            if (firstPerson) {
                FirstPersonHandSurface.addItem(arm, transform, layer.quads.all());
            } else {
                ThirdPersonItemSurface.addItem(transform, layer.quads.all());
            }
            builtMesh = true;
        }
        if (builtMesh) return;
        var box = ((ItemStackRenderState) (Object) this).getModelBoundingBox();
        if (box.getXsize() < 1.0e-4 && box.getYsize() < 1.0e-4 && box.getZsize() < 1.0e-4) {
            box = new AABB(-0.25, -0.25, -0.25, 0.25, 0.25, 0.25);
        }
        if (firstPerson) {
            FirstPersonHandSurface.addBox(arm, base,
                    (float) box.minX, (float) box.minY, (float) box.minZ,
                    (float) box.maxX, (float) box.maxY, (float) box.maxZ);
        } else {
            ThirdPersonItemSurface.addBox(poseStack.last().pose(),
                    (float) box.minX, (float) box.minY, (float) box.minZ,
                    (float) box.maxX, (float) box.maxY, (float) box.maxZ);
        }
    }
}
