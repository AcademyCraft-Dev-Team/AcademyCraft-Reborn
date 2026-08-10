package org.academy.internal.client.renderer.effect;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import org.academy.api.client.renderer.EffectRenderer;
import org.academy.api.client.resources.R;
import org.academy.internal.common.ability.electromaster.skills.lv3.MagneticWeapon;
import org.academy.internal.common.ability.electromaster.skills.lv4.IronSandArsenal;
import org.academy.internal.common.attachment.AttachmentTypes;
import org.joml.Matrix4f;

import java.util.List;

import static org.academy.AcademyCraft.academy;
import static org.academy.api.client.render.Render.RenderTypes.IRON_SAND_FIRST_PERSON;

public final class ElectromasterWeaponEffectRenderer implements EffectRenderer {
    public static final ElectromasterWeaponEffectRenderer INSTANCE =
            new ElectromasterWeaponEffectRenderer();
    public static final ContextKey<MagneticWeapon.Data> MAGNETIC_CONTEXT =
            new ContextKey<>(academy("magnetic_weapon"));
    public static final ContextKey<IronSandArsenal.Data> IRON_SAND_CONTEXT =
            new ContextKey<>(academy("iron_sand_operation"));
    public static final int SWEEP_DURATION_TICKS = 10;
    private static final SweepAnimationTimeline<SweepMarker> IRON_SAND_SWEEPS =
            new SweepAnimationTimeline<>();
    private static ClientLevel animationLevel;

    private ElectromasterWeaponEffectRenderer() {
    }

