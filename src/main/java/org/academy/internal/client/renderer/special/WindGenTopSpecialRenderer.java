package org.academy.internal.client.renderer.special;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.special.NoDataSpecialModelRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import org.academy.api.client.Resource;
import org.academy.internal.client.renderer.blockentity.WindGenTopRenderer;
import org.academy.internal.client.renderer.blockentity.state.WindGenTopRenderState;
import org.joml.Vector3f;

import java.util.Set;

public final class WindGenTopSpecialRenderer implements NoDataSpecialModelRenderer {
    public static final WindGenTopSpecialRenderer INSTANCE = new WindGenTopSpecialRenderer();

    private WindGenTopSpecialRenderer() {
    }

    @Override
    public void getExtents(Set<Vector3f> output) {
        var posestack = new PoseStack();
        posestack.scale(1.0F, -1.0F, -1.0F);
        WindGenTopRenderer.MODEL.root().getExtentsForGui(posestack, output);
    }

    @Override
    public void submit(ItemDisplayContext displayContext, PoseStack poseStack, SubmitNodeCollector nodeCollector, int packedLight, int packedOverlay, boolean hasFoil, int outlineColor) {
        poseStack.pushPose();
        poseStack.translate(0.5f, 1.5f, 0.5f);
        poseStack.mulPose(Axis.XP.rotationDegrees(180));
        if (displayContext == ItemDisplayContext.GUI) {
            poseStack.translate(0.075f, 0.075f, 0);
            poseStack.scale(0.85f, 0.85f, 0.85f);
        }
        if (displayContext.firstPerson()){
            poseStack.mulPose(Axis.YN.rotationDegrees(90));
        }
        nodeCollector.submitModel(WindGenTopRenderer.MODEL, new WindGenTopRenderState(), poseStack, RenderType.entityTranslucent(Resource.Textures.MODEL_WIND_GEN_TOP), packedLight, packedOverlay, outlineColor, null);
        poseStack.popPose();
    }

    public record Unbaked() implements SpecialModelRenderer.Unbaked {
        public static final Unbaked INSTANCE = new Unbaked();
        public static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(INSTANCE);

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }

        @Override
        public SpecialModelRenderer<?> bake(SpecialModelRenderer.BakingContext context) {
            return WindGenTopSpecialRenderer.INSTANCE;
        }
    }
}