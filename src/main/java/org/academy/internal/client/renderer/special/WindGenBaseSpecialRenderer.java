package org.academy.internal.client.renderer.special;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.special.NoDataSpecialModelRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import org.academy.internal.client.renderer.blockentity.WindGenBaseRenderer;
import org.joml.Vector3fc;

import java.util.function.Consumer;

public final class WindGenBaseSpecialRenderer implements NoDataSpecialModelRenderer {
    public static final WindGenBaseSpecialRenderer INSTANCE = new WindGenBaseSpecialRenderer();

    private WindGenBaseSpecialRenderer() {
    }

    @Override
    public void getExtents(Consumer<Vector3fc> output) {
        var posestack = new PoseStack();
        posestack.scale(1.0F, -1.0F, -1.0F);
        WindGenBaseRenderer.MODEL.root().getExtentsForGui(posestack, output);
    }

    @Override
    public void submit(ItemDisplayContext displayContext, PoseStack poseStack, SubmitNodeCollector nodeCollector, int packedLight, int packedOverlay, boolean hasFoil, int outlineColor) {
        poseStack.pushPose();
        var matrix4f = poseStack.last().pose();
        matrix4f.translate(0.5f, 0.875f, 0.5f);
        matrix4f.rotate(Axis.YP.rotationDegrees(0));
        matrix4f.scale(0.625f, 0.625f, 0.625f);
        if (displayContext != ItemDisplayContext.GUI) {
            matrix4f.translate(0, -0.5f, 0);
            matrix4f.rotate(Axis.YP.rotationDegrees(-270));
        }
        if (displayContext == ItemDisplayContext.GUI) {
            matrix4f.translate(0, -1.35f, 0);
        }
        WindGenBaseRenderer.MODEL.resetPose();
        poseStack.mulPose(Axis.XP.rotationDegrees(180));
        WindGenBaseRenderer.MODEL.render(poseStack, nodeCollector, packedLight, packedOverlay);
        matrix4f.translate(0, -2, 0);
        WindGenBaseRenderer.MODEL.renderPole(poseStack, nodeCollector, packedLight, packedOverlay);
        poseStack.popPose();
    }

    public record Unbaked() implements SpecialModelRenderer.Unbaked<Void> {
        public static final Unbaked INSTANCE = new Unbaked();
        public static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(INSTANCE);

        @Override
        public SpecialModelRenderer<Void> bake(SpecialModelRenderer.BakingContext context) {
            return WindGenBaseSpecialRenderer.INSTANCE;
        }

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }
    }
}