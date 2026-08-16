package org.academy.internal.client.renderer.special;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.academy.api.client.resources.R;
import org.academy.internal.client.world.item.ImagPhaseDowsingRodClient;
import org.academy.internal.common.world.item.Items;
import org.joml.Matrix4f;
import org.joml.Vector3fc;

import java.util.ArrayDeque;
import java.util.List;
import java.util.function.Consumer;

import static org.academy.internal.client.model.ImagPhaseDowsingRodModel.MODEL;

public final class ImagPhaseDowsingRodSpecialRenderer
        implements SpecialModelRenderer<ImagPhaseDowsingRodRenderState> {
    public static final ImagPhaseDowsingRodSpecialRenderer INSTANCE =
            new ImagPhaseDowsingRodSpecialRenderer();
    private static final float MAP_SCALE = 0.00065F;
    private static final float PIXEL_SIZE = MAP_SCALE * 16.0F;
    private static final float GRIP_X = 1.3F / 16.0F;
    private static final float GRIP_Y = 17.8F / 16.0F;
    private static final float THIRD_PERSON_SCALE = 0.475F;
    private static final float FIRST_PERSON_SCALE = 0.7125F;
    private static final ThreadLocal<ArrayDeque<RenderInvocation>> RENDER_QUEUE = new ThreadLocal<>();

    private ImagPhaseDowsingRodSpecialRenderer() {
    }

    public static void prepareItemRender(
            LivingEntity entity,
            ItemStack stack,
            ItemDisplayContext displayContext
    ) {
        var minecraft = Minecraft.getInstance();
        if (!stack.is(Items.IMAG_PHASE_DOWSING_ROD.get())) {
            RENDER_QUEUE.remove();
            return;
        }
        var showMap = entity == minecraft.player
                && displayContext != ItemDisplayContext.GUI
                && displayContext != ItemDisplayContext.FIXED
                && displayContext != ItemDisplayContext.GROUND;
        var queue = new ArrayDeque<RenderInvocation>(1);
        queue.add(new RenderInvocation(showMap, displayContext));
        RENDER_QUEUE.set(queue);
    }

    public static void prepareThirdPersonRender(LivingEntity entity) {
        var showMap = entity == Minecraft.getInstance().player;
        var queue = new ArrayDeque<RenderInvocation>(2);
        if (entity.getItemHeldByArm(HumanoidArm.RIGHT).is(Items.IMAG_PHASE_DOWSING_ROD.get())) {
            queue.add(new RenderInvocation(showMap, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND));
        }
        if (entity.getItemHeldByArm(HumanoidArm.LEFT).is(Items.IMAG_PHASE_DOWSING_ROD.get())) {
            queue.add(new RenderInvocation(showMap, ItemDisplayContext.THIRD_PERSON_LEFT_HAND));
        }
        if (queue.isEmpty()) RENDER_QUEUE.remove();
        else RENDER_QUEUE.set(queue);
    }

    @Override
    public ImagPhaseDowsingRodRenderState extractArgument(ItemStack stack) {
        var queue = RENDER_QUEUE.get();
        var invocation = queue == null ? null : queue.pollFirst();
        if (queue == null || queue.isEmpty()) RENDER_QUEUE.remove();
        return invocation == null
                ? new ImagPhaseDowsingRodRenderState(false, ItemDisplayContext.GUI)
                : new ImagPhaseDowsingRodRenderState(invocation.showMap(), invocation.displayContext());
    }

    @Override
    public void submit(
            ImagPhaseDowsingRodRenderState renderState,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            int packedLight,
            int packedOverlay,
            boolean hasFoil,
            int outlineColor
    ) {
        poseStack.pushPose();
        applyModelTransform(renderState.displayContext(), poseStack);
        collector.submitModel(
                MODEL,
                renderState,
                poseStack,
                RenderTypes.entityCutout(R.textures.IMAG_PHASE_DOWSING_ROD),
                packedLight,
                packedOverlay,
                outlineColor,
                null
        );
        poseStack.popPose();

        var targets = ImagPhaseDowsingRodClient.targetSections();
        if (renderState.showMap() && !targets.isEmpty()) submitMap(collector, targets);
    }

    private static void applyModelTransform(ItemDisplayContext displayContext, PoseStack poseStack) {
        if (isHeld(displayContext)) {
            poseStack.translate(0.5F, 0.5F, 0.5F);
            poseStack.mulPose(Axis.XP.rotationDegrees(180.0F));
            if (!displayContext.firstPerson()) {
                poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
            }
            var modelScale = displayContext.firstPerson()
                    ? FIRST_PERSON_SCALE
                    : THIRD_PERSON_SCALE;
            poseStack.scale(modelScale, modelScale, modelScale);
            poseStack.translate(-GRIP_X, -GRIP_Y, 0.0F);
            return;
        }

        poseStack.translate(0.5F, 1.5F, 0.5F);
        poseStack.rotateAround(Axis.XP.rotationDegrees(180.0F), 0.0F, 0.0F, 0.0F);
    }

    private static boolean isHeld(ItemDisplayContext displayContext) {
        return displayContext == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                || displayContext == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                || displayContext == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                || displayContext == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
    }

    private static void submitMap(SubmitNodeCollector collector, List<BlockPos> targets) {
        var player = Minecraft.getInstance().player;
        if (player == null) return;

        var mapPose = new PoseStack();
        mapPose.translate(0.0F, 0.0F, -0.65F);
        mapPose.mulPose(Axis.XP.rotationDegrees(player.getXRot()));
        mapPose.mulPose(Axis.YP.rotationDegrees(player.getYRot()));
        mapPose.scale(-1.0F, 1.0F, -1.0F);
        mapPose.translate(0.0F, -0.065F, 0.0F);
        var playerPosition = player.position();

        collector.submitCustomGeometry(
                mapPose,
                RenderTypes.debugFilledBox(),
                (pose, consumer) -> renderMap(consumer, pose.pose(), targets, playerPosition)
        );
    }

    private record RenderInvocation(boolean showMap, ItemDisplayContext displayContext) {
    }

    private static void renderMap(
            VertexConsumer consumer,
            Matrix4f matrix,
            List<BlockPos> targets,
            Vec3 playerPosition
    ) {
        addCuboid(
                consumer, matrix,
                -PIXEL_SIZE / 4.0F, 0.0F, -PIXEL_SIZE / 4.0F,
                PIXEL_SIZE / 2.0F, PIXEL_SIZE * 5.0F, PIXEL_SIZE / 2.0F,
                1.0F, 1.0F, 1.0F, 1.0F
        );

        for (var sectionOrigin : targets) {
            var relative = Vec3.atLowerCornerOf(sectionOrigin).subtract(playerPosition);
            var x = (float) relative.x() * MAP_SCALE;
            var y = (float) relative.y() * MAP_SCALE;
            var z = (float) relative.z() * MAP_SCALE;
            var red = 0.2F + Math.abs(sectionOrigin.getX() % 10) / 10.0F * 0.8F;
            var green = 0.2F + Math.abs(sectionOrigin.getY() % 10) / 10.0F * 0.8F;
            var blue = 0.2F + Math.abs(sectionOrigin.getZ() % 10) / 10.0F * 0.8F;
            var halfSize = PIXEL_SIZE / 2.0F;
            addCuboid(consumer, matrix, x, y, z, halfSize, halfSize, halfSize,
                    red, green, blue, 0.8F);
        }
    }

    private static void addCuboid(
            VertexConsumer consumer,
            Matrix4f matrix,
            float centerX,
            float centerY,
            float centerZ,
            float halfX,
            float halfY,
            float halfZ,
            float red,
            float green,
            float blue,
            float alpha
    ) {
        var x0 = centerX - halfX;
        var x1 = centerX + halfX;
        var y0 = centerY - halfY;
        var y1 = centerY + halfY;
        var z0 = centerZ - halfZ;
        var z1 = centerZ + halfZ;
        fillQuad(consumer, matrix, x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0, red, green, blue, alpha);
        fillQuad(consumer, matrix, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, red, green, blue, alpha);
        fillQuad(consumer, matrix, x0, y0, z0, x0, y1, z0, x0, y1, z1, x0, y0, z1, red, green, blue, alpha);
        fillQuad(consumer, matrix, x1, y0, z1, x1, y1, z1, x1, y1, z0, x1, y0, z0, red, green, blue, alpha);
        fillQuad(consumer, matrix, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, red, green, blue, alpha);
        fillQuad(consumer, matrix, x0, y0, z1, x0, y1, z1, x1, y1, z1, x1, y0, z1, red, green, blue, alpha);
    }

    private static void fillQuad(
            VertexConsumer consumer,
            Matrix4f matrix,
            float x0, float y0, float z0,
            float x1, float y1, float z1,
            float x2, float y2, float z2,
            float x3, float y3, float z3,
            float red, float green, float blue, float alpha
    ) {
        consumer.addVertex(matrix, x0, y0, z0).setColor(red, green, blue, alpha);
        consumer.addVertex(matrix, x1, y1, z1).setColor(red, green, blue, alpha);
        consumer.addVertex(matrix, x2, y2, z2).setColor(red, green, blue, alpha);
        consumer.addVertex(matrix, x3, y3, z3).setColor(red, green, blue, alpha);
    }

    @Override
    public void getExtents(Consumer<Vector3fc> output) {
        var poseStack = new PoseStack();
        poseStack.translate(0.5F, 1.5F, 0.5F);
        poseStack.scale(1.0F, -1.0F, -1.0F);
        MODEL.root().getExtentsForGui(poseStack, output);
    }

    public record Unbaked() implements SpecialModelRenderer.Unbaked<ImagPhaseDowsingRodRenderState> {
        public static final Unbaked INSTANCE = new Unbaked();
        public static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(INSTANCE);

        @Override
        public SpecialModelRenderer<ImagPhaseDowsingRodRenderState> bake(
                SpecialModelRenderer.BakingContext context
        ) {
            return ImagPhaseDowsingRodSpecialRenderer.INSTANCE;
        }

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }
    }
}
