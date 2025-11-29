package org.academy.internal.client.renderer.special;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.NoDataSpecialModelRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import org.academy.api.client.Resource;
import org.academy.internal.client.renderer.blockentity.state.SolarGenRenderState;
import org.joml.Vector3fc;

import java.util.function.Consumer;

import static org.academy.internal.client.renderer.blockentity.SolarGenRenderer.MODEL;

public final class SolarGenSpecialRenderer implements NoDataSpecialModelRenderer {
    public static final SolarGenSpecialRenderer INSTANCE = new SolarGenSpecialRenderer();

    private SolarGenSpecialRenderer() {
    }

    @Override
    public void getExtents(Consumer<Vector3fc> output) {
        var posestack = new PoseStack();
        posestack.scale(1.0F, -1.0F, -1.0F);
        MODEL.root().getExtentsForGui(posestack, output);
    }

    @Override
    public void submit(ItemDisplayContext displayContext, PoseStack poseStack, SubmitNodeCollector nodeCollector, int packedLight, int packedOverlay, boolean p_387131_, int p_451703_) {
        poseStack.pushPose();
        poseStack.translate(0.5f, 1.5f, 0.5f);
        if (displayContext.firstPerson()) {
            poseStack.translate(0, 0.5f, 0);
        }
        poseStack.mulPose(Axis.XP.rotationDegrees(180));
        MODEL.resetPose();
        nodeCollector.submitModel(MODEL, new SolarGenRenderState(), poseStack, RenderTypes.entityCutout(Resource.Textures.SOLAR_GEN_MODEL), packedLight, packedOverlay, 0, null);
        poseStack.popPose();
    }

    public record Unbaked() implements SpecialModelRenderer.Unbaked {
        public static final SolarGenSpecialRenderer.Unbaked INSTANCE = new SolarGenSpecialRenderer.Unbaked();
        public static final MapCodec<SolarGenSpecialRenderer.Unbaked> MAP_CODEC = MapCodec.unit(INSTANCE);

        @Override
        public SpecialModelRenderer<?> bake(SpecialModelRenderer.BakingContext context) {
            return SolarGenSpecialRenderer.INSTANCE;
        }

        @Override
        public MapCodec<SolarGenSpecialRenderer.Unbaked> type() {
            return MAP_CODEC;
        }
    }
}