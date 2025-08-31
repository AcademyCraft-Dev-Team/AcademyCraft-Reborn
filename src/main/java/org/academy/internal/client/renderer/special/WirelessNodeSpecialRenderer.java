package org.academy.internal.client.renderer.special;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.special.NoDataSpecialModelRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import org.academy.internal.client.renderer.blockentity.WirelessNodeBlockEntityRenderer;
import org.joml.Vector3f;

import java.util.Set;

public final class WirelessNodeSpecialRenderer implements NoDataSpecialModelRenderer {
    public static final WirelessNodeSpecialRenderer INSTANCE = new WirelessNodeSpecialRenderer();

    private WirelessNodeSpecialRenderer() {
    }

    @Override
    public void render(ItemDisplayContext displayContext, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay, boolean hasFoilType) {
        poseStack.pushPose();
        poseStack.translate(0.5f, 0, 0.5f);
        poseStack.rotateAround(Axis.XP.rotationDegrees(180), 0, 0, 0);
        WirelessNodeBlockEntityRenderer.WIRELESS_NODE_MODEL.render(poseStack, bufferSource, packedLight, packedOverlay);
        poseStack.popPose();
    }

    @Override
    public void getExtents(Set<Vector3f> output) {
        var posestack = new PoseStack();
        posestack.scale(1.0F, -1.0F, -1.0F);
        WirelessNodeBlockEntityRenderer.WIRELESS_NODE_MODEL.root().getExtentsForGui(posestack, output);
    }

    public record Unbaked() implements SpecialModelRenderer.Unbaked {
        public static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(new Unbaked());

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }

        @Override
        public SpecialModelRenderer<?> bake(EntityModelSet p_386553_) {
            return INSTANCE;
        }
    }
}