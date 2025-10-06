package org.academy.internal.client.renderer.special;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.special.NoDataSpecialModelRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import org.academy.api.client.render.RenderTypes;
import org.academy.internal.client.renderer.blockentity.AbilityDeveloperRenderer;
import org.academy.internal.client.renderer.blockentity.state.AbilityDeveloperRenderState;
import org.joml.Vector3f;

import java.util.Set;

public final class AbilityDeveloperSpecialRenderer implements NoDataSpecialModelRenderer {
    public static final AbilityDeveloperSpecialRenderer INSTANCE = new AbilityDeveloperSpecialRenderer();

    private AbilityDeveloperSpecialRenderer() {
    }

    @Override
    public void submit(ItemDisplayContext displayContext, PoseStack poseStack, SubmitNodeCollector nodeCollector, int packedLight, int packedOverlay, boolean hasFoil, int outlineColor) {
        poseStack.pushPose();
        poseStack.translate(0.5f, 0.5f, 0.5f);
        if (displayContext == ItemDisplayContext.GUI) {
            poseStack.last().normal().rotationX((float) Math.toRadians(180));
            poseStack.translate(0, 0.125f, 0);
            poseStack.scale(0.85f, 0.85f, 0.85f);
        }
        poseStack.rotateAround(Axis.XP.rotationDegrees(180), 0, 0, 0);
        poseStack.scale(0.5f, 0.5f, 0.5f);
        if (displayContext.firstPerson()) {
            poseStack.rotateAround(Axis.YN.rotationDegrees(90), 0, 0, 0);
        }
        nodeCollector.submitModel(AbilityDeveloperRenderer.MODEL, new AbilityDeveloperRenderState(), poseStack, RenderTypes.ABILITY_DEVELOPER, packedLight, packedOverlay, outlineColor, null);

        poseStack.popPose();
    }


    @Override
    public void getExtents(Set<Vector3f> output) {
    }

    public record Unbaked() implements SpecialModelRenderer.Unbaked {
        public static final Unbaked INSTANCE = new Unbaked();
        public static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(INSTANCE);

        @Override
        public SpecialModelRenderer<?> bake(BakingContext context) {
            return AbilityDeveloperSpecialRenderer.INSTANCE;
        }

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }
    }
}