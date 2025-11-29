package org.academy.internal.client.renderer.special;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.special.NoDataSpecialModelRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.world.item.ItemDisplayContext;
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
    public void submit(ItemDisplayContext displayContext, PoseStack poseStack, SubmitNodeCollector nodeCollector, int packedLight, int packedOverlay, boolean hasFoil, int outlineColor) {
        poseStack.pushPose();
        if (displayContext == ItemDisplayContext.THIRD_PERSON_LEFT_HAND || displayContext == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND) {
            poseStack.translate(0.25f, 0.25f, 0.75f);
        } else {
            poseStack.translate(0.5f, 0, 0.5f);
        }
        WindGenBaseRenderer.MODEL.renderPole(poseStack, nodeCollector, packedLight, packedOverlay);
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
            return WindGenPillarSpecialRenderer.INSTANCE;
        }
    }
}