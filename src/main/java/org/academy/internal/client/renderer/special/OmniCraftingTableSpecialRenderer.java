package org.academy.internal.client.renderer.special;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.special.NoDataSpecialModelRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import org.academy.internal.client.renderer.blockentity.OmniCraftingTableRenderer;
import org.joml.Vector3fc;

import java.util.function.Consumer;

public final class OmniCraftingTableSpecialRenderer implements NoDataSpecialModelRenderer {
    public static final OmniCraftingTableSpecialRenderer INSTANCE = new OmniCraftingTableSpecialRenderer();

    private OmniCraftingTableSpecialRenderer() {
    }

    @Override
    public void submit(ItemDisplayContext displayContext, PoseStack poseStack, SubmitNodeCollector nodeCollector, int packedLight, int packedOverlay, boolean hasFoil, int outlineColor) {
        poseStack.pushPose();
        {
            poseStack.scale(0.5f, 0.5f, 0.5f);
            poseStack.translate(0.5f, 0, 0.5f);
            poseStack.mulPose(Axis.XP.rotationDegrees(180));
            OmniCraftingTableRenderer.MODEL.resetPose();
            OmniCraftingTableRenderer.MODEL.render(poseStack, nodeCollector, packedLight, packedOverlay);
        }
        poseStack.popPose();
    }

    @Override
    public void getExtents(Consumer<Vector3fc> output) {
        var posestack = new PoseStack();
        posestack.scale(1.0F, -1.0F, -1.0F);
        OmniCraftingTableRenderer.MODEL.root().getExtentsForGui(posestack, output);
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
            return OmniCraftingTableSpecialRenderer.INSTANCE;
        }
    }
}