package org.academy.internal.client.renderer.special;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.special.NoDataSpecialModelRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import org.academy.internal.client.renderer.blockentity.WindGenBaseBlockEntityRenderer;
import org.joml.Vector3f;

import java.util.Set;

public final class WindGenBaseSpecialRenderer implements NoDataSpecialModelRenderer {
    public static final WindGenBaseSpecialRenderer INSTANCE = new WindGenBaseSpecialRenderer();

    private WindGenBaseSpecialRenderer() {
    }

    @Override
    public void render(ItemDisplayContext displayContext, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay, boolean hasFoilType) {
        poseStack.pushPose();
        var matrix4f = poseStack.last().pose();
        matrix4f.translate(0.5f, 0.875f, 0.5f);
        matrix4f.rotate(Axis.XP.rotationDegrees(180));
        matrix4f.rotate(Axis.YP.rotationDegrees(25));
        matrix4f.scale(0.625f, 0.625f, 0.625f);
        if (displayContext != ItemDisplayContext.GUI){
            matrix4f.translate(0, -0.5f, 0);
            matrix4f.rotate(Axis.YP.rotationDegrees(-90));
        }
        WindGenBaseBlockEntityRenderer.MODEL.resetPose();
        WindGenBaseBlockEntityRenderer.MODEL.render(poseStack, bufferSource, packedLight, packedOverlay);
        matrix4f.translate(0, -0.5f, 0);
        matrix4f.scale(1, -1, 1);
        matrix4f.rotate(Axis.YP.rotationDegrees(90));
        WindGenBaseBlockEntityRenderer.MODEL.renderPole(poseStack, bufferSource, packedLight, packedOverlay);
        poseStack.popPose();
    }

    @Override
    public void getExtents(Set<Vector3f> output) {
        var posestack = new PoseStack();
        posestack.scale(1.0F, -1.0F, -1.0F);
        WindGenBaseBlockEntityRenderer.MODEL.root().getExtentsForGui(posestack, output);
    }

    public record Unbaked() implements SpecialModelRenderer.Unbaked {
        public static final Unbaked INSTANCE = new Unbaked();
        public static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(INSTANCE);

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }

        @Override
        public SpecialModelRenderer<?> bake(EntityModelSet p_386553_) {
            return WindGenBaseSpecialRenderer.INSTANCE;
        }
    }
}