package org.academy.internal.client.renderer.effect;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.util.context.ContextKey;
import org.academy.api.client.compatibility.IrisCompat;
import org.academy.api.client.renderer.EffectRenderer;
import org.academy.internal.common.ability.accelerator.skills.lv5.AdvancedWingSweepPacket.WingKind;
import org.academy.internal.common.attachment.AttachmentTypes;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.academy.AcademyCraft.academy;
import static org.academy.api.client.render.Render.RenderTypes.BLACK_WING;
import static org.academy.api.client.render.Render.RenderTypes.PLATINUM_WING;
import static org.academy.api.client.render.Render.RenderTypes.WHITE_WING;

public final class WingEffectRenderer implements EffectRenderer {
    public static final ContextKey<Integer> ENTITY_ID_CONTEXT = new ContextKey<>(academy("wing_entity_id"));
    public static final ContextKey<Boolean> BLACK_CONTEXT = new ContextKey<>(academy("black_wing"));
    public static final ContextKey<Boolean> WHITE_CONTEXT = new ContextKey<>(academy("white_wing"));
    public static final ContextKey<Boolean> PLATINUM_CONTEXT = new ContextKey<>(academy("platinum_wing"));
    public static final WingEffectRenderer BLACK = new WingEffectRenderer(
            WingKind.BLACK, BLACK_CONTEXT, BLACK_WING, 1.0f
    );
    public static final WingEffectRenderer WHITE = new WingEffectRenderer(
            WingKind.WHITE, WHITE_CONTEXT, WHITE_WING, 0.11f / 0.075f
    );
    public static final WingEffectRenderer PLATINUM = new WingEffectRenderer(
            WingKind.PLATINUM, PLATINUM_CONTEXT, PLATINUM_WING, 0.11f / 0.075f
    );
    private static final Matrix4f BASE_MATRIX = new Matrix4f()
            .rotateX((float) Math.toRadians(90.0f))
            .translate(0, 0.25f, 0);
    private static final int SWEEP_DURATION_TICKS = 10;
    private static final float SWEEP_PIVOT_SIDE = 0.18f;
    private static final float SWEEP_PIVOT_FORWARD = 0.18f;
    private static final float SWEEP_PIVOT_UPPER_Z = -0.22f;
    private static final float SWEEP_BASE_YAW = 12.0f;
    private static final float SWEEP_ARC_DEGREES = 168.0f;
    private static final float SWEEP_SCALE = 1.12f;
    private static final float FIRST_PERSON_FORWARD = 0.62f;
    private static final float FIRST_PERSON_SIDE = 0.58f;
    private static final float FIRST_PERSON_DOWN = -0.28f;
    private static final float TORNADO_OFFSET_LEFT = 0.0f;
    private static final float TORNADO_OFFSET_RIGHT = 20.0f;
    private static final Map<WingKind, Map<Integer, List<SweepAnimation>>> SWEEP_ANIMATIONS =
            new EnumMap<>(WingKind.class);

    static {
        for (var kind : WingKind.values()) SWEEP_ANIMATIONS.put(kind, new HashMap<>());
    }

    private final WingKind kind;
    private final ContextKey<Boolean> contextKey;
    private final RenderType renderType;
    private final float ringWidthScale;

    private WingEffectRenderer(WingKind kind, ContextKey<Boolean> contextKey,
                               RenderType renderType, float ringWidthScale) {
        this.kind = kind;
        this.contextKey = contextKey;
        this.renderType = renderType;
        this.ringWidthScale = ringWidthScale;
    }

