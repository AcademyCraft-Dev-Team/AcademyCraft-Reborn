package org.academy.internal.client.renderer.special;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.special.NoDataSpecialModelRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import org.academy.internal.client.renderer.blockentity.WindGenBaseRenderer;
import org.joml.Vector3fc;

import java.util.function.Consumer;

public final class WindGenPillarSpecialRenderer implements NoDataSpecialModelRenderer {
    public static final WindGenPillarSpecialRenderer INSTANCE = new WindGenPillarSpecialRenderer();

    private WindGenPillarSpecialRenderer() {
    }

    @Override
    public void getExtents(Consumer<Vector3fc> output) {
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector nodeCollector, int packedLight, int packedOverlay, boolean hasFoil, int outlineColor) {
        poseStack.pushPose();
        poseStack.translate(0.5f, 0, 0.5f);
        WindGenBaseRenderer.MODEL.renderPole(poseStack, nodeCollector, packedLight, packedOverlay);
        poseStack.popPose();
    }

    public record Unbaked() implements SpecialModelRenderer.Unbaked<Void> {
        public static final Unbaked INSTANCE = new Unbaked();
        public static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(INSTANCE);

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }

        @Override
        public SpecialModelRenderer<Void> bake(SpecialModelRenderer.BakingContext context) {
            return WindGenPillarSpecialRenderer.INSTANCE;
        }
    }
}