package org.academy.internal.client.renderer.effect;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
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

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.academy.AcademyCraft.academy;
import static org.academy.api.client.render.Render.RenderTypes.BLACK_WING;
import static org.academy.api.client.render.Render.RenderTypes.BLACK_WING_FIRST_PERSON;
import static org.academy.api.client.render.Render.RenderTypes.PLATINUM_WING;
import static org.academy.api.client.render.Render.RenderTypes.PLATINUM_WING_BYPASS;
import static org.academy.api.client.render.Render.RenderTypes.PLATINUM_WING_FIRST_PERSON;
import static org.academy.api.client.render.Render.RenderTypes.WHITE_WING;
import static org.academy.api.client.render.Render.RenderTypes.WHITE_WING_FIRST_PERSON;

public final class WingEffectRenderer implements EffectRenderer {
    public static final ContextKey<Integer> ENTITY_ID_CONTEXT = new ContextKey<>(academy("wing_entity_id"));
    public static final ContextKey<Boolean> BLACK_CONTEXT = new ContextKey<>(academy("black_wing"));
    public static final ContextKey<Boolean> WHITE_CONTEXT = new ContextKey<>(academy("white_wing"));
    public static final ContextKey<Boolean> PLATINUM_CONTEXT = new ContextKey<>(academy("platinum_wing"));
    public static final WingEffectRenderer BLACK = new WingEffectRenderer(
            WingKind.BLACK, BLACK_CONTEXT, BLACK_WING, BLACK_WING_FIRST_PERSON, 1.0f
    );
    public static final WingEffectRenderer WHITE = new WingEffectRenderer(
            WingKind.WHITE, WHITE_CONTEXT, WHITE_WING, WHITE_WING_FIRST_PERSON, 0.11f / 0.075f
    );
    public static final WingEffectRenderer PLATINUM = new WingEffectRenderer(
            WingKind.PLATINUM, PLATINUM_CONTEXT, PLATINUM_WING, PLATINUM_WING_FIRST_PERSON,
            0.11f / 0.075f
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
    private static final float TORNADO_OFFSET_LEFT = 0.0f;
    private static final float TORNADO_OFFSET_RIGHT = 20.0f;
    private static final Map<WingKind, SweepAnimationTimeline<SweepAnimation>> SWEEP_ANIMATIONS =
            new EnumMap<>(WingKind.class);
    private static ClientLevel animationLevel;

    static {
        for (var kind : WingKind.values()) SWEEP_ANIMATIONS.put(kind, new SweepAnimationTimeline<>());
    }

    private final WingKind kind;
    private final ContextKey<Boolean> contextKey;
    private final RenderType renderType;
    private final RenderType firstPersonRenderType;
    private final float ringWidthScale;

    private WingEffectRenderer(WingKind kind, ContextKey<Boolean> contextKey,
                               RenderType renderType, RenderType firstPersonRenderType,
                               float ringWidthScale) {
        this.kind = kind;
        this.contextKey = contextKey;
        this.renderType = renderType;
        this.firstPersonRenderType = firstPersonRenderType;
        this.ringWidthScale = ringWidthScale;
    }

    public static void enqueueSweep(WingKind kind, int entityId, boolean leftWing,
                                    float yawOffsetDeg, float pitchOffsetDeg) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        if (animationLevel != minecraft.level) {
            clearSweeps();
            animationLevel = minecraft.level;
        }
        var entity = minecraft.level.getEntity(entityId);
        if (entity == null) return;
        SWEEP_ANIMATIONS.get(kind).enqueue(
                entityId,
                minecraft.level.getGameTime(),
                new SweepAnimation(leftWing, yawOffsetDeg, pitchOffsetDeg)
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
        var currentTick = (double) minecraft.level.getGameTime();
        for (var timeline : SWEEP_ANIMATIONS.values()) {
            timeline.prune(currentTick, SWEEP_DURATION_TICKS,
                    entityId -> minecraft.level.getEntity(entityId) != null);
        }
    }

    public static void clearSweeps() {
        for (var timeline : SWEEP_ANIMATIONS.values()) timeline.clear();
        animationLevel = null;
        PlatinumCosmosPass.clear();
    }

    @Override
    public void render(PoseStack poseStack, SubmitNodeCollector collector, int packedLight,
                       AvatarRenderState renderState, float yRot, float xRot) {
        if (IrisCompat.isShadowRendererActive()) return;
        if (!renderState.getRenderDataOrDefault(contextKey, false)) return;
        var entityId = renderState.getRenderDataOrDefault(ENTITY_ID_CONTEXT, -1);
        var minecraft = Minecraft.getInstance();
        var currentTick = minecraft.level == null
                ? renderState.ageInTicks
                : (double) minecraft.level.getGameTime() + renderState.partialTick;
        if (kind == WingKind.PLATINUM) {
            var mode = PlatinumCosmosPass.worldMode();
            if (mode == PlatinumCosmosRenderMode.EXACT) {
                PlatinumCosmosPass.enqueueThirdPerson(
                        poseStack, entityId, currentTick, renderState.ageInTicks
                );
                return;
            }
            submitThirdPersonGeometry(
                    poseStack, collector,
                    mode == PlatinumCosmosRenderMode.FALLBACK ? WHITE_WING : renderType,
                    entityId, currentTick, renderState.ageInTicks
            );
            return;
        }
        submitThirdPersonGeometry(
                poseStack, collector, renderType, entityId, currentTick, renderState.ageInTicks
        );
    }

    private void submitThirdPersonGeometry(
            PoseStack poseStack, SubmitNodeCollector collector, RenderType activeRenderType,
            int entityId, double currentTick, float effectTime
    ) {
        collector.submitCustomGeometry(poseStack, activeRenderType, (pose, buffer) -> {
            var wingStack = new PoseStack();
            wingStack.last().set(pose);
            wingStack.mulPose(BASE_MATRIX);
            renderPersistentWings(wingStack, buffer, effectTime);
            renderSweepAnimations(wingStack, buffer, entityId, currentTick, effectTime);
        });
    }

    void submitThirdPersonCosmos(
            PoseStack poseStack, SubmitNodeCollector collector,
            int entityId, double currentTick, float effectTime
    ) {
        submitThirdPersonGeometry(
                poseStack, collector, PLATINUM_WING_BYPASS,
                entityId, currentTick, effectTime
        );
    }

    @Override
    public void renderFirstPerson(PoseStack poseStack, SubmitNodeCollector collector,
                                  LocalPlayer player, int packedLight, float partialTick) {
        if (kind == WingKind.PLATINUM) {
            var mode = PlatinumCosmosPass.handMode();
            if (mode == PlatinumCosmosRenderMode.EXACT) return;
            if (mode == PlatinumCosmosRenderMode.FALLBACK) {
                IrisCompat.warnHandBridgeFallback();
                submitFirstPersonGeometry(
                        poseStack, collector, player, partialTick, WHITE_WING_FIRST_PERSON
                );
                return;
            }
        }
        submitFirstPersonGeometry(
                poseStack, collector, player, partialTick, firstPersonRenderType
        );
    }

    private boolean submitFirstPersonGeometry(
            PoseStack poseStack, SubmitNodeCollector collector, LocalPlayer player,
            float partialTick, RenderType activeRenderType
    ) {
        if (!isActive(player)) return false;
        var animations = SWEEP_ANIMATIONS.get(kind).entries(player.getId());
        if (animations.isEmpty()) return false;
        var currentTick = (double) player.level().getGameTime() + partialTick;
        collector.submitCustomGeometry(poseStack, activeRenderType,
                (pose, buffer) -> {
                    var projectionStack = new PoseStack();
                    projectionStack.last().set(pose);
                    renderFirstPersonSweeps(
                            projectionStack, buffer, animations, currentTick,
                            player.tickCount + partialTick
                    );
                });
        return true;
    }

    boolean submitFirstPersonCosmos(
            PoseStack poseStack, SubmitNodeCollector collector, LocalPlayer player,
            int packedLight, float partialTick
    ) {
        return submitFirstPersonGeometry(
                poseStack, collector, player, partialTick, PLATINUM_WING_BYPASS
        );
    }

    boolean submitFirstPersonFallback(
            PoseStack poseStack, SubmitNodeCollector collector, LocalPlayer player,
            int packedLight, float partialTick
    ) {
        return submitFirstPersonGeometry(
                poseStack, collector, player, partialTick, WHITE_WING_FIRST_PERSON
        );
    }

    @Override
    public boolean renderFirstPersonWhenHudHidden() {
        return kind != WingKind.PLATINUM || !IrisCompat.isShaderPackInUse();
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
                                       int entityId, double currentTick, float effectTime) {
        if (entityId < 0) return;
        var animations = SWEEP_ANIMATIONS.get(kind).entries(entityId);
        for (var entry : animations) {
            var progress = SweepAnimationTimeline.progress(entry, currentTick, SWEEP_DURATION_TICKS);
            if (progress < 0.0f || progress >= 1.0f) continue;
            var animation = entry.payload();
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
                    effectTime + (animation.leftWing ? TORNADO_OFFSET_LEFT : TORNADO_OFFSET_RIGHT)
            );
            poseStack.popPose();
        }
    }

