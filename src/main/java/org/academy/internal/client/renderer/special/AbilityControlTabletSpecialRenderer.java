package org.academy.internal.client.renderer.special;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.academy.api.client.resources.R;
import org.academy.internal.common.world.item.Items;
import org.joml.Vector3fc;

import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;

import static org.academy.internal.client.model.AbilityControlTabletModel.MODEL;
import static org.academy.internal.client.model.AbilityControlTabletModel.SCREEN_MODEL;

public final class AbilityControlTabletSpecialRenderer implements SpecialModelRenderer<AbilityControlTabletRenderState> {
    public static final AbilityControlTabletSpecialRenderer INSTANCE = new AbilityControlTabletSpecialRenderer();
    private static final long OPEN_ANIMATION_MILLIS = 459L;
    private static final Map<LivingEntity, EnumMap<InteractionHand, HandAnimationState>> HAND_ANIMATIONS =
            new WeakHashMap<>();
    private static final ThreadLocal<ArrayDeque<Long>> CURRENT_ANIMATION_TIMES = new ThreadLocal<>();

    private AbilityControlTabletSpecialRenderer() {
    }

    public static void tickHeldItems() {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            HAND_ANIMATIONS.clear();
            return;
        }

        var now = System.nanoTime();
        for (var player : level.players()) {
            updateHandState(player, InteractionHand.MAIN_HAND, player.getMainHandItem(), now);
            updateHandState(player, InteractionHand.OFF_HAND, player.getOffhandItem(), now);
        }
    }

    public static void prepareItemRender(
            LivingEntity entity,
            ItemStack stack,
            ItemDisplayContext displayContext
    ) {
        var hand = resolveHand(entity, displayContext);
        if (hand == null) {
            CURRENT_ANIMATION_TIMES.remove();
            return;
        }

        var now = System.nanoTime();
        var state = updateHandState(entity, hand, stack, now);
        if (state.tabletSelected()) {
            var animationTimes = new ArrayDeque<Long>(1);
            animationTimes.add(animationTime(state, now));
            CURRENT_ANIMATION_TIMES.set(animationTimes);
        } else {
            CURRENT_ANIMATION_TIMES.remove();
        }
    }

    public static void prepareThirdPersonRender(LivingEntity entity) {
        var now = System.nanoTime();
        var animationTimes = new ArrayDeque<Long>(2);
        addThirdPersonAnimationTime(entity, HumanoidArm.RIGHT, now, animationTimes);
        addThirdPersonAnimationTime(entity, HumanoidArm.LEFT, now, animationTimes);
        if (animationTimes.isEmpty()) {
            CURRENT_ANIMATION_TIMES.remove();
        } else {
            CURRENT_ANIMATION_TIMES.set(animationTimes);
        }
    }

    @Override
    public AbilityControlTabletRenderState extractArgument(ItemStack stack) {
        var animationTimes = CURRENT_ANIMATION_TIMES.get();
        var animationTime = animationTimes != null ? animationTimes.pollFirst() : null;
        if (animationTimes == null || animationTimes.isEmpty()) CURRENT_ANIMATION_TIMES.remove();
        return new AbilityControlTabletRenderState(
                animationTime != null ? animationTime : OPEN_ANIMATION_MILLIS
        );
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
        submitNodeCollector.submitModel(
                SCREEN_MODEL,
                renderState,
                poseStack,
                RenderTypes.entityTranslucent(R.textures.model.ability_control_tablet),
                LightCoordsUtil.lightCoordsWithEmission(packedLight, 5),
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

    private static HandAnimationState updateHandState(
            LivingEntity entity,
            InteractionHand hand,
            ItemStack stack,
            long now
    ) {
        var tabletSelected = stack.is(Items.ABILITY_CONTROL_TABLET.get());
        var handStates = HAND_ANIMATIONS.computeIfAbsent(
                entity,
                ignored -> new EnumMap<>(InteractionHand.class)
        );
        var previous = handStates.get(hand);
        var animationStart = tabletSelected && (previous == null || !previous.tabletSelected())
                ? now
                : previous != null ? previous.animationStartNanos() : now;
        var current = new HandAnimationState(tabletSelected, animationStart);
        handStates.put(hand, current);
        return current;
    }

    private static InteractionHand resolveHand(LivingEntity entity, ItemDisplayContext displayContext) {
        var renderedArm = switch (displayContext) {
            case FIRST_PERSON_RIGHT_HAND, THIRD_PERSON_RIGHT_HAND -> HumanoidArm.RIGHT;
            case FIRST_PERSON_LEFT_HAND, THIRD_PERSON_LEFT_HAND -> HumanoidArm.LEFT;
            default -> null;
        };
        if (renderedArm == null) return null;
        return handForArm(entity, renderedArm);
    }

    private static void addThirdPersonAnimationTime(
            LivingEntity entity,
            HumanoidArm arm,
            long now,
            ArrayDeque<Long> animationTimes
    ) {
        var stack = entity.getItemHeldByArm(arm);
        if (!stack.is(Items.ABILITY_CONTROL_TABLET.get())) return;
        var state = updateHandState(entity, handForArm(entity, arm), stack, now);
        animationTimes.add(animationTime(state, now));
    }

    private static InteractionHand handForArm(LivingEntity entity, HumanoidArm arm) {
        return arm == entity.getMainArm() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
    }

    private static long animationTime(HandAnimationState state, long now) {
        return Math.min(
                OPEN_ANIMATION_MILLIS,
                Math.max(0L, (now - state.animationStartNanos()) / 1_000_000L)
        );
    }

    private record HandAnimationState(boolean tabletSelected, long animationStartNanos) {
    }
}
