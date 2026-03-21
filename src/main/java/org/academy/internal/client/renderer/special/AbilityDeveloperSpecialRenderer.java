package org.academy.internal.client.renderer.special;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.special.NoDataSpecialModelRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import org.academy.api.client.Render;
import org.academy.internal.client.model.AbilityDeveloperModel;
import org.academy.internal.client.renderer.blockentity.state.AbilityDeveloperRenderState;
import org.joml.Vector3fc;

import java.util.function.Consumer;

public final class AbilityDeveloperSpecialRenderer implements NoDataSpecialModelRenderer {
    public static final AbilityDeveloperModel MODEL = new AbilityDeveloperModel(AbilityDeveloperModel.createBodyLayer().bakeRoot());
    public static final AbilityDeveloperSpecialRenderer INSTANCE = new AbilityDeveloperSpecialRenderer();

    private AbilityDeveloperSpecialRenderer() {
    }
    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, int overlayCoords, boolean hasFoil, int outlineColor) {
        poseStack.pushPose();
        poseStack.translate(0.5f, 0.5f, 0.5f);
        poseStack.rotateAround(Axis.XP.rotationDegrees(180), 0, 0, 0);
        poseStack.scale(0.5f, 0.5f, 0.5f);
        submitNodeCollector.submitModel(MODEL, new AbilityDeveloperRenderState(), poseStack, Render.RenderTypes.ABILITY_DEVELOPER, lightCoords, overlayCoords, outlineColor, null);

        poseStack.popPose();
    }
    @Override
    public void getExtents(Consumer<Vector3fc> output) {
        var posestack = new PoseStack();
        posestack.scale(1.0F, -1.0F, -1.0F);
        MODEL.root().getExtentsForGui(posestack, output);
    }

    public record Unbaked() implements SpecialModelRenderer.Unbaked<Void> {
        public static final Unbaked INSTANCE = new Unbaked();
        public static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(INSTANCE);

        @Override
        public SpecialModelRenderer<Void> bake(SpecialModelRenderer.BakingContext context) {
            return AbilityDeveloperSpecialRenderer.INSTANCE;
        }

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }
    }
}