package org.academy.internal.client.renderer.special;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.special.NoDataSpecialModelRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import org.academy.api.client.Resource;
import org.academy.internal.client.model.WindGenBaseModel;
import org.academy.internal.client.renderer.blockentity.state.WindGenBaseRenderState;
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
        WindGenBaseModel.MODEL.root().getExtentsForGui(posestack, output);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, int overlayCoords, boolean hasFoil, final int outlineColor) {
        poseStack.pushPose();
        poseStack.translate(0.5f, -0.5f, 0.5f);
        poseStack.scale(0.75F, 0.75F, 0.75F);
        poseStack.mulPose(Axis.XP.rotationDegrees(180));
        submitNodeCollector.submitModel(
                WindGenBaseModel.MODEL,
                WindGenBaseRenderState.NONE,
                poseStack, Resource.Textures.MODEL_WIND_GEN, lightCoords, overlayCoords, 0, null
        );
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