    public static void enqueueIronSandSweep(int entityId) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.level.getEntity(entityId) == null) return;
        if (animationLevel != minecraft.level) {
            clearSweeps();
            animationLevel = minecraft.level;
        }
        IRON_SAND_SWEEPS.enqueue(
                entityId,
                minecraft.level.getGameTime(),
                SweepMarker.INSTANCE
        );
    }

    public static void clientTick() {
        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            clearSweeps();
            return;
        }
        if (animationLevel != minecraft.level) {
            clearSweeps();
            animationLevel = minecraft.level;
            return;
        }
        IRON_SAND_SWEEPS.prune(
                minecraft.level.getGameTime(),
                SWEEP_DURATION_TICKS,
                entityId -> minecraft.level.getEntity(entityId) != null
        );
    }

    public static void clearSweeps() {
        IRON_SAND_SWEEPS.clear();
        animationLevel = null;
    }

    private static void renderIronSand(
            PoseStack poseStack,
            SubmitNodeCollector collector,
            int packedLight,
            float effectTime,
            double currentTick,
            List<SweepAnimationTimeline.Entry<SweepMarker>> animations
    ) {
        for (var i = 0; i < 12; i++) {
            var angle = effectTime * 0.075f + i * (float) (Math.PI * 2.0 / 12.0);
            var radius = 0.72f + (i % 3) * 0.13f;
            var y = -0.25f - (i % 4) * 0.43f;
            poseStack.pushPose();
            poseStack.translate(Math.cos(angle) * radius, y, Math.sin(angle) * radius);
            poseStack.mulPose(Axis.YP.rotationDegrees(-effectTime * 5.0f + i * 31.0f));
            poseStack.scale(0.22f, 0.22f, 0.22f);
            submitSandQuad(poseStack, collector, packedLight, 0.72f);
            poseStack.popPose();
        }

        for (var entry : animations) {
            var progress = SweepAnimationTimeline.progress(entry, currentTick, SWEEP_DURATION_TICKS);
            if (progress < 0.0f || progress >= 1.0f) continue;
            renderThirdPersonSweep(poseStack, collector, packedLight, progress);
        }
    }

    private static void renderThirdPersonSweep(PoseStack poseStack, SubmitNodeCollector collector,
                                               int packedLight, float progress) {
        var eased = progress * progress * (3.0f - 2.0f * progress);
        var sweepAngle = -60.0f + eased * 120.0f;
        var segments = 24;
        for (var i = 0; i < segments; i++) {
            var radialProgress = i / (float) (segments - 1);
            var radius = 0.8f + radialProgress * 11.2f;
            var trailingAngle = (1.0f - radialProgress) * 24.0f;
            var angle = Math.toRadians(sweepAngle - trailingAngle);
            poseStack.pushPose();
            poseStack.translate(Math.sin(angle) * radius,
                    -1.0 + Math.sin(progress * Math.PI) * 0.16 + Math.sin(i * 0.72) * 0.06,
                    -Math.cos(angle) * radius);
            poseStack.mulPose(Axis.YP.rotationDegrees((float) Math.toDegrees(angle) + 90.0f));
            poseStack.mulPose(Axis.ZP.rotationDegrees(-18.0f + radialProgress * 36.0f));
            var scale = 0.24f + radialProgress * 0.34f;
            poseStack.scale(scale, scale, scale);
            submitSandQuad(
                    poseStack,
                    collector,
                    packedLight,
                    (0.9f - radialProgress * 0.18f) * (1.0f - progress * 0.22f)
            );
            poseStack.popPose();
        }
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

    private static void submitSandQuad(PoseStack poseStack, SubmitNodeCollector collector,
                                       int packedLight, float alpha) {
        collector.submitCustomGeometry(
                poseStack,
                RenderTypes.entityTranslucent(R.textures.iron_sand_arsenal_effect),
                (pose, consumer) -> {
                    addLitQuad(consumer, pose.pose(), packedLight, alpha, false);
                    addLitQuad(consumer, pose.pose(), packedLight, alpha, true);
                }
        );
    }

    private static void submitFirstPersonSandQuad(PoseStack poseStack,
                                                  SubmitNodeCollector collector, float alpha) {
        collector.submitCustomGeometry(
                poseStack,
                IRON_SAND_FIRST_PERSON,
                (pose, consumer) -> addFirstPersonQuad(consumer, pose.pose(), alpha)
        );
    }

    private static void addLitQuad(VertexConsumer consumer, Matrix4f matrix, int light,
                                   float alpha, boolean reverse) {
        if (reverse) {
            litVertex(consumer, matrix, 1, -1, 1, 0, light, alpha);
            litVertex(consumer, matrix, -1, -1, 0, 0, light, alpha);
            litVertex(consumer, matrix, -1, 1, 0, 1, light, alpha);
            litVertex(consumer, matrix, 1, 1, 1, 1, light, alpha);
        } else {
            litVertex(consumer, matrix, -1, -1, 0, 0, light, alpha);
            litVertex(consumer, matrix, 1, -1, 1, 0, light, alpha);
            litVertex(consumer, matrix, 1, 1, 1, 1, light, alpha);
            litVertex(consumer, matrix, -1, 1, 0, 1, light, alpha);
        }
    }

    private static void addFirstPersonQuad(VertexConsumer consumer, Matrix4f matrix, float alpha) {
        firstPersonVertex(consumer, matrix, -1, -1, 0, 0, alpha);
        firstPersonVertex(consumer, matrix, 1, -1, 1, 0, alpha);
        firstPersonVertex(consumer, matrix, 1, 1, 1, 1, alpha);
        firstPersonVertex(consumer, matrix, -1, 1, 0, 1, alpha);
    }

    private static void litVertex(VertexConsumer consumer, Matrix4f matrix, float x, float y,
                                  float u, float v, int light, float alpha) {
        consumer.addVertex(matrix, x, y, 0)
                .setColor(0.78f, 0.82f, 0.88f, alpha)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(0, 0, 1);
    }

    private static void firstPersonVertex(VertexConsumer consumer, Matrix4f matrix,
                                          float x, float y, float u, float v, float alpha) {
        consumer.addVertex(matrix, x, y, 0)
                .setColor(0.78f, 0.82f, 0.88f, alpha)
                .setUv(u, v);
    }

    @Override
    public void render(PoseStack poseStack, SubmitNodeCollector collector, int packedLight,
                       AvatarRenderState state, float yRot, float xRot) {
        var ironSand = state.getRenderDataOrDefault(IRON_SAND_CONTEXT, IronSandArsenal.Data.DEFAULT);
        var minecraft = Minecraft.getInstance();
        var entityId = state.getRenderDataOrDefault(WingEffectRenderer.ENTITY_ID_CONTEXT, -1);
        var source = minecraft.level == null ? null : minecraft.level.getEntity(entityId);
        if (!(source instanceof Player) || !ironSand.active()) return;

        var currentTick = (double) minecraft.level.getGameTime() + state.partialTick;
        renderIronSand(
                poseStack,
                collector,
                packedLight,
                state.ageInTicks,
                currentTick,
                IRON_SAND_SWEEPS.entries(entityId)
        );
    }

    @Override
    public void renderFirstPerson(PoseStack poseStack, SubmitNodeCollector collector,
                                  LocalPlayer player, int packedLight, float partialTick) {
        var data = player.getData(AttachmentTypes.IRON_SAND_DATA.get());
        if (!data.active()) return;
        var animations = IRON_SAND_SWEEPS.entries(player.getId());
        if (animations.isEmpty()) return;

        var currentTick = (double) player.level().getGameTime() + partialTick;
        var handSide = player.getMainArm() == HumanoidArm.RIGHT ? 1.0f : -1.0f;
        for (var entry : animations) {
            var progress = SweepAnimationTimeline.progress(entry, currentTick, SWEEP_DURATION_TICKS);
            if (progress < 0.0f || progress >= 1.0f) continue;
            renderFirstPersonSweep(poseStack, collector, packedLight, handSide, progress);
        }
    }

    @Override
    public boolean renderFirstPersonWhenHudHidden() {
        return true;
    }

    private enum SweepMarker {
        INSTANCE
    }
}