    private void renderFirstPersonSweeps(PoseStack poseStack, VertexConsumer buffer,
                                         List<SweepAnimationTimeline.Entry<SweepAnimation>> animations,
                                         double currentTick, float effectTime) {
        for (var entry : animations) {
            var progress = SweepAnimationTimeline.progress(entry, currentTick, SWEEP_DURATION_TICKS);
            if (progress < 0.0f || progress >= 1.0f) continue;
            var animation = entry.payload();
            var projection = FirstPersonSweepGeometry.wingProjection(animation.leftWing, progress);
            if (projection.alpha() <= 0.001f) continue;

            poseStack.pushPose();
            poseStack.translate(projection.rootX(), projection.rootY(), projection.rootZ());
            poseStack.mulPose(new Quaternionf()
                    .rotateZ((float) Math.toRadians(projection.sweepDegrees()))
                    .rotateX((float) Math.toRadians(projection.tiltDegrees())));
            poseStack.scale(projection.scale(), projection.scale(), projection.scale());
            renderTornado(
                    poseStack,
                    buffer,
                    effectTime + (animation.leftWing ? TORNADO_OFFSET_LEFT : TORNADO_OFFSET_RIGHT),
                    projection.alpha() * 0.92f
            );
            poseStack.popPose();
        }
    }

    private void renderTornado(PoseStack poseStack, VertexConsumer buffer, float time) {
        StormWingEffectRenderer.renderSingleTornado(poseStack, buffer, time, ringWidthScale);
    }

    private void renderTornado(PoseStack poseStack, VertexConsumer buffer, float time, float alpha) {
        StormWingEffectRenderer.renderSingleTornado(poseStack, buffer, time, ringWidthScale, alpha);
    }

    private record SweepAnimation(boolean leftWing, float yawOffsetDeg, float pitchOffsetDeg) {
    }
}
