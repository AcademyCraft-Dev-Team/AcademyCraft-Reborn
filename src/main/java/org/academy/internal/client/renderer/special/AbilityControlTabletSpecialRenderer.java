package org.academy.internal.client.renderer.special;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.world.item.ItemStack;
import org.academy.api.client.resources.R;
import org.joml.Vector3fc;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;

import static org.academy.internal.client.model.AbilityControlTabletModel.MODEL;

public final class AbilityControlTabletSpecialRenderer implements SpecialModelRenderer<AbilityControlTabletRenderState> {
    public static final AbilityControlTabletSpecialRenderer INSTANCE = new AbilityControlTabletSpecialRenderer();
    private final Map<ItemStack, Long> animationStartTimes = new WeakHashMap<>();

    private AbilityControlTabletSpecialRenderer() {
    }

    @Override
    public AbilityControlTabletRenderState extractArgument(ItemStack stack) {
        var now = System.nanoTime();
        var startTime = animationStartTimes.computeIfAbsent(stack, ignored -> now);
        return new AbilityControlTabletRenderState(Math.max(0L, (now - startTime) / 1_000_000L));
    }

    @Override
    public void submit(
            AbilityControlTabletRenderState renderState,
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            int packedLight,
            int packedOverlay,
            boolean hasFoil,
            int outlineColor
    ) {
        poseStack.pushPose();
        poseStack.translate(0.5F, 1.5F, 0.5F);
        poseStack.rotateAround(Axis.XP.rotationDegrees(180.0F), 0.0F, 0.0F, 0.0F);
        submitNodeCollector.submitModel(
                MODEL,
                renderState,
                poseStack,
                RenderTypes.entityCutout(R.textures.model.ability_control_tablet),
                packedLight,
                packedOverlay,
                outlineColor,
                null
        );
        poseStack.popPose();
    }

    @Override
    public void getExtents(Consumer<Vector3fc> output) {
        var poseStack = new PoseStack();
        poseStack.translate(0.5F, 1.5F, 0.5F);
        poseStack.scale(1.0F, -1.0F, -1.0F);
        MODEL.root().getExtentsForGui(poseStack, output);
    }

    public record Unbaked() implements SpecialModelRenderer.Unbaked<AbilityControlTabletRenderState> {
        public static final Unbaked INSTANCE = new Unbaked();
        public static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(INSTANCE);

        @Override
        public SpecialModelRenderer<AbilityControlTabletRenderState> bake(SpecialModelRenderer.BakingContext context) {
            return AbilityControlTabletSpecialRenderer.INSTANCE;
        }

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }
    }
}
