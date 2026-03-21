package org.academy.internal.client.renderer.special;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.special.NoDataSpecialModelRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import org.academy.internal.client.renderer.blockentity.WirelessNodeRenderer;
import org.joml.Vector3fc;

import java.util.function.Consumer;

public final class WirelessNodeSpecialRenderer implements NoDataSpecialModelRenderer {
    public static final WirelessNodeSpecialRenderer INSTANCE = new WirelessNodeSpecialRenderer();

    private WirelessNodeSpecialRenderer() {
    }

    @Override
    public void getExtents(Consumer<Vector3fc> output) {
        var posestack = new PoseStack();
        posestack.scale(1.0F, -1.0F, -1.0F);
        WirelessNodeRenderer.MODEL.root().getExtentsForGui(posestack, output);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int packedLight, int packedOverlay, boolean p_387131_, int p_451703_) {
        poseStack.pushPose();
        poseStack.translate(0.5f, 0, 0.5f);
        poseStack.rotateAround(Axis.XP.rotationDegrees(180), 0, 0, 0);
        WirelessNodeRenderer.MODEL.resetPose();
        WirelessNodeRenderer.MODEL.render(poseStack, submitNodeCollector, packedLight, packedOverlay);
        poseStack.popPose();
    }

    public record Unbaked() implements SpecialModelRenderer.Unbaked<Void> {
        public static final Unbaked INSTANCE = new Unbaked();
        public static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(INSTANCE);

        @Override
        public SpecialModelRenderer<Void> bake(SpecialModelRenderer.BakingContext context) {
            return WirelessNodeSpecialRenderer.INSTANCE;
        }

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }
    }
}