    public static void enqueueSweep(WingKind kind, int entityId, boolean leftWing,
                                    float yawOffsetDeg, float pitchOffsetDeg) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        var entity = minecraft.level.getEntity(entityId);
        if (entity == null) return;
        var animation = new SweepAnimation(
                entity.tickCount,
                leftWing,
                yawOffsetDeg,
                pitchOffsetDeg
        );
        SWEEP_ANIMATIONS.get(kind)
                .computeIfAbsent(entityId, ignored -> new ArrayList<>())
                .add(animation);
    }

    @Override
    public void render(PoseStack poseStack, SubmitNodeCollector collector, int packedLight,
                       AvatarRenderState renderState, float yRot, float xRot) {
        if (IrisCompat.isShadowRendererActive()) return;
        if (!renderState.getRenderDataOrDefault(contextKey, false)) return;
        var entityId = renderState.getRenderDataOrDefault(ENTITY_ID_CONTEXT, -1);
        collector.submitCustomGeometry(poseStack, renderType, (pose, buffer) -> {
            var wingStack = new PoseStack();
            wingStack.last().set(pose);
            wingStack.mulPose(BASE_MATRIX);
            var time = renderState.ageInTicks;

            renderPersistentWings(wingStack, buffer, time);
            renderSweepAnimations(wingStack, buffer, entityId, time);
        });
    }

    @Override
    public void renderFirstPerson(PoseStack poseStack, SubmitNodeCollector collector,
                                  LocalPlayer player, int packedLight, float partialTick) {
        if (!isActive(player)) return;
        var animations = SWEEP_ANIMATIONS.get(kind).get(player.getId());
        if (animations == null || animations.isEmpty()) return;
        collector.submitCustomGeometry(poseStack, renderType, (pose, buffer) -> {
            var sweepStack = new PoseStack();
            sweepStack.last().set(pose);
            renderFirstPersonSweeps(
                    sweepStack,
                    buffer,
                    player,
                    player.tickCount + partialTick
            );
        });
    }

    private boolean isActive(LocalPlayer player) {
        return switch (kind) {
            case BLACK -> player.getData(AttachmentTypes.ACTIVATED_BLACK_WING.get());
            case WHITE -> player.getData(AttachmentTypes.ACTIVATED_WHITE_WING.get());
            case PLATINUM -> player.getData(AttachmentTypes.ACTIVATED_PLATINUM_WING.get());
        };
    }

    private void renderPersistentWings(PoseStack poseStack, VertexConsumer buffer, float time) {
        poseStack.pushPose();
        poseStack.mulPose(new Quaternionf()
                .rotateZ((float) Math.toRadians(30.0f))
                .rotateX((float) Math.toRadians(30.0f)));
        renderTornado(poseStack, buffer, time + TORNADO_OFFSET_LEFT);
        poseStack.popPose();

        poseStack.pushPose();
        poseStack.mulPose(new Quaternionf()
                .rotateZ((float) Math.toRadians(-30.0f))
                .rotateX((float) Math.toRadians(30.0f)));
        renderTornado(poseStack, buffer, time + TORNADO_OFFSET_RIGHT);
        poseStack.popPose();
    }

    private void renderSweepAnimations(PoseStack poseStack, VertexConsumer buffer,
                                       int entityId, float currentTick) {
        if (entityId < 0) return;
        var byEntity = SWEEP_ANIMATIONS.get(kind);
        var animations = byEntity.get(entityId);
        if (animations == null || animations.isEmpty()) return;
        for (var iterator = animations.iterator(); iterator.hasNext(); ) {
            var animation = iterator.next();
            var progress = (currentTick - animation.startTick) / SWEEP_DURATION_TICKS;
            if (progress >= 1.0f) {
                iterator.remove();
                continue;
            }
            if (progress < 0.0f) continue;
            var eased = 1.0f - (1.0f - progress) * (1.0f - progress);
            var side = animation.leftWing ? -1.0f : 1.0f;
            var sweepYaw = side * (SWEEP_BASE_YAW + SWEEP_ARC_DEGREES * eased)
                    + animation.yawOffsetDeg;

            poseStack.pushPose();
            poseStack.translate(side * SWEEP_PIVOT_SIDE, SWEEP_PIVOT_FORWARD, SWEEP_PIVOT_UPPER_Z);
            poseStack.mulPose(new Quaternionf().rotateZ((float) Math.toRadians(-sweepYaw)));
            poseStack.mulPose(new Quaternionf().rotateX((float) Math.toRadians(animation.pitchOffsetDeg)));
            poseStack.mulPose(new Quaternionf()
                    .rotateZ((float) Math.toRadians(animation.leftWing ? 30.0f : -30.0f))
                    .rotateX((float) Math.toRadians(30.0f)));
            poseStack.scale(SWEEP_SCALE, SWEEP_SCALE, SWEEP_SCALE);
            renderTornado(
                    poseStack,
                    buffer,
                    currentTick + (animation.leftWing ? TORNADO_OFFSET_LEFT : TORNADO_OFFSET_RIGHT)
            );
            poseStack.popPose();
        }
        if (animations.isEmpty()) byEntity.remove(entityId);
    }

    private void renderFirstPersonSweeps(PoseStack poseStack, VertexConsumer buffer,
                                         LocalPlayer player, float currentTick) {
        var byEntity = SWEEP_ANIMATIONS.get(kind);
        var animations = byEntity.get(player.getId());
        if (animations == null || animations.isEmpty()) return;
        for (var iterator = animations.iterator(); iterator.hasNext(); ) {
            var animation = iterator.next();
            var progress = (currentTick - animation.startTick) / SWEEP_DURATION_TICKS;
            if (progress >= 1.0f) {
                iterator.remove();
                continue;
            }
            if (progress < 0.0f) continue;
            var eased = 1.0f - (1.0f - progress) * (1.0f - progress);
            var side = animation.leftWing ? -1.0f : 1.0f;
            var sweepYaw = side * (SWEEP_BASE_YAW + SWEEP_ARC_DEGREES * (1.0f - eased))
                    + animation.yawOffsetDeg;

            poseStack.pushPose();
            poseStack.translate(side * FIRST_PERSON_SIDE, FIRST_PERSON_DOWN, -FIRST_PERSON_FORWARD);
            poseStack.mulPose(Axis.YP.rotationDegrees(-sweepYaw));
            poseStack.mulPose(Axis.XP.rotationDegrees(animation.pitchOffsetDeg));
            poseStack.mulPose(Axis.ZP.rotationDegrees(animation.leftWing ? 90.0f : -90.0f));
            poseStack.scale(SWEEP_SCALE, SWEEP_SCALE, SWEEP_SCALE);
            renderTornado(
                    poseStack,
                    buffer,
                    currentTick + (animation.leftWing ? TORNADO_OFFSET_LEFT : TORNADO_OFFSET_RIGHT)
            );
            poseStack.popPose();
        }
        if (animations.isEmpty()) byEntity.remove(player.getId());
    }

    private void renderTornado(PoseStack poseStack, VertexConsumer buffer, float time) {
        StormWingEffectRenderer.renderSingleTornado(poseStack, buffer, time, ringWidthScale);
    }

    private record SweepAnimation(float startTick, boolean leftWing,
                                  float yawOffsetDeg, float pitchOffsetDeg) {
    }
}
