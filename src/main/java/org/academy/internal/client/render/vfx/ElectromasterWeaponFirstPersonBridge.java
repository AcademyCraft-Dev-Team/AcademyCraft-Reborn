package org.academy.internal.client.render.vfx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.HumanoidArm;
import org.academy.api.client.renderer.EffectRenderer;
import org.academy.internal.common.attachment.AttachmentTypes;
import org.joml.Matrix4f;

import static org.academy.api.client.render.Render.RenderTypes.IRON_SAND_FIRST_PERSON;

public final class ElectromasterWeaponFirstPersonBridge implements EffectRenderer {
    public static final ElectromasterWeaponFirstPersonBridge INSTANCE =
            new ElectromasterWeaponFirstPersonBridge();

    private ElectromasterWeaponFirstPersonBridge() {
    }

    private static void renderFirstPersonSweep(PoseStack poseStack, SubmitNodeCollector collector,
                                               int packedLight, float handSide, float progress) {
        for (var i = 0; i < FirstPersonSweepGeometry.IRON_SAND_PARTICLES; i++) {
            var point = FirstPersonSweepGeometry.ironSandPosition(handSide, progress, i);
            var scale = FirstPersonSweepGeometry.ironSandScale(i);
            var alpha = FirstPersonSweepGeometry.ironSandAlpha(progress, i);
            if (alpha <= 0.001f) continue;

            poseStack.pushPose();
            poseStack.translate(point.x(), point.y(), point.z());
            poseStack.mulPose(Axis.ZP.rotationDegrees(-18.0f
                    + i / (float) (FirstPersonSweepGeometry.IRON_SAND_PARTICLES - 1) * 36.0f));
            poseStack.scale(scale, scale, scale);
            submitFirstPersonSandQuad(poseStack, collector, alpha);
            poseStack.popPose();
        }
    }

    private static void submitFirstPersonSandQuad(PoseStack poseStack,
                                                  SubmitNodeCollector collector, float alpha) {
        collector.submitCustomGeometry(
                poseStack,
                IRON_SAND_FIRST_PERSON,
                (pose, consumer) -> addFirstPersonQuad(consumer, pose.pose(), alpha)
        );
    }

    private static void addFirstPersonQuad(VertexConsumer consumer, Matrix4f matrix, float alpha) {
        firstPersonVertex(consumer, matrix, -1, -1, 0, 0, alpha);
        firstPersonVertex(consumer, matrix, 1, -1, 1, 0, alpha);
        firstPersonVertex(consumer, matrix, 1, 1, 1, 1, alpha);
        firstPersonVertex(consumer, matrix, -1, 1, 0, 1, alpha);
    }

    private static void firstPersonVertex(VertexConsumer consumer, Matrix4f matrix,
                                          float x, float y, float u, float v, float alpha) {
        consumer.addVertex(matrix, x, y, 0)
                .setColor(0.78f, 0.82f, 0.88f, alpha)
                .setUv(u, v);
    }

    @Override
    public void render(PoseStack poseStack, SubmitNodeCollector collector, int packedLight,
                       AvatarRenderState renderState, float yRot, float xRot) {
        // Third-person effects are rendered by ElectromasterWeaponVfx through the Vfx pipeline.
    }

    @Override
    public void renderFirstPerson(PoseStack poseStack, SubmitNodeCollector collector,
                                  LocalPlayer player, int packedLight, float partialTick) {
        var data = player.getData(AttachmentTypes.IRON_SAND_DATA.get());
        if (!data.active()) return;
        var animations = ElectromasterWeaponVfx.IRON_SAND_SWEEPS.entries(player.getId());
        if (animations.isEmpty()) return;

        var currentTick = (double) player.level().getGameTime() + partialTick;
        var handSide = player.getMainArm() == HumanoidArm.RIGHT ? 1.0f : -1.0f;
        for (var entry : animations) {
            var progress = SweepAnimationTimeline.progress(
                    entry, currentTick, ElectromasterWeaponVfx.SWEEP_DURATION_TICKS);
            if (progress < 0.0f || progress >= 1.0f) continue;
            renderFirstPersonSweep(poseStack, collector, packedLight, handSide, progress);
        }
    }

    @Override
    public boolean renderFirstPersonWhenHudHidden() {
        return true;
    }
}
